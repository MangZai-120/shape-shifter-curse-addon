package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.jackcooper.shapeShifterCurseAddon.entity.BridgeWebBullet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.blocks.RegCustomBlock;
import net.onixary.shapeShifterCurseFabric.entity.projectile.WebBullet;

/** SSC and SSCA hit handlers against real client/server worlds, including membrane replacement. */
public final class WebBulletAuthorityClientProbe {
    private static boolean starting, submitted;
    private static volatile boolean finished;
    private static volatile Throwable failure;
    private static int ticks, checks;
    private static final long START = System.nanoTime();

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    public static void install() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (finished || submitted) return;
            try {
                if (System.nanoTime() - START > 180_000_000_000L) throw new AssertionError("web hit client timed out");
                if (mc.getOverlay() != null) return;
                if (!starting && mc.currentScreen instanceof TitleScreen) {
                    starting = true;
                    mc.options.pauseOnLostFocus = false;
                    mc.options.getViewDistance().setValue(4);
                    mc.options.getMaxFps().setValue(60);
                    var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()), DataConfiguration.SAFE_MODE.enabledFeatures());
                    mc.createIntegratedServerLoader().createAndStart("WebAuthority-" + System.currentTimeMillis(),
                            new LevelInfo("Web projectile authority regression", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), data),
                            new GeneratorOptions(20261114L, false, false),
                            r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (mc.world == null || mc.player == null || mc.getServer() == null) return;
                if (mc.currentScreen != null) mc.setScreen(null);
                if (++ticks < 40) return;
                verifyClient(mc);
                submitted = true;
                mc.getServer().execute(() -> {
                    try {
                        var player = mc.getServer().getPlayerManager().getPlayerList().get(0);
                        verifyServer(player);
                        Files.writeString(Path.of("web-authority-result.json"), "{\"passed\":true,\"checks\":" + checks
                                + ",\"real_client_world\":true,\"real_server_world\":true,\"tiers\":[1,2,3],\"faces\":[\"up\",\"down\",\"north\"],\"membrane_replacement\":true}");
                        finished = true;
                        System.out.println("WEB_BULLET_AUTHORITY_PASS checks=" + checks);
                        mc.execute(mc::scheduleStop);
                    } catch (Throwable e) { fail(e); }
                });
            } catch (Throwable e) { fail(e); }
        });
    }

    private static WebBullet bullet(LivingEntity owner, boolean addon, int tier) {
        return addon ? new BridgeWebBullet(owner, tier) : new WebBullet(owner, tier);
    }

    private static BlockHitResult hit(BlockPos pos, Direction side) {
        return new BlockHitResult(Vec3d.ofCenter(pos).add(Vec3d.of(side.getVector()).multiply(.5)), side, pos, false);
    }

    private static void verifyClient(MinecraftClient mc) {
        var world = mc.world;
        var origin = mc.player.getBlockPos().up(48);
        world.setBlockState(origin, Blocks.STONE.getDefaultState());
        var before = new LinkedHashMap<BlockPos, BlockState>();
        BlockPos.iterate(origin.add(-3, -28, -3), origin.add(3, 20, 3)).forEach(p -> before.put(p.toImmutable(), world.getBlockState(p)));
        for (boolean addon : new boolean[]{false, true}) for (int tier = 1; tier <= 3; tier++) {
            for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH}) {
                var projectile = bullet(mc.player, addon, tier);
                projectile.onBlockHit(hit(origin, side));
                check(before.entrySet().stream().allMatch(e -> world.getBlockState(e.getKey()).equals(e.getValue())),
                        "no client ghost blocks: addon=" + addon + " tier=" + tier + " face=" + side);
                check(!projectile.isRemoved(), "client waits for server projectile removal");
            }
            var victim = EntityType.PIG.create(world);
            var projectile = bullet(mc.player, addon, tier);
            float health = victim.getHealth();
            projectile.onEntityHit(new EntityHitResult(victim));
            check(victim.getHealth() == health && victim.getStatusEffects().isEmpty(), "entity impact has no client-only combat effects");
            check(!projectile.isRemoved(), "entity impact awaits authoritative removal");
        }
    }

    private static void verifyServer(LivingEntity player) {
        var world = player.getWorld();
        var origin = player.getBlockPos().up(48);
        for (boolean addon : new boolean[]{false, true}) for (int tier = 1; tier <= 3; tier++) {
            for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH}) {
                BlockPos.iterate(origin.add(-3, -28, -3), origin.add(3, 20, 3)).forEach(p -> world.setBlockState(p, Blocks.AIR.getDefaultState()));
                world.setBlockState(origin, Blocks.STONE.getDefaultState());
                var first = origin.offset(side);
                if (addon) {
                    world.setBlockState(first, RegAddonBlocks.WEB_MEMBRANE.getDefaultState());
                    check(world.getBlockState(first).isOf(RegAddonBlocks.WEB_MEMBRANE), "replacement fixture contains a membrane");
                }
                var projectile = bullet(player, addon, tier);
                projectile.onBlockHit(hit(origin, side));
                int length = side == Direction.UP ? 4 + 4 * tier : side == Direction.DOWN ? new int[]{14, 18, 24}[tier - 1] : 6 + 4 * tier;
                Direction along = side == Direction.UP ? Direction.UP : Direction.DOWN;
                for (int i = 0; i < length; i++) check(world.getBlockState(first.offset(along, i)).isOf(RegCustomBlock.TEMP_WEB_BRIDGE),
                        "server preserves the complete ladder and replaces membranes: addon=" + addon + " tier=" + tier + " face=" + side + " step=" + i);
                check(projectile.isRemoved(), "server removes the projectile after its hit");
            }
        }
        BlockPos.iterate(origin, origin.up(20)).forEach(p -> world.setBlockState(p, Blocks.AIR.getDefaultState()));
        world.setBlockState(origin, Blocks.STONE.getDefaultState());
        for (boolean addon : new boolean[]{false, true}) {
            var projectile = bullet(player, addon, 1);
            projectile.EnableTopBlockBuild = false;
            projectile.onBlockHit(hit(origin, Direction.UP));
            check(world.getBlockState(origin.up()).isAir() && projectile.isRemoved(), "disabled top building is preserved");
        }
    }

    private static void fail(Throwable e) {
        if (failure != null) return;
        failure = e; finished = true; e.printStackTrace();
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }
    public static void verifyResult() {
        if (failure != null) throw new AssertionError("web bullet authority check failed", failure);
        if (!finished) throw new AssertionError("client exited before the web hit checks completed");
    }
}
