package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Uses the loaded Apoli multiple power and its real component tick instead of duplicating the scan. */
public final class NeoAnubisWitherHuntProbe {
    private static int checks;
    private static final String ROOT = "my_addon:form_anubis_wolf_sp_wither_hunt";
    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }
    private static void settle(ServerPlayerEntity p) throws Exception {
        var holder = PowerHolderComponent.KEY.get(p);
        var tick = holder.getClass().getMethod("serverTick");
        for (int i = 0; i < 40; i++) { p.age++; tick.invoke(holder); }
    }
    private static void speed(ServerPlayerEntity p, double expected, String reason) {
        double actual = p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        check(Math.abs(actual - expected) < 1e-5, reason + ": " + actual + " != " + expected);
    }
    public static void start(TestContext t) {
        t.waitAndRun(70, () -> {
            try {
                check(PowerTypeRegistry.contains(new Identifier(ROOT + "_wither_detected")), "Loaded multiple power uses the underscore child identifier");
                check(!PowerTypeRegistry.contains(new Identifier(ROOT + "/wither_detected")), "The old slash reference is not a registered power");
                var p = NeoFormCastingProbe.player(t, FormIdentifiers.ANUBIS_WOLF_SP, 0);
                p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(.1);
                BlockPos ground = p.getBlockPos().down();
                p.getServerWorld().setBlockState(ground, Blocks.STONE.getDefaultState());
                p.setPosition(ground.getX() + .5, ground.getY() + 1, ground.getZ() + .5); p.setOnGround(true);
                FormUtils.applyPower(p, new Identifier(ROOT), new Identifier("ssc_addon", "wither_hunt_probe"));
                settle(p); speed(p, .1, "No Wither target grants no speed");
                var cow = EntityType.COW.create(p.getServerWorld()); cow.setAiDisabled(true); cow.setNoGravity(true);
                cow.setPosition(p.getX() + 2, p.getY(), p.getZ());
                cow.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 400, 0));
                check(p.getServerWorld().spawnEntity(cow), "Actual Wither target spawned");
                settle(p);
                check(PowerUtils.getResourceValue(p, new Identifier(ROOT + "_wither_detected")) == 1, "Real area scan sets the detected resource");
                speed(p, .12, "Nearby Wither grants twenty percent speed on ordinary ground");
                p.getServerWorld().setBlockState(ground, Blocks.SOUL_SAND.getDefaultState()); settle(p);
                speed(p, .1, "Soul sand does not stack the hunt bonus");
                p.getServerWorld().setBlockState(ground, Blocks.STONE.getDefaultState()); settle(p); speed(p, .12, "Leaving soul sand restores the hunt bonus");
                cow.setPosition(p.getX() + 30, p.getY(), p.getZ()); settle(p); speed(p, .1, "A Wither target outside twenty-four blocks does not grant speed");
                cow.setPosition(p.getX() + 2, p.getY(), p.getZ()); cow.removeStatusEffect(StatusEffects.WITHER); settle(p);
                check(PowerUtils.getResourceValue(p, new Identifier(ROOT + "_wither_detected")) == 0, "Removing Wither clears the scan resource");
                speed(p, .1, "Removing Wither clears the speed modifier"); cow.discard();
                System.out.println("ANUBIS_WITHER_HUNT_PASS " + checks + " checks"); t.complete();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
