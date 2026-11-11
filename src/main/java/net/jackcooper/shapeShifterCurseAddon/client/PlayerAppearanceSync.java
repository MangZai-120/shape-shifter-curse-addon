package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/** Repairs remote rendering; preserves existing powers when an Origin registry is replaced. */
public final class PlayerAppearanceSync {
    private record Appearance(UUID uuid, String form, boolean keepOriginal, boolean color,
                              int primary, int accent1, int accent2, int eyeA, int eyeB,
                              boolean primaryReverse, boolean accent1Reverse, boolean accent2Reverse, boolean sound) {}
    private record Pending(Appearance appearance, long deadline) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static Object world;
    private PlayerAppearanceSync() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientPlayNetworking.registerGlobalReceiver(SscAddonNetworking.PACKET_BROADCAST_FORMS, (client, handler, buf, sender) -> {
            int count = buf.readInt();
            if (count < 0 || count > 1000) return;
            var received = new ArrayList<Appearance>(count);
            for (int i = 0; i < count; i++) received.add(new Appearance(buf.readUuid(), buf.readString(),
                    buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null) return;
                ensureWorld(client);
                for (Appearance appearance : received) {
                    PlayerEntity player = client.world.getPlayerByUuid(appearance.uuid);
                    if (player != null) { PENDING.remove(appearance.uuid); apply(client, player, appearance); }
                    // Tracking/spawn can settle one tick after the repair packet. Keep only the latest entry.
                    else if (PENDING.size() < 1000 || PENDING.containsKey(appearance.uuid))
                        PENDING.put(appearance.uuid, new Pending(appearance, System.nanoTime() + 10_000_000_000L));
                }
            });
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity, clientWorld) -> {
            var client = MinecraftClient.getInstance();
            ensureWorld(client);
            if (client.world != clientWorld || !(entity instanceof PlayerEntity player)) return;
            Pending pending = PENDING.remove(player.getUuid());
            if (pending != null && System.nanoTime() < pending.deadline) apply(client, player, pending.appearance);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ensureWorld(client);
            if (!PENDING.isEmpty()) {
                long now = System.nanoTime();
                PENDING.values().removeIf(pending -> now >= pending.deadline);
            }
        });
    }

    private static void clear() { PENDING.clear(); world = null; }
    private static void ensureWorld(MinecraftClient client) {
        if (world != client.world) { PENDING.clear(); world = client.world; }
    }

    private static void apply(MinecraftClient client, PlayerEntity player, Appearance a) {
        // The local player's authoritative state remains entirely on the CCA path.
        if (player == client.player) return;
        Identifier fid = a.form.isEmpty() ? null : Identifier.tryParse(a.form);
        if (fid != null) {
            var form = net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.getPlayerForm(fid);
            if (form != null) {
                var comp = net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent.COMPONENT.get(player);
                comp.nowForm = form;
                comp.nowFormID = fid;
                try {
                    var layerData = form.getFormLayer();
                    var layer = net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginLayers.getLayer(layerData.getLeft());
                    if (layer != null && layerData.getRight() != null) {
                        var origin = net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginRegistry.get(layerData.getRight());
                        if (origin != null) {
                            var oc = (net.onixary.shapeShifterCurseFabric.integration.origins.component.OriginComponent)
                                    net.onixary.shapeShifterCurseFabric.integration.origins.registry.ModComponents.ORIGIN.get(player);
                            // Registry sync replaces Origin objects without changing their IDs.
                            // SSC's setter compares references, then removes the old source after
                            // granting the new one. Reapplying the same ID would erase both.
                            var current = oc.getOrigin(layer);
                            if (current == null || !current.getIdentifier().equals(origin.getIdentifier()))
                                oc.setOrigin(layer, origin);
                        }
                    }
                } catch (Throwable ignored) {
                    // Legacy rendering repair: remote client power registries may be incomplete.
                }
                try { form.applyScale(player); } catch (Throwable ignored) { }
            }
        }
        var skin = net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent.SKIN_SETTINGS.get(player);
        skin.setKeepOriginalSkin(a.keepOriginal);
        skin.setEnableFormColor(a.color);
        skin.setFormColor(new net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ColorSetting(
                a.primary, a.accent1, a.accent2, a.eyeA, a.eyeB, a.primaryReverse, a.accent1Reverse, a.accent2Reverse));
        skin.setEnableFormRandomSound(a.sound);
    }
}
