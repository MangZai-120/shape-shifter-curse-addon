package net.jackcooper.shapeShifterCurseAddon.ability;

import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;

/** Real server world: input gates, native velocity, moisture and cooldown resources. */
public final class AxolotlWaterSpurtWorldProbe {
    private static int checks;
    private static final Identifier LAND = new Identifier("my_addon", "form_upgrade_axolotl_dash_hud_land");
    private static final Identifier WATER = new Identifier("my_addon", "form_upgrade_axolotl_dash_hud_water");
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void tap(ServerPlayerEntity player) {
        AxolotlWaterSpurtHandler.setClientSprintHeld(player, false);
        AxolotlWaterSpurtHandler.setClientSprintHeld(player, true);
    }
    private static void doubleTap(ServerPlayerEntity player) { tap(player); tap(player); }
    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            try {
                if (io.github.apace100.apoli.Apoli.config == null)
                    io.github.apace100.apoli.Apoli.config = new io.github.apace100.apoli.util.ApoliConfig();
                var player = NeoFormCastingProbe.player(test, FormIdentifiers.UPGRADE_AXOLOTL, 0);
                net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.setForm(player,
                    RegPlayerForms.playerForms.get(FormIdentifiers.UPGRADE_AXOLOTL));
                player.setVelocity(Vec3d.ZERO);
                doubleTap(player);
                check(player.getVelocity().lengthSquared() == 0, "Locked talent cannot dash");
                var progress = RegEvolutionComponent.EVOLUTION.get(player);
                progress.setRoute("axolotl");
                progress.setRouteStartForm(FormIdentifiers.UPGRADE_AXOLOTL.toString());
                for (String node : List.of("axolotl_base", "aquatic_adapt", "water_propel", "water_spurt")) progress.unlock(node);
                player.setAir(300);
                player.setSprinting(true); player.setSneaking(true);
                AxolotlWaterSpurtHandler.tick(player);
                check(player.getVelocity().lengthSquared() == 0, "Sprint plus sneak no longer dashes");
                player.setSneaking(false);
                tap(player);
                check(player.getVelocity().lengthSquared() == 0, "One sprint press does not dash on land");
                tap(player);
                check(player.getVelocity().lengthSquared() > 1, "Second sprint press dashes on land");
                check(player.getAir() == 288, "Land dash charges the existing twelve moisture");
                check(PowerUtils.getResourceValue(player, LAND) == 100, "Land HUD starts at the existing five seconds");
                player.setVelocity(Vec3d.ZERO);
                doubleTap(player);
                check(player.getVelocity().lengthSquared() == 0 && player.getAir() == 288, "Cooldown blocks another land dash without charging moisture");

                AxolotlWaterSpurtHandler.onPlayerDisconnect(player.getUuid());
                tap(player);
                AxolotlWaterSpurtHandler.onPlayerDisconnect(player.getUuid());
                tap(player);
                check(player.getVelocity().lengthSquared() == 0, "Disconnect clears a pending first tap");
                AxolotlWaterSpurtHandler.onPlayerDisconnect(player.getUuid());
                player.setAir(0);
                doubleTap(player);
                check(player.getVelocity().lengthSquared() == 0, "Insufficient land moisture blocks the gesture");

                BlockPos water = test.getAbsolutePos(new BlockPos(7, 3, 7));
                for (BlockPos pos : BlockPos.iterate(water.add(-1, -1, -1), water.add(1, 2, 1)))
                    test.getWorld().setBlockState(pos, Blocks.WATER.getDefaultState());
                player.refreshPositionAndAngles(water.getX()+.5, water.getY(), water.getZ()+.5, 0, 0);
                test.waitAndRun(5, () -> {
                    try {
                        // GameTest's creative mock does not run a network player's full
                        // movement tick. Ask vanilla to sense the real fluid blocks explicitly.
                        var updateWater = net.minecraft.entity.Entity.class.getDeclaredMethod("updateWaterState");
                        updateWater.setAccessible(true);
                        updateWater.invoke(player);
                        check(player.isTouchingWater(), "Fixture entered real water");
                        player.setVelocity(Vec3d.ZERO); player.setAir(200);
                        AxolotlWaterSpurtHandler.onPlayerDisconnect(player.getUuid());
                        tap(player);
                        check(player.getVelocity().lengthSquared() == 0, "One sprint press does not dash in water");
                        tap(player);
                        check(player.getVelocity().lengthSquared() > 1, "Second sprint press dashes in water");
                        check(player.getAir() == 200, "Water dash is free");
                        check(PowerUtils.getResourceValue(player, WATER) == 100, "Water HUD starts at five seconds");
                        player.setVelocity(Vec3d.ZERO);
                        doubleTap(player);
                        check(player.getVelocity().lengthSquared() == 0, "Water cooldown blocks a repeat gesture");
                        AxolotlWaterSpurtHandler.onPlayerDisconnect(player.getUuid());
                        System.out.println("AXOLOTL_DOUBLE_SPRINT_WORLD_PASS checks=" + checks);
                        test.complete();
                    } catch (Throwable failure) { throw new AssertionError(failure); }
                });
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }
}
