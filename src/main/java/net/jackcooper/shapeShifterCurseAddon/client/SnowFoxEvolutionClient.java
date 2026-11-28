package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxMotion;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxState;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.SnowFoxEffects;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.input.Input;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;

public final class SnowFoxEvolutionClient {
    private static boolean primary, secondary;
    private static Perspective previous;
    private static ClientPlayerEntity viewed;
    private static CameraPose entry, lastPose, recoveryStart;
    private static int lastX = -200, lastZ = -200;

    public static void register() {
        SnowFoxEffects.register();
        WorldRenderEvents.AFTER_ENTITIES.register(SnowFoxEffects::render);
        HudRenderCallback.EVENT.register(SnowFoxEvolutionClient::hud);
        ClientTickEvents.END_CLIENT_TICK.register(SnowFoxEvolutionClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(SnowFoxMotion.SPIKES,(mc,handler,buf,sender) -> {
            var world = mc.world;
            int count = buf.readVarInt();
            if (count < 1 || count > 48) return;
            var tips = new ArrayList<SnowFoxEffects.Tip>(count);
            for (int i=0;i<count;i++) tips.add(new SnowFoxEffects.Tip(
                    new Vec3d(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readFloat()));
            mc.execute(() -> { if (mc.world == world && world != null) SnowFoxEffects.spawn(world,tips); });
        });
    }

    private static void tick(MinecraftClient mc) {
        var p = mc.player;
        if (viewed != p || p == null || mc.world == null || !FormUtils.isForm(p,FormIdentifiers.UPGRADE_SNOW_FOX)) {
            restore(mc);
            primary = secondary = false;
            viewed = p;
            if (p == null || mc.world == null || !FormUtils.isForm(p,FormIdentifiers.UPGRADE_SNOW_FOX)) return;
        }
        var state = SnowFoxMotion.STATE.get(p);
        boolean shell = state.phase() == SnowFoxState.Phase.SHELL;
        if (shell) {
            if (previous == null) {
                previous = mc.options.getPerspective();
                entry = CameraPose.current(mc.gameRenderer.getCamera(), p.getCameraPosVec(mc.getTickDelta()));
                lastPose = entry;
                lastX = lastZ = -200;
            }
            if (mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK)
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (previous != null) {
            if (state.phase() != SnowFoxState.Phase.RECOVERY) restore(mc);
            else if (mc.options.getPerspective() != previous)
                mc.options.setPerspective(previous);
        }
        var a = SscAddonKeybindings.getPrimaryKey();
        var b = SscAddonKeybindings.getSecondaryKey();
        boolean pressedA = mc.currentScreen == null && a != null && a.isPressed();
        boolean pressedB = mc.currentScreen == null && b != null && b.isPressed();
        if (pressedA && !primary) ClientPlayNetworking.send(SnowFoxMotion.DIVE,PacketByteBufs.empty());
        if (pressedB && !secondary) ClientPlayNetworking.send(SnowFoxMotion.ROLL,PacketByteBufs.empty());
        primary=pressedA; secondary=pressedB;
    }

    private static void restore(MinecraftClient mc) {
        if (previous != null) mc.options.setPerspective(previous);
        previous = null;
        entry = lastPose = recoveryStart = null;
    }

    public static void movement(Input input) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        var state = SnowFoxMotion.STATE.get(mc.player);
        if (state.phase() == SnowFoxState.Phase.IDLE || state.phase() == SnowFoxState.Phase.RECOVERY) return;
        if (state.phase() == SnowFoxState.Phase.SHELL) {
            double yaw = Math.toRadians(mc.gameRenderer.getCamera().getYaw());
            // Shoulder cameras may already have rotated the movement impulses into body space.
            int f = (input.pressingForward ? 1 : 0) - (input.pressingBack ? 1 : 0);
            int s = (input.pressingLeft ? 1 : 0) - (input.pressingRight ? 1 : 0);
            int x = (int)Math.round(90*(s*Math.cos(yaw)-f*Math.sin(yaw)));
            int z = (int)Math.round(90*(f*Math.cos(yaw)+s*Math.sin(yaw)));
            if (x!=lastX || z!=lastZ || mc.player.age%10==0) {
                var buf = PacketByteBufs.create();
                buf.writeByte(MathHelper.clamp(x,-127,127)); buf.writeByte(MathHelper.clamp(z,-127,127));
                ClientPlayNetworking.send(SnowFoxMotion.STEER,buf);
                lastX=x; lastZ=z;
            }
        }
        input.movementForward=0; input.movementSideways=0; input.jumping=false; input.sneaking=false;
    }

    public record CameraPose(Vec3d offset, float yaw, float pitch) {
        static CameraPose current(Camera camera, Vec3d eye) {
            return new CameraPose(camera.getPos().subtract(eye), camera.getYaw(), camera.getPitch());
        }

        CameraPose blend(CameraPose end, float progress) {
            if (progress <= 0) return this;
            if (progress >= 1) return end;
            float t = progress * progress * (3 - 2 * progress);
            float angle = MathHelper.lerpAngleDegrees(t, yaw, end.yaw);
            // Rotate around the focus when moving from a front view to a rear view.
            Vec3d a = offset.rotateY((yaw - angle) * MathHelper.RADIANS_PER_DEGREE);
            Vec3d b = end.offset.rotateY((end.yaw - angle) * MathHelper.RADIANS_PER_DEGREE);
            return new CameraPose(a.lerp(b, t), angle, MathHelper.lerp(t, pitch, end.pitch));
        }
    }

    public static boolean controlsCamera(ClientPlayerEntity player) {
        return previous != null && viewed == player;
    }

    public static CameraPose shellCamera(Camera camera, ClientPlayerEntity player, Vec3d eye, float delta) {
        var state = SnowFoxMotion.STATE.get(player);
        if (state.phase() == SnowFoxState.Phase.SHELL) {
            double size = MathHelper.clamp((state.mass() - 10) / 40, 0, 1);
            Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
            Vec3d offset = look.multiply(-(4.9 + 4.5 * size)).add(0, 1.15 + 1.65 * size, 0);
            lastPose = entry.blend(new CameraPose(offset, camera.getYaw(), camera.getPitch()), state.age(delta) / 8);
            return lastPose;
        }
        if (state.phase() != SnowFoxState.Phase.RECOVERY) return null;
        if (recoveryStart == null) recoveryStart = lastPose;
        return recoveryStart.blend(CameraPose.current(camera, eye), state.age(delta) / 6);
    }

    private static void hud(DrawContext draw,float delta) {
        var mc=MinecraftClient.getInstance();
        if (mc.player==null || mc.options.hudHidden) return;
        var state=SnowFoxMotion.STATE.get(mc.player);
        if (state.phase()!=SnowFoxState.Phase.SHELL) return;
        float mass=state.mass();
        int x=draw.getScaledWindowWidth()/2-52, y=draw.getScaledWindowHeight()-68;
        draw.fill(x-1,y-1,x+105,y+6,0xdf132338);
        draw.fill(x,y,x+104,y+5,0xff345169);
        draw.fill(x,y,x+Math.round(104*mass/50),y+5,0xff92eaff);
        draw.drawCenteredTextWithShadow(mc.textRenderer,Text.translatable("hud.ssc_addon.snow_fox.mass",
                MathHelper.floor(mass)),x+52,y-11,0xffe4f9ff);
    }

    private SnowFoxEvolutionClient() {}
}
