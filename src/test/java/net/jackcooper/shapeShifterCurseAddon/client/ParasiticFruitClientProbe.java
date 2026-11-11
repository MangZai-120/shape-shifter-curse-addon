package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.ability.fruit.FruitSelection;
import net.jackcooper.shapeShifterCurseAddon.client.hud.*;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.state.RegParasiticFruitStateComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.*;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;

/** Actual client gestures, component packets, textures and HUD at every approved mode/side/scale. */
public final class ParasiticFruitClientProbe {
    private static boolean starting,finished;
    private static Throwable failure;
    private static int serverTicks,clientTicks,checks,scene;
    private static volatile boolean configured;
    private static boolean sceneRequested;
    private static int sceneWait;
    private static volatile int appliedScene=-1;
    private static int renderedScene=-1,renderedSceneFrames;
    private static final List<String> screenshots=new ArrayList<>();
    private static final long START=System.nanoTime();
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static KeyBinding key() throws Exception {
        return (KeyBinding)net.onixary.shapeShifterCurseFabric.client.ShapeShifterCurseFabricClient.class
                .getDeclaredField("useActiveSkill1PowerKeybind").get(null);
    }
    private static void shot(MinecraftClient mc,String name)throws Exception{
        try(var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){image.writeTo(Path.of(name));}screenshots.add(name);
    }
    public static void install(){
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context,delta)->{
            var mc=MinecraftClient.getInstance();
            if(!sceneRequested||scene>=60||mc.player==null)return;
            var selected=RegParasiticFruitStateComponent.STATE.get(mc.player).selection();
            var entries=SkillHudCatalog.entriesForHud(FormIdentifiers.BAT_PARASITIC_FRUIT,mc.player);
            if(appliedScene==scene&&selected==FruitSelection.values()[scene%5]&&entries.size()==(scene>=30?4:3)){
                if(renderedScene!=scene){renderedScene=scene;renderedSceneFrames=0;}
                renderedSceneFrames++;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(finished)return;
            if(System.nanoTime()-START>300_000_000_000L)throw new AssertionError("Fruit client probe timed out");
            if(!starting&&mc.currentScreen instanceof TitleScreen){
                starting=true;mc.options.pauseOnLostFocus=false;mc.options.getViewDistance().setValue(3);mc.options.getMaxFps().setValue(60);
                var data=new DataConfiguration(new DataPackSettings(List.of("vanilla","fabric"),List.of()),DataConfiguration.SAFE_MODE.enabledFeatures());
                mc.createIntegratedServerLoader().createAndStart("Fruit-"+System.currentTimeMillis(),
                        new LevelInfo("Fruit HUD",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),data),
                        new GeneratorOptions(20261111L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
            if(mc.world==null||mc.player==null||!configured||!net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(mc.player,FormIdentifiers.BAT_PARASITIC_FRUIT))return;
            if(mc.currentScreen!=null)mc.setScreen(null);
            int tick=++clientTicks;
            if(tick==1){
                mc.options.getGuiScale().setValue(3);mc.onResolutionChanged();mc.options.hudHidden=false;mc.inGameHud.getChatHud().clear(false);
                var cfg=SSCAddonConfig.client();var binding=cfg.getFormKeybind("bat_parasitic_fruit");if(binding!=null)binding.enabled=false;
                cfg.showCdBar=true;cfg.cdBarPosOffsetX=24;cfg.cdBarPosOffsetY=40;
                for(var fruit:FruitSelection.values()){
                    var id=new Identifier("my_addon","textures/gui/skill_icons/fruit_"+fruit.key()+".png");
                    try(var stream=mc.getResourceManager().getResource(id).orElseThrow().getInputStream();var image=NativeImage.read(stream)){
                        check(image.getWidth()==32&&image.getHeight()==32,"32-pixel approved icon exists");
                        for(int y=0;y<32;y++)for(int x=0;x<32;x++)check((image.getColor(x,y)>>>24)==255,"Every fruit icon has an opaque background");
                    }
                }
            }
            if(tick==10||tick==12)key().setPressed(true);
            if(tick==11||tick==13)key().setPressed(false);
            if(tick==20){
                check(RegParasiticFruitStateComponent.STATE.get(mc.player).selection()==FruitSelection.VINE,"Double tap selects Vine through real gesture packets");
                key().setPressed(true);
            }
            if(tick==35){
                check(RegParasiticFruitStateComponent.STATE.get(mc.player).charging(),"Charge state is synchronized from server");
                check(RegParasiticFruitStateComponent.STATE.get(mc.player).chargeFraction()>=0.45,"Main auxiliary bar receives charge progress");
                shot(mc,"fruit-charge.png");key().setPressed(false);
            }
            if(tick==48){
                check(!RegParasiticFruitStateComponent.STATE.get(mc.player).charging(),"Release clears synchronized charge");
                check(PowerHolderComponent.getPowers(mc.player,io.github.apace100.apoli.power.VariableIntPower.class).stream().filter(p->p.getType().getIdentifier().equals(FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY)).findFirst().orElseThrow().getValue()==9,"Real key release charges exactly one seed");
            }
            if(tick<60)return;
            if(!sceneRequested&&scene<60){
                sceneRequested=true;sceneWait=0;
                int index=scene;var fruit=FruitSelection.values()[index%5];boolean perk=index>=30;
                int scale=2+(index/10)%3;boolean mirror=(index/5)%2==1;
                mc.options.getGuiScale().setValue(scale);mc.onResolutionChanged();SSCAddonConfig.client().cdMirrorRight=mirror;
                mc.getServer().execute(()->{
                    try{
                        var p=mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());
                        var component=PlayerFormComponent.COMPONENT.get(p);
                        var type=PowerTypeRegistry.get(new Identifier("my_addon","perk_bat_parasitic_fruit_wingbeat"));
                        var source=new Identifier("my_addon","fruit_probe");
                        if(perk){component.formPerkMap.put(RegPerks.T_Bat3PerkTree,List.of(RegPerks.P_BatWingbeat));PowerHolderComponent.KEY.get(p).addPower(type,source);}
                        component.sync();PowerHolderComponent.sync(p);
                        RegParasiticFruitStateComponent.STATE.get(p).selection(fruit);RegParasiticFruitStateComponent.STATE.sync(p);
                        appliedScene=index;
                    }catch(Throwable e){fail(e);}
                });
            }
            if(sceneRequested&&scene<60){
                sceneWait++;
                var expected=FruitSelection.values()[scene%5];
                var actual=RegParasiticFruitStateComponent.STATE.get(mc.player).selection();
                var current=SkillHudCatalog.entriesForHud(FormIdentifiers.BAT_PARASITIC_FRUIT,mc.player);
                if(sceneWait>120)throw new AssertionError("Scene "+scene+" sync timeout, expected="+expected+", actual="+actual+", applied="+appliedScene+", slots="+current.size());
                if(sceneWait<8||actual!=expected||appliedScene!=scene||current.size()!=(scene>=30?4:3)||renderedScene!=scene||renderedSceneFrames<2)return;
                check(renderedScene==scene&&renderedSceneFrames>=2,"The selected mode rendered in two complete HUD frames before capture");
                var selected=FruitSelection.values()[scene%5];boolean perk=scene>=30;
                check(RegParasiticFruitStateComponent.STATE.get(mc.player).selection()==selected,"Server selection packet reaches client");
                var entries=SkillHudCatalog.entriesForHud(FormIdentifiers.BAT_PARASITIC_FRUIT,mc.player);
                check(entries.size()==(perk?4:3),"Selector and learned Wingbeat fit the HUD");
                check(entries.get(2).slot()==SkillHudLayout.Slot.LOWER&&entries.get(2).skill().resolveIcon(mc.player).getPath().endsWith("fruit_"+selected.key()+".png"),"Selected icon stays in lower slot");
                check(entries.get(0).skill().icon().getPath().endsWith("parasitic_seed.png")&&entries.get(1).skill().icon().getPath().endsWith("spore_bomb.png"),"Main icon pair is unchanged");
                if(perk)check(entries.get(3).slot()==SkillHudLayout.Slot.UPPER,"Wingbeat stays in upper slot");
                shot(mc,"fruit-hud-"+scene+"-"+selected.key()+".png");scene++;sceneRequested=false;
            }
            if(scene==60){
                Files.writeString(Path.of("fruit-client-result.json"),net.jackcooper.shapeShifterCurseAddon.spell.NeoState.JSON.toJson(Map.of("passed",true,"checks",checks,"screenshots",screenshots,"rendered_hud_scenes",60,"render_frames_per_scene",2)));
                finished=true;System.out.println("PARASITIC_FRUIT_CLIENT_PASS checks="+checks);mc.scheduleStop();
            }
        }catch(Throwable e){fail(e);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{try{
            if(finished||server.getPlayerManager().getPlayerList().isEmpty())return;
            if(++serverTicks==40){
                var p=server.getPlayerManager().getPlayerList().get(0);server.getOverworld().setTimeOfDay(6000);
                server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
                FormUtils._setForm(p,RegPlayerForms.getPlayerForm(FormIdentifiers.BAT_PARASITIC_FRUIT));
                PowerUtils.setResourceValueAndSync(p,FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY,10);
            }
            if(serverTicks==80)configured=true;
        }catch(Throwable e){fail(e);}});
    }
    private static void fail(Throwable e){if(failure!=null)return;failure=e;finished=true;e.printStackTrace();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());}
    public static void verifyResult(){if(failure!=null)throw new AssertionError("Fruit client probe failed",failure);if(!finished)throw new AssertionError("Fruit client stopped before completion");}
}
