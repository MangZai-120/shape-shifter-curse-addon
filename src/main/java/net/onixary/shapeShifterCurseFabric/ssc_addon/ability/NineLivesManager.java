/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.TrinketUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 朔望「九命」核心管理器（服务端权威）。
 * <p>
 * 机制：<b>被动死亡触发</b>——致死伤害时若剩余命数 &gt; 0，消耗 1 命并原地复活
 * （回 6 血 + 6 颗黄心吸收 30s + 1s 无敌 + 全员可闻的复活音效/特效）；触发后 3s 内再死亡不触发（真死）。
 * 命数上限 9；<b>脱离战斗</b>（10s 未受伤/未攻击）后每 20s 回 1 命；真死重生后回满 9 命。
 * 全部判定在服务端，多人一致。
 */
public final class NineLivesManager {
    private static final int MAX_LIVES = 8;
    private static final int REGEN_INTERVAL = 400;      // 脱战每 20s 回 1 命
    private static final int OUT_OF_COMBAT_TICKS = 200; // 10s 未战斗算脱战
    private static final int REVIVE_CD_TICKS = 60;      // 复活后 3s 内不再触发（真死窗口）
    private static final int INVULN_TICKS = 20;         // 复活后 1s 无敌
    private static final float REVIVE_HEAL = 6.0f;      // 复活回血
    private static final int ABSORB_DURATION = 600;     // 6 颗黄心吸收持续 30s
    private static final int ABSORB_AMPLIFIER = 2;      // Absorption III = 6 颗黄心
    private static final float REVIVE_HEAL_NECKLACE = 8.0f; // 戴朔望专属项链：复活回血 8
    private static final int INVULN_TICKS_NECKLACE = 36;    // 戴朔望专属项链：复活无敌 1.8s

    private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> REVIVE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> INVULN_END = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> REGEN_ACC = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> RESPAWN_REFILL = new ConcurrentHashMap<>();

    private NineLivesManager() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(p);
            }
        });
        // 真死重生后回满 9 命（延迟到 tick 里设，确保重生后 power 已授予）
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                RESPAWN_REFILL.put(newPlayer.getUuid(), Boolean.TRUE));
    }

    /** 标记进入战斗（受伤或攻击时调用）。 */
    public static void markCombat(ServerPlayerEntity player) {
        LAST_COMBAT.put(player.getUuid(), player.getWorld().getTime());
    }

    /** 是否脱离战斗（超过 10s 未战斗）。 */
    public static boolean isOutOfCombat(ServerPlayerEntity player) {
        long last = LAST_COMBAT.getOrDefault(player.getUuid(), Long.MIN_VALUE / 2);
        return player.getWorld().getTime() - last > OUT_OF_COMBAT_TICKS;
    }

    /** 复活后无敌窗口（此期间 mixin 取消一切伤害）；时长由复活时是否戴朔望专属项链决定（1s / 1.8s）。 */
    public static boolean isInvulnerable(ServerPlayerEntity player) {
        long end = INVULN_END.getOrDefault(player.getUuid(), Long.MIN_VALUE / 2);
        return player.getWorld().getTime() < end;
    }

    /**
     * 致死伤害时尝试用九命复活。返回 true 表示已复活（调用方应取消本次死亡）。
     */
    public static boolean tryRevive(ServerPlayerEntity player) {
        long now = player.getWorld().getTime();
        long rev = REVIVE_TICK.getOrDefault(player.getUuid(), Long.MIN_VALUE / 2);
        if (now - rev < REVIVE_CD_TICKS) {
            return false; // 复活后 3s cd 内不触发（真死）
        }
        int lives = PowerUtils.getResourceValue(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES);
        if (lives <= 0) {
            return false; // 无命可用，真死
        }
        doRevive(player);
        return true;
    }

    /**
     * 舍身自爆专用复活：绕过复活 cd（保证「必定复活」），仅要求尚有命数。
     * 供 {@link NovaSkillManager} 引爆时对自己调用——自己也被炸倒，但九命必定接住，净消耗 1 命。
     */
    public static void reviveForSelfDetonate(ServerPlayerEntity player) {
        int lives = PowerUtils.getResourceValue(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES);
        if (lives <= 0) {
            return; // 发动门控已保证有命；万一无命则不复活（也不真死，自己不受本次自爆影响）
        }
        doRevive(player);
    }

    /**
     * 复活核心：消耗 1 命 + 回血 + 6 颗黄心 30s + 无敌 + 全员可闻的图腾音效/特效。
     * 戴朔望专属项链「轮回猫瞳」时：回血 6→8、无敌 1s→1.8s，并在复活瞬间震退+减速周围敌人。
     */
    private static void doRevive(ServerPlayerEntity player) {
        boolean hasNecklace = TrinketUtils.isWearing(player, net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.NOVA_REVIVE_NECKLACE);
        float heal = hasNecklace ? REVIVE_HEAL_NECKLACE : REVIVE_HEAL;
        int invuln = hasNecklace ? INVULN_TICKS_NECKLACE : INVULN_TICKS;
        long now = player.getWorld().getTime();
        PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES, -1);
        player.setHealth(heal);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, ABSORB_DURATION, ABSORB_AMPLIFIER, false, false, true));
        REVIVE_TICK.put(player.getUuid(), now);
        INVULN_END.put(player.getUuid(), now + invuln);
        markCombat(player);
        // 复活音效：全员可闻（第一参 null），音量适中（0.6）避免过响
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6F, 1.2F);
        if (player.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.6, 0.4, 0.2);
            if (hasNecklace) {
                reviveBurst(player, sw);
            }
        }
    }

    /** 朔望专属项链专属：复活瞬间对周围 4 格内非白名单敌人震退 + 减速，给自己喘息空间。 */
    private static void reviveBurst(ServerPlayerEntity player, ServerWorld sw) {
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(4.0), e -> e != player && e.isAlive())) {
            if (WhitelistUtils.isProtected(player, e)) {
                continue;
            }
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            if (dx * dx + dz * dz < 1.0e-4) {
                dx = player.getRotationVector().x;
                dz = player.getRotationVector().z;
            }
            e.takeKnockback(0.8, -dx, -dz);
            e.velocityModified = true;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, true, true));
        }
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.5, player.getZ(), 8, 1.5, 0.3, 1.5, 0.0);
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.8F);
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        boolean isNova = FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA);
        // 重生回满 9 命
        if (Boolean.TRUE.equals(RESPAWN_REFILL.get(player.getUuid()))) {
            if (isNova && PowerUtils.hasResource(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES, 0)) {
                PowerUtils.setResourceValueAndSync(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES, MAX_LIVES);
                RESPAWN_REFILL.remove(player.getUuid());
            } else if (!isNova) {
                RESPAWN_REFILL.remove(player.getUuid());
            }
        }
        if (!isNova || !PowerUtils.hasResource(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES, 0)) {
            REGEN_ACC.remove(player.getUuid());
            return;
        }
        int lives = PowerUtils.getResourceValue(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES);
        if (lives >= MAX_LIVES) {
            REGEN_ACC.put(player.getUuid(), 0);
            return;
        }
        if (!isOutOfCombat(player)) {
            return; // 战斗中不恢复
        }
        int acc = REGEN_ACC.getOrDefault(player.getUuid(), 0) + 1;
        if (acc >= REGEN_INTERVAL) {
            PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES, 1);
            acc = 0;
        }
        REGEN_ACC.put(player.getUuid(), acc);
    }
}
