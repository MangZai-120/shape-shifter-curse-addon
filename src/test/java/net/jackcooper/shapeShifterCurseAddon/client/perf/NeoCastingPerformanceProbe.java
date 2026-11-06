package net.jackcooper.shapeShifterCurseAddon.client.perf;

import com.mojang.blaze3d.platform.GlDebugInfo;
import dev.emi.trinkets.api.TrinketsApi;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.ssca.neomagic.*;
import java.nio.file.*;
import java.util.*;

/** Native client measurements in a fresh disposable world; never opens player saves or LAN. */
public final class NeoCastingPerformanceProbe {
    private static final String[] SCENES={"idle","fire_bolt","lunar_mend","explosion"};
    private static final int[] SCENE_TICKS={400,400,400,1000};
    private static final int WARMUP=100, CONTROLLED_MASTERY=120000;
    private static final Object SAMPLES=new Object();
    private static final List<List<Double>> FRAMES=new ArrayList<>(),TICKS=new ArrayList<>(),INTERVALS=new ArrayList<>();
    private static final List<List<NeoState.Experiment>> CAST_RECORDS=new ArrayList<>();
    private static final int[] CASTS=new int[SCENES.length],ATTEMPTS=new int[SCENES.length],FOCUSED_FRAMES=new int[SCENES.length];
    private static final List<String> SCREENSHOTS=new ArrayList<>();
    private static final boolean[] SHOT_TAKEN=new boolean[SCENES.length];
    private static final long STARTED=System.nanoTime();
    private static volatile int scene=-1,sampling=-1,phaseTick=-1;
    private static volatile boolean finished;
    private static volatile Throwable failure;
    private static int clientTicks,worldTicks,frameScene=-1,previousTickScene=-1;
    private static long lastFrame,tickStart,previousTick;
    private static boolean starting,installed;
    private static int requestedViewDistance,requestedSimulationDistance,actualViewDistance,actualSimulationDistance;
    private static String label,worldName;
    private static Map<String,Object> environment;
    private static ItemStack book;
    private static Recording recording;

