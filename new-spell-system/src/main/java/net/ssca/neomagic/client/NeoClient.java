package net.ssca.neomagic.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.ssca.neomagic.*;
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
            hud=n==null?new NbtCompound():n;
            if(n!=null){data.burden=n.getDouble("C");data.recovery=n.getInt("Recovery");data.form=n.getString("Form");profile=NeoState.JSON.fromJson(n.getString("Profile"),Profile.class);}
        });});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{hud=new NbtCompound();token=null;data=new NeoState.PlayerData();COSTS.clear();TUNINGS.clear();BOOK_EFFECTS.clear();});
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
                COSTS.clear();COSTS.putAll(nextCosts);TUNINGS.clear();TUNINGS.putAll(nextTunings);
                BOOK_EFFECTS.clear();BOOK_EFFECTS.putAll(nextBook);
                if(n.getBoolean("Open"))client.setScreen(new NeoScreen());
                else if(client.currentScreen instanceof NeoScreen screen)screen.updated();
                }catch(RuntimeException malformed){NeoMagic.LOG.warn("Ignoring invalid neomagic research snapshot",malformed);}
    }
    private static void renderHud(DrawContext c,float delta) {
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.options.hudHidden||!hud.getBoolean("Visible"))return;
        int width=Math.min(220,mc.getWindow().getScaledWidth()-16),x=8,y=8;
        double value=hud.getDouble("C"),safety=hud.getDouble("S"),max=Math.max(safety+60,value*1.1);
        c.fill(x-3,y-3,x+width+3,y+58,0xC0121D2A);
        c.fill(x,y+13,x+width,y+20,0xFF394657);
        c.fill(x,y+13,x+(int)(width*Math.min(value,safety)/max),y+20,0xFF72B4A5);
        if(value>safety)c.fill(x+(int)(width*safety/max),y+13,x+(int)(width*value/max),y+20,0xFFD57462);
        int safeX=x+(int)(width*safety/max);c.fill(safeX,y+11,safeX+1,y+22,0xFFFFD57B);
        c.drawText(mc.textRenderer,NeoMagic.text("hud_bar",f(value),f(safety),f(Math.max(0,value-safety))),x,y,0xFFE5D7B6,false);
        c.drawText(mc.textRenderer,NeoMagic.text("hud_risk",f(hud.getDouble("Q")),pc(hud.getDouble("Failure")),pc(hud.getDouble("Offset"))),x,y+25,0xFFB2D2D8,false);
        c.drawText(mc.textRenderer,hud.getBoolean("Stored")?NeoMagic.text("stored_hint"):hud.getInt("Age")>=0?
                NeoMagic.text("hud_preparing",hud.getInt("Age"),hud.getInt("Prep"),f(hud.getDouble("Paid"))):
                NeoMagic.text("hud_recovery",hud.getInt("Recovery")/20.0,hud.getInt("CD")/20.0,hud.getInt("Blocked")/20.0),x,y+37,0xFFD2CBC0,false);
        if(hud.getInt("Age")>=0)c.drawText(mc.textRenderer,NeoMagic.text("hud_deviation",pc(hud.getDouble("Cumulative")),f(hud.getDouble("Yaw")),f(hud.getDouble("Pitch"))),x,y+49,0xFFD2CBC0,false);
    }
    public static String f(double n) {return String.format(Locale.ROOT,"%.1f",n);}
    public static String pc(double n) {return String.format(Locale.ROOT,"%.1f%%",n*100);}
}
