package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;

import java.util.Set;
import java.util.UUID;

/**
 * 白名单工具类 - 统一判断技能是否应跳过某目标
 *
 * 服务端总开关 SSCAddonServerConfig#whitelistEnabled（默认 true）：
 *  - 开启：保留原白名单行为（玩家/驯服宠物/恕魔友方豁免；白名单非空时仅保护白名单）
 *  - 关闭：攻击性技能 isProtected → 永远 false；强化类 isBuffTarget → 仅作用于非怪物/敌对生物
 */
public class WhitelistUtils {

private WhitelistUtils() {
throw new UnsupportedOperationException("Utility class");
}

/** 攻击性技能保护判定。返回 true 表示 attacker 的技能应跳过 target。 */
public static boolean isProtected(ServerPlayerEntity attacker, LivingEntity target) {
if (target == attacker) return true;

if (!SSCAddonConfig.server().whitelistEnabled) {
return false;
}

Set<String> tags = attacker.getCommandTags();
boolean whitelistEmpty = tags.stream().noneMatch(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX));

if (whitelistEmpty) {
if (target instanceof PlayerEntity) return true;
if (target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
if (attacker.getServerWorld().getPlayerByUuid(tameable.getOwnerUuid()) != null) return true;
}
return hasOwnerTag(target);
} else {
if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + target.getUuidAsString())) return true;
if (target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + tameable.getOwnerUuid().toString()))
return true;
}
for (String tag : target.getCommandTags()) {
if (tag.startsWith("owner:")) {
String ownerUuid = tag.substring(6);
if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + ownerUuid)) return true;
}
}
return false;
}
}

/** 通过 ownerUuid 查找玩家后调用 isProtected。 */
public static boolean isProtected(UUID ownerUuid, ServerWorld world, LivingEntity target) {
if (ownerUuid == null) return false;
if (!(world.getPlayerByUuid(ownerUuid) instanceof ServerPlayerEntity owner)) return false;
return isProtected(owner, target);
}

/** 强化/治疗类技能的目标判定。返回 true 表示 target 应该接收 buff。 */
public static boolean isBuffTarget(ServerPlayerEntity caster, LivingEntity target) {
if (target == caster) return true;

if (!SSCAddonConfig.server().whitelistEnabled) {
return !isHostileOrMonster(target);
}

Set<String> tags = caster.getCommandTags();
boolean whitelistEmpty = tags.stream().noneMatch(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX));

if (whitelistEmpty) {
if (target instanceof PlayerEntity) return true;
if (target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
if (caster.getServerWorld().getPlayerByUuid(tameable.getOwnerUuid()) != null) return true;
}
return hasOwnerTag(target);
} else {
if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + target.getUuidAsString())) return true;
if (target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + tameable.getOwnerUuid().toString()))
return true;
}
for (String tag : target.getCommandTags()) {
if (tag.startsWith("owner:")) {
String ownerUuid = tag.substring(6);
if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + ownerUuid)) return true;
}
}
return false;
}
}

private static boolean hasOwnerTag(LivingEntity entity) {
return entity.getCommandTags().stream().anyMatch(t -> t.startsWith("owner:"));
}

private static boolean isHostileOrMonster(LivingEntity entity) {
return entity instanceof HostileEntity || entity instanceof Monster;
}
}