package net.jackcooper.shapeShifterCurseAddon.anomaly;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Applies only to explicitly owned active teleports; safety evacuation is never intercepted globally. */
public final class AnomalyTeleportGuard {
    private AnomalyTeleportGuard() {}
    public static boolean isTeleportSpell(net.jackcooper.shapeShifterCurseAddon.spell.Spell spell) {
        if (spell == null || !spell.getId().getNamespace().equals("ssc_addon")) return false;
        return switch (spell.getId().getPath()) {
            case "space_blink", "space_recall", "pocket_space" -> true;
            default -> false;
        };
    }
    public static boolean reject(ServerPlayerEntity player) {
        if (!AnomalyEffects.teleportBlocked(player)) return false;
        player.sendMessage(Text.translatable("message.ssc_addon.anomaly.teleport_blocked"), true);
        return true;
    }
}