    public static void install() throws Exception {
        if(installed)throw new IllegalStateException("Performance probe installed twice");
        installed=true;
        label=System.getProperty("neomagic.performance","baseline");
        if(!label.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))throw new IllegalArgumentException("Invalid performance label");
        requestedViewDistance=distance("ssca.audit.viewDistance",6,2,32);
        requestedSimulationDistance=distance("ssca.audit.simulationDistance",6,5,32);
        for(String suffix:List.of(".json","-raw.json",".jfr"))
            if(Files.exists(Path.of(label+suffix)))throw new IllegalStateException("Performance output exists; use a new neoPerfLabel: "+label);
        worldName="NeoPerf-"+label+"-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8);
        for(String ignored:SCENES){FRAMES.add(new ArrayList<>());TICKS.add(new ArrayList<>());INTERVALS.add(new ArrayList<>());CAST_RECORDS.add(new ArrayList<>());}
        recording=new Recording(Configuration.getConfiguration("profile"));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(finished||failure!=null)return;
            try {
                if(System.nanoTime()-STARTED>600_000_000_000L)throw new AssertionError("Performance timeout at scene "+scene+" / tick "+worldTicks);
                if(mc.getOverlay()!=null)return;
                // Test-only normal shutdown reproduces the completion gate without killing the JVM.
                if(Boolean.getBoolean("ssca.audit.earlyExit")&&mc.currentScreen instanceof TitleScreen){mc.scheduleStop();return;}
                if(!starting&&++clientTicks>40&&mc.currentScreen instanceof TitleScreen){
                    starting=true;mc.options.getEnableVsync().setValue(false);mc.options.getMaxFps().setValue(260);
                    mc.options.getViewDistance().setValue(requestedViewDistance);mc.options.getSimulationDistance().setValue(requestedSimulationDistance);mc.options.pauseOnLostFocus=false;
                    actualViewDistance=mc.options.getViewDistance().getValue();actualSimulationDistance=mc.options.getSimulationDistance().getValue();
                    environment=new LinkedHashMap<>();
                    environment.put("cpu",GlDebugInfo.getCpuInfo());environment.put("gpu_renderer",GlDebugInfo.getRenderer());
                    environment.put("gl_vendor",GlDebugInfo.getVendor());environment.put("gl_version",GlDebugInfo.getVersion());
                    environment.put("java_version",System.getProperty("java.version"));environment.put("java_vendor",System.getProperty("java.vendor"));
                    environment.put("os",System.getProperty("os.name")+" "+System.getProperty("os.version"));
                    environment.put("processors",Runtime.getRuntime().availableProcessors());environment.put("max_heap_bytes",Runtime.getRuntime().maxMemory());
                    environment.put("framebuffer_width",mc.getWindow().getFramebufferWidth());environment.put("framebuffer_height",mc.getWindow().getFramebufferHeight());
                    var loader=net.fabricmc.loader.api.FabricLoader.getInstance();
                    environment.put("mods",loader.getAllMods().stream().collect(java.util.stream.Collectors.toMap(
                            mod->mod.getMetadata().getId(),mod->mod.getMetadata().getVersion().getFriendlyString(),(a,b)->a,TreeMap::new)));
                    mc.createIntegratedServerLoader().createAndStart(worldName,
                        new LevelInfo(worldName,GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(20261027L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if(mc.world==null||mc.player==null)return;
                if(mc.currentScreen!=null)mc.setScreen(null);
                int current=scene,phase=phaseTick;
                mc.player.setYaw(0);mc.player.setPitch(current==3?10:0);
                // Screenshots are captured during warmup, before any measured frame intervals.
                if(current>=0&&!SHOT_TAKEN[current]&&phase>=(current==3?40:10)&&phase<WARMUP-10){
                    String filename=label+"-"+SCENES[current]+".png";
                    try(var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){image.writeTo(Path.of(filename));}
                    synchronized(SAMPLES){SCREENSHOTS.add(filename);SHOT_TAKEN[current]=true;}
                }
            } catch(Throwable error){fail(error);}
        });
        WorldRenderEvents.START.register(context->{
            long now=System.nanoTime();
            synchronized(SAMPLES){
                int current=sampling;
                if(lastFrame!=0&&frameScene==current&&current>=0){
                    FRAMES.get(current).add((now-lastFrame)/1e6);
                    if(MinecraftClient.getInstance().isWindowFocused())FOCUSED_FRAMES[current]++;
                }
                lastFrame=now;frameScene=current;
            }
        });
        ServerTickEvents.START_SERVER_TICK.register(server->{
            tickStart=System.nanoTime();
            synchronized(SAMPLES){
                int current=sampling;
                if(previousTick!=0&&previousTickScene==current&&current>=0)INTERVALS.get(current).add((tickStart-previousTick)/1e6);
                previousTick=tickStart;previousTickScene=current;
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            var state=NeoState.get(server);state.tutorialPolicyInitialized=true;state.tutorialAutoEnabled=false;state.markDirty();
            // All mod initializer END handlers have been registered before this timing listener.
            ServerTickEvents.END_SERVER_TICK.register(NeoCastingPerformanceProbe::serverTick);
        });
    }

    private static void serverTick(net.minecraft.server.MinecraftServer server){
        if(finished||failure!=null||server.getPlayerManager().getPlayerList().isEmpty())return;
        var player=server.getPlayerManager().getPlayerList().get(0);worldTicks++;
        try {
            if(worldTicks==40){
                book=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setLevel(book,3);
                book=equipBook(player,book);
                NeoRuntime.data(player).spellbookMasteryExpTen=CONTROLLED_MASTERY;
                player.getAbilities().invulnerable=true;player.setYaw(0);player.setPitch(0);player.setHealth(player.getMaxHealth());
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(net.minecraft.item.Items.BLAZE_ROD));
                server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
                server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);server.getOverworld().setTimeOfDay(6000);
            }
            if(worldTicks<200)return;
            if(worldTicks==200)recording.start();
            int next=0,phase=worldTicks-200;
            while(next<SCENES.length&&phase>=SCENE_TICKS[next]){phase-=SCENE_TICKS[next];next++;}
            if(next>=SCENES.length){
                synchronized(SAMPLES){sampling=-1;}
                scene=-1;phaseTick=-1;finish(player);return;
            }
            if(next!=scene){scene=next;System.out.println("NEOMAGIC PERF scene="+SCENES[scene]);}
            phaseTick=phase;
            synchronized(SAMPLES){sampling=phase>=WARMUP?next:-1;}
            if(scene>0&&!SpellChannelManager.isCasting(player)&&(scene==3?phase==20:phase%100==0))startCast(player);
            synchronized(SAMPLES){if(sampling>=0)TICKS.get(sampling).add((System.nanoTime()-tickStart)/1e6);}
        }catch(Throwable error){fail(error);}
    }

