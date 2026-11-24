package net.jackcooper.shapeShifterCurseAddon.evolution;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Vanilla levels and progress describe the spendable bar, even after enchanting or /experience set. */
public final class EvolutionExperience {
    private EvolutionExperience() {}

    private static long barPoints(PlayerEntity player) {
        long width = EvolutionComponent.levelUpCost(player.experienceLevel);
        return Math.max(0L, Math.min(width - 1, Math.round((double) player.experienceProgress * width)));
    }

    public static int available(PlayerEntity player, EvolutionComponent comp) {
        int room = Math.max(0, EvolutionComponent.MAX_INVESTED_XP - comp.getExp());
        long points = barPoints(player);
        for (int level = player.experienceLevel; level > 0 && points < room; level--) {
            points += EvolutionComponent.levelUpCost(level - 1);
        }
        return (int) Math.min(room, points);
    }

    static boolean spend(ServerPlayerEntity player, int amount) {
        int level = player.experienceLevel;
        long remainder = barPoints(player) - amount;
        while (level > 0 && remainder < 0) remainder += EvolutionComponent.levelUpCost(--level);
        if (remainder < 0) return false;
        long width = EvolutionComponent.levelUpCost(level);
        float progress = (float) ((double) remainder / width);
        // At extreme command-set levels the float bar cannot represent every integer payment.
        if (progress >= 1 || Math.round((double) progress * width) != remainder) return false;
        player.setExperienceLevel(level); // also invalidates vanilla's last-sent experience
        player.experienceProgress = progress;
        player.totalExperience = Math.max(0, Math.max(0, player.totalExperience) - amount);
        return true;
    }
}
