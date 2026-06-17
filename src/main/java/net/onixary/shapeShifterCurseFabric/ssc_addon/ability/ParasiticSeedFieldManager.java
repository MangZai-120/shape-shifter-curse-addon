/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.ParasiticFruitSeedPower;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 寄生果蝠主技能「灵果寄生」落地种子圈管理器。
 * <p>
 * 投掷物未命中生物、落地时生成一个「灵果种子圈」：地面绿色混凝土核心（block_marker 粒子，不改世界方块）
 * + 绿色治疗环粒子（学 RC4 healing_crystal 并绿色化）；半径 1 内任意玩家走入即拾取，
 * 获得寄生效果（时长 = 最大时长 − 已落地时长，由施法者持有的 power 施加，友/敌果实自动判定）；
 * 寿命耗尽或被拾取后消失，消失时喷绿色粒子。
 * <p>
 * 服务端权威，所有粒子由服务端 spawnParticles 广播，保证多人主客机一致。
 */
public final class ParasiticSeedFieldManager {
    /** 拾取 / 治疗环半径（格） */
    public static final double FIELD_RADIUS = 1.0;
    /** 治疗环绿色粉尘 */
    private static final DustParticleEffect RING_DUST = new DustParticleEffect(new Vector3f(0.30f, 0.85f, 0.30f), 1.0f);

    private static final CopyOnWriteArrayList<SeedField> FIELDS = new CopyOnWriteArrayList<>();

    private ParasiticSeedFieldManager() {
    }

    private static final class SeedField {
        final UUID casterUuid;
        final RegistryKey<World> worldKey;
        final Vec3d pos;
        final long spawnTick;
        final long endTick;
        final UUID standUuid;
        final boolean twinPod;
        float ringProgress;

        SeedField(UUID casterUuid, RegistryKey<World> worldKey, Vec3d pos, long spawnTick, long endTick, UUID standUuid, boolean twinPod) {
            this.casterUuid = casterUuid;
            this.worldKey = worldKey;
            this.pos = pos;
            this.spawnTick = spawnTick;
            this.endTick = endTick;
            this.standUuid = standUuid;
            this.twinPod = twinPod;
        }
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(ParasiticSeedFieldManager::onWorldTick);
    }

    /** 投掷物落地时调用：在落点生成一个灵果种子圈，寿命 = lifeTicks。双生种荷时核心图标为双生种荷。 */
    public static void spawnField(ServerPlayerEntity caster, ServerWorld world, Vec3d pos, int lifeTicks, boolean twinPod) {
        long now = world.getTime();
        // 悬浮的核心图标（small armor_stand 头戴，仿 RC4 healing_crystal）：双生种荷时为双生种荷，否则火把花种子
        ArmorStandEntity stand = new ArmorStandEntity(world, pos.x, pos.y, pos.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        NbtCompound nbt = new NbtCompound();
        stand.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Small", true);
        nbt.putBoolean("Marker", true);
        nbt.putBoolean("NoBasePlate", true);
        stand.readCustomDataFromNbt(nbt);
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(twinPod
                ? net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.TWIN_POD
                : Items.TORCHFLOWER_SEEDS));
        world.spawnEntity(stand);

        FIELDS.add(new SeedField(caster.getUuid(), world.getRegistryKey(), pos, now, now + Math.max(20, lifeTicks), stand.getUuid(), twinPod));
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        world.spawnParticles(ParticleTypes.WARPED_SPORE, pos.x, pos.y + 0.2, pos.z, 30, 0.5, 0.3, 0.5, 0.02);
    }

