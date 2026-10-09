package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.minecraft.client.util.math.MatrixStack;

/** Preserve the widget's internal depth order below vanilla chat (background 50, text 100). */
public final class SpellbookHudLayer {
    private static final float DEPTH_SCALE = 0.1f;

    private SpellbookHudLayer() {}

    public static void push(MatrixStack matrices) {
        matrices.push();
        // Scale positions only: x/y layout and item lighting normals remain unchanged.
        // Slot overlays 260 -> 26; default item depth 150 -> 15.
        matrices.peek().getPositionMatrix().scale(1, 1, DEPTH_SCALE);
    }
}
