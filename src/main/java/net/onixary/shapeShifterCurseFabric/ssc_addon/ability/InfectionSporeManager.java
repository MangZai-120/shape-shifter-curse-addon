/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 寄生果蝠次要技能：感染孢子状态管理器。
 * <p>
 * 服务端权威，多人环境下所有判定与粒子均由服务端驱动并按需投递给客户端，
 * 保证主客机一致与无客机延迟。
 * <p>
 * 行为：
 * - 感染目标每 25t（1.25s，与中毒 I 一致）受到 1 点直接伤害。
 * - 感染目标 1.5 格内的未感染、非白名单生物会被传染，剩余时间继承施加者剩余时间。
 * - 已感染目标再次被命中时忽略（不刷新、不叠加）。
 * - 感染期间粒子表现：他人视角每 5t 一次绿色感染粒子；被感染玩家自己视角 1/4 频率。
 */
public final class InfectionSporeManager {

    /** 伤害触发间隔（tick）：与中毒 I 一致，每 25t（1.25s）一次 */
    public static final int DAMAGE_INTERVAL = 25;
    /** 传染半径（格） */
    public static final double INFECT_SPREAD_RADIUS = 1.5;
    /** 他人视角粒子周期 */
    public static final int PARTICLE_INTERVAL_OTHERS = 5;
    /** 第一人称粒子周期（约 1/4 频率） */
    public static final int PARTICLE_INTERVAL_SELF = 20;
    /** 攻击者造成伤害减免比例 */
    public static final float DAMAGE_REDUCTION = 0.15f;

    /** 被感染目标 UUID -> 感染数据 */
    private static final Map<UUID, InfectionData> ENTRIES = new ConcurrentHashMap<>();
    /** 友方治疗孢子目标 UUID -> 治疗数据（每 60t +1HP，持续 durationTicks） */
    private static final Map<UUID, HealData> HEAL_ENTRIES = new ConcurrentHashMap<>();
    /** 友方治疗每 tick 量 */
    public static final float TICK_HEAL = 1.0f;
    /** 毒雾敌人每次掉血量 */
    public static final float TICK_DAMAGE = 1.0f;
    /** 友方治疗间隔（与感染节奏一致：3 秒） */
    public static final int HEAL_INTERVAL = 60;

