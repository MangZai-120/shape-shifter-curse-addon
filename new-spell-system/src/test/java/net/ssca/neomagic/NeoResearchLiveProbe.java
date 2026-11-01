package net.ssca.neomagic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.block.*;
import net.jackcooper.shapeShifterCurseAddon.client.screen.*;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.screen.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

/** Native client/server screen and packet acceptance in an isolated disposable world. */
public final class NeoResearchLiveProbe {
    private static boolean starting,finished;
    private static volatile Throwable failure;
    private static int stage,ticks,total,checks;
    private static volatile SpellResearchTableBlockEntity research;
    private static volatile InfusionAltarBlockEntity preparation;
    private static volatile ServerPlayerEntity player;
    private static final List<String> screenshots=new ArrayList<>();
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void next(){stage++;ticks=0;}
    private static Object field(Object object,String name)throws Exception{
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static void click(Screen screen,Text text){
        var button=screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(b->b.visible&&b.getMessage().getString().equals(text.getString())).findFirst().orElseThrow(()->new AssertionError("Missing button: "+text.getString()));
        check(button.active,"Actual button active: "+text.getString());
        screen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);
        screen.mouseReleased(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);
    }
    private static int left(Screen screen){return (screen.width-312)/2;}
    private static int top(Screen screen){return screen instanceof InfusionAltarScreen?(screen.height-232)/2:(screen.height-240)/2+28;}
    private static Text slotted(String key,Object...args){return SlottedResearchManager.text(key,args);}
    private static Text altar(String key,Object...args){return Text.translatable("gui.ssc_addon.infusion_altar."+key,args);}
    private static void shot(MinecraftClient mc,String name)throws Exception{
        Files.createDirectories(Path.of("research-screenshots"));
        try(var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){image.writeTo(Path.of("research-screenshots",name));}
        screenshots.add(name);
    }
    private static void setup(MinecraftClient mc){
        var server=mc.getServer();player=server.getPlayerManager().getPlayer(mc.player.getUuid());
        player.changeGameMode(GameMode.SURVIVAL);player.getInventory().clear();
        var world=player.getServerWorld();
        for(var pos:BlockPos.iterate(new BlockPos(-4,3,-4),new BlockPos(6,3,4)))world.setBlockState(pos,Blocks.STONE.getDefaultState());
        player.teleport(world,.5,4,.5,0,0);
        world.setTimeOfDay(6000);server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
        world.setBlockState(new BlockPos(1,4,0),RegAddonBlocks.SPELL_RESEARCH_TABLE.getDefaultState());
        world.setBlockState(new BlockPos(3,4,0),RegAddonBlocks.INFUSION_ALTAR.getDefaultState());
        research=(SpellResearchTableBlockEntity)world.getBlockEntity(new BlockPos(1,4,0));
        preparation=(InfusionAltarBlockEntity)world.getBlockEntity(new BlockPos(3,4,0));
        var knowledge=FormationKnowledgeComponent.get(player);knowledge.research().bind(WorldRuneState.get(server).worldId());
        knowledge.research().rank=5;knowledge.research().knownGlyphs=0x3ffff;knowledge.recordSpell("fire_bolt");FormationKnowledgeComponent.sync(player);
        research.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,8));research.setStack(1,new ItemStack(SscAddon.FORMATION_INK_FIRE,32));
        research.setStack(2,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,64));research.setStack(4,new ItemStack(RegCustomItem.MOONDUST_CRYSTAL_SHARD,8));
        var scroll=ScrollData.create("fire_bolt",1);ScrollData.setUses(scroll,1);scroll.getOrCreateNbt().putString("SpaceBinding","preserved");scroll.getOrCreateNbt().putLong("CooldownEnd",123456);
        research.setStack(7,scroll);
        var book=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setExpTen(book,6000);preparation.setStack(0,book);
        preparation.setStack(1,new ItemStack(RegCustomItem.MOONDUST_CRYSTAL_SHARD,2));preparation.setStack(2,new ItemStack(RegCustomItem.MORPHSCALE_CORE,2));
        player.openHandledScreen(research);
    }
    private static void fillManually(SlottedFormationScreen screen,int[] pattern)throws Exception{
        int left=left(screen),top=top(screen);var points=RuneLayout.positions(2);
        for(int at=0;at<RuneLayout.baseSize(2);at++){
            int glyph=pattern[at];screen.mouseScrolled(left+20,top+50,glyph>=10?-20:20);
            int scroll=(int)field(screen,"paletteScroll");
            double px=left+8+glyph%2*22+11,py=top+26+glyph/2*22-scroll+11;
            screen.mouseClicked(px,py,0);
            double sx=left+63+points.get(at).x()*172/512,sy=top+19+points.get(at).y()*172/512;
            screen.mouseReleased(sx,sy,0);
        }
    }
    private static void prepareMaintenance(){
        preparation.setStack(1,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,4));
        preparation.setStack(InfusionAltarScreenHandler.MAINTENANCE_INK,new ItemStack(SscAddon.FORMATION_INK_FIRE,5));
        var scroll=ScrollData.create("fire_bolt",2);ScrollData.setUses(scroll,1);preparation.setStack(InfusionAltarScreenHandler.MAINTENANCE_SCROLL,scroll);
        var world=WorldRuneState.get(player.getServer());var language=world.language();
        int[] pattern=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,language);
        RuneRole[] outer={RuneRole.STORE,RuneRole.SOURCE,RuneRole.STABLE,RuneRole.STABLE,RuneRole.SOURCE};
        for(int i=0;i<outer.length;i++)pattern[RuneLayout.baseSize(2)+i]=language.glyph(outer[i]);
        var evaluation=RuneBuildEvaluator.evaluate(2,pattern,language,SlottedSpellRecipes.all());
        preparation.setStack(InfusionAltarScreenHandler.MAINTENANCE_SCHEME,net.jackcooper.shapeShifterCurseAddon.item.SpellFormationItem.createEnhanced(world,2,pattern,evaluation));
        player.currentScreenHandler.sendContentUpdates();
    }
    public static void install(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(mc.getOverlay()!=null||finished)return;
            if(!starting&&mc.currentScreen instanceof TitleScreen){
                starting=true;mc.options.pauseOnLostFocus=false;mc.options.getViewDistance().setValue(4);mc.options.getMaxFps().setValue(60);
                mc.createIntegratedServerLoader().createAndStart("Research-Acceptance-"+System.currentTimeMillis(),
                        new LevelInfo("Research acceptance",GameMode.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(20261101L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
            if(mc.world==null||mc.player==null)return;
            ticks++;total++;if(ticks>600||total>3000)throw new AssertionError("Research live timeout at stage "+stage);
            switch(stage){
                case 0->{if(ticks<50)return;mc.setScreen(null);mc.getServer().execute(()->{try{setup(mc);}catch(Throwable e){fail(e);}});next();}
                case 1->{if(!(mc.currentScreen instanceof SpellResearchTableScreen)||ticks<10)return;
                    var craft=PacketByteBufs.create();craft.writeString("fire_bolt");craft.writeVarInt(1);ClientPlayNetworking.send(SscAddonNetworking.PACKET_SCROLL_CRAFT,craft);
                    var upgrade=PacketByteBufs.create();upgrade.writeString("fire_bolt");upgrade.writeVarInt(2);ClientPlayNetworking.send(SscAddonNetworking.PACKET_SCROLL_UPGRADE,upgrade);next();}
                case 2->{if(ticks<10)return;var handler=(SpellResearchTableScreenHandler)mc.player.currentScreenHandler;
                    check(handler.getSlot(6).getStack().isEmpty()&&handler.getSlot(1).getStack().getCount()==32&&ScrollData.getLevel(handler.getSlot(7).getStack())==1,"Retired craft and upgrade packets neither produce items nor consume materials");
                    click(mc.currentScreen,slotted("tab.2"));next();}
                case 3->{if(ticks<6)return;check(((List<?>)field(mc.currentScreen,"entries")).size()==1,"Atlas lists unlocked spell and hides unknown spells");
                    int x=left(mc.currentScreen),y=top(mc.currentScreen);mc.currentScreen.mouseClicked(x+19,y+53,0);mc.currentScreen.mouseReleased(x+19,y+53,0);click(mc.currentScreen,Text.literal("+"));next();}
                case 4->{if(ticks<6)return;shot(mc,"01-atlas.png");click(mc.currentScreen,Text.translatable("gui.ssc_addon.research.project_spell"));next();}
                case 5->{if(!(mc.currentScreen instanceof SlottedFormationScreen screen)||ticks<6||(boolean)field(screen,"busy"))return;
                    check(Arrays.stream((int[])field(screen,"slots")).allMatch(v->v==-1),"Projection does not auto-fill any rune slots");
                    check((boolean)field(screen,"referenceVisible")&&Arrays.stream((int[])field(screen,"reference")).filter(v->v>=0).count()==3,"Unlocked target has a visible current-tier foundation projection");
                    shot(mc,"02-projection.png");click(screen,slotted("make_mode"));fillManually(screen,(int[])field(screen,"reference"));next();}
                case 6->{if(ticks<20||(boolean)field(mc.currentScreen,"busy"))return;click(mc.currentScreen,slotted("test"));next();}
                case 7->{if(ticks<6||(boolean)field(mc.currentScreen,"busy"))return;
                    check((boolean)field(mc.currentScreen,"runeValid"),"Actual manually placed glyphs pass the server evaluator");
                    shot(mc,"03-manual-upgrade.png");click(mc.currentScreen,slotted("finish"));next();}
                case 8->{var handler=(SpellResearchTableScreenHandler)mc.player.currentScreenHandler;var scroll=handler.getSlot(6).getStack();if(ScrollData.getLevel(scroll)!=2||scroll.isEmpty())return;
                    check(handler.getSlot(7).getStack().isEmpty()&&handler.getSlot(1).getStack().getCount()==30,"Upgrade input and exact ink payment synchronized to real client");
                    check(ScrollData.getUses(scroll)==1&&scroll.getNbt().getString("SpaceBinding").equals("preserved")&&scroll.getNbt().getLong("CooldownEnd")==123456,"Actual upgraded scroll retains uses cooldown and binding");
                    int x=left(mc.currentScreen),y=top(mc.currentScreen);mc.currentScreen.mouseClicked(x+293,y+117,0);mc.currentScreen.mouseReleased(x+293,y+117,0);next();}
                case 9->{if(ticks<8)return;check(mc.player.getInventory().main.stream().anyMatch(s->ScrollData.getSpell(s)==SpellRegistry.get("fire_bolt")&&ScrollData.getLevel(s)==2),"Research result collects through actual synchronized slot click");
                    mc.getServer().execute(()->{player.closeHandledScreen();player.openHandledScreen(preparation);});next();}
                case 10->{if(!(mc.currentScreen instanceof InfusionAltarScreen)||ticks<8)return;shot(mc,"04-book-config.png");click(mc.currentScreen,altar("upgrade_to",2));next();}
                case 11->{if(SpellbookData.getLevel(mc.player.currentScreenHandler.getSlot(0).getStack())!=2)return;
                    check(mc.player.currentScreenHandler.getSlot(2).getStack().getCount()==1,"Book upgrade retains its original core fee");
                    click(mc.currentScreen,altar("maintenance"));mc.getServer().execute(()->{try{prepareMaintenance();}catch(Throwable e){fail(e);}});next();}
                case 12->{if(ticks<10)return;check(((InfusionAltarScreenHandler)mc.player.currentScreenHandler).isMaintenance(),"Actual maintenance page active");click(mc.currentScreen,altar("repair"));next();}
                case 13->{var h=mc.player.currentScreenHandler;var s=h.getSlot(InfusionAltarScreenHandler.MAINTENANCE_SCROLL).getStack();if(ticks<8||ScrollData.getUses(s)!=ScrollData.getMaxUses(s))return;
                    check(h.getSlot(InfusionAltarScreenHandler.MAINTENANCE_INK).getStack().getCount()==4&&h.getSlot(1).getStack().getCount()==2,"Repair fees and restored uses synchronize");click(mc.currentScreen,altar("imprint"));next();}
                case 14->{var h=mc.player.currentScreenHandler;if(!h.getSlot(InfusionAltarScreenHandler.MAINTENANCE_SCHEME).getStack().isEmpty())return;
                    check(RuneScheme.isModified(h.getSlot(InfusionAltarScreenHandler.MAINTENANCE_SCROLL).getStack()),"Imprint creates a synchronized modified scroll");shot(mc,"05-maintenance.png");click(mc.currentScreen,altar("salvage"));next();}
                case 15->{if(ticks<4)return;click(mc.currentScreen,altar("salvage_confirm"));next();}
                case 16->{if(ticks<8||!mc.player.currentScreenHandler.getSlot(InfusionAltarScreenHandler.MAINTENANCE_SCROLL).getStack().isEmpty())return;
                    check(mc.player.getInventory().main.stream().anyMatch(s->s.isOf(RegCustomItem.UNTREATED_MOONDUST)),"Confirmed salvage returns dust to actual player inventory");
                    finished=true;Files.writeString(Path.of("research-result.json"),NeoState.JSON.toJson(Map.of("passed",true,"checks",checks,"screenshots",screenshots,"client_server_sync",true)));
                    System.out.println("NEO_RESEARCH_LIVE_PASS checks="+checks);mc.scheduleStop();}
            }
        }catch(Throwable e){fail(e);}});
    }
    private static void fail(Throwable problem){if(failure!=null)return;failure=problem;finished=true;problem.printStackTrace();
        try{Files.writeString(Path.of("research-failure.json"),NeoState.JSON.toJson(Map.of("stage",stage,"ticks",ticks,"error",problem.toString())));}catch(Exception ignored){}
        MinecraftClient.getInstance().scheduleStop();}
    public static void verifyResult()throws Exception{if(failure!=null)throw new AssertionError("Research live acceptance failed",failure);if(!finished||!Files.isRegularFile(Path.of("research-result.json")))throw new AssertionError("Research live acceptance incomplete");}
}
