/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.entity.InfectionSporeBombEntity;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

/**
 * 寄生果蝠次要技能：感染孢子炸弹。
 * 投出一颗西瓜种子样式的孢子炸弹，落地或撞击生物时无伤害爆炸，4 格内
 * 非白名单生物被施加感染孢子状态（参见 InfectionSporeManager）。
 */
public class ParasiticSporeBombPower extends ActiveCooldownPower implements net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownHolder {

    // 以下均为默认值；运行时从 balance 快照读取（abilities.parasitic_spore_bomb）
    /** 投掷物初速度（与原版雪球速度相近） */
    private static final float PROJECTILE_SPEED = 1.4f;
    /** 散布抖动 */
    private static final float PROJECTILE_DIVERGENCE = 0.5f;
    private static final int ENERGY_COST = 1;

    /** balance 快照读取（快照未初始化回退默认常量） */
    private static final BalanceReader BAL = new BalanceReader("abilities.parasitic_spore_bomb");

    private final net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec spec;
    private final int cooldownTicks;
    /** 内部冷却结束 tick：客户端侧门禁用 */
    private long internalCooldownEndTime = 0L;

    public ParasiticSporeBombPower(PowerType<?> type, LivingEntity entity,
                                   net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec spec,
                                   HudRender hudRender, Active.Key key) {
        super(type, entity, Math.max(1, spec.cooldown()), hudRender, e -> {
        });
        this.spec = spec;
        this.cooldownTicks = spec.cooldown();
        this.setKey(key);
    }

    @Override
    public net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec cooldownSpec() {
        return spec;
    }

    public static PowerFactory<Power> createFactory() {
        return new PowerFactory<>(new Identifier("my_addon", "parasitic_spore_bomb"),
                net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec.addFields(new SerializableData()
                        .add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
                        .add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
                        400, SkillCastManager.START_ON_CAST),
                data -> {
                    var spec = net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec.read(data);
                    return (type, player) -> new ParasiticSporeBombPower(type, player, spec, data.get("hud_render"), data.get("key"));
                }
        ).allowCondition();
    }

    @Override
    public boolean canUse() {
        if (entity instanceof ServerPlayerEntity sp) {
            return net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.ready(sp, powerIdentifier());
        }
        return entity.getWorld().getTime() >= internalCooldownEndTime;
    }

    @Override
    public void onUse() {
        if (entity instanceof net.minecraft.server.network.ServerPlayerEntity syncPlayer
                && !net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.isPlayerReady(syncPlayer)) return;
        if (!(entity instanceof ServerPlayerEntity caster)) return;
        if (caster.getWorld().isClient) return;
        if (caster.hasStatusEffect(SscAddon.PURIFIED)) return;
        if (!canUse()) return;

        // 能量检查：不足则播放失败音效
        int energyCost = BAL.i("energy_cost", ENERGY_COST);
        if (!PowerUtils.hasResource(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, energyCost)) {
            caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4f, 1.7f);
            return;
        }
        PowerUtils.changeResourceValueAndSync(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, -energyCost);

        // 生成投掷物（贴图改为史莱姆球）
        InfectionSporeBombEntity bomb = new InfectionSporeBombEntity(caster.getWorld(), caster);
        bomb.setItem(net.minecraft.item.Items.SLIME_BALL.getDefaultStack());
        bomb.setOwner(caster);
        // 从眼部位置发射；速度向量与玩家视线一致
        bomb.setPos(caster.getX(), caster.getEyeY() - 0.1, caster.getZ());
        bomb.setVelocity(caster, caster.getPitch(), caster.getYaw(), 0.0f,
                (float) BAL.d("projectile_speed", PROJECTILE_SPEED),
                (float) BAL.d("projectile_divergence", PROJECTILE_DIVERGENCE));
        // 抵消投掷者的水平移动以保持初速一致
        Vec3d ownerVel = caster.getVelocity();
        bomb.setVelocity(bomb.getVelocity().add(ownerVel.x, caster.isOnGround() ? 0.0 : ownerVel.y, ownerVel.z));
        caster.getWorld().spawnEntity(bomb);

        caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.6f, 1.6f);

        internalCooldownEndTime = entity.getWorld().getTime() + cooldownTicks;
        this.use();
        net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.instant(caster, powerIdentifier());
    }

    /** 本 power 的稳定技能 ID（= power 注册路径）。 */
    public String powerIdentifier() {
        return type != null && type.getIdentifier() != null
                ? type.getIdentifier().toString() : "my_addon:parasitic_spore_bomb";
    }
}
