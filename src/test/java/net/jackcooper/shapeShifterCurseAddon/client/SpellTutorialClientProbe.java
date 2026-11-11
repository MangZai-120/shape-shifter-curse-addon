package net.jackcooper.shapeShifterCurseAddon.client;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.block.*;
import net.jackcooper.shapeShifterCurseAddon.client.screen.SpellResearchTableScreen;
import net.jackcooper.shapeShifterCurseAddon.client.tutorial.SpellTutorialWeb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoState;
import org.lwjgl.glfw.GLFW;

/** 原生客户端真实入场、确认包、跳过、重看与既有世界验收。 */
public final class SpellTutorialClientProbe {
    private static boolean starting,finished;
    private static int stage,ticks,total,checks;
    private static String mode;
    private static final List<String> shots=new ArrayList<>();
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void next(){stage++;ticks=0;}
    private static Object field(Object object,String name)throws Exception{Field f=(object instanceof Class<?> type?type:object.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(object instanceof Class<?>?null:object);}
    private static void click(Screen screen,String key){
        var button=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(b->b.visible&&b.getMessage().getString().equals(Text.translatable("tutorial.ssc_addon."+key).getString())).findFirst().orElseThrow();
        check(button.active,"Button active: "+key);
        screen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);
        screen.mouseReleased(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);
    }
    private static void shot(MinecraftClient client,String name)throws Exception{
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(Path.of(name));}shots.add(name);
    }
    private static void openTable(MinecraftClient client){
        client.getServer().execute(()->{
            var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            var pos=p.getBlockPos().add(1,0,0);p.getServerWorld().setBlockState(pos,RegAddonBlocks.SPELL_RESEARCH_TABLE.getDefaultState());
            p.openHandledScreen((SpellResearchTableBlockEntity)p.getServerWorld().getBlockEntity(pos));
        });
    }
    public static void install(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{try{
            if(finished||client.getOverlay()!=null)return;
            if(!starting&&client.currentScreen instanceof TitleScreen title){
                starting=true;mode=Files.readString(Path.of("tutorial-probe-mode.txt")).trim();
                client.options.pauseOnLostFocus=false;client.options.getMaxFps().setValue(60);client.options.getViewDistance().setValue(4);
                if(mode.equals("fresh"))client.createIntegratedServerLoader().createAndStart("Tutorial-"+System.currentTimeMillis(),
                        new LevelInfo("Tutorial acceptance",GameMode.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(20261101L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                else{String name;try(var files=Files.list(Path.of("saves"))){name=files.filter(Files::isDirectory).findFirst().orElseThrow().getFileName().toString();}
                    client.createIntegratedServerLoader().start(title,name);}
            }
            if(client.world==null||client.player==null){
                if(starting&&client.currentScreen instanceof net.minecraft.client.gui.screen.BackupPromptScreen prompt){
                    var accept=prompt.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                            .filter(b->b.getMessage().getString().equals(Text.translatable("selectWorld.backupJoinSkipButton").getString())).findFirst().orElseThrow();
                    prompt.mouseClicked(accept.getX()+accept.getWidth()/2.0,accept.getY()+accept.getHeight()/2.0,0);
                }
                return;
            }
            ticks++;total++;if(total>1800)throw new AssertionError("Tutorial timeout mode="+mode+", stage="+stage+", screen="+client.currentScreen);
            var state=NeoState.get(client.getServer());
            switch(stage){
                case 0->{
                    if(mode.equals("fresh")){
                        if(!(client.currentScreen instanceof ConfirmScreen))return;
                        check(state.tutorialPolicyInitialized&&state.tutorialAutoEnabled,"Fresh world retains automatic tutorial policy");
                        check(!state.player(client.player.getUuid()).spellTutorialSeen,"Showing prompt does not acknowledge browser handoff");
                        check(client.currentScreen.getTitle().getString().equals(Text.translatable("tutorial.ssc_addon.web.title").getString()),"Spell video confirmation shown");
                        shot(client,"01-video-prompt.png");click(client.currentScreen,"form.web.prompt.cancel");next();
                    }else{
                        if(ticks<120)return;
                        check(!(client.currentScreen instanceof ConfirmScreen),"Existing world does not auto-open video prompt");
                        check(mode.equals("old")?!state.tutorialAutoEnabled:state.player(client.player.getUuid()).spellTutorialSeen,"Legacy policy and returning-player handoff record persist");
                        openTable(client);stage=2;ticks=0;
                    }
                }
                case 1->{if(ticks<35)return;
                    check(!state.player(client.player.getUuid()).spellTutorialSeen,"Cancelling leaves viewing record untouched");
                    check(client.currentScreen==null,"Cancelling returns to gameplay");openTable(client);next();}
                case 2->{if(!(client.currentScreen instanceof SpellResearchTableScreen)||ticks<10)return;
                    var method=SpellTutorialWeb.class.getDeclaredMethod("url",MinecraftClient.class);method.setAccessible(true);
                    var url=(java.net.URI)method.invoke(null,client);
                    check(url.toString().contains("lang=")&&url.toString().contains("cast=")&&url.toString().contains("switch="),"URL includes language and actual casting key labels");
                    check(url.getScheme().equals("http")&&url.getHost().equals("127.0.0.1"),"Synchronized page uses local loopback server");
                    Files.writeString(Path.of("tutorial-browser-url.txt"),url.toString());shot(client,"02-video-entry.png");click(client.currentScreen,"replay");next();}
                case 3->{if(ticks<35||!state.player(client.player.getUuid()).spellTutorialSeen)return;
                    check(client.currentScreen==null,"Manual replay opens browser without the old slideshow");
                    check(state.player(client.player.getUuid()).spellTutorialSeen,"Actual OS browser dispatch is acknowledged by server");
                    shot(client,"03-browser-handoff.png");next();}
                case 4->{if(ticks<60)return;
                    check(client.currentScreen==null,"No native slideshow reopens after handoff");
                    finished=true;Files.writeString(Path.of("tutorial-result.json"),NeoState.JSON.toJson(Map.of("passed",true,"mode",mode,"checks",checks,"screenshots",shots,"browser_handoff",true)));
                    System.out.println("SPELL_TUTORIAL_CLIENT_PASS mode="+mode+" checks="+checks);client.scheduleStop();}
            }
        }catch(Throwable error){finished=true;error.printStackTrace();
            try{Files.writeString(Path.of("tutorial-failure.json"),NeoState.JSON.toJson(Map.of("stage",stage,"error",error.toString())));}catch(Exception ignored){}
            MinecraftClient.getInstance().scheduleStop();}});
    }
}
