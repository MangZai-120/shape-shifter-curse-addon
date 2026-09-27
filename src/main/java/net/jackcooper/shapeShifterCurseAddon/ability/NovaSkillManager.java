/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

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
    // ==== 以下常量为默认值；运行时从 balance 快照读取（scope: abilities.nova，数据包可覆盖）====
    // 闪避
    private static final float BASE_DODGE = 0.15f;      // 默认：基础被动闪避 15%
    private static final float DODGE_PER_LEAP = 0.20f;  // 默认：灵跃每次 +20%
    private static final int DODGE_DURATION = 60;       // 默认：灵跃闪避加成持续 3s
    private static final float DODGE_CAP = 0.85f;       // 默认：闪避几率上限，避免完全无敌
    // 灵跃闪身
    private static final int LEAP_WINDOW = 100;         // 默认：第 1→2 次窗口 5s
    private static final int LEAP_CD = 200;             // 默认：用满 2 次 cd 10s
    private static final int LEAP_CD_SHORT = 120;       // 默认：只用 1 次超时 cd 6s
    private static final double LEAP_POWER = 1.2;       // 默认：跳冲水平速度
    private static final int LEAP_INPUT_GAP = 5;        // 输入去抖窗口(tick)：必须 > leap power 的 cooldown(3)，否则挡不住按住的重复触发。未登记 balance，保持常量
    // 舍身爆炸
    private static final int CHARGE_TIME = 100;         // 默认：蓄力 5s
    private static final int EXPLODE_CD = 600;          // 默认：cd 30s
    private static final int LETHAL_RADIUS = 5;         // 默认：致命半径 5 格
    private static final int MAX_RADIUS = 12;           // 默认：最远半径 12 格
    private static final float MAX_DAMAGE = 50.0f;      // 默认：致命伤害 50
    // 蓄力期代码减速：GENERIC_MOVEMENT_SPEED MULTIPLY_TOTAL -0.70 = 精确减速 70%（非药水缓慢，可精确到 70%）。默认；运行时从 balance 快照读取
    private static final UUID CHARGE_SLOW_UUID = UUID.fromString("9f3c1e5a-7b2d-4c8e-a1f6-0d9e8c7b6a54");
    private static final double CHARGE_SLOW_AMOUNT = -0.70;

    /** 服务端权威 balance 快照读取（快照未初始化时回退上方默认常量）。 */
    private static final BalanceReader BAL = new BalanceReader("abilities.nova");

    private static final Map<UUID, Float> DODGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DODGE_EXPIRE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LEAP_COUNT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LEAP_FIRST = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LEAP_CD_END = new ConcurrentHashMap<>();
    /** 灵跃最近一次「收到触发」的 tick：用于抑制 active_self 电平触发在一次按键内的多 tick 重复。 */
    private static final Map<UUID, Long> LEAP_LAST_INPUT = new ConcurrentHashMap<>();
    /** 舍身爆炸蓄力会话（2026-09-27 审计修复）：起手把 chargeTime/lethalRadius/maxRadius/maxDamage
     * 快照进会话——100t 蓄力可能跨数据包 reload，若进度判定与 explode 结算即时读 balance，
     * reload 后同一发爆炸会用新参数；改读会话快照保证单次释放全程同参。 */
    private static final Map<UUID, ChargeSession> CHARGE_START = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> EXPLODE_CD_END = new ConcurrentHashMap<>();

    /** 单次蓄力的起手参数快照（构造后不变）。 */
    private record ChargeSession(long startTick, int chargeTime, int lethalRadius, int maxRadius, float maxDamage) {
    }

    private NovaSkillManager() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                tickPlayer(p);
            }
        });
        // 断线清理八张状态表：防离线条目残留泄漏，也避免重连后残留旧 CD/蓄力起点误触发
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            DODGE.remove(id);
            DODGE_EXPIRE.remove(id);
            LEAP_COUNT.remove(id);
            LEAP_FIRST.remove(id);
            LEAP_CD_END.remove(id);
            LEAP_LAST_INPUT.remove(id);
            CHARGE_START.remove(id);
            EXPLODE_CD_END.remove(id);
        });
    }

    // ==== 闪避 ====

    /** 受伤时判定是否闪避（由 damage mixin 调用）。返回 true = 免疫本次伤害。 */
    public static boolean rollDodge(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) return false;
        float chance = (float) BAL.d("base_dodge", BASE_DODGE);
        Float extra = DODGE.get(player.getUuid());
        Long exp = DODGE_EXPIRE.get(player.getUuid());
        if (extra != null && exp != null && player.getWorld().getTime() <= exp) {
            chance += extra;
        }
        chance = Math.min((float) BAL.d("dodge_cap", DODGE_CAP), chance);
        return player.getRandom().nextFloat() < chance;
    }

    private static void addDodge(ServerPlayerEntity player) {
        float cap = (float) BAL.d("dodge_cap", DODGE_CAP);
        float cur = DODGE.getOrDefault(player.getUuid(), 0f);
        DODGE.put(player.getUuid(), Math.min(cap, cur + (float) BAL.d("dodge_per_leap", DODGE_PER_LEAP)));
        DODGE_EXPIRE.put(player.getUuid(), player.getWorld().getTime() + BAL.i("dodge_duration", DODGE_DURATION));
    }

    // ==== 灵跃闪身（次技能 sp_secondary）====

    public static void tryLeap(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) return;
        long now = player.getWorld().getTime();
        // 输入去抖：power active_self(cooldown=1) 是电平触发，一次物理按键会持续多 tick 重复触发本方法。
        // 记录最近一次「收到触发」的 tick（无论如何都更新）；若距上次触发 < GAP，视为同一次按住的重复 → 忽略。
        // 这样按住/单击只认第一次，松开（停止触发）后再按才算新的一次，正确区分单击与连按。
        Long lastInput = LEAP_LAST_INPUT.put(player.getUuid(), now);
        if (lastInput != null && now - lastInput < LEAP_INPUT_GAP) {
            return;
        }
        if (now < LEAP_CD_END.getOrDefault(player.getUuid(), 0L)) return; // cd 中
        int count = LEAP_COUNT.getOrDefault(player.getUuid(), 0);
        long first = LEAP_FIRST.getOrDefault(player.getUuid(), 0L);
        if (count == 0 || now - first > BAL.i("leap_window", LEAP_WINDOW)) {
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
        double leapPower = BAL.d("leap_power", LEAP_POWER);
        player.setVelocity(look.x * leapPower, Math.max(0.42, look.y * leapPower + 0.25), look.z * leapPower);
        player.velocityModified = true;
        addDodge(player); // +20% 闪避
        // 灵跃粒子：脚下蹬地烟环 + 沿跳冲方向破空拖尾 + 锐气暴击（敏捷灵动）
        if (player.getWorld() instanceof ServerWorld sw) {
            // 脚下蹬地烟环（沿脚下一整圈均匀分布，蹬地爆发感、非固定点）
            spawnRing(sw, player, ParticleTypes.CLOUD, player.getX(), player.getY() + 0.08, player.getZ(),
                    0.6, 12, 0.05);
            // 沿跳冲方向的破空拖尾（锐气流线，敏捷灵动；owner 打标：本人第一人称避让）
            for (int i = 1; i <= 6; i++) {
                double t = i / 6.0;
                net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.CLOUD,
                        player.getX() + look.x * t * 1.6,
                        player.getY() + 0.5 + look.y * t * 1.6,
                        player.getZ() + look.z * t * 1.6,
                        1, 0.05, 0.05, 0.05, 0.02);
            }
            net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.CRIT, player.getX(), player.getY() + 0.6, player.getZ(),
                    10, 0.3, 0.3, 0.3, 0.35);
        }
        // 灵跃：幻影闪现（幻术师镜像瞬移）+ 轻盈破空，敏捷灵动
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 0.8F, 1.4F);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5F, 1.8F);
        if (count >= 2) {
            int leapCd = BAL.i("leap_cd", LEAP_CD);
            LEAP_CD_END.put(player.getUuid(), now + leapCd);
            LEAP_COUNT.put(player.getUuid(), 0);
            PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, leapCd);
        }
    }

    // ==== 舍身爆炸（主技能 sp_primary）====

    public static void startCharge(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) return;
        long now = player.getWorld().getTime();
        if (now < EXPLODE_CD_END.getOrDefault(player.getUuid(), 0L)) return; // cd 中
        if (CHARGE_START.containsKey(player.getUuid())) return; // 已在蓄力
        if (PowerUtils.getResourceValue(player, FormIdentifiers.OCELOT_NOVA_NINE_LIVES) <= 0) return; // 无命不能自爆
        // 起手快照：本次释放全程（进度判定/视觉/爆炸结算）读会话字段，不随 reload 换参
        ChargeSession session = new ChargeSession(now,
                BAL.i("charge_time", CHARGE_TIME),
                BAL.i("lethal_radius", LETHAL_RADIUS),
                BAL.i("max_radius", MAX_RADIUS),
                (float) BAL.d("max_damage", MAX_DAMAGE));
        CHARGE_START.put(player.getUuid(), session);
        // 代码减速 70%（属性修改器 MULTIPLY_TOTAL -0.70，精确非药水缓慢）+ 抗性 III
        applyChargeSlow(player);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,
                session.chargeTime(), 2, false, false, true));
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
                    CHARGE_SLOW_UUID, "Nova Charge Slow", BAL.d("charge_slow_amount", CHARGE_SLOW_AMOUNT),
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
        net.jackcooper.shapeShifterCurseAddon.network.SustainedVisuals.stop(player,
                net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Kind.NOVA_CHARGE);
    }

    private static void explode(ServerPlayerEntity player, ChargeSession session) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        // 读起手会话快照（防 reload 后结算换参）：致命半径/最远半径/伤害均周起手值
        int lethalRadius = session.lethalRadius();
        int maxRadius = session.maxRadius();
        float maxDamage = session.maxDamage();
        // 舍身代价（消耗 1 命）不在此处直接扣，改由结尾对自己引爆触发九命复活来扣，避免重复扣命。
        List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(maxRadius), e -> e != player && e.isAlive());
        for (LivingEntity e : targets) {
            if (WhitelistUtils.isProtected(player, e)) continue; // 无友伤
            double dist = e.distanceTo(player);
            if (dist > maxRadius) continue;
            float dmg;
            if (dist <= lethalRadius) {
                dmg = maxDamage;
            } else {
                dmg = maxDamage * (1.0f - (float) ((dist - lethalRadius) / (maxRadius - lethalRadius)));
            }
            if (dmg <= 0) continue;
            e.damage(sw.getDamageSources().explosion(player, player), dmg); // 归属玩家、无破坏方块
        }
        // 爆炸范围可视化：三层圈勾勒实际范围——致命圈（暗红，最醒目）→ 衰减带（橙灰过渡）→ 最远波及圈（淡烟外沿）。
        // 每圈沿整圈动态分布（随机起始角 + 角度/径向抖动），非固定点、层次清晰而不刺眼。
        double ringY = player.getY() + 0.15;
        DustParticleEffect lethalDust = new DustParticleEffect(new Vector3f(0.82f, 0.14f, 0.12f), 1.2f);
        // 范围预警圈（owner=null 不打标）：本人第一人称也要看清爆炸范围，不做避让
        spawnRing(sw, null, lethalDust, player.getX(), ringY, player.getZ(), lethalRadius, lethalRadius * 10, 0.25);
        double midRadius = (lethalRadius + maxRadius) / 2.0;
        DustParticleEffect midDust = new DustParticleEffect(new Vector3f(0.62f, 0.36f, 0.22f), 1.0f);
        spawnRing(sw, null, midDust, player.getX(), ringY, player.getZ(), midRadius, (int) (midRadius * 7), 0.35);
        spawnRing(sw, null, ParticleTypes.SMOKE, player.getX(), ringY, player.getZ(), maxRadius, maxRadius * 5, 0.45);
        // 中心爆炸主体 + 烟云（owner 打标：仅本人第一人称避让；爆炸圈是范围预警保留原样）
        net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0, 0, 0, 0);
        net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 30, 1.5, 1.0, 1.5, 0.1);
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

    /** 沿半径 radius 的水平圆周均匀撒 count 个粒子（起始角随机 + 角度/径向抖动，避免呆板固定点）。owner 非空时打标避让。 */
    private static void spawnRing(ServerWorld sw, net.minecraft.entity.Entity owner, ParticleEffect particle, double cx, double cy, double cz,
                                  double radius, int count, double yJitter) {
        double start = sw.random.nextDouble() * Math.PI * 2;
        double step = Math.PI * 2 / count;
        for (int i = 0; i < count; i++) {
            double angle = start + step * i + (sw.random.nextDouble() - 0.5) * step * 0.7;
            double r = radius + (sw.random.nextDouble() - 0.5) * 0.5;
            net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, owner, particle, cx + Math.cos(angle) * r, cy + (sw.random.nextDouble() - 0.5) * yJitter,
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
                && now - LEAP_FIRST.getOrDefault(player.getUuid(), 0L) > BAL.i("leap_window", LEAP_WINDOW)) {
            int leapCdShort = BAL.i("leap_cd_short", LEAP_CD_SHORT);
            LEAP_CD_END.put(player.getUuid(), now + leapCdShort);
            LEAP_COUNT.put(player.getUuid(), 0);
            PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, leapCdShort);
        }
        // 舍身爆炸蓄力：黑烟粒子 + 蓄满自爆（进度与半径全部读会话快照，reload 不换参）
        ChargeSession session = CHARGE_START.get(player.getUuid());
        if (session != null) {
            long cs = session.startTick();
            int chargeTime = session.chargeTime();
            int lethalRadius = session.lethalRadius();
            int maxRadius = session.maxRadius();
            if (player.getWorld() instanceof ServerWorld) {
                double chargeProgress = (now - cs) / (double) chargeTime; // 0→1 蓄力进度
                net.jackcooper.shapeShifterCurseAddon.network.SustainedVisuals.touch(player,
                        net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Kind.NOVA_CHARGE,
                        (int) (now - cs), chargeTime, lethalRadius, maxRadius);
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
                if (now - cs >= chargeTime - 20 && (now - cs) % 10 == 0) {
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0F, 0.8F);
                }
            }
            if (now - cs >= chargeTime) {
                explode(player, session);
                int explodeCd = BAL.i("explode_cd", EXPLODE_CD);
                EXPLODE_CD_END.put(player.getUuid(), now + explodeCd);
                PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, explodeCd);
                endCharge(player); // 自爆完毕：解除减速 + 禁疾跑标记 + 清蓄力计时
            }
        } else if (PowerUtils.getResourceValue(player, FormIdentifiers.OCELOT_NOVA_CHARGING) > 0) {
            // 断线/重启后蓄力计时（内存态）丢失但蓄力标记（持久化资源）残留 → 自愈，避免永久减速与禁疾跑
            endCharge(player);
        }
    }
}
