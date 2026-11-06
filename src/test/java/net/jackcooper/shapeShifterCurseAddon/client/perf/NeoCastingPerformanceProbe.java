package net.jackcooper.shapeShifterCurseAddon.client.perf;

import dev.emi.trinkets.api.TrinketsApi;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.ssca.neomagic.*;
import java.nio.file.*;
import java.util.*;

/** 仅用于一次性平坦世界的真实客户端性能采样，不访问玩家存档或开放局域网。 */
public final class NeoCastingPerformanceProbe {
    private static final String[] SCENES={"idle","fire_bolt","lunar_mend","explosion"};
    private static final List<List<Double>> FRAMES=new ArrayList<>(),TICKS=new ArrayList<>();
    private static volatile int scene=-1;
    private static int clientTicks,worldTicks,casts;
    private static long lastFrame,tickStart;
    private static boolean starting,finished;
    private static ItemStack book;
    private static Recording recording;
    public static void install() throws Exception {
        for(String ignored:SCENES){FRAMES.add(Collections.synchronizedList(new ArrayList<>()));TICKS.add(new ArrayList<>());}
        recording=new Recording(Configuration.getConfiguration("profile"));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.getOverlay()!=null)return;
            if(!starting&&++clientTicks>40&&mc.currentScreen instanceof TitleScreen){
                starting=true;mc.options.getEnableVsync().setValue(false);mc.options.getMaxFps().setValue(260);
                mc.options.getViewDistance().setValue(6);mc.options.pauseOnLostFocus=false;
                String name="NeoPerf-"+System.getProperty("neomagic.performance");
                if(Files.exists(mc.runDirectory.toPath().resolve("saves/"+name+"/level.dat")))mc.createIntegratedServerLoader().start(mc.currentScreen,name);
                else mc.createIntegratedServerLoader().createAndStart(name,
                    new LevelInfo(name,GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(20261027L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
        });
        WorldRenderEvents.START.register(context->{
            long now=System.nanoTime();int s=scene;
            if(lastFrame!=0&&s>=0&&s<SCENES.length)FRAMES.get(s).add((now-lastFrame)/1e6);
            lastFrame=now;
        });
        ServerTickEvents.START_SERVER_TICK.register(server->tickStart=System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(finished||server.getPlayerManager().getPlayerList().isEmpty())return;
            var p=server.getPlayerManager().getPlayerList().get(0);worldTicks++;
            try {
                if(worldTicks==40){
                    book=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setLevel(book,3);
                    TrinketsApi.getTrinketComponent(p).orElseThrow().getInventory().get("moonlit").get("spellbook").setStack(0,book);
                    p.getAbilities().invulnerable=true;p.setYaw(0);p.setPitch(0);p.setHealth(p.getMaxHealth());
                    p.setStackInHand(Hand.MAIN_HAND,new ItemStack(net.minecraft.item.Items.BLAZE_ROD));
                    server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
                    server.getOverworld().setTimeOfDay(6000);
                }
                if(worldTicks<200)return;
                if(worldTicks==200)recording.start();
                int next=(worldTicks-200)/300;
                if(next>=SCENES.length){finished=true;scene=-1;finish();return;}
                if(next!=scene){scene=next;System.out.println("NEOMAGIC PERF scene="+SCENES[scene]);}
                if(scene>0&&(worldTicks-200)%100==0){
                    NeoRuntime.data(p).burden=0;SharedSpellCooldowns.clearAllFor(p);
                    SpellbookData.setScroll(book,0,ScrollData.create(SCENES[scene],1));
                    p.setHealth(10);SpellCastManager.cast(p,0);
                    if(SpellChannelManager.isCasting(p))casts++;
                }
                TICKS.get(scene).add((System.nanoTime()-tickStart)/1e6);
            }catch(Throwable error){error.printStackTrace();System.exit(2);}
        });
    }
    private static Map<String,Object> statistics(List<Double> values){
        List<Double> sorted; synchronized(values){sorted=new ArrayList<>(values);}Collections.sort(sorted);
        if(sorted.isEmpty())throw new AssertionError("No native performance samples");
        return Map.of("samples",sorted.size(),"mean_ms",sorted.stream().mapToDouble(v->v).average().orElseThrow(),
            "p50_ms",sorted.get(sorted.size()/2),"p95_ms",sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.95))),"max_ms",sorted.get(sorted.size()-1));
    }
    private static void finish() throws Exception {
        if(casts<4)throw new AssertionError("Too few real channel starts: "+casts);
        recording.stop();String label=System.getProperty("neomagic.performance");
        Path jfr=Files.createTempFile("neo-perf-",".jfr");recording.dump(jfr);recording.close();
        Files.copy(jfr,Path.of(label+".jfr"),StandardCopyOption.REPLACE_EXISTING);Files.delete(jfr);
        Map<String,Object> report=new LinkedHashMap<>();report.put("kind","native-integrated-client-flat-world");report.put("casts",casts);
        for(int i=0;i<SCENES.length;i++)report.put(SCENES[i],Map.of("frame_interval",statistics(FRAMES.get(i)),"server_tick",statistics(TICKS.get(i))));
        Files.writeString(Path.of(label+".json"),NeoState.JSON.toJson(report));
        System.out.println("NEOMAGIC PERF "+label+" "+NeoState.JSON.toJson(report));
        MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());
    }
}
