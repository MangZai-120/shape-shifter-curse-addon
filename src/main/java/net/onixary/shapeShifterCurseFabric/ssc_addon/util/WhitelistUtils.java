package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;

import java.util.Set;
import java.util.UUID;

/**
 * 白名单工具类 - 统一判断技能是否应跳过某目标
 * <p>
 * 规则：
 * - 白名单为空：技能不影响所有玩家及其召唤物（驯服动物 / 以"owner:"标签关联的恕魔）
 * - 白名单非空：技能不影响白名单内生物及其召唤物
 */
public class WhitelistUtils {

    private WhitelistUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 返回 true 表示 attacker 的技能应跳过（不伤害）target。
     */
    public static boolean isProtected(ServerPlayerEntity attacker, LivingEntity target) {
        if (target == attacker) return true; // 不攻击自身

        Set<String> tags = attacker.getCommandTags();
        boolean whitelistEmpty = tags.stream().noneMatch(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX));

        if (whitelistEmpty) {
            // 保护：所有玩家
            if (target instanceof PlayerEntity) return true;
            // 保护：所有玩家拥有的驯服动物
            if (target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
                if (attacker.getServerWorld().getPlayerByUuid(tameable.getOwnerUuid()) != null) return true;
            }
            // 保护：带 "owner:" 标签的实体（即恕魔类召唤物）
            if (hasOwnerTag(target)) return true;
            return false;
        } else {
            // 保护：直接在白名单中的实体（玩家/生物 UUID）
            if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + target.getUuidAsString())) return true;
            // 保护：驯服动物的主人在白名单中
            if (target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
                if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + tameable.getOwnerUuid().toString())) return true;
            }
            // 保护：恕魔类召唤物的主人在白名单中
            for (String tag : target.getCommandTags()) {
                if (tag.startsWith("owner:")) {
                    String ownerUuid = tag.substring(6);
                    if (tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + ownerUuid)) return true;
                }
            }
            return false;
        }
    }

    /**
     * 通过 ownerUuid 查找玩家后调用 {@link #isProtected(ServerPlayerEntity, LivingEntity)}。
     * 适用于 FrostStorm 等非玩家施法者但有 ownerUuid 的实体。
     */
    public static boolean isProtected(UUID ownerUuid, ServerWorld world, LivingEntity target) {
        if (ownerUuid == null) return false;
        if (!(world.getPlayerByUuid(ownerUuid) instanceof ServerPlayerEntity owner)) return false;
        return isProtected(owner, target);
    }

    // ---- 私有辅助 ----

    private static boolean hasOwnerTag(LivingEntity entity) {
        return entity.getCommandTags().stream().anyMatch(t -> t.startsWith("owner:"));
    }
}