    private static void startCast(ServerPlayerEntity player){
        ATTEMPTS[scene]++;
        var data=NeoRuntime.data(player);data.burden=0;SharedSpellCooldowns.clearAllFor(player);
        book=SpellCastManager.getEquippedBook(player);
        if(!validBook(book))throw new AssertionError("Production spellbook lookup lost the equipped test book");
        ItemStack scroll=ScrollData.create(SCENES[scene],1);SpellbookData.setScroll(book,0,scroll);
        ItemStack selected=SpellCastManager.getEquippedBook(player);
        if(!validBook(selected)||!ItemStack.areEqual(SpellbookData.getScroll(selected,0),scroll))
            throw new AssertionError("The equipped live spellbook did not retain the injected "+SCENES[scene]+" scroll");
        book=selected;
        Set<String> before=new HashSet<>();for(var record:data.records)before.add(record.id);
        player.setHealth(10);player.setYaw(0);player.setPitch(scene==3?10:0);SpellCastManager.cast(player,0);
        var added=data.records.stream().filter(record->!before.contains(record.id)&&record.spell.endsWith(":"+SCENES[scene])).toList();
        if(added.size()>1)throw new AssertionError("One request created multiple casting records");
        if(!added.isEmpty()){CASTS[scene]++;CAST_RECORDS.get(scene).add(added.get(0));}
    }

    private static ItemStack equipBook(ServerPlayerEntity player,ItemStack requested) throws Exception {
        dev.emi.trinkets.api.TrinketInventory placed=null;
        Throwable trinketsFailure=null;
        try {
            var component=TrinketsApi.getTrinketComponent(player);
            var group=component.isEmpty()?null:component.get().getInventory().get("moonlit");
            placed=group==null?null:group.get("spellbook");
            if(placed!=null&&placed.size()>0)placed.setStack(0,requested);
            ItemStack selected=SpellCastManager.getEquippedBook(player);
            if(validBook(selected)){
                environment.put("equipped_path","trinkets:moonlit/spellbook");return selected;
            }
        }catch(Exception|LinkageError error){trinketsFailure=error;}
        // Remove the unselected test stack so a mixed-framework run never equips two books.
        if(placed!=null&&placed.size()>0)placed.setStack(0,ItemStack.EMPTY);
        Class<?> api=null;
        for(ClassLoader loader:new ClassLoader[]{Thread.currentThread().getContextClassLoader(),NeoCastingPerformanceProbe.class.getClassLoader()}){
            if(loader==null)continue;
            try{api=Class.forName("top.theillusivec4.curios.api.CuriosApi",true,loader);break;}
            catch(ClassNotFoundException ignored){}
        }
        if(api==null)throw new AssertionError("Neither Trinkets nor the Curios API can equip a production-visible test spellbook",trinketsFailure);
        Object holder=api.getMethod("getCuriosInventory",net.minecraft.entity.LivingEntity.class).invoke(null,player);
        if(holder==null)throw new AssertionError("Curios returned no inventory for the test player");
        Object resolved=holder instanceof Optional<?>?holder:holder.getClass().getMethod("resolve").invoke(holder);
        if(!(resolved instanceof Optional<?> optional)||optional.isEmpty())throw new AssertionError("Curios inventory capability is not ready");
        Object inventory=optional.get();
        inventory.getClass().getMethod("setEquippedCurio",String.class,int.class,ItemStack.class).invoke(inventory,"spellbook",0,requested);
        ItemStack selected=SpellCastManager.getEquippedBook(player);
        if(!validBook(selected))throw new AssertionError("Curios spellbook equipment is not visible through the production spellbook lookup");
        environment.put("equipped_path","curios:spellbook");
        return selected; // Some frameworks copy stacks; all later mutations target the live equipped object.
    }

    private static boolean validBook(ItemStack stack){
        return stack!=null&&!stack.isEmpty()&&stack.isOf(SscAddon.MOON_DUST_SPELLBOOK)&&SpellbookData.getLevel(stack)==3;
    }

