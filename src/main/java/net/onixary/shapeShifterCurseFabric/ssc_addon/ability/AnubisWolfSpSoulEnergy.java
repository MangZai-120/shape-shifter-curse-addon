package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

/**
* SP阿努比斯之狼 - 灵魂能量系统
* 通过击杀/战斗积累灵魂能量（最大100点），满能量时下一次死亡领域施放将被增强：
* - 半径 24→32
* - 充能时间 2秒→1秒
* - 持续时间 15秒→20秒
* - 凋零II 替代 凋零I
* - 自动召唤6只冥狼
* 能量满时消耗全部能量施放增强领域。
*/
public class AnubisWolfSpSoulEnergy {

private AnubisWolfSpSoulEnergy() {
}

// ==================== 常量 ====================
/** 最大灵魂能量 */
public static final int MAX_ENERGY = 100;
/** 在死亡领域内击杀获得的能量 */
private static final int KILL_IN_DOMAIN_ENERGY = 20;
/** 冥狼击杀获得的能量 */
private static final int MINION_KILL_ENERGY = 10;
/** 普通击杀获得的能量 */
private static final int REGULAR_KILL_ENERGY = 5;

// ==================== 公开接口 ====================

/**
* 获取玩家当前灵魂能量
*/
public static int getEnergy(ServerPlayerEntity player) {
return PowerUtils.getResourceValue(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY);
}

/**
* 增加灵魂能量（自动限制上限）
*/
public static void addEnergy(ServerPlayerEntity player, int amount) {
int current = getEnergy(player);
int newValue = Math.min(current + amount, MAX_ENERGY);

// 直接存储到Apoli资源
PowerUtils.setResourceValueAndSync(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY, newValue);

// 刚好满能量时播放提示音
if (current < MAX_ENERGY && newValue >= MAX_ENERGY) {
player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.6f, 1.2f);
}
}

/**
* 检查灵魂能量是否已满
*/
public static boolean isFullEnergy(ServerPlayerEntity player) {
return getEnergy(player) >= MAX_ENERGY;
}

/**
* 消耗全部灵魂能量（施放增强领域时调用）
*/
public static void consumeEnergy(ServerPlayerEntity player) {
PowerUtils.setResourceValueAndSync(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY, 0);
}

/**
* 清除玩家能量（断线/变形时）
* 直接将Apoli资源设为0
*/
public static void clearPlayer(ServerPlayerEntity player) {
if (player != null) {
PowerUtils.setResourceValueAndSync(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY, 0);
}
}

/**
* 设置灵魂能量（由set_mana命令调用）
*/
public static void setEnergy(ServerPlayerEntity player, int amount) {
int clamped = Math.min(Math.max(amount, 0), MAX_ENERGY);
PowerUtils.setResourceValueAndSync(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY, clamped);
}

// ==================== 事件注册 ====================

/**
* 注册击杀事件监听器（在SscAddon.onInitialize中调用）
*/
public static void registerEvents() {
ServerLivingEntityEvents.AFTER_DEATH.register(AnubisWolfSpSoulEnergy::onEntityDeath);
}

/**
* 实体死亡时的处理逻辑
*/
private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
if (entity.getWorld().isClient()) return;

// 情况1：玩家直接击杀
if (damageSource.getAttacker() instanceof ServerPlayerEntity killer) {
if (!FormUtils.isForm(killer, FormIdentifiers.ANUBIS_WOLF_SP)) return;

// 检查击杀是否发生在死亡领域内
if (AnubisWolfSpDeathDomain.hasActiveDomain(killer.getUuid())
&& AnubisWolfSpDeathDomain.isInActiveDomain(killer.getUuid(), entity.getBlockPos())) {
// 增强领域范围内击杀不获取能量
if (!AnubisWolfSpDeathDomain.isEnhancedDomain(killer.getUuid())) {
addEnergy(killer, KILL_IN_DOMAIN_ENERGY);
}
} else {
addEnergy(killer, REGULAR_KILL_ENERGY);
}
return;
}

// 情况2：冥狼击杀（AnubisWolfMinionEntity的owner获得能量）
if (damageSource.getAttacker() instanceof AnubisWolfMinionEntity wolf) {
java.util.UUID ownerUuid = wolf.getMinionOwnerUUID();
if (ownerUuid == null) return;

PlayerEntity ownerEntity = entity.getWorld().getPlayerByUuid(ownerUuid);
if (ownerEntity instanceof ServerPlayerEntity ownerPlayer) {
if (!FormUtils.isForm(ownerPlayer, FormIdentifiers.ANUBIS_WOLF_SP)) return;
// 增强领域范围内冥狼击杀不获取能量
if (AnubisWolfSpDeathDomain.isEnhancedDomain(ownerUuid)
&& AnubisWolfSpDeathDomain.isInActiveDomain(ownerUuid, entity.getBlockPos())) {
return;
}
addEnergy(ownerPlayer, MINION_KILL_ENERGY);
}
}
}
}
