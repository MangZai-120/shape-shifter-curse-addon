package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import com.google.gson.JsonParser;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Checks the rendered model tip against travel, independently of spawn/tracker yaw and pitch. */
public final class SpellFrostSpikeDirectionTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (var stream = SpellFrostSpikeDirectionTest.class.getResourceAsStream(
                "/assets/ssc_addon/models/item/frost_thorn_model.json")) {
            if (stream == null) throw new AssertionError("Missing actual frost thorn model");
            var json = JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var elements = json.getAsJsonArray("elements");
            // The narrow front-most piece is the tip. Reading the asset prevents a guessed model axis.
            var tip = elements.get(0).getAsJsonObject();
            check(tip.getAsJsonArray("from").get(2).getAsDouble() == -2,
                    "Actual model tip is the negative Z end");
            var ground = json.getAsJsonObject("display").getAsJsonObject("ground");
            for (var angle : ground.getAsJsonArray("rotation"))
                check(angle.getAsDouble() == 0, "Actual ground transform does not rotate the tip axis");
        }
        for (int yaw = -180; yaw <= 180; yaw += 10) {
            for (int pitch = -90; pitch <= 90; pitch += 10) {
                double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                var direction = new Vec3d(-Math.sin(y) * Math.cos(p), -Math.sin(p),
                        Math.cos(y) * Math.cos(p));
                for (double speed : new double[]{0.25, 0.75, 1.5, 4.0}) {
                    MatrixStack matrices = new MatrixStack();
                    // Deliberately wrong entity angles reproduce the remote/stale-angle failure.
                    SpellFrostSpikeRenderer.applyFlightRotation(matrices, direction.multiply(speed), 73, -29);
                    Vector3f tip = new Vector3f(0, 0, -1)
                            .mulDirection(matrices.peek().getPositionMatrix()).normalize();
                    check(new Vec3d(tip.x, tip.y, tip.z).distanceTo(direction) < 1e-5,
                            "Rendered tip follows velocity at yaw=" + yaw + ", pitch=" + pitch + ", speed=" + speed);
                }
            }
        }
        // A stopped projectile keeps its last meaningful orientation instead of snapping to a default axis.
        MatrixStack stationary = new MatrixStack();
        SpellFrostSpikeRenderer.applyFlightRotation(stationary, Vec3d.ZERO, -90, -45);
        Vector3f tip = new Vector3f(0, 0, -1).mulDirection(stationary.peek().getPositionMatrix()).normalize();
        check(new Vec3d(tip.x, tip.y, tip.z).distanceTo(new Vec3d(Math.sqrt(.5), Math.sqrt(.5), 0)) < 1e-5,
                "Zero velocity preserves last yaw/pitch");
        System.out.println("Frost spike rendered flight direction: " + checks + " checks passed.");
    }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
