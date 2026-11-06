package net.jackcooper.shapeShifterCurseAddon.tutorial;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** The client asks only when the book is open; an actual display acknowledgement records it. */
public final class FormTutorialManager {
    public static final Identifier REQUEST = new Identifier("ssc_addon", "form_tutorial_request");
    public static final Identifier OFFER = new Identifier("ssc_addon", "form_tutorial_offer");
    public static final Identifier ACTION = new Identifier("ssc_addon", "form_tutorial_action");
    private record Pending(ServerPlayerEntity player, UUID nonce, Identifier form) { }
    private record RequestKey(UUID player, boolean manual) { }
    private record Request(ServerPlayerEntity player, Identifier form, int tick) { }
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<RequestKey, Request> REQUESTED = new HashMap<>();
    private static boolean registered;

    private FormTutorialManager() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDING.values().removeIf(pending -> pending.player.getServer() == server);
            REQUESTED.values().removeIf(request -> request.player.getServer() == server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            var pending = PENDING.get(id);
            if (pending != null && pending.player == handler.player) PENDING.remove(id);
            REQUESTED.values().removeIf(request -> request.player == handler.player);
        });
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() < 18 || buf.readableBytes() > 1046) return;
            final UUID requestId;
            final Identifier form;
            final boolean manual;
            try {
                requestId = buf.readUuid();
                form = Identifier.tryParse(buf.readString(256));
                manual = buf.readBoolean();
                if (form == null || buf.isReadable()) return;
            } catch (RuntimeException malformed) {
                return;
            }
            server.execute(() -> offer(player, form, manual, requestId));
        });
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != 16) return;
            UUID nonce = buf.readUuid();
            server.execute(() -> acknowledge(player, nonce));
        });
    }

    public static boolean offer(ServerPlayerEntity player, Identifier requestedForm, boolean manual) {
        return offer(player, requestedForm, manual, UUID.randomUUID());
    }

    private static boolean offer(ServerPlayerEntity player, Identifier requestedForm, boolean manual, UUID requestId) {
        if (!isCurrentSession(player)) return false;
        Identifier form = FormIdMigration.canonical(requestedForm);
        if (!matchesForm(player, form) || !ServerPlayNetworking.canSend(player, OFFER)) return false;
        int now = player.getServer().getTicks();
        RequestKey key = new RequestKey(player.getUuid(), manual);
        Request last = REQUESTED.get(key);
        if (last != null && last.player == player && last.form.equals(form) && now >= last.tick && now - last.tick < 20) return false;
        REQUESTED.put(key, new Request(player, form, now));
        if (!manual && FormTutorialState.get(player.getServer()).hasSeen(player.getUuid(), form)) return false;
        UUID nonce = UUID.randomUUID();
        PENDING.put(player.getUuid(), new Pending(player, nonce, form));
        var packet = PacketByteBufs.create();
        packet.writeUuid(requestId);
        packet.writeUuid(nonce);
        packet.writeIdentifier(form);
        packet.writeBoolean(manual);
        ServerPlayNetworking.send(player, OFFER, packet);
        return true;
    }

    public static boolean acknowledge(ServerPlayerEntity player, UUID nonce) {
        if (!isCurrentSession(player)) return false;
        Pending pending = PENDING.get(player.getUuid());
        if (pending == null || pending.player != player || !pending.nonce.equals(nonce)) return false;
        if (!matchesForm(player, pending.form)) {
            PENDING.remove(player.getUuid(), pending);
            return false;
        }
        FormTutorialState.get(player.getServer()).markSeen(player.getUuid(), pending.form);
        PENDING.remove(player.getUuid(), pending);
        return true;
    }

    private static boolean isCurrentSession(ServerPlayerEntity player) {
        return player != null && player.getServer() != null
                && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player;
    }

    private static boolean matchesForm(ServerPlayerEntity player, Identifier form) {
        if (form == null || !FormTutorialCatalog.supports(form)) return false;
        var component = RegPlayerFormComponent.PLAYER_FORM.get(player);
        return component != null && component.nowForm != null
                && form.equals(FormIdMigration.canonical(component.nowForm.getFormID()));
    }
}