    private InfectionSporeManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(InfectionSporeManager::onWorldTick);
    }

    /** 施加感染。命中已感染目标时直接重置时长（保留 caster 与 nextDamageTick）。 */
    public static void infect(ServerPlayerEntity caster, LivingEntity target, int durationTicks) {
        if (!(target.getWorld() instanceof ServerWorld serverWorld)) return;
        if (target == caster) return;
        // 施加专属「中毒」buff（图标 + 周期扣血，效果同中毒；扣血由 buff 负责，本管理器不再直接伤害）
        target.addStatusEffect(new StatusEffectInstance(
                net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.BAT_POISON, Math.max(20, durationTicks), 0, false, true, true));
        UUID targetUuid = target.getUuid();
        long now = serverWorld.getTime();
        long newEnd = now + Math.max(20, durationTicks);
        InfectionData existing = ENTRIES.get(targetUuid);
        if (existing != null) {
            // 命中已感染目标 → 仅刷新 endTick；保留原 caster 与下一次伤害 tick 节拍
            existing.endTick = newEnd;
            return;
        }
        InfectionData data = new InfectionData(
                caster.getUuid(),
                serverWorld.getRegistryKey(),
                newEnd,
                now + DAMAGE_INTERVAL
        );
        ENTRIES.put(targetUuid, data);
    }

    /** 用于 mixin 查询 */
    public static boolean isInfected(UUID uuid) {
        return ENTRIES.containsKey(uuid);
    }

    /** 对友方目标施加治疗孢子。命中已在治疗中的目标时直接重置时长（保留 nextHealTick 节拍）。 */
    public static void applyFriendHeal(ServerPlayerEntity caster, LivingEntity target, int durationTicks) {
        if (!(target.getWorld() instanceof ServerWorld serverWorld)) return;
        UUID targetUuid = target.getUuid();
        long now = serverWorld.getTime();
        long newEnd = now + Math.max(20, durationTicks);
        HealData existing = HEAL_ENTRIES.get(targetUuid);
        if (existing != null) {
            existing.endTick = newEnd;
            return;
        }
        HealData data = new HealData(
                serverWorld.getRegistryKey(),
                newEnd,
                now + HEAL_INTERVAL
        );
        HEAL_ENTRIES.put(targetUuid, data);
    }

    public static int size() {
        return ENTRIES.size();
    }

    private static void onWorldTick(ServerWorld world) {
        if (!HEAL_ENTRIES.isEmpty()) {
            tickHeals(world);
        }
        if (!CLOUDS.isEmpty()) {
            tickClouds(world);
        }
        if (ENTRIES.isEmpty()) return;
        long now = world.getTime();
        MinecraftServer server = world.getServer();

        Iterator<Map.Entry<UUID, InfectionData>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, InfectionData> e = it.next();
            InfectionData data = e.getValue();
            // 仅处理与本世界匹配的条目，跨维度由 world tick 各自维护
            if (!data.worldKey.equals(world.getRegistryKey())) continue;

            Entity entity = world.getEntity(e.getKey());
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                it.remove();
                continue;
            }
            if (now >= data.endTick) {
                it.remove();
                continue;
            }

            // 1) 周期扣血由 BAT_POISON buff 负责（见 infect/spreadFrom），此处不再直接伤害

            // 2) 传染（仅当源施加者仍在线时启用，便于继承"剩余时间"语义）
            spreadFrom(server, target, data, now);

            // 3) 粒子表现：他人 1×, 被感染玩家自己 1/4
            emitInfectionParticles(world, target, now);
        }
    }

    /** 在 1.5 格内寻找未感染目标，使用源施加者的白名单进行过滤。 */
    private static void spreadFrom(MinecraftServer server, LivingEntity host, InfectionData data, long now) {
        ServerPlayerEntity caster = server.getPlayerManager().getPlayer(data.casterUuid);
        if (caster == null) return; // 施加者掉线则不传染（保留现有感染计时）

        ServerWorld world = (ServerWorld) host.getWorld();
        Box box = host.getBoundingBox().expand(INFECT_SPREAD_RADIUS);
        double sqRadius = INFECT_SPREAD_RADIUS * INFECT_SPREAD_RADIUS;
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != host && e.isAlive() && host.squaredDistanceTo(e) <= sqRadius
                        && !ENTRIES.containsKey(e.getUuid()));

        if (candidates.isEmpty()) return;
        int remaining = (int) Math.max(20, data.endTick - now);
        for (LivingEntity candidate : candidates) {
            if (WhitelistUtils.isProtected(caster, candidate)) continue;
            // 时间继承自当前宿主
            InfectionData child = new InfectionData(
                    data.casterUuid,
                    world.getRegistryKey(),
                    now + remaining,
                    now + DAMAGE_INTERVAL
            );
            ENTRIES.put(candidate.getUuid(), child);
            candidate.addStatusEffect(new StatusEffectInstance(
                    net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.BAT_POISON, Math.max(20, remaining), 0, false, true, true));
        }
    }

    /** 服务端构造 ParticleS2CPacket 并按观察者身份分发，实现"自己看少一些"。 */
    private static void emitInfectionParticles(ServerWorld world, LivingEntity target, long now) {
        boolean othersTick = now % PARTICLE_INTERVAL_OTHERS == 0;
        boolean selfTick = now % PARTICLE_INTERVAL_SELF == 0;
        if (!othersTick && !selfTick) return;

        Vec3d pos = target.getPos();
        // 在目标周围发出绿色感染粒子（HAPPY_VILLAGER 默认绿色）
        ParticleS2CPacket pkt = new ParticleS2CPacket(
                ParticleTypes.HAPPY_VILLAGER,
                false,
                pos.x, pos.y + target.getHeight() * 0.6, pos.z,
                0.4f, 0.5f, 0.4f, 0.0f, 2);

        UUID targetUuid = target.getUuid();
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            // 距离剔除（与原版广播半径一致 32 格）
            if (viewer.squaredDistanceTo(target) > 32 * 32) continue;
            boolean isSelf = viewer.getUuid().equals(targetUuid);
            if (isSelf ? selfTick : othersTick) {
                viewer.networkHandler.sendPacket(pkt);
            }
        }
    }

    /** 用于 mixin：在攻击者被感染时减少其伤害。 */
    public static float reduceDamageIfInfected(LivingEntity attacker, float amount) {
        if (attacker == null) return amount;
        if (!ENTRIES.containsKey(attacker.getUuid())) return amount;
        return amount * (1.0f - DAMAGE_REDUCTION);
    }

    private static final class InfectionData {
        final UUID casterUuid;
        final RegistryKey<World> worldKey;
        long endTick;
        long nextDamageTick;

        InfectionData(UUID casterUuid, RegistryKey<World> worldKey, long endTick, long nextDamageTick) {
            this.casterUuid = casterUuid;
            this.worldKey = worldKey;
            this.endTick = endTick;
            this.nextDamageTick = nextDamageTick;
        }
    }

    private static void tickHeals(ServerWorld world) {
        long now = world.getTime();
        Iterator<Map.Entry<UUID, HealData>> it = HEAL_ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, HealData> e = it.next();
            HealData data = e.getValue();
            if (!data.worldKey.equals(world.getRegistryKey())) continue;
            Entity entity = world.getEntity(e.getKey());
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                it.remove();
                continue;
            }
            if (now >= data.endTick) {
                it.remove();
                continue;
            }
            if (now >= data.nextHealTick) {
                data.nextHealTick = now + HEAL_INTERVAL;
                target.heal(TICK_HEAL);
                // 视觉反馈：心心粒子
                world.spawnParticles(ParticleTypes.HEART,
                        target.getX(), target.getY() + target.getHeight() * 0.8, target.getZ(),
                        2, 0.3, 0.2, 0.3, 0.0);
            }
        }
    }

    private static final class HealData {
        final RegistryKey<World> worldKey;
        long endTick;
        long nextHealTick;

        HealData(RegistryKey<World> worldKey, long endTick, long nextHealTick) {
            this.worldKey = worldKey;
            this.endTick = endTick;
            this.nextHealTick = nextHealTick;
        }
    }

    // ============================ 滞留毒雾云子系统 ============================
    /** 毒雾云半径（格）：直径 4 格 → 半径 2 格 */
    public static final double CLOUD_RADIUS = 2.0;
    /** 毒雾云扫描生物的间隔（tick）：每 1.5s 一次范围内效果 */
    public static final int CLOUD_SCAN_INTERVAL = 30;
    /** 毒雾云粒子间隔（tick） */
    public static final int CLOUD_PARTICLE_INTERVAL = 2;
    /** 净化驱散毒雾云的判定半径（格）：被净化个体此范围内的云会被驱散 */
    public static final double CLOUD_PURIFY_REACH = 8.0;
    /** 墨绿色中毒粒子（与感染孢子弹视觉一致） */
    private static final DustParticleEffect CLOUD_POISON_DUST = new DustParticleEffect(new Vector3f(0.30f, 0.50f, 0.10f), 1.2f);
    /** 活跃的毒雾云列表（服务端权威） */
    private static final List<CloudData> CLOUDS = new CopyOnWriteArrayList<>();

    /**
     * 在指定位置生成一团滞留毒雾云（感染孢子弹落地时调用）。
     *
     * @param caster        施放者（用于白名单判定与感染归属）
     * @param world         服务端世界
     * @param center        云中心（落点）
     * @param radius        云半径（格）
     * @param durationTicks 云存活时长（tick），同时也是进入者可获得的最大感染时长
     */
    public static void spawnCloud(ServerPlayerEntity caster, ServerWorld world, Vec3d center, double radius, int durationTicks) {
        long now = world.getTime();
        // 毒雾不可叠加：若落点附近（两云半径之和内）已有同维度毒雾，则仅刷新其寿命，不新增
        for (CloudData c : CLOUDS) {
            if (!c.worldKey.equals(world.getRegistryKey())) continue;
            double merge = radius + c.radius;
            if (c.squaredDistanceTo(center.x, center.y, center.z) <= merge * merge) {
                c.endTick = Math.max(c.endTick, now + Math.max(20, durationTicks));
                return;
            }
        }
        CLOUDS.add(new CloudData(
                caster.getUuid(),
                world.getRegistryKey(),
                center.x, center.y, center.z,
                radius,
                now + Math.max(20, durationTicks),
                now + CLOUD_SCAN_INTERVAL
        ));
    }

    /** 移除目标身上的感染（供悦灵净化调用）。 */
    public static void cureInfection(UUID uuid) {
        ENTRIES.remove(uuid);
    }

    /** 驱散给定位置 reach 范围内、同维度的所有毒雾云（供悦灵净化调用）。 */
    public static void dissipateCloudsNear(ServerWorld world, Vec3d pos, double reach) {
        if (CLOUDS.isEmpty()) return;
        RegistryKey<World> key = world.getRegistryKey();
        double sq = reach * reach;
        CLOUDS.removeIf(c -> c.worldKey.equals(key) && c.squaredDistanceTo(pos.x, pos.y, pos.z) <= sq);
    }

    private static void tickClouds(ServerWorld world) {
        long now = world.getTime();
        MinecraftServer server = world.getServer();
        for (CloudData cloud : CLOUDS) {
            if (!cloud.worldKey.equals(world.getRegistryKey())) continue;
            if (now >= cloud.endTick) {
                CLOUDS.remove(cloud);
                continue;
            }
            // 1) 粒子表现（服务端广播，多人一致）
            if (now % CLOUD_PARTICLE_INTERVAL == 0) {
                emitCloudParticles(world, cloud);
            }
            // 2) 周期扫描：范围内每 1.5s 直接施加效果（站着才生效，离开即停）
            if (now >= cloud.nextScanTick) {
                cloud.nextScanTick = now + CLOUD_SCAN_INTERVAL;
                ServerPlayerEntity caster = server.getPlayerManager().getPlayer(cloud.casterUuid);
                if (caster == null) continue; // 施放者掉线则仅留粒子，不生效（需其白名单判定）
                Box box = new Box(
                        cloud.x - cloud.radius, cloud.y - cloud.radius, cloud.z - cloud.radius,
                        cloud.x + cloud.radius, cloud.y + cloud.radius, cloud.z + cloud.radius);
                double sqRadius = cloud.radius * cloud.radius;
                List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                        e -> e.isAlive() && e != caster
                                && cloud.squaredDistanceTo(e.getX(), e.getY(), e.getZ()) <= sqRadius);
                for (LivingEntity target : targets) {
                    if (WhitelistUtils.isProtected(caster, target)) {
                        // 友军：回 1 血 + 加速 I（短时，仅范围内维持）
                        target.heal(TICK_HEAL);
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, true, true));
                        world.spawnParticles(ParticleTypes.HEART,
                                target.getX(), target.getY() + target.getHeight() * 0.8, target.getZ(),
                                1, 0.3, 0.2, 0.3, 0.0);
                    } else {
                        // 敌人：掉 1 血 + 减速 I（短时，仅范围内维持）
                        target.damage(target.getDamageSources().magic(), TICK_DAMAGE);
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, true, true));
                    }
                }
            }
        }
    }

    private static void emitCloudParticles(ServerWorld world, CloudData cloud) {
        // 主体毒雾：贴地铺开的墨绿色中毒粒子
        world.spawnParticles(CLOUD_POISON_DUST,
                cloud.x, cloud.y + 0.5, cloud.z,
                8, cloud.radius * 0.6, 0.45, cloud.radius * 0.6, 0.0);
        // 低频飘散的孢子，增强"雾"的体积感
        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                cloud.x, cloud.y + 0.3, cloud.z,
                2, cloud.radius * 0.5, 0.3, cloud.radius * 0.5, 0.0);
    }

    private static final class CloudData {
        final UUID casterUuid;
        final RegistryKey<World> worldKey;
        final double x;
        final double y;
        final double z;
        final double radius;
        long endTick;
        long nextScanTick;

        CloudData(UUID casterUuid, RegistryKey<World> worldKey, double x, double y, double z,
                  double radius, long endTick, long nextScanTick) {
            this.casterUuid = casterUuid;
            this.worldKey = worldKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.endTick = endTick;
            this.nextScanTick = nextScanTick;
        }

        double squaredDistanceTo(double ox, double oy, double oz) {
            double dx = x - ox;
            double dy = y - oy;
            double dz = z - oz;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
