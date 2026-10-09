package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Checks the actual HUD transform, including fallback item lighting and matrix restoration. */
public final class SpellbookHudLayerTest {
    private static int checks;

    public static void main(String[] args) {
        for (float scale : new float[]{1, 2, 4}) {
            MatrixStack matrices = new MatrixStack();
            matrices.translate(16, 208, 0);
            matrices.scale(scale, scale, 1);
            Matrix4f original = new Matrix4f(matrices.peek().getPositionMatrix());
            Matrix3f normals = new Matrix3f(matrices.peek().getNormalMatrix());

            SpellbookHudLayer.push(matrices);
            Matrix4f layer = matrices.peek().getPositionMatrix();
            float previousDepth = Float.NEGATIVE_INFINITY;
            for (float depth : new float[]{0, 0.03f, 150, 260, 260.03f}) {
                Vector3f before = original.transformPosition(new Vector3f(31, 26, depth));
                Vector3f after = layer.transformPosition(new Vector3f(31, 26, depth));
                check(close(before.x, after.x) && close(before.y, after.y), "GUI scale " + scale + " preserves x/y at depth " + depth);
                check(after.z >= previousDepth && after.z < 50, "widget layers retain their order below chat background");
                previousDepth = after.z;
            }
            check(layer.transformPosition(new Vector3f(0, 0, 260)).z <
                    original.transformPosition(new Vector3f(0, 0, 100)).z, "chat text covers cooldown and rarity overlays");
            check(normals.equals(matrices.peek().getNormalMatrix(), 0), "fallback item lighting normals stay unchanged");
            matrices.pop();
            check(original.equals(matrices.peek().getPositionMatrix(), 0), "later HUD and chat receive the original transform");
            check(normals.equals(matrices.peek().getNormalMatrix(), 0), "normal matrix restores after the widget");
        }
        System.out.println("Spellbook HUD layer: " + checks + " transform checks passed.");
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 0.001f;
    }

    private static void check(boolean passed, String reason) {
        checks++;
        if (!passed) throw new AssertionError(reason);
    }
}
