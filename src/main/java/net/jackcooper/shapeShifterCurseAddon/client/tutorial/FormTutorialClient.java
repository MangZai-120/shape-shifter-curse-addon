package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.tutorial.FormTutorialCatalog;
import net.jackcooper.shapeShifterCurseAddon.tutorial.FormTutorialManager;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.custom_ui.BookOfShapeShifterScreenV2_P1;
import net.onixary.shapeShifterCurseFabric.custom_ui.BookOfShapeShifterScreenV2_P2;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** Open only from the player's book, after init has finished and the server has checked its ledger. */
public final class FormTutorialClient {
    private record Request(Screen book, ClientPlayNetworkHandler connection, Identifier form, boolean manual, UUID id) {}
    private record Offer(Request request, UUID nonce) {}
    private static Request queued, requested;
    private static Offer pending;
    private static Screen lastBook;
    private static Identifier lastForm;
    private static boolean registered;

    private FormTutorialClient() {}

    public static Identifier currentForm(MinecraftClient client) {
        if (client.player == null) return null;
        var form = RegPlayerFormComponent.PLAYER_FORM.get(client.player).nowForm;
        return form == null ? null : FormIdMigration.canonical(form.getFormID());
    }

    private static boolean ownBook(MinecraftClient client, Screen screen) {
        return client.player != null && (screen instanceof BookOfShapeShifterScreenV2_P1 first && first.currentPlayer == client.player
                || screen instanceof BookOfShapeShifterScreenV2_P2 second && second.currentPlayer == client.player);
    }

    public static void register() {
        if (registered) return;
        registered = true;
        FormTutorialLessons.register();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!ownBook(client, screen)) return;
            Identifier form = currentForm(client);
            if (!FormTutorialCatalog.supports(form)) return;
            if (screen instanceof BookOfShapeShifterScreenV2_P1) {
                int scale = ShapeShifterCurseFabric.clientConfig.newStartBookForBiggerScreen ? 2 : 1;
                int x = width / 2 - BookOfShapeShifterScreenV2_P1.BookSizeX * scale / 2;
                int y = height / 2 - BookOfShapeShifterScreenV2_P1.BookSizeY * scale / 2;
                var replay = ButtonWidget.builder(FormTutorialScreen.text("replay"), button -> schedule(client, screen, true))
                        .dimensions(x + 35 * scale, y + 81 * scale, 70 * scale, 12 * scale).build();
                replay.active = ClientPlayNetworking.canSend(FormTutorialManager.REQUEST);
                Screens.getButtons(screen).add(replay);
            }
            if (lastBook != screen || !form.equals(lastForm)) {
                lastBook = screen;
                lastForm = form;
                schedule(client, screen, false);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FormTutorialManager.OFFER, (client, handler, buf, sender) -> {
            final UUID requestId, nonce;
            final Identifier form;
            final boolean manual;
            try {
                requestId = buf.readUuid();
                nonce = buf.readUuid();
                form = buf.readIdentifier();
                manual = buf.readBoolean();
                if (buf.isReadable() || !FormTutorialCatalog.supports(form)) return;
            } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
                return;
            }
            client.execute(() -> {
                Request request = requested;
                if (request != null && request.connection == handler && client.getNetworkHandler() == handler
                        && request.id.equals(requestId) && request.form.equals(form) && request.manual == manual && matches(client, request)) {
                    pending = new Offer(request, nonce);
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            queued = requested = null;
            pending = null;
            lastBook = null;
            lastForm = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (queued != null) {
                Request request = queued;
                queued = null;
                if (matches(client, request) && ClientPlayNetworking.canSend(FormTutorialManager.REQUEST)) {
                    requested = request;
                    pending = null;
                    var packet = PacketByteBufs.create();
                    packet.writeUuid(request.id);
                    packet.writeIdentifier(request.form);
                    packet.writeBoolean(request.manual);
                    ClientPlayNetworking.send(FormTutorialManager.REQUEST, packet);
                }
            }
            if (pending == null) return;
            Offer offer = pending;
            if (matches(client, offer.request) && client.getOverlay() != null) return;
            pending = null;
            if (!matches(client, offer.request)) return;
            requested = null;
            Runnable acknowledge = () -> {
                if (client.getNetworkHandler() != offer.request.connection || !offer.request.form.equals(currentForm(client))
                        || !ClientPlayNetworking.canSend(FormTutorialManager.ACTION)) return;
                var packet = PacketByteBufs.create();
                packet.writeUuid(offer.nonce);
                ClientPlayNetworking.send(FormTutorialManager.ACTION, packet);
            };
            if (FormTutorialWeb.supports(offer.request.form)) {
                FormTutorialWeb.open(client, offer.request.form, acknowledge);
            } else {
                client.setScreen(new FormTutorialScreen(offer.request.book, offer.request.form, acknowledge));
            }
        });
    }

    private static void schedule(MinecraftClient client, Screen book, boolean manual) {
        Identifier form = currentForm(client);
        if (FormTutorialCatalog.supports(form) && client.getNetworkHandler() != null) {
            queued = new Request(book, client.getNetworkHandler(), form, manual, UUID.randomUUID());
        }
    }

    private static boolean matches(MinecraftClient client, Request request) {
        return client.world != null && client.player != null && client.player.isAlive()
                && client.getNetworkHandler() == request.connection && client.currentScreen == request.book
                && ownBook(client, request.book) && request.form.equals(currentForm(client));
    }
}
