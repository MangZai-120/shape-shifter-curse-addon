package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;

public class SkillBlocker {
    private static final String SKILL_BLOCKED_PREFIX = "ssc_skill_blocked:";
    
    private SkillBlocker() {} // Utility class
    
    public static boolean isSkillBlocked(ServerPlayerEntity player, String form, String skill) {
        String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
        if (player.getCommandTags().contains(tag)) {
            return true;
        }
        
        String skillId = form + ":" + skill;
        return SSCAddonConfig.server().disabledSkills.contains(skillId);
    }
    
    public static void blockSkill(ServerPlayerEntity player, String form, String skill) {
        String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
        player.addCommandTag(tag);
    }
    
    public static void unblockSkill(ServerPlayerEntity player, String form, String skill) {
        String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
        player.getCommandTags().remove(tag);
    }
}