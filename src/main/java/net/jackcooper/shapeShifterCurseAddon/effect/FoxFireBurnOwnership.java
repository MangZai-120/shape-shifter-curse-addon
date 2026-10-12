package net.jackcooper.shapeShifterCurseAddon.effect;

import net.jackcooper.shapeShifterCurseAddon.util.SscIgnitedEntityAccessor;
import net.minecraft.entity.LivingEntity;

import java.util.UUID;

/** 狐火归因：明确记录的最新 UUID 优先，离线不会退回历史施法者。 */
public record FoxFireBurnOwnership(UUID uuid, boolean recorded) {
    private static final String BURN_PREFIX = "ssc_burn:";
    private static final String UNOWNED = BURN_PREFIX + "unowned";
    private static final String LEGACY_PREFIX = "ssc_owner:";

    public static FoxFireBurnOwnership resolve(LivingEntity target) {
        if (target instanceof SscIgnitedEntityAccessor accessor && accessor.sscAddon$getIgniterUuid() != null) {
            return new FoxFireBurnOwnership(accessor.sscAddon$getIgniterUuid(), true);
        }
        FoxFireBurnOwnership burn = fromTags(target.getCommandTags(), BURN_PREFIX);
        return burn.recorded ? burn : fromTags(target.getCommandTags(), LEGACY_PREFIX);
    }

    private static FoxFireBurnOwnership fromTags(Iterable<String> tags, String prefix) {
        UUID owner = null;
        for (String tag : tags) {
            if (!tag.startsWith(prefix)) continue;
            if (tag.equals(UNOWNED)) return new FoxFireBurnOwnership(null, true);
            UUID candidate;
            try {
                candidate = UUID.fromString(tag.substring(prefix.length()));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            // 旧存档可能积累多个 UUID；无先后信息时不猜测归属。
            if (owner != null && !owner.equals(candidate)) return new FoxFireBurnOwnership(null, true);
            owner = candidate;
        }
        return new FoxFireBurnOwnership(owner, owner != null);
    }

    public static void record(LivingEntity target, UUID owner) {
        target.getCommandTags().removeIf(tag -> tag.startsWith(BURN_PREFIX));
        if (target instanceof SscIgnitedEntityAccessor accessor) accessor.sscAddon$setIgniterUuid(owner);
        // 保留 ssc_owner: 召唤物归属；狐火只写自己的专属前缀。
        target.addCommandTag(owner == null ? UNOWNED : BURN_PREFIX + owner);
    }
}
