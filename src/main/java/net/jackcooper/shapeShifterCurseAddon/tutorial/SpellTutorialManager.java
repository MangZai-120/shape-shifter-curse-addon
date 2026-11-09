package net.jackcooper.shapeShifterCurseAddon.tutorial;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneRole;
import net.jackcooper.shapeShifterCurseAddon.spell.research.WorldRuneState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoState;

/** 新世界按玩家播放一次；只确认真实显示，不在登录时提前消耗观看记录。 */
public final class SpellTutorialManager {
    public static final Identifier OFFER=new Identifier("ssc_addon","spell_tutorial_offer");
    public static final Identifier ACTION=new Identifier("ssc_addon","spell_tutorial_action");
    private record Pending(ServerPlayerEntity player,UUID nonce) {}
    private record Delivery(ServerPlayerEntity player,int until) {}
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static final Map<UUID,Integer> REQUESTED=new HashMap<>();
    private static final Map<MinecraftServer,Boolean> FRESH=new HashMap<>();
    private static final Map<UUID,Delivery> JOIN_QUEUE=new HashMap<>();
    private static boolean registered;
    private SpellTutorialManager() {}

    public static void register() {
        if(registered)return;registered=true;
        ServerLifecycleEvents.SERVER_STARTING.register(server->
                FRESH.put(server,!server.getSaveProperties().getMainWorldProperties().isInitialized()));
        ServerLifecycleEvents.SERVER_STARTED.register(SpellTutorialManager::initializePolicy);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{FRESH.remove(server);PENDING.clear();REQUESTED.clear();JOIN_QUEUE.clear();});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->
                JOIN_QUEUE.put(handler.player.getUuid(),new Delivery(handler.player,server.getTicks()+200)));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(JOIN_QUEUE.isEmpty())return;
            for(var entry:java.util.List.copyOf(JOIN_QUEUE.entrySet())){
                Delivery delivery=entry.getValue();var player=delivery.player;
                if(player.getServer()!=server)continue;
                NeoState state=NeoState.get(server);
                if(server.getPlayerManager().getPlayer(entry.getKey())!=player||server.getTicks()>delivery.until
                        ||!state.tutorialAutoEnabled||state.player(entry.getKey()).spellTutorialSeen){JOIN_QUEUE.remove(entry.getKey(),delivery);continue;}
                if(ServerPlayNetworking.canSend(player,OFFER)){offer(player,true);JOIN_QUEUE.remove(entry.getKey(),delivery);}
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var pending=PENDING.get(handler.player.getUuid());
            if(pending!=null&&pending.player==handler.player)PENDING.remove(handler.player.getUuid());
            var delivery=JOIN_QUEUE.get(handler.player.getUuid());
            if(delivery!=null&&delivery.player==handler.player)JOIN_QUEUE.remove(handler.player.getUuid());
            var current=server.getPlayerManager().getPlayer(handler.player.getUuid());
            if(current==null||current==handler.player)REQUESTED.remove(handler.player.getUuid());
        });
        ServerPlayNetworking.registerGlobalReceiver(ACTION,(server,player,handler,buf,sender)->{
            if(buf.readableBytes()<1)return;
            int action=buf.readUnsignedByte();
            if(action==0&&buf.readableBytes()==0)server.execute(()->offer(player,false));
            else if(action==1&&buf.readableBytes()==16){UUID nonce=buf.readUuid();server.execute(()->acknowledge(player,nonce));}
        });
    }

    private static void initializePolicy(MinecraftServer server) {
        NeoState state=NeoState.get(server);
        if(state.tutorialPolicyInitialized)return;
        state.tutorialPolicyInitialized=true;
        state.tutorialAutoEnabled=FRESH.getOrDefault(server,false);
        state.markDirty();
    }

    public static boolean offer(ServerPlayerEntity player,boolean automatic) {
        var server=player.getServer();
        if(server==null||server.getPlayerManager().getPlayer(player.getUuid())!=player||!ServerPlayNetworking.canSend(player,OFFER))return false;
        initializePolicy(server);
        NeoState state=NeoState.get(server);
        if(automatic&&(!state.tutorialAutoEnabled||state.player(player.getUuid()).spellTutorialSeen))return false;
        int now=server.getTicks();
        Integer last=REQUESTED.get(player.getUuid());
        if(!automatic&&last!=null&&now>=last&&now-last<20)return false;
        REQUESTED.put(player.getUuid(),now);
        UUID nonce=UUID.randomUUID();PENDING.put(player.getUuid(),new Pending(player,nonce));
        var language=WorldRuneState.get(server);
        var packet=PacketByteBufs.create();packet.writeUuid(nonce);packet.writeUuid(language.worldId());packet.writeBoolean(automatic);
        for(RuneRole role:new RuneRole[]{RuneRole.SOURCE,RuneRole.GAIN,RuneRole.STABLE})packet.writeByte(language.language().glyph(role));
        ServerPlayNetworking.send(player,OFFER,packet);
        return true;
    }

    public static boolean acknowledge(ServerPlayerEntity player,UUID nonce) {
        var pending=PENDING.get(player.getUuid());
        if(pending==null||pending.player!=player||!pending.nonce.equals(nonce)
                ||player.getServer().getPlayerManager().getPlayer(player.getUuid())!=player)return false;
        NeoState state=NeoState.get(player.getServer());
        state.player(player.getUuid()).spellTutorialSeen=true;state.markDirty();
        PENDING.remove(player.getUuid());
        return true;
    }
}
