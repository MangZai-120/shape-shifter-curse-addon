package net.ssca.neomagic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.ability.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.minion.IPlayerEntityMinion;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import java.nio.file.*;
import java.util.*;

/** Real client sees a commanded native wolf and receives yellow/orange/red/green brand snapshots. */
public final class NeoCourtLiveProbe {
    private static boolean starting;private static volatile boolean finished,wolfSeen;
    private static volatile Throwable failure;private static volatile int ticks;private static int checks,frames;
    private static ServerPlayerEntity player;private static IronGolemEntity target;
    private static volatile UUID targetId,wolfId;
    private static final Set<String> colors=new HashSet<>(),shots=new HashSet<>();
    private static final Map<String,Integer> pending=new HashMap<>();
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void form(Identifier id){
        net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils._loadForm(player,RegPlayerForms.playerForms.get(id),true);
        RegPlayerFormComponent.PLAYER_FORM.sync(player);net.onixary.shapeShifterCurseFabric.integration.origins.registry.ModComponents.ORIGIN.sync(player);NeoRuntime.refreshForm(player);
    }
    private static IronGolemEntity target(){var e=EntityType.IRON_GOLEM.create(player.getServerWorld());e.setAiDisabled(true);e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500);e.setHealth(500);e.refreshPositionAndAngles(player.getX(),player.getY(),player.getZ()+7,180,0);check(player.getServerWorld().spawnEntity(e),"Live target spawned");targetId=e.getUuid();return e;}
    private static void cast(String name){var spell=SpellRegistry.get(name);check(SpellChannelManager.start(player,spell,ScrollData.create(name),1,false,775,10,30,()->true,at->spell.cast(player,spell.getBaseDamage(),false,1),cd->{}),"Actual release channel accepts "+name);}
    public static void install(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            frames++;if(mc.getOverlay()!=null)return;
            if(!starting&&mc.currentScreen instanceof TitleScreen){starting=true;mc.options.pauseOnLostFocus=false;mc.options.getViewDistance().setValue(4);mc.options.getMaxFps().setValue(60);mc.options.language="zh_cn";mc.createIntegratedServerLoader().createAndStart("Court-Acceptance-"+System.currentTimeMillis(),new LevelInfo("Court acceptance",GameMode.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),DataConfiguration.SAFE_MODE),new GeneratorOptions(20261030L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());}
            if(mc.world==null||mc.player==null||finished)return;if(mc.currentScreen!=null)mc.setScreen(null);
            if(targetId!=null)for(Entity e:mc.world.getEntities())if(e.getUuid().equals(targetId)){
                Vec3d aim=e.getBoundingBox().getCenter().subtract(mc.player.getEyePos());mc.player.setYaw((float)Math.toDegrees(Math.atan2(-aim.x,aim.z)));mc.player.setPitch((float)-Math.toDegrees(Math.atan2(aim.y,Math.sqrt(aim.x*aim.x+aim.z*aim.z))));
            }
            if(wolfId!=null&&ticks<210)for(Entity entity:mc.world.getEntities())if(entity.getUuid().equals(wolfId)){wolfSeen=true;shot(mc,"01-commanded-wolf.png");}
            if(targetId!=null&&ticks>=250){for(String color:List.of("yellow","orange","red","green"))if(ErosionBrandClientState.hasColor(targetId,color)){colors.add(color);shot(mc,"brand-"+color+".png");}}
        }catch(Throwable e){fail(e);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{try{
            if(finished||server.getPlayerManager().getPlayerList().isEmpty())return;ticks++;
            if(ticks==70){player=server.getPlayerManager().getPlayerList().get(0);player.setNoGravity(true);player.setYaw(0);player.setPitch(0);server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);server.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false,server);server.getOverworld().setTimeOfDay(6000);NeoRuntime.data(player).spellbookMasteryExpTen=120000;form(FormIdentifiers.ANUBIS_WOLF_SP);AnubisWolfSpSoulEnergy.setEnergy(player,20);target=target();}
            if(ticks==100)cast("fire_bolt");
            if(ticks==160){var ids=((IPlayerEntityMinion)player).shape_shifter_curse$getMinionsByMinionID(AnubisWolfMinionEntity.MinionID);check(!ids.isEmpty()&&ids.size()<=AnubisWolfSpSummonWolves.maximumWolves(player),"Real native hit retains native summons and minion cap; wolves="+ids.size()+", target health="+target.getHealth()+", souls="+AnubisWolfSpSoulEnergy.getEnergy(player));var type=Class.forName(CourtSpellLink.class.getName()+"$Store");var get=type.getDeclaredMethod("get",net.minecraft.server.MinecraftServer.class);get.setAccessible(true);var store=get.invoke(null,server);var orders=type.getDeclaredField("orders");orders.setAccessible(true);check(!((Map<?,?>)orders.get(store)).isEmpty(),"Native hit issues a real court order alongside original passive summoning");wolfId=(UUID)((Map<?,?>)orders.get(store)).keySet().iterator().next();check(AnubisWolfSpSoulEnergy.getEnergy(player)==15,"Actual client player pays exactly five souls");}
            if(ticks==205)check(wolfSeen,"Real client received and rendered native minion");
            if(ticks==220){target.discard();form(FormIdentifiers.GOLDEN_SANDSTORM_SP);target=target();}
            if(ticks==240||ticks==275||ticks==320||ticks==380||ticks==440)cast("frost_spike");
            if(ticks==310)check("yellow".equals(GoldenSandstormErosionBrand.getBrandColor(player.getUuid(),targetId)),"Two native spell hits within two seconds leave one stack");
            if(ticks==500){check("green".equals(GoldenSandstormErosionBrand.getBrandColor(player.getUuid(),targetId)),"Native spells reach original passive burst and green lock");MinecraftClient.getInstance().execute(()->{try{check(colors.containsAll(List.of("yellow","orange","red","green")),"Real client receives all four original brand colors");finished=true;var report=new LinkedHashMap<String,Object>();report.put("checks",checks);report.put("wolf_seen",wolfSeen);report.put("brand_colors",colors);report.put("screenshots",shots);Files.writeString(Path.of("court-result.json"),NeoState.JSON.toJson(report));System.out.println("NEO_COURT_LIVE_PASS checks="+checks);MinecraftClient.getInstance().scheduleStop();}catch(Throwable e){fail(e);}});}
        }catch(Throwable e){fail(e);}});
    }
    private static void shot(MinecraftClient mc,String name)throws Exception{if(shots.contains(name)||frames<pending.computeIfAbsent(name,id->frames+2))return;shots.add(name);Files.createDirectories(Path.of("court-screenshots"));try(var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){image.writeTo(Path.of("court-screenshots",name));}}
    private static void fail(Throwable e){if(failure!=null)return;failure=e;finished=true;e.printStackTrace();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());}
    public static void verifyResult(){if(failure!=null)throw new AssertionError("Court live test failed",failure);if(!finished)throw new AssertionError("Client closed before court acceptance completed");}
}
