package net.jackcooper.shapeShifterCurseAddon.client.camera;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import virtuoel.pehkui.api.ScaleTypes;

/** Measures real rendered eye vertices, camera and server eye positions for every addon form. */
public final class FormCameraClientProbe {
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static List<Identifier> forms;
    private static volatile Throwable failure;
    private static volatile boolean ready;
    private static boolean starting, finished, submitted, sampling, sheep;
    private static int ticks, index, pose, scaleIndex;
    private static final boolean VERIFY=Boolean.getBoolean("ssca.camera.verify");
    private static final float[] SCALES=VERIFY ? new float[]{1,0.5f,0.25f} : new float[]{1};
    private static double serverEye;
    private static final Bounds eyes = new Bounds();
    private static final VertexConsumer DISCARD = new Bounds();
    private static final long START = System.nanoTime();

    public static VertexConsumer captureEye(String bone, VertexConsumer buffer) {
        return sampling && (bone.equals("eyeRoot") || (sheep && bone.equals("head") && eyes.vertices==0)) ? eyes : buffer;
    }

    private static final class Bounds implements VertexConsumer {
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        int vertices;
        void clear() { minY=Double.POSITIVE_INFINITY; maxY=Double.NEGATIVE_INFINITY; vertices=0; }
        public VertexConsumer vertex(double x,double y,double z) { minY=Math.min(minY,y);maxY=Math.max(maxY,y);vertices++;return this; }
        public VertexConsumer color(int r,int g,int b,int a) { return this; }
        public VertexConsumer texture(float u,float v) { return this; }
        public VertexConsumer overlay(int u,int v) { return this; }
        public VertexConsumer light(int u,int v) { return this; }
        public VertexConsumer normal(float x,float y,float z) { return this; }
        public void next() {}
        public void fixedColor(int r,int g,int b,int a) {}
        public void unfixColor() {}
    }

    private static void prepare(MinecraftClient mc) {
        ready=false;ticks=0;
        mc.options.sneakKey.setPressed(pose==1);
        mc.getServer().execute(() -> { try {
            var p=mc.getServer().getPlayerManager().getPlayerList().get(0);
            p.clearStatusEffects();p.setSneaking(pose==1);
            FormUtils._setForm(p,RegPlayerForms.getPlayerForm(forms.get(index)));
            ScaleTypes.BASE.getScaleData(p).setScale(SCALES[scaleIndex]);
            sheep=forms.get(index).equals(FormIdentifiers.SHEEP_FORM);
            if(sheep)p.addStatusEffect(new StatusEffectInstance(SscAddon.SHEEP_FORM,20000));
            p.getInventory().clear();p.setYaw(0);p.setPitch(0);
            p.teleport(p.getServerWorld(),0.5,-60,0.5,0,0);
            ready=true;
        } catch(Throwable e) { fail(e); } });
    }

