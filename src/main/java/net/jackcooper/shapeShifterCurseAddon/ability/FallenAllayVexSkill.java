package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** 堕落悦灵召唤恼鬼：施放持续到最后一只恼鬼消失，之后按 power JSON 的 cooldown 起算（on_end）。 */
public final class FallenAllayVexSkill {

	public static final String SKILL_ID = "my_addon:form_fallen_allay_sp_active_vex_key_activation";
	private static final String CAST_TAG = "ssca_cast:";
	private static final String VEX_TAG = "ssc_fallen_allay_vex";
	private static final double SEARCH_RADIUS = 128.0;

	private FallenAllayVexSkill() {
	}

	public static boolean hasOwnedVex(ServerPlayerEntity owner, Entity except) {
		String ownerTag = "owner:" + owner.getUuidAsString();
		return !owner.getServerWorld().getEntitiesByClass(VexEntity.class, owner.getBoundingBox().expand(SEARCH_RADIUS),
				v -> v != except && v.isAlive() && v.getCommandTags().contains(VEX_TAG) && v.getCommandTags().contains(ownerTag)).isEmpty();
	}

    private static boolean hasOwnedVex(ServerPlayerEntity owner, Entity except, long castId) {
        String ownerTag = "owner:" + owner.getUuidAsString();
        String castTag = CAST_TAG + castId;
        return !owner.getServerWorld().getEntitiesByClass(VexEntity.class, owner.getBoundingBox().expand(SEARCH_RADIUS),
                v -> v != except && v.isAlive() && v.getCommandTags().contains(VEX_TAG)
                        && v.getCommandTags().contains(ownerTag) && v.getCommandTags().contains(castTag)).isEmpty();
    }

	/** 召唤动作之后调用：有恼鬼在场即进入持续阶段，一只都没召出按失败结算。 */
	public static void onSummoned(ServerPlayerEntity owner) {
		long castId = SkillCooldowns.currentCastId(owner, SKILL_ID);
        if (castId < 0) return;
        for (var vex : owner.getServerWorld().getEntitiesByClass(VexEntity.class, owner.getBoundingBox().expand(SEARCH_RADIUS),
                v -> v.isAlive() && v.getCommandTags().contains(VEX_TAG)
                        && v.getCommandTags().contains("owner:" + owner.getUuidAsString()))) {
            if (vex.getCommandTags().stream().noneMatch(tag -> tag.startsWith(CAST_TAG))) vex.addCommandTag(CAST_TAG + castId);
        }
        if (hasOwnedVex(owner, null, castId)) SkillCooldowns.released(owner, SKILL_ID, castId);
		else SkillCooldowns.failed(owner, SKILL_ID, castId);
	}

	/** 恼鬼死亡时调用：最后一只消失即结束施放。 */
	public static void onVexGone(ServerPlayerEntity owner, Entity vex) {
		for (String tag : vex.getCommandTags()) {
            if (!tag.startsWith(CAST_TAG)) continue;
            try {
                long castId = Long.parseLong(tag.substring(CAST_TAG.length()));
                if (!hasOwnedVex(owner, vex, castId)) SkillCooldowns.ended(owner, SKILL_ID, castId);
            } catch (NumberFormatException ignored) { }
        }
	}

	/** 兜底：恼鬼被卸载/清除（不走死亡）时，持续阶段也能结束，避免技能永久锁死。 */
	public static void watchdog(ServerPlayerEntity owner) {
		SkillCastManager.Cast cast = SkillCastManager.get(owner.getServerWorld()).control(owner.getUuid(), SKILL_ID);
		if (cast != null && cast.phase == SkillCastManager.PHASE_ACTIVE && !hasOwnedVex(owner, null, cast.castId)) {
			SkillCooldowns.ended(owner, SKILL_ID, cast.castId);
		}
	}
}
