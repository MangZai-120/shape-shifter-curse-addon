package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoRuntime;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoState;

/** Real walking input, synchronized collapse attributes and vanilla body rotation. */
public final class CollapseBodyYawClientProbe {
    private record Scenario(String name,double ratio,boolean forward,boolean backward,boolean left,boolean swing,float body) {}
    private static final Scenario[] SCENARIOS={
            new Scenario("healthy-forward",0,true,false,false,false,90),
            new Scenario("maximum-forward",1.9,true,false,false,false,90),
            new Scenario("maximum-diagonal",1.9,true,false,true,false,45),
            new Scenario("maximum-backward",1.9,false,true,false,false,90),
            new Scenario("maximum-swing",1.9,true,false,true,true,90),
            new Scenario("maximum-idle",1.9,false,false,false,false,40),
            new Scenario("recovered-diagonal",0,true,false,true,false,45),
            new Scenario("healthy-swing",0,true,false,true,true,90)};
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private static final List<Float> bodySamples=new ArrayList<>();
    private static volatile boolean ready;
    private static volatile double ratio;
    private static boolean started,done,passed;
    private static int scenario,ticks,total,samples;
    private static double error,maxError,distance,minDistance,maxDistance;
    private CollapseBodyYawClientProbe() {}

    public static void install(){
        ClientLifecycleEvents.CLIENT_STARTED.register(client->org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getHandle()));
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            var state=NeoState.get(server);state.tutorialPolicyInitialized=true;state.tutorialAutoEnabled=false;
            var world=server.getOverworld();world.setTimeOfDay(6000);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
            world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false,server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(done||server.getPlayerManager().getPlayerList().isEmpty())return;
            var player=server.getPlayerManager().getPlayerList().get(0);
            if(!ready){
                var world=server.getOverworld();player.changeGameMode(GameMode.SURVIVAL);
                net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils._setForm(player,
                        net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.getPlayerForm(new Identifier("shape-shifter-curse:original_shifter")));
                NeoRuntime.refreshForm(player);
                player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(.1);
                player.teleport(world,.5,world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,0,0),.5,90,0);
                ready=true;
            }
            player.setHealth(player.getMaxHealth());player.getHungerManager().setFoodLevel(20);
            var data=NeoRuntime.data(player);data.burden=NeoRuntime.profile(player).safety()*ratio;data.recovery=10000;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(done||client.getOverlay()!=null)return;
            try{
                if(!started&&client.currentScreen instanceof TitleScreen){
                    started=true;client.options.pauseOnLostFocus=false;client.options.getMaxFps().setValue(60);
                    client.options.getViewDistance().setValue(4);client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    client.createIntegratedServerLoader().createAndStart("CollapseYaw-"+System.currentTimeMillis(),
                            new LevelInfo("Collapse body yaw",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),DataConfiguration.SAFE_MODE),
                            new GeneratorOptions(20261102L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if(client.player==null||client.world==null||!ready)return;
                if(++total>2000)throw new AssertionError("Body-yaw probe timeout, scenario="+scenario+", ticks="+ticks);
                if(client.currentScreen!=null)client.setScreen(null);
                var test=SCENARIOS[scenario];ratio=test.ratio();
                if(ticks<25){
                    input(client,false,false,false);ticks++;
                    if(ticks==25&&(NeoClient.hud.getInt("CollapseStage")!=(test.ratio()>0?5:0)||!client.player.isOnGround()))ticks--;
                    if(ticks==25){
                        client.player.setYaw(90);client.player.setPitch(12);client.player.bodyYaw=0;client.player.prevBodyYaw=0;
                        client.player.setVelocity(Vec3d.ZERO);samples=0;error=maxError=distance=maxDistance=0;minDistance=Double.MAX_VALUE;bodySamples.clear();
                    }
                    return;
                }
                input(client,test.forward(),test.backward(),test.left());client.player.setYaw(90);
                if(test.swing()&&ticks%5==0)client.player.swingHand(Hand.MAIN_HAND);
                ticks++;
                if(ticks>65){
                    double sample=Math.abs(MathHelper.wrapDegrees(client.player.bodyYaw-test.body()));
                    double dx=client.player.getX()-client.player.prevX,dz=client.player.getZ()-client.player.prevZ;
                    double moved=Math.hypot(dx,dz);samples++;error+=sample;maxError=Math.max(maxError,sample);
                    distance+=moved;minDistance=Math.min(minDistance,moved);maxDistance=Math.max(maxDistance,moved);bodySamples.add(client.player.bodyYaw);
                }
                if(ticks<85)return;
                var row=new LinkedHashMap<String,Object>();row.put("scenario",test.name());row.put("stage",NeoClient.hud.getInt("CollapseStage"));
                row.put("expected_body_yaw",test.body());row.put("actual_body_yaw",client.player.bodyYaw);
                row.put("mean_error",error/samples);row.put("max_error",maxError);
                row.put("mean_displacement",distance/samples);row.put("min_displacement",minDistance);row.put("max_displacement",maxDistance);
                row.put("movement_attribute",client.player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
                row.put("body_samples",List.copyOf(bodySamples));
                results.add(row);
                try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(Path.of(test.name()+".png"));}
                System.out.println("Collapse body yaw sample: "+NeoState.JSON.toJson(row));
                input(client,false,false,false);ticks=0;scenario++;
                if(scenario==SCENARIOS.length)finish(client);
            }catch(Throwable failure){
                failure.printStackTrace();done=true;input(client,false,false,false);
                try{Files.writeString(Path.of("body-yaw-result.json"),NeoState.JSON.toJson(Map.of("passed",false,"error",failure.toString(),"samples",results)));}catch(Exception ignored){}
                client.scheduleStop();
            }
        });
    }
    private static void input(MinecraftClient client,boolean forward,boolean backward,boolean left){
        client.options.forwardKey.setPressed(forward);client.options.backKey.setPressed(backward);
        client.options.leftKey.setPressed(left);client.options.rightKey.setPressed(false);client.options.sprintKey.setPressed(false);
        if(client.player!=null)client.player.setSprinting(false);
    }
    private static void finish(MinecraftClient client)throws Exception{
        boolean baseline=Boolean.getBoolean("collapseBody.baseline");
        if(baseline){
            if(((Number)results.get(1).get("mean_error")).doubleValue()<35)throw new AssertionError("Original slow body rotation did not reproduce");
            for(int i:new int[]{0,6})if(((Number)results.get(i).get("max_error")).doubleValue()>3)throw new AssertionError("Healthy control did not settle");
        }else{
            for(var row:results)if(!row.get("scenario").toString().endsWith("swing")){
                if(((Number)row.get("max_error")).doubleValue()>3)throw new AssertionError("Body orientation mismatch: "+row);
            }
            // Swinging briefly yields to movement between arm swings; compare actual vanilla controls.
            var normal=(List<?>)results.get(7).get("body_samples");
            var slowed=(List<?>)results.get(4).get("body_samples");
            for(int i=0;i<normal.size();i++)if(Math.abs(MathHelper.wrapDegrees(((Number)normal.get(i)).floatValue()-((Number)slowed.get(i)).floatValue()))>.01){
                throw new AssertionError("Collapse swing transition differs from healthy control at sample "+i);
            }
        }
        var maximum=results.get(1);
        if(((Number)maximum.get("max_displacement")).doubleValue()>=.05)throw new AssertionError("Maximum slowdown must reproduce movement below vanilla threshold");
        passed=true;done=true;
        Files.writeString(Path.of("body-yaw-result.json"),NeoState.JSON.toJson(Map.of("passed",true,"baseline",baseline,"actual_input_and_packets",true,"samples",results)));
        client.scheduleStop();
    }
    public static void verifyResult(){if(!done||!passed)throw new AssertionError("Body-yaw native acceptance did not finish");}
}
