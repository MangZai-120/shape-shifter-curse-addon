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
import net.jackcooper.shapeShifterCurseAddon.network.ConfigurationReceipt;
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

import java.nio.ByteBuffer;
import java.util.Map;

/** Configuration completion is the authenticated point at which local appearance can be uploaded. */
@Environment(EnvType.CLIENT)
public final class ClientConfigurationSync {
    private static final JsonSnapshotWire.Receiver CHUNKS = new JsonSnapshotWire.Receiver();
    private static final ConfigurationReceipt RECEIPT = new ConfigurationReceipt();
    private static boolean appearancePending;
    private static int appearanceDelay;
    private ClientConfigurationSync() {}

    public static void register() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.getNetworkHandler() == null || client.getNetworkHandler() == handler) clear();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            appearancePending = true;
            appearanceDelay = 20;
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientConfigurationSync::tickAppearance);
        receive(JsonSnapshotSync.BEGIN, 16, (bytes) -> {
            if (bytes.length != 16) throw new IllegalArgumentException("Invalid configuration announcement");
            var data = ByteBuffer.wrap(bytes);
            RECEIPT.begin(data.getLong(), data.getLong());
        });
        receive(JsonSnapshotSync.CHUNKS, JsonSnapshotWire.FRAME_HEADER_BYTES + JsonSnapshotWire.CHUNK_BYTES, bytes -> {
            var received = CHUNKS.offer(bytes);
            if (received != null) apply(received.kind(), received.content());
        });
        receive(SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC, JsonSnapshotWire.LEGACY_LIMIT,
                bytes -> apply(JsonSnapshotWire.Kind.SPELLS, JsonSnapshotWire.decode(bytes)));
        receive(SscAddonNetworking.PACKET_EVO_ROUTES_SYNC, JsonSnapshotWire.LEGACY_LIMIT,
                bytes -> apply(JsonSnapshotWire.Kind.EVOLUTION, JsonSnapshotWire.decode(bytes)));
    }

    private static void receive(Identifier channel, int maximum, java.util.function.Consumer<byte[]> consumer) {
        ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buffer, sender) -> {
            if (buffer.readableBytes() > maximum) {
                client.execute(() -> fail(client, handler, "Configuration packet too large"));
                return;
            }
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes);
            client.execute(() -> {
                if (client.getNetworkHandler() != handler) return;
                try { consumer.accept(bytes); }
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
        RECEIPT.applied(kind);
    }

    public static boolean canCommit(long session, long revision) { return RECEIPT.canCommit(session, revision); }

    public static void committed(long session) {
        if (RECEIPT.needsAppearanceRefresh(session)) {
            appearancePending = true;
            appearanceDelay = 0;
        }
    }

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
        CHUNKS.clear(); RECEIPT.clear(); EvolutionRegistry.clearClient();
        appearancePending = false; appearanceDelay = 0;
    }
}