    private static boolean sample(MinecraftClient mc) throws Exception {
        var p=mc.player;
        if (!FormUtils.getPlayerForm(p).getFormID().equals(forms.get(index))) throw new AssertionError("form sync");
        eyes.clear();sampling=true;
        try {
            mc.getEntityRenderDispatcher().render(p,0,0,0,0,1,new MatrixStack(), layer -> DISCARD,15728880);
        } finally { sampling=false; }
        if(eyes.vertices==0) throw new AssertionError("no rendered eye vertices: "+forms.get(index));
        // Blink animation flattens the mesh around its lower edge. Measure an open-eye frame.
        if(!sheep && eyes.maxY-eyes.minY < 0.08*ScaleTypes.HEIGHT.getScaleData(p).getScale()) return false;
        var row=new LinkedHashMap<String,Object>();
        double eye=(eyes.minY+eyes.maxY)/2+(sheep ? (eyes.maxY-eyes.minY)/12 : 0);
        var camera=new Camera();
        camera.update(mc.world,p,false,false,1);
        for(int i=0;i<30;i++)camera.updateEyeHeight();
        camera.update(mc.world,p,false,false,1);
        double cameraHeight=camera.getPos().y-p.getY();
        row.put("form",forms.get(index).toString());row.put("pose",pose==0?"standing":"sneaking");
        row.put("extra_scale",SCALES[scaleIndex]);row.put("camera_height",cameraHeight);
        row.put("body_type",FormUtils.getPlayerForm(p).getBodyType().toString());
        row.put("entity_pose",p.getPose().toString());row.put("sneaking",p.isSneaking());
        row.put("render_offset",mc.getEntityRenderDispatcher().getRenderer(p).getPositionOffset(p,1).toString());
        row.put("vertices",eyes.vertices);row.put("model_eye",eye);row.put("model_eye_min",eyes.minY);row.put("model_eye_max",eyes.maxY);
        row.put("client_eye",p.getStandingEyeHeight());row.put("server_eye",serverEye);
        row.put("eye_error",p.getStandingEyeHeight()-eye);
        row.put("height",ScaleTypes.HEIGHT.getScaleData(p).getScale());
        row.put("eye_base",ScaleTypes.EYE_HEIGHT.getScaleData(p).getBaseScale());
        row.put("hitbox",p.getHeight());results.add(row);
        Files.writeString(Path.of("camera-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));
        System.out.println("CAMERA_SAMPLE "+row);
        if(VERIFY) {
            double tolerance=0.003*SCALES[scaleIndex];
            if(Math.abs(p.getStandingEyeHeight()-eye)>tolerance)throw new AssertionError("model eye mismatch "+row);
            if(Math.abs(serverEye-eye)>tolerance)throw new AssertionError("server eye mismatch "+row);
            if(Math.abs(cameraHeight-eye)>tolerance)throw new AssertionError("actual camera mismatch "+row);
            if(scaleIndex==0 && pose==0) {
                Files.createDirectories(Path.of("screenshots"));
                try(var frame=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())) {frame.writeTo(Path.of("screenshots",forms.get(index).getPath()+".png"));}
            }
        }
        return true;
    }

    public static void install() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { try {
            if(finished)return;
            if(System.nanoTime()-START>540_000_000_000L)throw new AssertionError("camera audit timed out");
            if(mc.getOverlay()!=null)return;
            if(!starting && mc.currentScreen instanceof TitleScreen) {
                starting=true;mc.options.pauseOnLostFocus=false;mc.options.getViewDistance().setValue(3);
                mc.options.getMaxFps().setValue(60);mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                var data=new DataConfiguration(new DataPackSettings(List.of("vanilla","fabric"),List.of()),DataConfiguration.SAFE_MODE.enabledFeatures());
                var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
                mc.createIntegratedServerLoader().createAndStart("Camera-"+System.currentTimeMillis(),
                    new LevelInfo("Camera geometry audit",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,data),
                    new GeneratorOptions(20261115L,false,false),r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
            if(mc.world==null || mc.player==null || mc.getServer()==null)return;
            if(mc.currentScreen!=null)mc.setScreen(null);
            if(!submitted) {
                if(++ticks<40)return;submitted=true;
                forms=RegPlayerForms.playerForms.keySet().stream().filter(id -> id.getNamespace().equals("ssc_addon"))
                    .filter(id -> RegPlayerForms.getPlayerForm(id).getFormID().equals(id))
                    .filter(id -> VERIFY || !id.equals(FormIdentifiers.SHEEP_FORM)).sorted().toList();
                prepare(mc);return;
            }
            if(!ready || ++ticks<30)return;
            if(ticks==30) {
                ready=false;
                mc.getServer().execute(() -> {serverEye=mc.getServer().getPlayerManager().getPlayerList().get(0).getStandingEyeHeight();ready=true;});
                return;
            }
            if(!sample(mc))return;
            if(++pose==2) {pose=0;if(++scaleIndex==SCALES.length){scaleIndex=0;index++;}}
            if(index<forms.size())prepare(mc);
            else {finished=true;Files.writeString(Path.of("camera-complete.json"),"{\"completed\":true,\"rows\":"+results.size()+"}");mc.options.sneakKey.setPressed(false);System.out.println("CAMERA_AUDIT_COMPLETE rows="+results.size());mc.scheduleStop();}
        } catch(Throwable e) {fail(e);} });
    }
    private static void fail(Throwable e) {failure=e;finished=true;e.printStackTrace();MinecraftClient.getInstance().scheduleStop();}
    public static void verifyResult() {if(failure!=null)throw new AssertionError(failure);if(!finished)throw new AssertionError("incomplete camera audit");}
}