    private static void onWorldTick(ServerWorld world) {
        if (FIELDS.isEmpty()) return;
        long now = world.getTime();
        MinecraftServer server = world.getServer();
        for (SeedField f : FIELDS) {
            if (!f.worldKey.equals(world.getRegistryKey())) continue;

            // 到期消失 + 绿色粒子
            if (now >= f.endTick) {
                killStand(world, f);
                spawnDisappearParticles(world, f.pos);
                FIELDS.remove(f);
                continue;
            }

            // 视觉：绿色混凝土核心 + 旋转绿色治疗环
            drawField(world, f, now);

            // 半径 1 内任意存活生物触发（玩家+怪物+动物，友/敌果由白名单判定）；排除种子圈 armor_stand。
            // 允许施法者本人触发（客机可吃自己种子），但需种子落地 ≥10t 后，避免刚扔出被自己立即踩发
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(f.casterUuid);
            if (caster == null) continue;   // 施法者离线则保留种子圈，等其上线或自然到期
            boolean casterArmed = now - f.spawnTick >= 10;
            Box box = new Box(f.pos.subtract(FIELD_RADIUS, FIELD_RADIUS, FIELD_RADIUS),
                    f.pos.add(FIELD_RADIUS, FIELD_RADIUS, FIELD_RADIUS));
            double sqRadius = FIELD_RADIUS * FIELD_RADIUS;
            List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && !e.getUuid().equals(f.standUuid) && !e.isSpectator()
                            && (casterArmed || e != caster)
                            && e.squaredDistanceTo(f.pos) <= sqRadius);
            if (!nearby.isEmpty()) {
                for (LivingEntity picker : nearby) {
                    // 触发后按生物交战状态定寿命（非交战 15s / 交战 5s，同命中）；双生种荷时扩散额外 1 人/无人叠 2 层
                    ParasiticFruitSeedPower.plantSeedSpread(caster, picker, f.twinPod);
                }
                spawnPickupParticles(world, f.pos);
                killStand(world, f);
                FIELDS.remove(f);
            }
        }
    }

    /** 绿色混凝土核心 + 旋转绿色治疗环（学 RC4 healing_crystal 并绿色化，半径 1）。 */
    private static void drawField(ServerWorld world, SeedField f, long now) {
        double x = f.pos.x, y = f.pos.y, z = f.pos.z;
        // 悬浮方块缓慢自转（仿 RC4 healing_crystal）
        Entity stand = world.getEntity(f.standUuid);
        if (stand != null) {
            stand.setYaw(stand.getYaw() + 5.0f);
        }
        // 旋转治疗环（半径 1 的 8 点圈）
        f.ringProgress += 0.12f;
        double rot = f.ringProgress;
        for (int i = 0; i < 8; i++) {
            double a = 2 * Math.PI * i / 8 + rot;
            double px = x + FIELD_RADIUS * Math.cos(a);
            double pz = z + FIELD_RADIUS * Math.sin(a);
            world.spawnParticles(RING_DUST, px, y + 0.15, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // 周期绿星 + 青绿孢子上飘
        if (now % 6 == 0) {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.8, z, 1, 0.25, 0.4, 0.25, 0.0);
        }
        world.spawnParticles(ParticleTypes.WARPED_SPORE, x, y + 0.6, z, 2, 0.35, 0.3, 0.35, 0.01);
    }

    private static void spawnPickupParticles(ServerWorld world, Vec3d pos) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.PLAYERS, 0.9f, 1.3f);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.3, pos.z, 18, 0.4, 0.5, 0.4, 0.0);
        world.spawnParticles(ParticleTypes.WARPED_SPORE, pos.x, pos.y + 0.3, pos.z, 20, 0.5, 0.4, 0.5, 0.02);
    }

    private static void spawnDisappearParticles(ServerWorld world, Vec3d pos) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.PLAYERS, 0.6f, 0.9f);
        world.spawnParticles(ParticleTypes.WARPED_SPORE, pos.x, pos.y + 0.2, pos.z, 24, 0.5, 0.3, 0.5, 0.03);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.2, pos.z, 10, 0.4, 0.3, 0.4, 0.0);
    }

    /** 移除种子圈的悬浮方块 armor_stand。 */
    private static void killStand(ServerWorld world, SeedField f) {
        Entity stand = world.getEntity(f.standUuid);
        if (stand != null) stand.discard();
    }
}
