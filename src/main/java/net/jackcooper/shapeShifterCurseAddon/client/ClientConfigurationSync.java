package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration;
import net.jackcooper.shapeShifterCurseAddon.client.colorpicker.AdvancedColorBridge;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry;
import net.jackcooper.shapeShifterCurseAddon.network.ClientConfigurationSession;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceSchema;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceSnapshot;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceWire;
import net.jackcooper.shapeShifterCurseAddon.network.JsonSnapshotSync;
import net.jackcooper.shapeShifterCurseAddon.network.JsonSnapshotWire;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.networking.ModPackets;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2C;

import java.util.Map;

/** Configuration completion is the authenticated point at which local appearance can be uploaded. */
@Environment(EnvType.CLIENT)
public final class ClientConfigurationSync {
    private static ClientConfigurationSession session;
    private static boolean appearancePending;
    private static int appearanceDelay;
    private ClientConfigurationSync() {}

    public static void register(BalanceSchema schema) {
        session = new ClientConfigurationSession(schema, ClientConfigurationSync::apply, completion -> {
            var ack = PacketByteBufs.create();
            ack.writeLong(completion.session()); ack.writeLong(completion.revision());
            ClientPlayNetworking.send(BalanceIntegration.PACKET_BALANCE_ACK, ack);
            if (completion.refreshAppearance()) { appearancePending = true; appearanceDelay = 0; }
            net.jackcooper.shapeShifterCurseAddon.util.ClientResourceCache.invalidate();
        });
        ClientPlayConnectionEvents.INIT.register((handler, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.getNetworkHandler() == null || client.getNetworkHandler() == handler) clear();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            appearancePending = true;
            appearanceDelay = 20;
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientConfigurationSync::tickAppearance);
        receive(JsonSnapshotSync.BEGIN, 16, ClientConfigurationSession.Part.BEGIN);
        receive(JsonSnapshotSync.BEGIN_SCOPED, 16, ClientConfigurationSession.Part.BEGIN_SCOPED);
        receive(JsonSnapshotSync.SCOPED_CHUNKS,
                JsonSnapshotWire.SESSION_HEADER_BYTES + JsonSnapshotWire.FRAME_HEADER_BYTES + JsonSnapshotWire.CHUNK_BYTES,
                ClientConfigurationSession.Part.CHUNKS_SCOPED);
        receive(JsonSnapshotSync.CHUNKS, JsonSnapshotWire.FRAME_HEADER_BYTES + JsonSnapshotWire.CHUNK_BYTES,
                ClientConfigurationSession.Part.CHUNKS);
        receive(SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC, JsonSnapshotWire.LEGACY_LIMIT,
                ClientConfigurationSession.Part.SPELLS);
        receive(SscAddonNetworking.PACKET_EVO_ROUTES_SYNC, JsonSnapshotWire.LEGACY_LIMIT,
                ClientConfigurationSession.Part.EVOLUTION);
        receive(BalanceIntegration.PACKET_BALANCE_S2C, BalanceWire.MAX_CHUNK_BYTES + 1024,
                ClientConfigurationSession.Part.BALANCE);
    }

    private static void receive(Identifier channel, int maximum, ClientConfigurationSession.Part part) {
        ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buffer, sender) -> {
            if (buffer.readableBytes() > maximum) {
                client.execute(() -> fail(client, handler, "Configuration packet too large"));
                return;
            }
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
            client.execute(() -> {
                if (client.getNetworkHandler() != handler) return;
                try { session.accept(part, bytes); }
                catch (RuntimeException invalid) { fail(client, handler, invalid.getMessage()); }
            });
        });
    }

    private static void fail(MinecraftClient client, ClientPlayNetworkHandler handler, String reason) {
        if (client.getNetworkHandler() == handler)
            handler.getConnection().disconnect(Text.literal("SSCA configuration sync failed: " + reason));
    }

    private static void apply(JsonSnapshotWire.Kind kind, Map<String, String> content) {
        if (kind == JsonSnapshotWire.Kind.SPELLS) SpellRegistry.INSTANCE.applyClientSync(content);
        else if (!EvolutionRegistry.CLIENT.applyClientSync(content))
            throw new IllegalArgumentException("Invalid evolution snapshot");
    }

    public static BalanceSnapshot snapshot() { return session == null ? null : session.snapshot(); }
    public static boolean isReady() { return session != null && session.isReady(); }

    private static void tickAppearance(MinecraftClient client) {
        if (!appearancePending || client.player == null || client.world == null || client.getNetworkHandler() == null) return;
        if (appearanceDelay > 0) { appearanceDelay--; return; }
        // On an SSCA server, wait for the post-authentication configuration transfer.
        // SSC-only servers retain the original JOIN upload once their channels are registered.
        if (ClientPlayNetworking.canSend(BalanceIntegration.PACKET_BALANCE_ACK) && !BalanceIntegration.clientReady()) return;
        if (!ClientPlayNetworking.canSend(ModPackets.UPDATE_CUSTOM_SETTING)
                || !ClientPlayNetworking.canSend(ModPackets.UPDATE_CUSTOM_COLOR)) return;
        try {
            ModPacketsS2C.sendUpdateCustomSetting(true);
            AdvancedColorBridge.sendColors(AdvancedColorBridge.snapshotFromConfig());
            Identifier repair = ClientPlayNetworking.canSend(SscAddonNetworking.PACKET_REQUEST_FORM_SYNC_V2)
                    ? SscAddonNetworking.PACKET_REQUEST_FORM_SYNC_V2 : SscAddonNetworking.PACKET_REQUEST_ALL_FORM_SYNC;
            if (ClientPlayNetworking.canSend(repair)) ClientPlayNetworking.send(repair, PacketByteBufs.empty());
            else if (ClientPlayNetworking.canSend(BalanceIntegration.PACKET_BALANCE_ACK)) {
                appearanceDelay = 20;
                return;
            }
            appearancePending = false;
        } catch (RuntimeException unavailable) {
            appearanceDelay = 20;
        }
    }

    private static void clear() {
        session.clear(); EvolutionRegistry.clearClient(); SpellRegistry.INSTANCE.clearClient();
        appearancePending = false; appearanceDelay = 0;
    }
}
