package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.fabricmc.fabric.api.networking.v1.*;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;

public final class FormationResearchNetworking {
    public static final Identifier ACTION = new Identifier("ssc_addon", "formation_research_action"), STATE = new Identifier("ssc_addon", "formation_research_state");
    private static final Map<UUID, Long> LAST = new HashMap<>();
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() > 32768) return;
            final int syncId; final NbtCompound request;
            try { syncId = buf.readVarInt(); request = buf.readNbt(); if (request == null || buf.isReadable()) return; }
            catch (RuntimeException malformed) { return; }
            server.execute(() -> {
                long now = System.nanoTime(); long prior = LAST.getOrDefault(player.getUuid(), 0L);
                int action = request.getInt("Action");
                if (action < SlottedResearchManager.OPEN || action > SlottedResearchManager.INSPECT) return;
                boolean limited=action==SlottedResearchManager.TEST||action==SlottedResearchManager.COMPLETE;
                if (limited && now - prior < 150_000_000L) {
                    if (SlottedResearchManager.context(player, syncId) != null) send(player,syncId,request,SlottedResearchManager.text("wait"));
                    return;
                }
                if(limited)LAST.put(player.getUuid(), now);
                SlottedResearchManager.handle(player, syncId, request);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUuid()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
    }
    private static void send(ServerPlayerEntity p, int syncId, NbtCompound request, Text message) {
        if (!ServerPlayNetworking.canSend(p, STATE)) return;
        var world = WorldRuneState.get(p.getServer()); var progress = FormationKnowledgeComponent.get(p).research(); progress.bind(world.worldId());
        NbtCompound n = new NbtCompound(); n.putUuid("World", world.worldId());
        n.putBoolean("Slotted",true);n.putInt("Action",request.getInt("Action"));n.putInt("Level",request.getInt("Level"));n.putInt("Revision",request.getInt("Revision"));
        if(request.containsUuid("Request"))n.putUuid("Request",request.getUuid("Request"));
        n.put("Progress",progress.write());n.putUuid("Operation",progress.completionToken);
        var buf=PacketByteBufs.create();buf.writeVarInt(syncId);buf.writeNbt(n);buf.writeText(message);buf.writeBoolean(false);buf.writeVarInt(-1);buf.writeVarInt(-1);buf.writeVarInt(0);ServerPlayNetworking.send(p,STATE,buf);
    }
}