    private static Map<String,Object> statistics(List<Double> values){
        List<Double> sorted=new ArrayList<>(values);Collections.sort(sorted);
        if(sorted.isEmpty())throw new AssertionError("No native performance samples");
        double mean=sorted.stream().mapToDouble(value->value).average().orElseThrow();
        return Map.of("samples",sorted.size(),"mean_ms",mean,
            "p50_ms",sorted.get(sorted.size()/2),"p95_ms",sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.95))),
            "p99_ms",sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.99))),"max_ms",sorted.get(sorted.size()-1),"rate_per_second",1000.0/mean);
    }

    public static void verifyResult(){
        if(recording!=null)recording.close();
        if(failure!=null)throw new AssertionError("Performance probe failed",failure);
        if(!finished)throw new AssertionError("Performance client stopped before producing complete measurements");
    }

    private static void finish(ServerPlayerEntity player) throws Exception {
        int[] released=new int[SCENES.length];
        List<Map<String,Object>> records=new ArrayList<>();
        for(int i=1;i<SCENES.length;i++){
            if(CASTS[i]<(i==3?1:2))throw new AssertionError("Too few real channel starts for "+SCENES[i]+": "+CASTS[i]);
            for(var record:CAST_RECORDS.get(i)){
                if(!record.result.equals("released"))throw new AssertionError(SCENES[i]+" did not finish its real charge and release: "+record.result);
                released[i]++;
                records.add(Map.of("id",record.id,"spell",record.spell,"result",record.result,"preparation_ticks",record.preparation));
            }
        }
        Map<String,Object> report=new LinkedHashMap<>(),raw=new LinkedHashMap<>();
        synchronized(SAMPLES){
            if(SCREENSHOTS.size()!=SCENES.length)throw new AssertionError("A warmup scene screenshot was not captured");
            for(int i=0;i<SCENES.length;i++){
                if(TICKS.get(i).size()!=SCENE_TICKS[i]-WARMUP||INTERVALS.get(i).size()!=SCENE_TICKS[i]-WARMUP-1)
                    throw new AssertionError("Incomplete server samples for "+SCENES[i]);
                report.put(SCENES[i],Map.of("frame_interval",statistics(FRAMES.get(i)),"server_event_window",statistics(TICKS.get(i)),
                        "tick_interval",statistics(INTERVALS.get(i)),"focused_frame_samples",FOCUSED_FRAMES[i]));
            }
            raw.put("scenes",SCENES);raw.put("frame_ms",FRAMES);raw.put("server_event_ms",TICKS);raw.put("tick_interval_ms",INTERVALS);
            report.put("screenshots",List.copyOf(SCREENSHOTS));
        }
        report.put("kind","native-integrated-client-flat-world");report.put("label",label);
        report.put("run_token",System.getProperty("ssca.audit.runToken","standalone"));report.put("completed",true);
        report.put("world",worldName);report.put("environment",environment);
        report.put("casts",Arrays.stream(CASTS).sum());report.put("casts_by_scene",CASTS);report.put("attempts_by_scene",ATTEMPTS);
        report.put("released_by_scene",released);report.put("cast_records",records);
        report.put("settings",Map.of("seed",20261027L,"view_distance",actualViewDistance,"simulation_distance",actualSimulationDistance,"fps_option_value",260,
                "fps_limit_mode","unlimited in Minecraft 1.20.1","vsync",false,"scene_ticks",SCENE_TICKS,"scene_warmup_ticks",WARMUP,
                "controlled_mastery_exp_ten",CONTROLLED_MASTERY,"form",NeoRuntime.data(player).form));
        report.put("measurement_scope","Frame callback wall-clock intervals and START-to-probe-END event windows; event windows exclude tick work outside those callbacks. JFR profiling is enabled. Warmup screenshots are outside timed samples.");
        recording.stop();recording.dump(Path.of(label+".jfr"));recording.close();
        Files.writeString(Path.of(label+"-raw.json"),NeoState.JSON.toJson(raw));
        Files.writeString(Path.of(label+".json"),NeoState.JSON.toJson(report));
        finished=true;
        System.out.println("NEOMAGIC PERF "+label+" "+NeoState.JSON.toJson(report));
        MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());
    }

    private static void fail(Throwable error){
        synchronized(SAMPLES){if(failure!=null||finished)return;failure=error;sampling=-1;}
        error.printStackTrace();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());
    }

    private static int distance(String property,int fallback,int minimum,int maximum){
        String raw=System.getProperty(property,Integer.toString(fallback));
        final int value;
        try{value=Integer.parseInt(raw);}catch(NumberFormatException error){throw new IllegalArgumentException(property+" must be an integer",error);}
        if(value<minimum||value>maximum)throw new IllegalArgumentException(property+" must be within "+minimum+".."+maximum);
        return value;
    }
}
