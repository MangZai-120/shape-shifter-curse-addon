/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.InfectionSporeManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.List;

/**
 * 寄生果蝠次要技能：感染孢子炸弹的投掷物。
 * 飞行时拖出绿色粒子，命中方块或生物时无伤害爆炸，向 EXPLOSION_RADIUS 范围内
 * 非白名单生物施加感染孢子状态。
 */
public class InfectionSporeBombEntity extends ThrownItemEntity {
    /** 爆炸特效作用半径 */
    public static final double EXPLOSION_RADIUS = 4.0;
    /** 默认感染/治疗时长（10s）：命中生物与落地毒雾云统一 */
    public static final int DEFAULT_INFECTION_TICKS = 200;
    /** 墨绿色中毒粒子（与 StatusEffects.POISON 视觉色调一致） */
    private static final DustParticleEffect POISON_DUST = new DustParticleEffect(new Vector3f(0.30f, 0.50f, 0.10f), 1.0f);

    public InfectionSporeBombEntity(EntityType<? extends InfectionSporeBombEntity> entityType, World world) {
        super(entityType, world);
    }

    public InfectionSporeBombEntity(World world, LivingEntity owner) {
        super(SscAddon.INFECTION_SPORE_BOMB_ENTITY, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        // 视觉模型：史莱姆球
        return Items.SLIME_BALL;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        // 飞行尾迹：墨绿色中毒粒子（轻量，每 tick 2 个）
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(POISON_DUST,
                    this.getX(), this.getY() + 0.1, this.getZ(),
                    2, 0.06, 0.06, 0.06, 0.0);
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (this.getWorld().isClient) return;
        if (this.isRemoved()) return;

        explode();
        // 落地（命中方块）时额外生成一团滞留毒雾云：进入者按云剩余寿命被感染/治疗
        if (hitResult.getType() == HitResult.Type.BLOCK
                && this.getWorld() instanceof ServerWorld sw
                && this.getOwner() instanceof ServerPlayerEntity caster) {
            // 落地额外特效：药水粒子向上飘散
            spawnRisingPotionParticles(sw, hitResult.getPos());
            InfectionSporeManager.spawnCloud(caster, sw, hitResult.getPos(),
                    InfectionSporeManager.CLOUD_RADIUS, DEFAULT_INFECTION_TICKS);
        }
        this.discard();
    }

    /** 通用碰撞预过滤：忽略发射者本人。 */
    @Override
    protected boolean canHit(net.minecraft.entity.Entity entity) {
        return super.canHit(entity) && entity != this.getOwner();
    }

    private void explode() {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        Vec3d pos = this.getPos();

        // 爆炸特效：粒子 + 音效（无任何实体伤害）
        // 弹出节奏不变：物品碎片散射（贴图改为史莱姆球） + 主体粒子改为墨绿色中毒色
        ItemStackParticleEffect itemFx = new ItemStackParticleEffect(ParticleTypes.ITEM, Items.SLIME_BALL.getDefaultStack());
        world.spawnParticles(itemFx, pos.x, pos.y, pos.z, 18, 0.4, 0.4, 0.4, 0.15);
        world.spawnParticles(POISON_DUST, pos.x, pos.y + 0.2, pos.z, 30, 0.6, 0.4, 0.6, 0.05);
        world.spawnParticles(POISON_DUST, pos.x, pos.y + 0.2, pos.z, 25, 1.5, 1.0, 1.5, 0.0);
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8f, 1.4f);

        // 仅当发射者是服务端玩家时进行白名单判定与感染
        ServerPlayerEntity caster = (this.getOwner() instanceof ServerPlayerEntity sp) ? sp : null;
        if (caster == null) return;

        Box box = new Box(pos.subtract(EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS),
                pos.add(EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS));
        double sqRadius = EXPLOSION_RADIUS * EXPLOSION_RADIUS;
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e.squaredDistanceTo(pos) <= sqRadius && e != caster);

        for (LivingEntity target : targets) {
            // 受白名单保护的友方（玩家、宠物等）→ 接受治疗孢子（每 3s +1HP，持续 15s）
            // 非白名单目标 → 接受感染孢子
            if (WhitelistUtils.isProtected(caster, target)) {
                InfectionSporeManager.applyFriendHeal(caster, target, DEFAULT_INFECTION_TICKS);
            } else {
                InfectionSporeManager.infect(caster, target, DEFAULT_INFECTION_TICKS);
            }
        }
    }

    /** 落地额外特效：药水粒子向上飘散（count=0 时后三位为速度，vy>0 即上飘）。 */
    private void spawnRisingPotionParticles(ServerWorld w, Vec3d pos) {
        for (int i = 0; i < 14; i++) {
            double vx = (this.random.nextDouble() - 0.5) * 0.05;
            double vy = 0.08 + this.random.nextDouble() * 0.08;   // 正向上 → 上飘
            double vz = (this.random.nextDouble() - 0.5) * 0.05;
            w.spawnParticles(ParticleTypes.EFFECT,
                    pos.x + (this.random.nextDouble() - 0.5) * 0.8,
                    pos.y + 0.1,
                    pos.z + (this.random.nextDouble() - 0.5) * 0.8,
                    0, vx, vy, vz, 1.0);
        }
    }
}
