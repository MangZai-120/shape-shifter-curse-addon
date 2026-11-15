package net.jackcooper.shapeShifterCurseAddon.network;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.jackcooper.shapeShifterCurseAddon.ability.AllaySPGroupHeal;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;

/** Real receiver dispatch and server task queue; the connections have no network transport. */
public final class MultiplayerSyncWorldProbe {
    private static int checks;
    private static ServerPlayerEntity actor, observer;
    private static ProbeConnection connection;
    private static ServerPlayNetworkHandler originalHandler;
    private static UUID target;
    private static boolean inputHeld = true;

    private static final class ProbeConnection extends ClientConnection {
        boolean open = true;
        ProbeConnection() { super(NetworkSide.SERVERBOUND); }
        @Override public boolean isOpen() { return open; }
        @Override public void send(Packet<?> packet, PacketCallbacks callbacks) {}
    }

    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            checks += MultiplayerSyncWireChecks.run();
            connection = new ProbeConnection();
            actor = player(test, "sync-actor", connection);
            observer = player(test, "sync-observer", new ProbeConnection());
            originalHandler = actor.networkHandler;
            enqueue();
        });
        test.waitAndRun(72, () -> {
            check(actor.getCommandTags().contains(tag()), "Current player's queued request executes");
            check(!observer.getCommandTags().contains(tag()), "A different player is untouched");
            enqueue();
            connection.open = false;
        });
        test.waitAndRun(74, () -> {
            check(!actor.getCommandTags().contains(tag()), "Closed connection cannot mutate before removal");
            connection.open = true;
            enqueue();
            // Native respawn retains the handler but replaces handler.player.
            originalHandler.player = new ServerPlayerEntity(test.getWorld().getServer(), test.getWorld(), actor.getGameProfile());
        });
        test.waitAndRun(76, () -> {
            check(!actor.getCommandTags().contains(tag()), "Old entity request cannot execute after handler rebind");
            originalHandler.player = actor;
            enqueue();
            actor.networkHandler = new ServerPlayNetworkHandler(actor.getServer(), new ProbeConnection(), actor);
        });
        test.waitAndRun(78, () -> {
            check(!actor.getCommandTags().contains(tag()), "Replaced connection cannot mutate its old player");
            actor.networkHandler = originalHandler;
            enqueue();
            actor.teleport(actor.getServer().getWorld(net.minecraft.world.World.NETHER), 0, 90, 0, 0, 0);
        });
        test.waitAndRun(80, () -> {
            check(!actor.getCommandTags().contains(tag()), "Queued action does not follow player to another dimension");
            actor.teleport(test.getWorld(), 0, 90, 0, 0, 0);
            Thread reset = new Thread(() -> ServerPacketExecutor.execute(actor.getServer(), actor,
                    originalHandler, false, () -> inputHeld = false));
            reset.start();
            try { reset.join(5000); } catch (InterruptedException error) { throw new AssertionError(error); }
            check(!reset.isAlive() && inputHeld, "Pure input reset is queued on the server thread");
            actor.teleport(actor.getServer().getWorld(net.minecraft.world.World.NETHER), 0, 90, 0, 0, 0);
        });
        test.waitAndRun(82, () -> {
            check(!inputHeld, "Pure input reset follows a valid session across dimension change");
            enqueue();
            actor.getServer().getPlayerManager().remove(actor);
        });
        test.waitAndRun(84, () -> {
            check(!actor.getCommandTags().contains(tag()), "Disconnected player cannot mutate persistent tags");
            System.out.println("MULTIPLAYER_SYNC_WORLD_PASS checks=" + checks);
            test.complete();
        });
    }

    private static ServerPlayerEntity player(TestContext test, String name, ProbeConnection connection) {
        var p = new ServerPlayerEntity(test.getWorld().getServer(), test.getWorld(), new GameProfile(UUID.randomUUID(), name));
        p.getServer().getPlayerManager().onPlayerConnect(connection, p);
        p.setNoGravity(true);
        p.getAbilities().invulnerable = true;
        return p;
    }

    private static String tag() { return AllaySPGroupHeal.WHITELIST_TAG_PREFIX + target; }

    private static void enqueue() {
        target = UUID.randomUUID();
        SscAddonNetworking.onPlayerDisconnect(actor.getUuid()); // Each case is independent of the UI rate limit.
        var error = new AtomicReference<Throwable>();
        Thread incoming = new Thread(() -> {
            var packet = PacketByteBufs.create();
            try {
                packet.writeUuid(target);
                if (!ServerNetworkingImpl.getAddon(originalHandler).handle(
                        new CustomPayloadC2SPacket(SscAddonNetworking.PACKET_WHITELIST_GUI_ADD, packet)))
                    throw new AssertionError("Actual whitelist receiver was not registered");
            } catch (Throwable failure) { error.set(failure); }
            finally { packet.release(); }
        }, "SSCA-receiver-regression");
        incoming.start();
        try { incoming.join(5000); } catch (InterruptedException e) { throw new AssertionError(e); }
        check(!incoming.isAlive() && error.get() == null, "Receiver decoded and queued without error: " + error.get());
        check(!actor.getCommandTags().contains(tag()), "Network thread did not mutate player state");
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
