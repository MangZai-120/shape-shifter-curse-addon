package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Two independently launched native observers of the isolated LAN host. */
public final class MultiplayerSyncRemoteProbe {
    private static final Path SHARED = Path.of(System.getProperty("ssca.sync.shared"));
    private static final String ROLE = System.getProperty("ssca.sync.role");
    private static final long START = System.nanoTime();
    private static boolean connecting, seen, respawned, finished;
    private static Object firstEntity;
    private static int ticks, checks;
    public static void install() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(SHARED.resolve(ROLE + "-failure.txt"), failure.toString()); } catch (Exception ignored) {}
                mc.scheduleStop(); finished = true;
            }
        });
    }
    private static void tick(MinecraftClient mc) throws Exception {
        if (finished) return;
        if (System.nanoTime() - START > 360_000_000_000L) throw new AssertionError("Remote sync timed out");
        if (mc.getOverlay() != null) return;
        if (connecting && mc.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen) {
            StringBuilder reason = new StringBuilder(mc.currentScreen.getTitle().getString());
            for (var field : mc.currentScreen.getClass().getDeclaredFields()) {
                if (net.minecraft.text.Text.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    reason.append(" ").append(((net.minecraft.text.Text) field.get(mc.currentScreen)).getString());
                }
            }
            throw new AssertionError("Remote connection failed: " + reason);
        }
        if (!connecting && mc.currentScreen instanceof TitleScreen) {
            connecting = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.getViewDistance().setValue(3);
            mc.options.getMaxFps().setValue(30);
            String address = "127.0.0.1:" + Integer.getInteger("ssca.sync.port", 25695);
            ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(address), new ServerInfo("SSCA sync probe", address, false), false);
        }
        if (mc.world == null || mc.player == null) return;
        if (mc.currentScreen != null) mc.setScreen(null);
        if (++ticks < 20) return;
        UUID host = UUID.fromString(Files.readString(SHARED.resolve("ready.txt")).trim());
        var entity = mc.world.getPlayerByUuid(host);
        if (!seen && entity != null && UpgradeAxolotlSpearRenderState.isCharging(host)) {
            check(net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.clientReady(), "Remote full configuration acknowledged");
            check(entity != mc.player, "Observed player is remote");
            check(!UpgradeAxolotlSpearRenderState.isCharging(mc.player.getUuid()), "Remote state does not leak to observer");
            firstEntity = entity; seen = true;
            Files.writeString(SHARED.resolve(ROLE + "-seen.json"), "{\"passed\":true}");
        }
        if (seen && !respawned && entity != null && entity != firstEntity) {
            check(!UpgradeAxolotlSpearRenderState.isCharging(host), "Entity unload and retracking clear old spear state");
            respawned = true;
            Files.writeString(SHARED.resolve(ROLE + "-respawn.json"), "{\"passed\":true}");
        }
        if (respawned && Files.exists(SHARED.resolve("dimension.txt")) && entity == null) {
            check(!UpgradeAxolotlSpearRenderState.isCharging(host), "Stop tracking on dimension transfer clears remote state");
            check(net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.clientReady(), "Other player's lifecycle preserves observer configuration");
            Files.writeString(SHARED.resolve(ROLE + "-result.json"), "{\"passed\":true,\"checks\":" + checks + "}");
            finished = true;
            mc.scheduleStop();
        }
    }
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
        System.out.println("MULTIPLAYER_SYNC_CHECK " + message);
    }
}
