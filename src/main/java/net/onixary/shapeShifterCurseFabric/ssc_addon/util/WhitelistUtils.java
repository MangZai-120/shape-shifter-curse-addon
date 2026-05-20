package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Angerable;
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
 * <p>
 * 服务端总开关 SSCAddonServerConfig#whitelistEnabled（默认 true）：
 * - 开启：保留原白名单行为（玩家/驯服宠物/恕魔友方豁免；白名单非空时仅保护白名单）
 * - 关闭：攻击性技能 isProtected → 永远 false；强化类 isBuffTarget → 跳过敌对/Monster/未驯服的 Angerable（蜂/野生狼/北极熊/铁傀儡/末影人/僵尸猪灵等）
 */
public class WhitelistUtils {

    /** 自定义模式标志 tag：存在=自定义模式（按列表判定）；缺失=默认模式（保护所有玩家+宠物，无视列表） */
    public static final String CUSTOM_MODE_TAG = "ssc_allay_wl_mode:custom";

    /** 生物白名单 tag 前缀。约定由"友军标记"物品写入玩家自身的 command tags：
     *  形如 {@code ssc_allay_wl_mob:<UUID>}。仅在自定义模式下生效。 */
    public static final String WHITELIST_MOB_TAG_PREFIX = "ssc_allay_wl_mob:";

    private WhitelistUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 是否处于自定义模式。默认模式下列表内容仍保留，但不参与判定。 */
    public static boolean isCustomMode(ServerPlayerEntity player) {
        return player.getCommandTags().contains(CUSTOM_MODE_TAG);
    }

    /** 设置模式，返回是否真的发生了变化。 */
    public static boolean setCustomMode(ServerPlayerEntity player, boolean custom) {
        if (custom) return player.addCommandTag(CUSTOM_MODE_TAG);
        return player.getCommandTags().remove(CUSTOM_MODE_TAG);
    }

