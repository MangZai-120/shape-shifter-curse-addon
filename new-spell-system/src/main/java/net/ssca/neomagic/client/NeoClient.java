package net.ssca.neomagic.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.ssca.neomagic.*;
import net.jackcooper.shapeShifterCurseAddon.client.hud.NeoStabilityBar;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

public final class NeoClient implements ClientModInitializer {
    public static NeoState.PlayerData data=new NeoState.PlayerData();
    public static Profile profile=new Profile(100,1,1,1,1,true);
    public static final Map<Core,Coefficients> COSTS=new EnumMap<>(Core.class);
    public static final Map<String,Tuning> TUNINGS=new HashMap<>();
    public static final Map<Core,double[]> BOOK_EFFECTS=new EnumMap<>(Core.class);
    public static Tuning tuning(Blueprint b){return TUNINGS.getOrDefault(b.identity()+"#"+b.band(),TUNINGS.getOrDefault(b.identity(),Tuning.defaults()));}
    public static double bodyMastery(Blueprint b) {
        var values=data.progress.get(b.identity());double needed=tuning(b).mastery()>0?tuning(b).mastery():masteryNeeded(b);
        return values==null?0:clamp(values[b.band()]/needed,0,1);
    }
    public static double bookPower(Blueprint b){return BOOK_EFFECTS.getOrDefault(b.output(),new double[]{1,1})[1];}
    public static Quote preview(Blueprint b){var q=quote(b,bodyMastery(b),COSTS.getOrDefault(b.core(),coefficients(b.core())),tuning(b));double m=BOOK_EFFECTS.getOrDefault(b.core(),new double[]{1,1})[0];return new Quote(q.normal()*m,q.actual()*m,q.floor(),q.plan());}
    public static NbtCompound hud=new NbtCompound();
    private static int failureFlash,resetFlash;
    public static UUID token;
    public static boolean table;
    public static String rules="";
    public static void action(String action,Blueprint b,String hypothesis,String conclusion,String citations) {
        if(token==null)return;
        var n=new NbtCompound();n.putUuid("Token",token);n.putString("Action",action);
        if(b!=null)n.putString("Blueprint",NeoState.JSON.toJson(b));
        if(hypothesis!=null)n.putString("Hypothesis",hypothesis);
        if(conclusion!=null)n.putString("Conclusion",conclusion);
        if(citations!=null)n.putString("Citations",citations);
        var packet=PacketByteBufs.create();packet.writeNbt(n);ClientPlayNetworking.send(NeoNetwork.ACTION,packet);
    }
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(NeoNetwork.STATE,(client,handler,buf,sender)->{
            NbtCompound n=buf.readNbt();if(n==null)return;
            client.execute(()->acceptSnapshot(client,n));
        });
        ClientPlayNetworking.registerGlobalReceiver(NeoNetwork.HUD,(client,handler,buf,sender)->{var n=buf.readNbt();client.execute(()->{
            if(n!=null&&hud.contains("Form")){
                boolean newResult=!n.getString("ResultId").equals(hud.getString("ResultId"))||!n.getString("Result").equals(hud.getString("Result"));
                if(newResult&&n.getString("Result").equals("failed"))failureFlash=20;
                if(!n.getString("Form").equals(hud.getString("Form"))&&n.getDouble("C")==0)resetFlash=20;
            }
            hud=n==null?new NbtCompound():n;
            if(n!=null){data.burden=n.getDouble("C");data.recovery=n.getInt("Recovery");data.spellbookMasteryExpTen=n.getInt("MasteryExpTen");net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.acceptClientExperience(data.spellbookMasteryExpTen);data.form=n.getString("Form");profile=NeoState.JSON.fromJson(n.getString("Profile"),Profile.class);}
        });});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{hud=new NbtCompound();failureFlash=resetFlash=0;token=null;data=new NeoState.PlayerData();net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.acceptClientExperience(0);COSTS.clear();TUNINGS.clear();BOOK_EFFECTS.clear();});
        NeoStabilityBar.registerResources();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
            hud.putBoolean("RecentFailure",failureFlash>0);hud.putBoolean("RecentReset",resetFlash>0);
            if(failureFlash>0)failureFlash--;if(resetFlash>0)resetFlash--;
        });
        HudRenderCallback.EVENT.register(NeoClient::renderHud);
    }
    public static void acceptSnapshot(MinecraftClient client,NbtCompound n) {
                try {
                var nextData=NeoState.JSON.fromJson(new String(NeoPayload.unpack(n.getByteArray("DataJson")),java.nio.charset.StandardCharsets.UTF_8),NeoState.PlayerData.class);
                NeoState.validate(nextData);
                var nextProfile=NeoState.JSON.fromJson(n.getString("Profile"),Profile.class);var nextToken=n.getUuid("Token");
                var nextCosts=new EnumMap<Core,Coefficients>(Core.class);var nextTunings=new HashMap<String,Tuning>();
                var object=com.google.gson.JsonParser.parseString(n.getString("Costs")).getAsJsonObject();
                for(Core c:Core.values())nextCosts.put(c,NeoState.JSON.fromJson(object.get(c.name()),Coefficients.class));
                var nextBook=new EnumMap<Core,double[]>(Core.class);
                if(!n.getString("BookEffects").isEmpty()) {
                    var effects=com.google.gson.JsonParser.parseString(n.getString("BookEffects")).getAsJsonObject();
                    for(Core core:Core.values()) {
                        var values=NeoState.JSON.fromJson(effects.get(core.name()),double[].class);
                        if(values==null||values.length!=2||Arrays.stream(values).anyMatch(v->!Double.isFinite(v))||values[0]<=0||values[1]<0)throw new IllegalArgumentException("Invalid book modifiers");
                        nextBook.put(core,values);
                    }
                }
                var tunings=com.google.gson.JsonParser.parseString(new String(NeoPayload.unpack(n.getByteArray("Tunings")),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                tunings.entrySet().forEach(e->nextTunings.put(e.getKey(),NeoState.JSON.fromJson(e.getValue(),Tuning.class)));
                if(nextProfile==null||nextCosts.values().stream().anyMatch(java.util.Objects::isNull))throw new IllegalArgumentException("Missing snapshot rules");
                data=nextData;profile=nextProfile;token=nextToken;table=n.getBoolean("Table");rules=n.getString("Rules");
                net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.acceptClientExperience(data.spellbookMasteryExpTen);
                COSTS.clear();COSTS.putAll(nextCosts);TUNINGS.clear();TUNINGS.putAll(nextTunings);
                BOOK_EFFECTS.clear();BOOK_EFFECTS.putAll(nextBook);
                if(n.getBoolean("Open"))client.setScreen(new NeoScreen());
                else if(client.currentScreen instanceof NeoScreen screen)screen.updated();
                }catch(RuntimeException malformed){NeoMagic.LOG.warn("Ignoring invalid neomagic research snapshot",malformed);}
    }
    private static void renderHud(DrawContext c,float delta) {
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.options.hudHidden||!hud.getBoolean("Visible"))return;
        var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();
        if(cfg.showDisintegrationDebugBar)NeoStabilityHud.draw(c,mc.textRenderer,hud,8,8,Math.min(NeoStabilityHud.WIDTH,mc.getWindow().getScaledWidth()-16),System.nanoTime()/50_000_000.0);
    }
    public static void renderPixelBarBeforeChat(DrawContext c,float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||mc.options.hudHidden||!hud.getBoolean("Visible"))return;
        var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();if(!cfg.showNeoStabilityBar)return;
        int[] pos=NeoStabilityBar.position(cfg.neoStabilityBarPosType,cfg.neoStabilityBarPosOffsetX,cfg.neoStabilityBarPosOffsetY,mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight());NeoStabilityBar.draw(c,hud,pos[0],pos[1]);
    }
    public static String f(double n) {return String.format(Locale.ROOT,"%.1f",n);}
    public static String pc(double n) {return String.format(Locale.ROOT,"%.1f%%",n*100);}
}
