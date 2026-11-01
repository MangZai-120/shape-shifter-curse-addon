package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.effect.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.ssca.neomagic.*;
import net.ssca.neomagic.client.NeoClient;

/** Native packaged client/integrated-server packet and vanilla/Iris visual acceptance. */
public final class CollapsePenaltyClientProbe {
    private static boolean started,done;
    private static volatile boolean ready;
    private static volatile double ratio=1.7;
    private static volatile double sheepSafety;
    private static int phase,ticks,total,checks;
    private static final List<String> shots=new ArrayList<>();
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void shot(net.minecraft.client.MinecraftClient client,String name)throws Exception{
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(Path.of(name));}shots.add(name);
    }
    private static void next(){phase++;ticks=0;}
    public static void install(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{var state=NeoState.get(server);state.tutorialPolicyInitialized=true;state.tutorialAutoEnabled=false;});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(done||server.getPlayerManager().getPlayerList().isEmpty())return;
            var p=server.getPlayerManager().getPlayerList().get(0);
            if(!ready){
                server.getOverworld().setTimeOfDay(6000);server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
                server.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false,server);
                p.changeGameMode(GameMode.SURVIVAL);p.setNoGravity(true);
                p.teleport(server.getOverworld(),0,5,0,0,0);
                net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils._setForm(p,
                        net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.getPlayerForm(new Identifier("my_addon:golden_sandstorm_sp")));
                NeoRuntime.refreshForm(p);ready=true;
            }
            var data=NeoRuntime.data(p);data.burden=NeoRuntime.profile(p).safety()*ratio;data.recovery=10000;
            if(p.getHealth()<10)p.setHealth(p.getMaxHealth());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{try{
            if(done||client.getOverlay()!=null)return;
            if(!started&&client.currentScreen instanceof TitleScreen){started=true;
                client.options.pauseOnLostFocus=false;client.options.getMaxFps().setValue(60);client.options.getViewDistance().setValue(4);
                client.createIntegratedServerLoader().createAndStart("Collapse-"+System.currentTimeMillis(),
                        new LevelInfo("Collapse acceptance",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(20261101L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());}
            if(client.player==null||client.world==null||!ready)return;
            if(++total>1400)throw new AssertionError("Native collapse timeout phase="+phase+", HUD="+NeoClient.hud);
            if(client.currentScreen!=null)client.setScreen(null);
            ticks++;
            if(phase==0){
                if(NeoClient.hud.getInt("CollapseStage")!=4||ticks<110)return;
                check(client.player.hasStatusEffect(StatusEffects.NAUSEA),"Actual immune form receives code nausea");
                check(!client.player.hasStatusEffect(StatusEffects.WITHER),"Fourth band has no black hearts");
                shot(client,"01-nausea.png");ratio=1.9;next();
            }else if(phase==1){
                if(NeoClient.hud.getInt("CollapseStage")!=5||ticks<25)return;
                check(client.player.hasStatusEffect(StatusEffects.NAUSEA),"Maximum tier retains nausea");
                check(client.player.hasStatusEffect(StatusEffects.BLINDNESS),"Maximum tier has vanilla/Iris blindness carrier");
                check(client.player.hasStatusEffect(StatusEffects.WITHER)&&client.player.getStatusEffect(StatusEffects.WITHER).getAmplifier()==2,"Vanilla HUD sees Wither III for black hearts");
                var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                check(!p.hasStatusEffect(StatusEffects.WITHER),"Server never exposes convertible wither potion");
                shot(client,"02-black-hearts-blindness.png");client.player.clearStatusEffects();next();
            }else if(phase==2){
                if(ticks<3)return;
                check(client.player.hasStatusEffect(StatusEffects.NAUSEA)&&client.player.hasStatusEffect(StatusEffects.WITHER)&&client.player.hasStatusEffect(StatusEffects.BLINDNESS),"Local power cleanse cannot suppress code visuals");
                ratio=0;next();
            }else if(phase==3){
                if(NeoClient.hud.getInt("CollapseStage")!=0||ticks<15)return;
                check(!client.player.hasStatusEffect(StatusEffects.NAUSEA)&&!client.player.hasStatusEffect(StatusEffects.WITHER)&&!client.player.hasStatusEffect(StatusEffects.BLINDNESS),"All owned visuals disappear on recovery");
                shot(client,"03-recovered.png");
                var map=((net.jackcooper.shapeShifterCurseAddon.mixin.client.LivingEntityStatusEffectsAccessor)client.player).sscAddon$getActiveStatusEffects();
                var normal=new StatusEffectInstance(StatusEffects.BLINDNESS,400);map.put(StatusEffects.BLINDNESS,normal);
                net.jackcooper.shapeShifterCurseAddon.client.hud.CollapsePenaltyClient.tick(client);
                check(map.get(StatusEffects.BLINDNESS)==normal,"Ordinary blindness is never removed by collapse cleanup");
                map.remove(StatusEffects.BLINDNESS);
                client.getServer().execute(()->{
                    var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    sheepSafety=NeoRuntime.profile(p).safety();
                    p.addStatusEffect(new StatusEffectInstance(net.jackcooper.shapeShifterCurseAddon.SscAddon.SHEEP_FORM,200));
                });next();
            }else if(phase==4){
                if(!NeoClient.hud.getString("Form").equals("my_addon:sheep_spell_form")||ticks<15)return;
                check(Math.abs(NeoClient.hud.getDouble("S")-sheepSafety)<1e-5,"Real sheep transformation keeps pre-transform safety in actual HUD packets");
                check(Math.abs(NeoClient.hud.getDouble("BodyS")-sheepSafety)<1e-5,"Sheep marker uses inherited base capacity");
                shot(client,"04-sheep-inherited-safety.png");
                client.getServer().execute(()->client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).removeStatusEffect(net.jackcooper.shapeShifterCurseAddon.SscAddon.SHEEP_FORM));next();
            }else if(phase==5){
                if(!NeoClient.hud.getString("Form").equals("my_addon:golden_sandstorm_sp")||ticks<10)return;
                check(Math.abs(NeoClient.hud.getDouble("S")-sheepSafety)<1e-5,"Expiration restores original form and safety");done=true;
                Files.writeString(Path.of("collapse-result.json"),NeoState.JSON.toJson(Map.of("passed",true,"checks",checks,"screenshots",shots,"actual_hud_packets",true)));
                client.scheduleStop();
            }
        }catch(Throwable failure){failure.printStackTrace();done=true;try{Files.writeString(Path.of("collapse-result.json"),NeoState.JSON.toJson(Map.of("passed",false,"phase",phase,"error",failure.toString())));}catch(Exception ignored){}client.scheduleStop();}});
    }
}
