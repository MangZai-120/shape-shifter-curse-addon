package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownPacket;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSync;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Native client plus integrated server, using actual S2C packets and native respawn/teleport. */
public final class MultiplayerSyncClientProbe {
    private static final String CLOCK = "my_addon:sync_probe";
    private static final long START = System.nanoTime();
    private static boolean starting, finished;
    private static boolean lanOpened;
    private static final boolean MULTIPLAYER = Boolean.getBoolean("ssca.sync.multiplayer");
    private static final Path SHARED = Path.of(System.getProperty("ssca.sync.shared", "."));
    private static volatile Throwable failure;
    private static final java.util.concurrent.atomic.AtomicInteger serverWork = new java.util.concurrent.atomic.AtomicInteger();
    private static int ticks, phase, checks;
    private static Object oldPlayer, oldWorld;
    private static io.github.apace100.apoli.power.PowerType<io.github.apace100.apoli.power.ResourcePower> pendingPower;
    private static final net.minecraft.util.Identifier PENDING_POWER = new net.minecraft.util.Identifier("my_addon", "pending_sync_probe");

    public static void install() {
        if (!System.getProperty("ssca.sync.role", "host").equals("host")) {
            MultiplayerSyncRemoteProbe.install(); return;
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable error) { fail(error); }
        });
    }

    private static void tick(MinecraftClient mc) throws Exception {
        if (finished || failure != null) return;
        if (System.nanoTime() - START > 420_000_000_000L) throw new AssertionError("Sync client timed out, phase=" + phase);
        if (mc.getOverlay() != null) return;
        if (!starting && mc.currentScreen instanceof TitleScreen) {
            starting = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.getViewDistance().setValue(3);
            mc.options.getMaxFps().setValue(60);
            var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()), DataConfiguration.SAFE_MODE.enabledFeatures());
            mc.createIntegratedServerLoader().createAndStart("MultiplayerSync-" + System.currentTimeMillis(),
                    new LevelInfo("Sync regression", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), data),
                    new GeneratorOptions(20261115L, false, false),
                    r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
        }
        if (mc.world == null || mc.player == null || mc.getServer() == null) return;
        // A native dimension packet may reach the client while the server is still
        // generating the destination. Start observation ticks only after our operation returns.
        if (serverWork.get() != 0) return;
        if (MULTIPLAYER && !lanOpened) {
            mc.getServer().setOnlineMode(false); // Isolated test identities have no account credentials.
            check(mc.getServer().openToLan(GameMode.CREATIVE, true, Integer.getInteger("ssca.sync.port", 25695)), "Isolated LAN server opened");
            lanOpened = true;
            Files.createDirectories(SHARED);
            Files.writeString(SHARED.resolve("ready.txt"), mc.player.getUuidAsString());
        }
        if (MULTIPLAYER && phase == 0 && mc.getServer().getCurrentPlayerCount() < 3) return;
        if (MULTIPLAYER && phase == 1 && (!Files.exists(SHARED.resolve("guest-a-seen.json")) || !Files.exists(SHARED.resolve("guest-b-seen.json")))) return;
        if (MULTIPLAYER && phase == 3 && (!Files.exists(SHARED.resolve("guest-a-respawn.json")) || !Files.exists(SHARED.resolve("guest-b-respawn.json")))) return;
        if (mc.currentScreen != null) mc.setScreen(null);
        if (++ticks < (phase == 0 ? 50 : 15)) return;
        ticks = 0;
        switch (phase++) {
            case 0 -> {
                check(net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.clientReady(), "Client configuration handshake completed");
                oldPlayer = mc.player; oldWorld = mc.world;
                server(mc, p -> { mirrors(p); cooldown(p, p.getId(), p.getWorld().getRegistryKey().getValue().toString(), true); });
            }
            case 1 -> {
                check(ClawClientState.isActive(), "Claw state received over native S2C");
                check(DashClientState.phase == DashClientState.PHASE_HOVER, "Dash state received over native S2C");
                check(NovaLeapClientState.remainingFraction(mc.world.getTime()) > 0, "Leap window received");
                check(FallenAllayVexClientState.remainingFraction(mc.world.getTime()) > 0, "Summon window received");
                clockPresent();
                server(mc, p -> p.networkHandler.player = p.getServer().getPlayerManager().respawnPlayer(p, true));
            }
            case 2 -> {
                check(mc.player != oldPlayer, "Native respawn replaced client player");
                check(mc.world == oldWorld, "Same-dimension respawn retained world object");
                cleared(mc, "respawn");
                server(mc, p -> {
                    cooldown(p, p.getId() + 100000, p.getWorld().getRegistryKey().getValue().toString(), true);
                    cooldown(p, p.getId(), "minecraft:the_end", true);
                });
            }
            case 3 -> {
                check(SkillCooldownClient.get(CLOCK) == null, "Wrong entity and dimension snapshots were rejected");
                server(mc, p -> { mirrors(p); cooldown(p, p.getId(), p.getWorld().getRegistryKey().getValue().toString(), true); });
            }
            case 4 -> {
                clockPresent();
                check(DashClientState.phase == DashClientState.PHASE_HOVER, "New-life state applies after cleanup");
                oldWorld = mc.world;
                if (MULTIPLAYER) Files.writeString(SHARED.resolve("dimension.txt"), "teleporting");
                server(mc, p -> p.teleport(p.getServer().getWorld(World.NETHER), 0, 90, 0, 0, 0));
            }
            case 5 -> {
                check(mc.world != oldWorld && mc.world.getRegistryKey().equals(World.NETHER), "Native dimension transfer completed");
                cleared(mc, "dimension");
                server(mc, p -> cooldown(p, 0, "", false));
            }
            case 6 -> {
                clockPresent();
                check(true, "Legacy cooldown list without context remains supported");
                pendingPower = new io.github.apace100.apoli.power.PowerType<>(PENDING_POWER, null) {
                    @Override public io.github.apace100.apoli.power.ResourcePower create(net.minecraft.entity.LivingEntity owner) {
                        return new io.github.apace100.apoli.power.ResourcePower(this, owner,
                                io.github.apace100.apoli.util.HudRender.DONT_RENDER, 0, 0, 100, null, null);
                    }
                };
                io.github.apace100.apoli.power.PowerTypeRegistry.register(PENDING_POWER, pendingPower);
                server(mc, p -> {
                    var packet = PacketByteBufs.create();
                    packet.writeIdentifier(p.getWorld().getRegistryKey().getValue());
                    packet.writeLong(p.getWorld().getTime()); packet.writeVarInt(1);
                    new net.jackcooper.shapeShifterCurseAddon.network.CountdownSync.State(
                            new net.jackcooper.shapeShifterCurseAddon.network.CountdownSync.Key(p.getUuid(), PENDING_POWER),
                            p.getId(), new net.jackcooper.shapeShifterCurseAddon.network.CountdownValue(10, p.getWorld().getTime(), 0, false)).write(packet);
                    ServerPlayNetworking.send(p, net.jackcooper.shapeShifterCurseAddon.network.CountdownSync.STATE, packet);
                });
            }
            case 7 -> {
                var pendingField = CountdownClient.class.getDeclaredField("PENDING");
                pendingField.setAccessible(true);
                check(((java.util.Map<?, ?>) pendingField.get(null)).containsKey(
                        new net.jackcooper.shapeShifterCurseAddon.network.CountdownSync.Key(mc.player.getUuid(), PENDING_POWER)),
                        "Older countdown anchor is pending before the native update");
                var powers = io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(mc.player);
                powers.addPower(pendingPower, PENDING_POWER);
                var resource = pendingPower.get(mc.player);
                resource.fromTag(net.minecraft.nbt.NbtInt.of(70));
                check(resource.getValue() == 70, "Native resource update applied before pending countdown binds");
            }
            case 8 -> {
                check(pendingPower.get(mc.player).getValue() == 70, "Older pending anchor cannot overwrite newer native resource sync");
                io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(mc.player).removeAllPowersFromSource(PENDING_POWER);
                server(mc, p -> net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.onServerReload(p.getServer()));
            }
            case 9 -> {
                check(net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.clientReady(), "Configuration completes again on live reload");
                if (MULTIPLAYER) {
                    check(Files.exists(SHARED.resolve("guest-a-result.json")), "First remote observer completed lifecycle checks");
                    check(Files.exists(SHARED.resolve("guest-b-result.json")), "Second remote observer completed lifecycle checks");
                }
                mc.world.disconnect();
                mc.disconnect();
                AbilityVisualLifecycle.ensureWorld(mc.world);
                check(SkillCooldownClient.get(CLOCK) == null, "Disconnect removed cooldown mirror");
                check(!ClawClientState.isActive() && DashClientState.phase == 0, "Disconnect removed ability mirrors");
                check(!net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.clientReady()
                        && net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.clientSnapshot() == null,
                        "Disconnect clears the complete configuration session");
                finished = true;
                Files.writeString(Path.of("multiplayer-sync-client-result.json"), "{\"passed\":true,\"checks\":" + checks
                        + ",\"native_clients\":" + (MULTIPLAYER ? 3 : 1) + ",\"integrated_server\":true,\"remote_multiplayer_verified\":" + MULTIPLAYER + "}");
                System.out.println("MULTIPLAYER_SYNC_CLIENT_PASS checks=" + checks);
                mc.scheduleStop();
            }
            default -> throw new AssertionError("Unexpected phase");
        }
    }

    private static void mirrors(ServerPlayerEntity p) {
        net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.setForm(p,
                net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.getPlayerForm(
                        net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.OCELOT_SP));
        SscAddonNetworking.syncClawState(p, 2, .25f, 0);
        SscAddonNetworking.syncDashState(p, DashClientState.PHASE_HOVER, 80);
        SscAddonNetworking.syncNovaLeapWindow(p, 200);
        SscAddonNetworking.syncFallenAllayVexWindow(p, 200);
        if (MULTIPLAYER) SscAddonNetworking.syncSpearChargeState(p, true);
    }

    private static void cooldown(ServerPlayerEntity p, int id, String dimension, boolean context) {
        System.out.println("MULTIPLAYER_SYNC_CLOCK_SEND context=" + context + " world=" + p.getWorld().getRegistryKey()
                + " time=" + p.getWorld().getTime() + " authoritative="
                + SkillCastManager.get(p.getServer().getOverworld()).snapshot(p.getUuid(), SkillCastManager.now(p)));
        var packet = PacketByteBufs.create();
        SkillCooldownPacket.write(packet, List.of(new SkillCastManager.View(CLOCK, 1, 200, 200)));
        if (context) { packet.writeVarInt(id); packet.writeIdentifier(new net.minecraft.util.Identifier(dimension)); }
        ServerPlayNetworking.send(p, SkillCooldownSync.ID, packet);
    }

    private static void cleared(MinecraftClient mc, String reason) {
        check(!ClawClientState.isActive(), reason + " cleared claw");
        check(DashClientState.phase == 0, reason + " cleared dash");
        check(NovaLeapClientState.remainingFraction(mc.world.getTime()) < 0, reason + " cleared leap");
        check(FallenAllayVexClientState.remainingFraction(mc.world.getTime()) < 0, reason + " cleared summons");
        check(SkillCooldownClient.get(CLOCK) == null, reason + " cleared cooldown");
    }

    private static void clockPresent() {
        var clock = SkillCooldownClient.get(CLOCK);
        check(clock != null && clock.remainingNow(SkillCooldownClient.now()) > 0,
                "Cooldown received and locally advances: clock=" + clock + ", now=" + SkillCooldownClient.now());
    }

    private static void server(MinecraftClient mc, Consumer<ServerPlayerEntity> operation) {
        var id = mc.player.getUuid();
        serverWork.incrementAndGet();
        mc.getServer().execute(() -> {
            try { operation.accept(mc.getServer().getPlayerManager().getPlayer(id)); }
            catch (Throwable error) { fail(error); }
            finally { serverWork.decrementAndGet(); }
        });
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
        System.out.println("MULTIPLAYER_SYNC_CHECK " + message);
    }

    private static void fail(Throwable error) {
        failure = error; error.printStackTrace();
        try { Files.writeString(Path.of("multiplayer-sync-client-failure.txt"), "phase=" + phase + " " + error); }
        catch (Exception ignored) {}
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Sync client failed", failure);
        if (!finished) throw new AssertionError("Sync client stopped before completion");
    }
}