    /** 读取玩家身上记录的生物白名单 UUID 列表（按 UUID 字符串排序，保证稳定）。 */
    public static java.util.List<UUID> getWhitelistedMobUuids(ServerPlayerEntity player) {
        java.util.List<UUID> result = new java.util.ArrayList<>();
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(WHITELIST_MOB_TAG_PREFIX)) {
                String body = tag.substring(WHITELIST_MOB_TAG_PREFIX.length());
                // 支持扩展格式 <UUID>:<typeNs>:<typePath>，取第一段作为 UUID
                String uuidPart = body.contains(":") ? body.substring(0, body.indexOf(':')) : body;
                try {
                    result.add(UUID.fromString(uuidPart));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        result.sort(java.util.Comparator.comparing(UUID::toString));
        return result;
    }

    /** 读取生物白名单条目附加的 typeId 字符串（"ns:path" 形式），不存在则返回 null。 */
    public static String getMobTypeId(ServerPlayerEntity player, UUID mobUuid) {
        String prefix = WHITELIST_MOB_TAG_PREFIX + mobUuid.toString() + ":";
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                return tag.substring(prefix.length()); // ns:path
            }
        }
        return null;
    }

    /** 移除某个生物白名单条目（不论有无 typeId 后缀），返回是否真的发生了变化。 */
    public static boolean removeMobFromWhitelist(ServerPlayerEntity player, UUID mobUuid) {
        String base = WHITELIST_MOB_TAG_PREFIX + mobUuid.toString();
        String prefix = base + ":";
        java.util.Set<String> tags = player.getCommandTags();
        boolean removed = tags.remove(base);
        // 同时移除带 typeId 后缀的形式
        java.util.Iterator<String> it = tags.iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    /** 判断某生物 UUID 是否在玩家的生物白名单内（兼容带 typeId 后缀的格式）。 */
    public static boolean isMobWhitelisted(ServerPlayerEntity player, UUID mobUuid) {
        String base = WHITELIST_MOB_TAG_PREFIX + mobUuid.toString();
        String prefix = base + ":";
        for (String tag : player.getCommandTags()) {
            if (tag.equals(base) || tag.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * 攻击性技能保护判定。返回 true 表示 attacker 的技能应跳过 target。
     */
    public static boolean isProtected(ServerPlayerEntity attacker, LivingEntity target) {
        if (target == attacker) return true;

        if (!SSCAddonConfig.server().whitelistEnabled) {
            return false;
        }

        Set<String> tags = attacker.getCommandTags();
        boolean customMode = tags.contains(CUSTOM_MODE_TAG);
        // 默认模式（未启用自定义）= 列表视为空，永远走"保护所有玩家+宠物"分支
        boolean whitelistEmpty = !customMode
                || tags.stream().noneMatch(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX));

        // 自定义模式下额外检查生物白名单（默认模式不生效，因为默认模式不区分名单）
        if (customMode && isMobWhitelisted(attacker, target.getUuid())) {
            return true;
        }

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
                String ownerUuid = extractOwnerUuid(tag);
                if (ownerUuid != null
                        && tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + ownerUuid)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 通过 ownerUuid 查找玩家后调用 isProtected。
     * 主人离线/跨维度时采用保守策略：保护玩家、已驯服宠物以及带 owner tag 的实体，
     * 避免持续实体（如冰风暴）在主人离线后误伤受保护目标。
     */
    public static boolean isProtected(UUID ownerUuid, ServerWorld world, LivingEntity target) {
        if (ownerUuid == null) return false;
        // 跨维度查找：使用 server.getPlayerManager 而非 world.getPlayerByUuid
        // 关服瞬间或客户端世界，getServer() 可能返回 null —— 防御性判空，避免 NPE
        var server = world.getServer();
        ServerPlayerEntity owner = (server == null) ? null : server.getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) {
            return isProtected(owner, target);
        }
        // 主人离线：保守保护玩家、已驯实体、带 owner tag 的生物
        if (!SSCAddonConfig.server().whitelistEnabled) return false;
        if (target instanceof PlayerEntity ||
            target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null)
            return true;
        return hasOwnerTag(target);
    }

    /**
     * 强化/治疗类技能的目标判定。返回 true 表示 target 应该接收 buff。
     */
    public static boolean isBuffTarget(ServerPlayerEntity caster, LivingEntity target) {
        if (target == caster) return true;

        if (!SSCAddonConfig.server().whitelistEnabled) {
            return !isHostileOrMonster(target);
        }

        Set<String> tags = caster.getCommandTags();
        boolean customMode = tags.contains(CUSTOM_MODE_TAG);
        boolean whitelistEmpty = !customMode
                || tags.stream().noneMatch(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX));

        if (customMode && isMobWhitelisted(caster, target.getUuid())) {
            return true;
        }

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
                String ownerUuid = extractOwnerUuid(tag);
                if (ownerUuid != null
                        && tags.contains(AllaySPGroupHeal.WHITELIST_TAG_PREFIX + ownerUuid)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean hasOwnerTag(LivingEntity entity) {
        return entity.getCommandTags().stream()
                .anyMatch(t -> t.startsWith("owner:") || t.startsWith("ssc_owner:"));
    }

    /**
     * 抽取 owner tag 中的 UUID 字符串。兼容 owner: 与 ssc_owner: 两种前缀。
     */
    private static String extractOwnerUuid(String tag) {
        if (tag.startsWith("ssc_owner:")) return tag.substring("ssc_owner:".length());
        if (tag.startsWith("owner:")) return tag.substring("owner:".length());
        return null;
    }

    private static boolean isHostileOrMonster(LivingEntity entity) {
        if (entity instanceof HostileEntity || entity instanceof Monster) return true;
        // 中立但受击会激怒的生物（蜂、野生狼、北极熊、铁傀儡、僵尸猪灵、末影人等）
        // 已驯服的 Angerable（如驯服狼）视为友好可治疗
        if (entity instanceof Angerable) {
            return !(entity instanceof TameableEntity tame) || tame.getOwnerUuid() == null;
        }
        return false;
    }
}