package net.jackcooper.shapeShifterCurseAddon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.CastingCircleRenderer;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.DeathFinaleGeometry;
import net.jackcooper.shapeShifterCurseAddon.spell.DeathFinaleManager;
import net.jackcooper.shapeShifterCurseAddon.spell.DeathFinaleRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client presentation only. Danger is individualized by the server, never inferred from local whitelist settings. */
@Environment(EnvType.CLIENT)
public final class DeathFinaleClient {
    private static final Identifier FROST = new Identifier("minecraft", "textures/misc/powder_snow_outline.png");
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static ClientWorld world;
    private record View(UUID owner, Vec3d center, double radius, int duration, int elapsed,
                        boolean released, boolean danger, long received) {
        float age(float delta) { return elapsed + Math.max(0, world.getTime() - received) + delta; }
        Vec3d displayCenter(float delta) {
            var caster = world.getPlayerByUuid(owner);
            return !released && caster != null ? caster.getLerpedPos(delta) : center;
        }
    }
    private DeathFinaleClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DeathFinaleManager.SNAPSHOT, (client, handler, buf, sender) -> {
            var dimension = buf.readIdentifier();
            int count = buf.readVarInt();
            Map<UUID, View> incoming = new HashMap<>();
            for (int i = 0; i < count; i++) {
                UUID id = buf.readUuid(), owner = buf.readUuid();
                Vec3d center = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                double radius = buf.readDouble();
                int duration = buf.readVarInt(), elapsed = buf.readVarInt();
                incoming.put(id, new View(owner, center, radius, duration, elapsed, buf.readBoolean(), buf.readBoolean(), 0));
            }
            client.execute(() -> {
                if (client.world == null || !client.world.getRegistryKey().getValue().equals(dimension)) return;
                world = client.world;
                VIEWS.clear();
                incoming.forEach((id, v) -> VIEWS.put(id, new View(v.owner, v.center, v.radius, v.duration,
                        v.elapsed, v.released, v.danger, world.getTime())));
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { VIEWS.clear(); world = null; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.world) { VIEWS.clear(); world = client.world; }
            if (world != null) VIEWS.values().removeIf(v -> world.getTime() - v.received > 30);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(DeathFinaleClient::renderWorld);
        HudRenderCallback.EVENT.register(DeathFinaleClient::renderHud);
    }

    private static void renderWorld(WorldRenderContext context) {
        if (world == null || world != context.world() || context.consumers() == null) return;
        var camera = context.camera().getPos();
        var matrices = context.matrixStack();
        var vertices = context.consumers().getBuffer(RenderLayer.getDebugQuads());
        for (View v : VIEWS.values()) {
            float age = v.age(context.tickDelta());
            float progress = Math.min(1, age / v.duration);
            float alpha = v.released ? Math.max(0, 1 - (age - v.duration) / DeathFinaleRules.AFTERGLOW_TICKS) : 1;
            if (alpha <= 0) continue;
            Vec3d center = v.displayCenter(context.tickDelta());
            matrices.push();
            matrices.translate(center.x - camera.x, center.y + .035 - camera.y, center.z - camera.z);
            CastingCircleRenderer.drawPattern(vertices, matrices, 2.8, 0, age * .008, alpha,
                    DeathFinaleGeometry.STROKES, DeathFinaleGeometry.OCHRE);
            // True-radius horizontal boundary plus two upright great circles show vertical reach too.
            CastingCircleRenderer.ring(vertices, matrices.peek().getPositionMatrix(), v.radius, .055, 0,
                    DeathFinaleGeometry.OCHRE, alpha * .85f);
            for (int axis = 0; axis < 2; axis++) {
                matrices.push();
                matrices.multiply((axis == 0 ? RotationAxis.POSITIVE_X : RotationAxis.POSITIVE_Z).rotationDegrees(90));
                CastingCircleRenderer.ring(vertices, matrices.peek().getPositionMatrix(), v.radius, .025, 0,
                        DeathFinaleGeometry.OCHRE, alpha * .32f);
                matrices.pop();
            }
            // Thirteen large dial marks count down while the inner sand ring contracts.
            int remaining = (int) Math.ceil(13 * (1 - progress));
            for (int i = 0; i < remaining; i++) {
                double angle = -Math.PI / 2 + i * Math.PI * 2 / 13;
                CastingCircleRenderer.line(vertices, matrices.peek().getPositionMatrix(),
                        Math.cos(angle) * v.radius * .92, Math.sin(angle) * v.radius * .92,
                        Math.cos(angle) * v.radius, Math.sin(angle) * v.radius, .10,
                        DeathFinaleGeometry.LIGHT, alpha);
            }
            double movingRadius = v.released ? v.radius * Math.min(1, (age - v.duration) / 8)
                    : Math.max(0.1, v.radius * (1 - progress));
            CastingCircleRenderer.ring(vertices, matrices.peek().getPositionMatrix(), movingRadius, .065, 0,
                    DeathFinaleGeometry.LIGHT, alpha * .65f);
            matrices.pop();
        }
    }

    private static void renderHud(DrawContext ctx, float delta) {
        var client = MinecraftClient.getInstance();
        if (world == null || client.world != world || client.player == null || !client.player.isAlive()
                || client.player.isSpectator() || client.options.hudHidden) return;
        View danger = VIEWS.values().stream().filter(v -> v.danger && !v.released)
                .min(java.util.Comparator.comparingDouble(v -> v.duration - v.age(delta))).orElse(null);
        if (danger == null) return;
        float age = danger.age(delta), progress = Math.min(1, age / danger.duration);
        float pulse = (float) (.5 + .5 * Math.sin(age * (progress > .77f ? .63 : .31)));
        float opacity = .38f + .26f * progress + .10f * pulse;
        int width = ctx.getScaledWindowWidth(), height = ctx.getScaledWindowHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        ctx.setShaderColor(1f, .065f, .035f, opacity);
        try {
            ctx.drawTexture(FROST, 0, 0, 0f, 0f, width, height, width, height);
        } finally {
            ctx.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
        int seconds = DeathFinaleRules.secondsLeft((int) age, danger.duration);
        ctx.drawCenteredTextWithShadow(client.textRenderer,
                Text.translatable("hud.ssc_addon.death_finale.warning", seconds), width / 2, height / 5, 0xFF6655);
    }
}
