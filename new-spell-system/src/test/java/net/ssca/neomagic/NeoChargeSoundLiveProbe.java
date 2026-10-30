package net.ssca.neomagic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.util.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Real server cue and client moving-sound listener, on the 59/60 tick boundary. */
public final class NeoChargeSoundLiveProbe {
    private static boolean starting;private static volatile boolean finished;private static volatile Throwable failure;
    private static volatile int ticks;private static volatile String stage="startup";private static int checks,releases;private static ServerPlayerEntity player;
    private static final Map<String,List<String>> sounds=new ConcurrentHashMap<>();
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static class TimedSpell extends Spell {
        final int duration;TimedSpell(int duration){this(duration,"frost_armor");}TimedSpell(int duration,String path){super(new Identifier("ssc_addon",path),SpellRarity.WHITE);this.duration=duration;}
        @Override public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity p,int level,boolean solo){return new SpellCastingRules.Profile(duration,.8,false);}
        @Override public SpellCastingRules.Mode getCastingMode(){return SpellCastingRules.Mode.AUTOMATIC;}
        @Override public void cast(ServerPlayerEntity p,float power,boolean solo){}
    }
    private static void start(int duration){start(duration,false);}
    private static void start(int duration,boolean domain){
        stage=(domain?"domain_":"standard_")+duration;
        var actualDomain=new net.jackcooper.shapeShifterCurseAddon.spell.spells.DomainSpell();
        Spell spell=domain?new TimedSpell(duration,"domain"){
            @Override public void onChannelStarted(ServerPlayerEntity p){actualDomain.onChannelStarted(p);}
            @Override public boolean canContinueCasting(ServerPlayerEntity p,net.minecraft.item.ItemStack scroll){return actualDomain.canContinueCasting(p,scroll);}
            @Override public void onChannelEnded(ServerPlayerEntity p,boolean interrupted){actualDomain.onChannelEnded(p,interrupted);}
        }:new TimedSpell(duration);
        check(SpellChannelManager.start(player,spell,ScrollData.create(domain?"domain":"frost_armor"),1,false,990,1,20,()->true,at->{releases++;if(domain){DomainManager.activate(player);DomainManager.remove(player);}player.getServerWorld().playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),SoundCategory.PLAYERS,1,1);},cd->{}),"Real channel accepted "+stage);
    }
    private static void verify(String label,boolean audible){var events=sounds.getOrDefault(label,List.of());check(events.contains("block.beacon.activate")==audible,"Server beacon cue obeys actual duration "+label+": "+events);check(events.contains("block.conduit.ambient.short")==audible,"Charge loop obeys actual duration "+label+": "+events);check(events.contains("block.note_block.pling"),"Release sound still plays for "+label);}
    public static void install(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(mc.getOverlay()!=null)return;
            if(!starting&&mc.currentScreen instanceof TitleScreen){starting=true;mc.options.pauseOnLostFocus=false;mc.options.getViewDistance().setValue(4);mc.options.getMaxFps().setValue(60);mc.getSoundManager().registerListener((sound,set)->{String id=sound.getId().getPath();if(Set.of("block.beacon.activate","block.conduit.ambient.short","block.note_block.pling").contains(id))sounds.computeIfAbsent(stage,k->Collections.synchronizedList(new ArrayList<>())).add(id);});mc.createIntegratedServerLoader().createAndStart("Charge-Sound-"+System.currentTimeMillis(),new LevelInfo("Charge sound acceptance",GameMode.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),DataConfiguration.SAFE_MODE),new GeneratorOptions(20261030L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());}
            if(mc.world!=null&&mc.player!=null&&!finished&&mc.currentScreen!=null)mc.setScreen(null);
        }catch(Throwable e){fail(e);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{try{
            if(finished||server.getPlayerManager().getPlayerList().isEmpty())return;ticks++;
            if(ticks==70){player=server.getPlayerManager().getPlayerList().get(0);player.setNoGravity(true);NeoRuntime.data(player).spellbookMasteryExpTen=120000;server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);}
            if(ticks==100)start(59);
            if(ticks==190){verify("standard_59",false);start(60);}
            if(ticks==280){verify("standard_60",true);start(8);}
            if(ticks==330){verify("standard_8",false);start(59,true);}
            if(ticks==420){verify("domain_59",false);start(60,true);}
            if(ticks==510){verify("domain_60",true);check(releases==5,"Quiet and audible channels all release exactly once");MinecraftClient.getInstance().execute(()->{try{finished=true;var result=new LinkedHashMap<String,Object>();result.put("checks",checks);result.put("sounds_by_actual_ticks",sounds);Files.writeString(Path.of("charge-sound-result.json"),NeoState.JSON.toJson(result));System.out.println("NEO_CHARGE_SOUND_LIVE_PASS checks="+checks);MinecraftClient.getInstance().scheduleStop();}catch(Throwable e){fail(e);}});}
        }catch(Throwable e){fail(e);}});
    }
    private static void fail(Throwable e){if(failure!=null)return;failure=e;finished=true;e.printStackTrace();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());}
    public static void verifyResult(){if(failure!=null)throw new AssertionError("Charge sound test failed",failure);if(!finished)throw new AssertionError("Client exited early");}
}
