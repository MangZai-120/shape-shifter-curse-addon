/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import org.joml.Vector3f;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 朔望「九命灵猫」主/次主动技能 + 闪避 管理器（服务端权威）。
 * <p>
 * 三套系统：
 * <ul>
 *   <li><b>闪避</b>：基础被动闪避 {@link #BASE_DODGE}，灵跃每次 +{@link #DODGE_PER_LEAP}（限时 {@link #DODGE_DURATION}）。
 *       受伤时按当前几率概率免疫（不受伤、不击退）——由 SscAddonLivingEntityMixin 调用 {@link #rollDodge}。</li>
 *   <li><b>灵跃闪身</b>（次技能 sp_secondary）：向准星跳冲，空中可用，可连用 2 次（第 1→2 次窗口 {@link #LEAP_WINDOW}）；
 *       每次 +20% 闪避；用满 2 次 cd {@link #LEAP_CD}，只用 1 次且超时 cd {@link #LEAP_CD_SHORT}。</li>
 *   <li><b>舍身爆炸</b>（主技能 sp_primary）：蓄力 {@link #CHARGE_TIME}（减速 70% + 抗性 III + TNT 音效 + 黑烟粒子），
 *       蓄满自爆——消耗 1 九命，致命半径 {@link #LETHAL_RADIUS} 伤害 {@link #MAX_DAMAGE}，最远 {@link #MAX_RADIUS} 随距离衰减，
 *       不破坏方块、无友伤（白名单/宠物免伤）、归属玩家；cd {@link #EXPLODE_CD}。</li>
 * </ul>
 * 触发接线：sp_primary 按键 → {@link #startCharge}；sp_secondary 按键 → {@link #tryLeap}。
 * 需在 power JSON（apoli:active_self, key.ssc_addon.sp_primary/secondary）或按键 C2S 包里调用本类方法。
 */
public final class NovaSkillManager {
    // 闪避
    private static final float BASE_DODGE = 0.15f;      // 基础被动闪避 15%
    private static final float DODGE_PER_LEAP = 0.20f;  // 灵跃每次 +20%
    private static final int DODGE_DURATION = 60;       // 灵跃闪避加成持续 3s
    private static final float DODGE_CAP = 0.85f;       // 闪避几率上限，避免完全无敌
    // 灵跃闪身
    private static final int LEAP_WINDOW = 100;         // 第 1→2 次窗口 5s
    private static final int LEAP_CD = 200;             // 用满 2 次 cd 10s
    private static final int LEAP_CD_SHORT = 120;       // 只用 1 次超时 cd 6s
    private static final double LEAP_POWER = 1.2;       // 跳冲水平速度
    // 舍身爆炸
    private static final int CHARGE_TIME = 100;         // 蓄力 5s
    private static final int EXPLODE_CD = 600;          // cd 30s
    private static final int LETHAL_RADIUS = 5;         // 致命半径 5 格
    private static final int MAX_RADIUS = 12;           // 最远半径 12 格
    private static final float MAX_DAMAGE = 50.0f;      // 致命伤害 50
    // 蓄力期代码减速：GENERIC_MOVEMENT_SPEED MULTIPLY_TOTAL -0.70 = 精确减速 70%（非药水缓慢，可精确到 70%）
    private static final UUID CHARGE_SLOW_UUID = UUID.fromString("9f3c1e5a-7b2d-4c8e-a1f6-0d9e8c7b6a54");
    private static final double CHARGE_SLOW_AMOUNT = -0.70;

    private static final Map<UUID, Float> DODGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DODGE_EXPIRE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LEAP_COUNT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LEAP_FIRST = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LEAP_CD_END = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CHARGE_START = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> EXPLODE_CD_END = new ConcurrentHashMap<>();

    private NovaSkillManager() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(p);
            }
        });
    }

    // ==== 闪避 ====

    /** 受伤时判定是否闪避（由 damage mixin 调用）。返回 true = 免疫本次伤害。 */
    public static boolean rollDodge(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) return false;
        float chance = BASE_DODGE;
        Float extra = DODGE.get(player.getUuid());
        Long exp = DODGE_EXPIRE.get(player.getUuid());
        if (extra != null && exp != null && player.getWorld().getTime() <= exp) {
            chance += extra;
        }
        chance = Math.min(DODGE_CAP, chance);
        return player.getRandom().nextFloat() < chance;
    }

    private static void addDodge(ServerPlayerEntity player) {
        float cur = DODGE.getOrDefault(player.getUuid(), 0f);
        DODGE.put(player.getUuid(), Math.min(DODGE_CAP, cur + DODGE_PER_LEAP));
        DODGE_EXPIRE.put(player.getUuid(), player.getWorld().getTime() + DODGE_DURATION);
    }

    // ==== 灵跃闪身（次技能 sp_secondary）====

    public static void tryLeap(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) return;
        long now = player.getWorld().getTime();
        if (now < LEAP_CD_END.getOrDefault(player.getUuid(), 0L)) return; // cd 中
        int count = LEAP_COUNT.getOrDefault(player.getUuid(), 0);
        long first = LEAP_FIRST.getOrDefault(player.getUuid(), 0L);
        if (count == 0 || now - first > LEAP_WINDOW) {
            count = 1;
            LEAP_FIRST.put(player.getUuid(), now);
        } else if (count == 1) {
            count = 2;
        } else {
            return;
        }
        LEAP_COUNT.put(player.getUuid(), count);
        // 向准星方向跳冲（空中可用）
        Vec3d look = player.getRotationVector();
        player.setVelocity(look.x * LEAP_POWER, Math.max(0.42, look.y * LEAP_POWER + 0.25), look.z * LEAP_POWER);
        player.velocityModified = true;
        addDodge(player); // +20% 闪避
        // 灵跃粒子：脚下蹬地烟环 + 沿跳冲方向破空拖尾 + 锐气暴击（敏捷灵动）
        if (player.getWorld() instanceof ServerWorld sw) {
            // 脚下蹬地烟环（沿脚下一整圈均匀分布，蹬地爆发感、非固定点）
            spawnRing(sw, ParticleTypes.CLOUD, player.getX(), player.getY() + 0.08, player.getZ(),
                    0.6, 12, 0.05);
            // 沿跳冲方向的破空拖尾（锐气流线，敏捷灵动）
            for (int i = 1; i <= 6; i++) {
                double t = i / 6.0;
                sw.spawnParticles(ParticleTypes.CLOUD,
                        player.getX() + look.x * t * 1.6,
                        player.getY() + 0.5 + look.y * t * 1.6,
                        player.getZ() + look.z * t * 1.6,
                        1, 0.05, 0.05, 0.05, 0.02);
            }
            sw.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.6, player.getZ(),
                    10, 0.3, 0.3, 0.3, 0.35);
        }
        // 灵跃：幻影闪现（幻术师镜像瞬移）+ 轻盈破空，敏捷灵动
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 0.8F, 1.4F);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5F, 1.8F);
        if (count >= 2) {
            LEAP_CD_END.put(player.getUuid(), now + LEAP_CD);
            LEAP_COUNT.put(player.getUuid(), 0);
            PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, LEAP_CD);
        }
    }

    // ==== 舍身爆炸（主技能 sp_primary）====

    public static void startCharge(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) return;
        long now = player.getWorld().getTime();
        if (now < EXPLODE_CD_END.getOrDefault(player.getUuid(), 0L)) return; // cd 中
        if (CHARGE_START.containsKey(player.getUuid())) return; // 已在蓄力
        if (PowerUtils.getResourceValue(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES) <= 0) return; // 无命不能自爆
        CHARGE_START.put(player.getUuid(), now);
        // 代码减速 70%（属性修改器 MULTIPLY_TOTAL -0.70，精确非药水缓慢）+ 抗性 III
        applyChargeSlow(player);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, CHARGE_TIME, 2, false, false, true));
        // 蓄力起手：深沉能量汇聚（导管激活低调版）+ TNT 点燃引信声，预示危险充能
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.6F);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 1.2F, 1.0F);
        // 标记蓄力中 → 同步客户端，供门控版 sneaking_speed_up 判断（蓄力期禁 shift 潜行加速）
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.OCELOT_NOVA_CHARGING, 1);
    }

    /** 施加蓄力期代码减速 70%（先移除同 UUID 旧修改器防叠加）。 */
    private static void applyChargeSlow(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CHARGE_SLOW_UUID);
            speed.addTemporaryModifier(new EntityAttributeModifier(
                    CHARGE_SLOW_UUID, "Nova Charge Slow", CHARGE_SLOW_AMOUNT,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** 结束蓄力：移除减速修改器 + 清蓄力标记（解除禁疾跑）+ 清蓄力计时。所有结束路径统一走此方法，防减速泄漏。 */
    private static void endCharge(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CHARGE_SLOW_UUID);
        }
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.OCELOT_NOVA_CHARGING, 0);
        CHARGE_START.remove(player.getUuid());
    }

    private static void explode(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        // 舍身代价（消耗 1 命）不在此处直接扣，改由结尾对自己引爆触发九命复活来扣，避免重复扣命。
        List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(MAX_RADIUS), e -> e != player && e.isAlive());
        for (LivingEntity e : targets) {
            if (WhitelistUtils.isProtected(player, e)) continue; // 无友伤
            double dist = e.distanceTo(player);
            if (dist > MAX_RADIUS) continue;
            float dmg;
            if (dist <= LETHAL_RADIUS) {
                dmg = MAX_DAMAGE;
            } else {
                dmg = MAX_DAMAGE * (1.0f - (float) ((dist - LETHAL_RADIUS) / (MAX_RADIUS - LETHAL_RADIUS)));
            }
            if (dmg <= 0) continue;
            e.damage(sw.getDamageSources().explosion(player, player), dmg); // 归属玩家、无破坏方块
        }
        // 爆炸范围可视化：三层圈勾勒实际范围——致命圈（暗红，最醒目）→ 衰减带（橙灰过渡）→ 最远波及圈（淡烟外沿）。
        // 每圈沿整圈动态分布（随机起始角 + 角度/径向抖动），非固定点、层次清晰而不刺眼。
        double ringY = player.getY() + 0.15;
        DustParticleEffect lethalDust = new DustParticleEffect(new Vector3f(0.82f, 0.14f, 0.12f), 1.2f);
        spawnRing(sw, lethalDust, player.getX(), ringY, player.getZ(), LETHAL_RADIUS, LETHAL_RADIUS * 10, 0.25);
        double midRadius = (LETHAL_RADIUS + MAX_RADIUS) / 2.0;
        DustParticleEffect midDust = new DustParticleEffect(new Vector3f(0.62f, 0.36f, 0.22f), 1.0f);
        spawnRing(sw, midDust, player.getX(), ringY, player.getZ(), midRadius, (int) (midRadius * 7), 0.35);
        spawnRing(sw, ParticleTypes.SMOKE, player.getX(), ringY, player.getZ(), MAX_RADIUS, MAX_RADIUS * 5, 0.45);
        // 中心爆炸主体 + 烟云
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 30, 1.5, 1.0, 1.5, 0.1);
        // 自爆引爆：音爆冲击 + 厚重爆炸 + 末影龙余威，三层叠出毁灭感
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0F, 0.9F);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.8F, 0.7F);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.7F, 1.0F);
        // 舍身：自己也被炸倒，九命必定复活（绕过复活 cd 与闪避、净耗 1 命）——浴火重生
        NineLivesManager.reviveForSelfDetonate(player);
    }

    // ==== 粒子辅助 ====

    /** 沿半径 radius 的水平圆周均匀撞 count 个粒子（起始角随机 + 角度/径向抖动，避免呆板固定点）。 */
    private static void spawnRing(ServerWorld sw, ParticleEffect particle, double cx, double cy, double cz,
                                  double radius, int count, double yJitter) {
        double start = sw.random.nextDouble() * Math.PI * 2;
        double step = Math.PI * 2 / count;
        for (int i = 0; i < count; i++) {
            double angle = start + step * i + (sw.random.nextDouble() - 0.5) * step * 0.7;
            double r = radius + (sw.random.nextDouble() - 0.5) * 0.5;
            sw.spawnParticles(particle, cx + Math.cos(angle) * r, cy + (sw.random.nextDouble() - 0.5) * yJitter,
                    cz + Math.sin(angle) * r, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 时间驱动的旋转范围环：沿半径 radius 圆周稀疏均匀撒 count 个点，起始角随 timeSeed 缓慢旋转（speed 控快慢与方向）
     * + 每点角度/径向/高度抖动。瞬时低密度（不刺眼）+ 逐 tick 旋转（流动、非固定点）→ 累积勾勒完整范围圈。
     * 用于蓄力期持续预告爆炸范围。
     */
    private static void spawnArcRing(ServerWorld sw, ParticleEffect particle, double cx, double cy, double cz,
                                     double radius, long timeSeed, double speed, int count) {
        if (count < 1) count = 1;
        double base = timeSeed * speed;
        double step = Math.PI * 2 / count;
        for (int i = 0; i < count; i++) {
            double angle = base + step * i + (sw.random.nextDouble() - 0.5) * step * 0.6;
            double r = radius + (sw.random.nextDouble() - 0.5) * 0.5;
            sw.spawnParticles(particle, cx + Math.cos(angle) * r, cy + (sw.random.nextDouble() - 0.5) * 0.15,
                    cz + Math.sin(angle) * r, 1, 0, 0, 0, 0);
        }
    }

    // ==== tick ====

    private static void tickPlayer(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) {
            // 蓄力中途切换形态：属性修改器不随 apoli power 消失，须手动解除减速与禁疾跑标记
            if (CHARGE_START.containsKey(player.getUuid())) {
                endCharge(player);
            }
            return;
        }
        long now = player.getWorld().getTime();
        // 闪避加成过期清理
        Long exp = DODGE_EXPIRE.get(player.getUuid());
        if (exp != null && now > exp) {
            DODGE.remove(player.getUuid());
            DODGE_EXPIRE.remove(player.getUuid());
        }
        // 灵跃：只用 1 次且超过窗口 → 进入短 cd
        if (LEAP_COUNT.getOrDefault(player.getUuid(), 0) == 1
                && now - LEAP_FIRST.getOrDefault(player.getUuid(), 0L) > LEAP_WINDOW) {
            LEAP_CD_END.put(player.getUuid(), now + LEAP_CD_SHORT);
            LEAP_COUNT.put(player.getUuid(), 0);
            PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, LEAP_CD_SHORT);
        }
        // 舍身爆炸蓄力：黑烟粒子 + 蓄满自爆
        Long cs = CHARGE_START.get(player.getUuid());
        if (cs != null) {
            if (player.getWorld() instanceof ServerWorld sw) {
                double chargeProgress = (now - cs) / (double) CHARGE_TIME; // 0→1 蓄力进度
                // 蓄力粒子：头顶黑烟 + 身周火星（引信感）
                sw.spawnParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                        3, 0.3, 0.4, 0.3, 0.02);
                sw.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.7, player.getZ(),
                        2, 0.35, 0.4, 0.35, 0.01);
                // 蓄力全程持续预告爆炸范围：致命圈（暗红，稍清晰）+ 最远波及圈（淡灰白，很淡、反向旋转）。
                // 时间驱动旋转 + 稀疏 + 抖动 = 流动光环勾勒范围，瞬时低密度不刺眼、非固定点；密度随蓄力进度略增。
                double warnY = player.getY() + 0.08;
                DustParticleEffect lethalWarn = new DustParticleEffect(new Vector3f(0.72f, 0.12f, 0.12f), 0.9f);
                spawnArcRing(sw, lethalWarn, player.getX(), warnY, player.getZ(),
                        LETHAL_RADIUS, now, 0.15, 14 + (int) (chargeProgress * 8));
                DustParticleEffect edgeWarn = new DustParticleEffect(new Vector3f(0.68f, 0.68f, 0.70f), 0.8f);
                spawnArcRing(sw, edgeWarn, player.getX(), warnY, player.getZ(),
                        MAX_RADIUS, now, -0.11, 18 + (int) (chargeProgress * 10));
                // 蓄力充能音：导管低鸣底噪，每 0.5s 一次、随进度升调（音量压低让位 TNT 引信声）
                if ((now - cs) % 10 == 0) {
                    float pitch = 0.7F + 0.8F * (float) chargeProgress;
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.PLAYERS, 0.4F, pitch);
                }
                // TNT 引信「滴滴」声：每 0.6s 一次，随进度渐响渐急（点燃后持续燃烧感）
                if ((now - cs) % 12 == 0) {
                    float fuseVol = 0.5F + 0.4F * (float) chargeProgress;
                    float fusePitch = 1.0F + 0.3F * (float) chargeProgress;
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, fuseVol, fusePitch);
                }
                // 临爆前 1 秒：监守者心跳预警（每 0.5s），提示即将引爆
                if (now - cs >= CHARGE_TIME - 20 && (now - cs) % 10 == 0) {
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0F, 0.8F);
                }
            }
            if (now - cs >= CHARGE_TIME) {
                explode(player);
                EXPLODE_CD_END.put(player.getUuid(), now + EXPLODE_CD);
                PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, EXPLODE_CD);
                endCharge(player); // 自爆完毕：解除减速 + 禁疾跑标记 + 清蓄力计时
            }
        } else if (PowerUtils.getResourceValue(player, FormIdentifiers.OCELOT_NOVA_CHARGING) > 0) {
            // 断线/重启后蓄力计时（内存态）丢失但蓄力标记（持久化资源）残留 → 自愈，避免永久减速与禁疾跑
            endCharge(player);
        }
    }
}
