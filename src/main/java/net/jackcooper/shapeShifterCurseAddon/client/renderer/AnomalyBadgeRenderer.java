package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jackcooper.shapeShifterCurseAddon.anomaly.AnomalyLedger;
import net.jackcooper.shapeShifterCurseAddon.client.AnomalyClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Small, depth-tested living-entity badges drawn from the existing authoritative anomaly mirror. */
@Environment(EnvType.CLIENT)
public final class AnomalyBadgeRenderer {
    private static final Identifier ATLAS = new Identifier("ssc_addon", "textures/gui/anomaly/badges64.png");
    private static final float[][] CORNERS = {{1, 0}, {1, 1}, {0, 1}, {0, 0}};
    // Render-thread scratch objects; no matrices/vectors are allocated for individual badges.
    private static final Matrix4f BILLBOARD = new Matrix4f();
    private static final Vector3f RIGHT = new Vector3f();

    private AnomalyBadgeRenderer() {}

    public static void render(WorldRenderContext context) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.options.hudHidden
                || AnomalyClient.trackedCount() == 0 || context.consumers() == null) return;
        var camera = context.camera();
        var cameraPosition = camera.getPos();
        var right = RIGHT.set(-1, 0, 0).rotate(camera.getRotation());
        float delta = context.tickDelta();
        VertexConsumer vertices = null;

        // This tick-cached list contains only synchronized targets, including NPCs; no world scan.
        var targets = AnomalyClient.accumulationTargets();
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            if (target.isSpectator() || target.isInvisibleTo(client.player)
                    || target == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()) continue;
            var amounts = AnomalyClient.accumulation(target);
            if (amounts.isEmpty()) continue;
            double x = MathHelper.lerp(delta, target.prevX, target.getX()) - cameraPosition.x;
            double y = MathHelper.lerp(delta, target.prevY, target.getY()) - cameraPosition.y;
            double z = MathHelper.lerp(delta, target.prevZ, target.getZ()) - cameraPosition.z;
            if (x * x + y * y + z * z > AnomalyBadgeLayout.MAX_DISTANCE_SQUARED
                    || DomainRenderer.blocksTargetingClient(target)) continue;
            if (context.frustum() != null && !context.frustum().isVisible(target.getBoundingBox().expand(1))) continue;

            // Project the body bounds onto camera-right so diagonal views also leave a clear gap.
            float radius = AnomalyBadgeLayout.bodyRadius(target.getWidth(),target.getHeight()) * (Math.abs(right.x) + Math.abs(right.z))
                    + target.getHeight() * .5f * Math.abs(right.y);
            float scale = AnomalyBadgeLayout.displayScale(target instanceof PlayerEntity,target.getHealth());
            if (vertices == null) {
                // Normal text depth testing hides badges behind walls; no see-through layer.
                // Full-bright white vertices preserve the authored metallic highlights and colours.
                vertices = context.consumers().getBuffer(RenderLayer.getText(ATLAS));
            }
            BILLBOARD.set(context.matrixStack().peek().getPositionMatrix())
                    .translate((float)x, (float)y + AnomalyBadgeLayout.top(target.getHeight(), amounts.size(), scale), (float)z);
            faceCamera(BILLBOARD, camera.getRotation());
            scaleAndOffset(BILLBOARD, radius, scale);
            badges(vertices, BILLBOARD, amounts, AnomalyBadgeLayout.SIZE * .5f);
        }
    }

    static void faceCamera(Matrix4f matrix, Quaternionfc cameraRotation) {
        // Camera's local +Z is its look direction. Like vanilla FlyingItemEntityRenderer,
        // turn the quad around Y so +X is screen-right and its +Z front faces the viewer.
        matrix.rotate(cameraRotation).rotateY((float)Math.PI);
    }

    static void scaleAndOffset(Matrix4f matrix, float bodyRadius, float scale) {
        // Keep the body radius in world units; shrinking it would pull the UI inside large NPCs.
        matrix.translate(bodyRadius + AnomalyBadgeLayout.MODEL_GAP, 0, 0).scale(scale);
    }

    static int badges(VertexConsumer vertices, Matrix4f matrix, Map<String, Double> amounts, float left) {
        int slot = 0;
        for (int i = 0; i < AnomalyBadgeLayout.ELEMENTS.size(); i++) {
            var element = AnomalyBadgeLayout.ELEMENTS.get(i);
            Double amount = amounts.get(element.id());
            if (amount == null || !Double.isFinite(amount) || !(amount > 0)) continue;
            float x = left + AnomalyBadgeLayout.x(slot), y = AnomalyBadgeLayout.y(slot++);
            tile(vertices, matrix, element.baseTile(), x, y, 0);
            progress(vertices, matrix, element.progressTile(), x, y,
                    AnomalyBadgeLayout.fraction(amount, AnomalyLedger.MAXIMUM));
        }
        return slot;
    }

    /** The static 64px face and full-colour progress texture share one atlas and buffer. */
    static void tile(VertexConsumer vertices, Matrix4f matrix, int tile, float x, float y, float z) {
        float half = AnomalyBadgeLayout.SIZE * .5f;
        float u = AnomalyBadgeLayout.u(tile), v = AnomalyBadgeLayout.v(tile);
        float uEnd = u + 1f / AnomalyBadgeLayout.ATLAS_COLUMNS;
        float vEnd = v + 1f / AnomalyBadgeLayout.ATLAS_ROWS;
        vertex(vertices, matrix, x - half, y + half, z, u, v);
        vertex(vertices, matrix, x - half, y - half, z, u, vEnd);
        vertex(vertices, matrix, x + half, y - half, z, uEnd, vEnd);
        vertex(vertices, matrix, x + half, y + half, z, uEnd, v);
    }

    /** Clockwise from twelve o'clock; at most five triangles, never a frame atlas or per-pixel mesh. */
    static void progress(VertexConsumer vertices, Matrix4f matrix, int tile, float x, float y, double fraction) {
        if (!(fraction > 0) || !Double.isFinite(fraction)) return;
        if (fraction >= 1) { tile(vertices, matrix, tile, x, y, .001f); return; }
        double end = fraction * Math.PI * 2;
        float fromX = .5f, fromY = 0;
        for (int corner = 0; corner < 4; corner++) {
            double angle = Math.PI * .25 + corner * Math.PI * .5;
            if (angle >= end) break;
            float toX = CORNERS[corner][0], toY = CORNERS[corner][1];
            sector(vertices, matrix, tile, x, y, fromX, fromY, toX, toY);
            fromX = toX; fromY = toY;
        }
        double dx = Math.sin(end), dy = -Math.cos(end), scale = .5 / Math.max(Math.abs(dx), Math.abs(dy));
        sector(vertices, matrix, tile, x, y, fromX, fromY,
                (float)(.5 + dx * scale), (float)(.5 + dy * scale));
    }

    private static void sector(VertexConsumer vertices, Matrix4f matrix, int tile, float x, float y,
                               float fromX, float fromY, float toX, float toY) {
        // RenderLayer.getText uses QUADS: the second triangle is degenerate, the first faces the camera.
        local(vertices, matrix, tile, x, y, .5f, .5f);
        local(vertices, matrix, tile, x, y, toX, toY);
        local(vertices, matrix, tile, x, y, fromX, fromY);
        local(vertices, matrix, tile, x, y, fromX, fromY);
    }

    private static void local(VertexConsumer vertices, Matrix4f matrix, int tile, float x, float y, float u, float v) {
        vertex(vertices, matrix, x + (u - .5f) * AnomalyBadgeLayout.SIZE,
                y + (.5f - v) * AnomalyBadgeLayout.SIZE, .001f,
                AnomalyBadgeLayout.u(tile) + u / AnomalyBadgeLayout.ATLAS_COLUMNS,
                AnomalyBadgeLayout.v(tile) + v / AnomalyBadgeLayout.ATLAS_ROWS);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f m, float x, float y, float z,
                               float u, float v) {
        // VertexConsumer's matrix convenience method allocates a vector for each vertex on 1.20.1.
        vertices.vertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                        m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                        m.m02() * x + m.m12() * y + m.m22() * z + m.m32())
                .color(255, 255, 255, 255).texture(u, v).light(0xF000F0).next();
    }
}
