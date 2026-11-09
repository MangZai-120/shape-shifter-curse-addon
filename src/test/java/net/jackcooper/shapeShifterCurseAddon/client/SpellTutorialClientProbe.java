package net.jackcooper.shapeShifterCurseAddon.client;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.block.*;
import net.jackcooper.shapeShifterCurseAddon.client.screen.SpellResearchTableScreen;
import net.jackcooper.shapeShifterCurseAddon.client.tutorial.SpellTutorialScreen;
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
                        if(ticks==200&&!(client.currentScreen instanceof SpellTutorialScreen)){
                            System.out.println("TUTORIAL_DELIVERY_DIAGNOSTIC pending="+field(net.jackcooper.shapeShifterCurseAddon.client.tutorial.SpellTutorialClient.class,"pending")
                                    +" offered="+field(net.jackcooper.shapeShifterCurseAddon.tutorial.SpellTutorialManager.class,"REQUESTED")
                                    +" queued="+field(net.jackcooper.shapeShifterCurseAddon.tutorial.SpellTutorialManager.class,"JOIN_QUEUE"));
                        }
                        if(!(client.currentScreen instanceof SpellTutorialScreen))return;
                        check(state.tutorialPolicyInitialized&&state.tutorialAutoEnabled,"Actually created world opts into automatic tutorial");next();
                    }else{
                        check(!(client.currentScreen instanceof SpellTutorialScreen),"Existing world does not auto-open tutorial");
                        if(ticks<120)return;
                        check(state.tutorialPolicyInitialized,"Existing world records its tutorial policy");
                        check(mode.equals("old")?!state.tutorialAutoEnabled:state.tutorialAutoEnabled,"Old policy is disabled; new-world policy persists after reload");
                        check(mode.equals("old")?!state.player(client.player.getUuid()).spellTutorialSeen:state.player(client.player.getUuid()).spellTutorialSeen,
                                "Old-save migration and returning player's viewing record are correct");
                        openTable(client);stage=4;ticks=0;
                    }
                }
                case 1->{
                    if(!state.player(client.player.getUuid()).spellTutorialSeen)return;
                    if(ticks<245)return;
                    check(true,"Real shown acknowledgement reached server");
                    var screen=(SpellTutorialScreen)client.currentScreen;
                    check((int)field(screen,"page")==1,"Tutorial advances automatically after one page duration");
                    click(screen,"pause");check((boolean)field(screen,"paused"),"Pause toggles actual screen state");next();
                }
                case 2->{
                    if(ticks<5)return;
                    var screen=(SpellTutorialScreen)client.currentScreen;shot(client,"01-analysis.png");
                    click(screen,"next");next();
                }
                case 3->{
                    var screen=(SpellTutorialScreen)client.currentScreen;if(ticks<5)return;
                    int page=(int)field(screen,"page");
                    if(page==3||page==6||page==7)shot(client,"page-"+page+".png");
                    check(screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                            .allMatch(b->b.getX()>=0&&b.getY()>=0&&b.getX()+b.getWidth()<=screen.width&&b.getY()+b.getHeight()<=screen.height),"Controls fit the actual GUI scale");
                    if(page<SpellTutorialScreen.PAGE_COUNT-1){click(screen,"next");ticks=0;return;}
                    click(screen,"finish");check(client.currentScreen==null,"Finishing returns to the real world");openTable(client);next();
                }
                case 4->{if(!(client.currentScreen instanceof SpellResearchTableScreen)||ticks<6)return;
                    shot(client,"02-replay-entry.png");click(client.currentScreen,"replay");next();}
                case 5->{if(!(client.currentScreen instanceof SpellTutorialScreen)||ticks<6)return;
                    check((int)field(client.currentScreen,"page")==0,"Research-table replay starts the tutorial from page one");
                    shot(client,"03-replay.png");click(client.currentScreen,"skip");check(client.currentScreen==null,"Skip immediately returns to gameplay");next();}
                case 6->{if(ticks<40)return;
                    check(client.currentScreen==null,"Skipped tutorial does not reopen");
                    check(state.player(client.player.getUuid()).spellTutorialSeen,"Skip/replay completion is persisted by actual acknowledgement");
                    finished=true;Files.writeString(Path.of("tutorial-result.json"),NeoState.JSON.toJson(Map.of("passed",true,"mode",mode,"checks",checks,"screenshots",shots)));
                    System.out.println("SPELL_TUTORIAL_CLIENT_PASS mode="+mode+" checks="+checks);client.scheduleStop();}
            }
        }catch(Throwable error){finished=true;error.printStackTrace();
            try{Files.writeString(Path.of("tutorial-failure.json"),NeoState.JSON.toJson(Map.of("stage",stage,"error",error.toString())));}catch(Exception ignored){}
            MinecraftClient.getInstance().scheduleStop();}});
    }
}
