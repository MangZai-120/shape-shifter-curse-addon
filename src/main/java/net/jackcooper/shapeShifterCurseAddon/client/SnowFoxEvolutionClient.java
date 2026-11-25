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
    private static Vec3d initialOffset = Vec3d.ZERO;
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
        if (shell && previous == null) {
            previous = mc.options.getPerspective();
            initialOffset = mc.gameRenderer.getCamera().getPos().subtract(p.getCameraPosVec(mc.getTickDelta()));
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            lastX = lastZ = -200;
        } else if (previous != null && !shell && state.phase() != SnowFoxState.Phase.RECOVERY) restore(mc);
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
    }

    public static void movement(Input input) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        var state = SnowFoxMotion.STATE.get(mc.player);
        if (state.phase() == SnowFoxState.Phase.IDLE || state.phase() == SnowFoxState.Phase.RECOVERY) return;
        if (state.phase() == SnowFoxState.Phase.SHELL) {
            double yaw = Math.toRadians(mc.player.getYaw());
            double f = input.movementForward, s = input.movementSideways;
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

    public static boolean cameraActive() { return previous != null; }
    public static float frontWeight(ClientPlayerEntity player,float delta) {
        if (previous!=Perspective.THIRD_PERSON_FRONT) return 0;
        return 1-cameraProgress(SnowFoxMotion.STATE.get(player),delta);
    }
    private static float cameraProgress(SnowFoxState state,float delta) {
        float time=state.phase()==SnowFoxState.Phase.RECOVERY ? 1-state.age(delta)/6 : state.age(delta)/8;
        float t=MathHelper.clamp(time,0,1);
        return t*t*(3-2*t);
    }
    public static Vec3d cameraOffset(ClientPlayerEntity player,float delta,Vec3d normalBack) {
        var state=SnowFoxMotion.STATE.get(player);
        float t = cameraProgress(state,delta);
        Vec3d origin = previous==Perspective.FIRST_PERSON ? Vec3d.ZERO :
                state.phase()==SnowFoxState.Phase.RECOVERY ?
                        normalBack.multiply(previous==Perspective.THIRD_PERSON_FRONT ? -1 : 1) : initialOffset;
        double size = MathHelper.clamp((state.mass()-10)/40,0,1);
        // Pehkui scales the normal third-person offset to the fox body. Use the visible shell
        // diameter to frame the entire ball and keep the ground ahead visible at maximum size.
        Vec3d direction = Vec3d.fromPolar(player.getPitch(delta), player.getYaw(delta));
        Vec3d follow = direction.multiply(-(4.9 + 4.5 * size)).add(0, 1.15 + 1.65 * size, 0);
        return origin.lerp(follow,t);
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
        draw.drawCenteredTextWithShadow(mc.textRenderer,Text.translatable("hud.my_addon.snow_fox.mass",
                MathHelper.floor(mass)),x+52,y-11,0xffe4f9ff);
    }

    private SnowFoxEvolutionClient() {}
}
