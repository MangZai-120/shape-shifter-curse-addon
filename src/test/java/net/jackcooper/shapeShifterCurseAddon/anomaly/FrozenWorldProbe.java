package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Disposable real ServerWorld entities; these checks do not replace client or multiplayer acceptance. */
public final class FrozenWorldProbe {
    private static int checks;
    private static int nextSlot;
    private static final List<PigEntity> TARGETS = new ArrayList<>();

    private FrozenWorldProbe() { }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void near(double actual, double expected, String reason) {
        check(Math.abs(actual - expected) < 1e-6, reason + ": " + actual + " != " + expected);
    }

    private static PigEntity target(TestContext context) {
        PigEntity target = EntityType.PIG.create(context.getWorld());
        check(target != null, "A real pig entity is created");
        BlockPos position = context.getAbsolutePos(new BlockPos(2 + nextSlot++ % 6 * 3, 6, 2 + nextSlot / 6 * 3));
        target.refreshPositionAndAngles(position.getX() + .5, position.getY(), position.getZ() + .5, 35, 12);
        target.setAiDisabled(true);
        target.setNoGravity(true);
        check(context.getWorld().spawnEntity(target), "The target is added to the real ServerWorld");
        TARGETS.add(target);
        return target;
    }

    private static void frozen(PigEntity target) {
        check(FrozenControl.freeze(target, FrozenControl.DEFAULT_DURATION_TICKS), "The registered freeze accepts a living target");
        check(target.hasStatusEffect(SscAddon.FROZEN), "The real registered frozen status is present");
        check(target.getStatusEffect(SscAddon.FROZEN).getDuration() == 100, "Ice enclosure is initially exactly five seconds");
        check(FrozenControl.serverLocked(target.getUuid()) && FrozenControl.pose(target) != null,
                "Frozen lifecycle owns the target and its original view");
        check(AbilityUseGuard.fullyControlled(target), "Existing active-ability guard recognizes ice enclosure");
    }

    private static void released(PigEntity target, String reason) {
        check(!target.hasStatusEffect(SscAddon.FROZEN), reason + ": frozen effect is removed");
        check(!FrozenControl.serverLocked(target.getUuid()) && FrozenControl.pose(target) == null,
                reason + ": control ownership and pose are released");
    }

    private static void later(TestContext context, long ticks, Runnable action) {
        context.waitAndRun(ticks, () -> {
            try { action.run(); }
            catch (Throwable failure) {
                failure.printStackTrace();
                context.throwGameTestException(failure.toString());
            }
        });
    }

    public static void start(TestContext context) {
        checks = 0;
        nextSlot = 0;
        TARGETS.clear();
        later(context, 70, () -> run(context));
    }

    private static void run(TestContext context) {
        check(FrozenControl.DEFAULT_DURATION_TICKS == 100 && FrozenControl.SHATTER_SLOW_TICKS == 60,
                "Confirmed timing constants are five and three seconds");

        PigEntity natural = target(context);
        double naturalSpeed = natural.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        frozen(natural);
        near(natural.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), 0, "Ice enclosure removes all voluntary movement speed");
        later(context, 98, () -> check(natural.hasStatusEffect(SscAddon.FROZEN), "Five-second enclosure remains active before its final ticks"));
        later(context, 102, () -> {
            released(natural, "Five-second natural expiry");
            near(natural.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), naturalSpeed, "Natural expiry restores the original movement speed");
            check(!natural.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "Natural thaw does not invent the hit-only recovery slow");
        });

        PigEntity struck = target(context);
        double struckSpeed = struck.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        frozen(struck);
        float beforeHealth = struck.getHealth();
        check(struck.damage(context.getWorld().getDamageSources().generic(), 2), "A real ordinary damage call succeeds");
        near(struck.getHealth(), beforeHealth - 2, "The breaking hit retains its original health damage");
        released(struck, "The successful hit immediately shatters existing ice");
        check(struck.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "The breaking hit adds the independent recovery slow");
        check(struck.getStatusEffect(SscAddon.FROZEN_RECOVERY).getDuration() == 60,
                "Recovery slow starts at exactly sixty ticks");
        near(struck.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), struckSpeed * .75,
                "Recovery slow is exactly twenty-five percent, rather than vanilla Slowness rounding");
        later(context, 58, () -> {
            check(struck.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "Recovery slow remains present before three seconds elapse");
            near(struck.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), struckSpeed * .75,
                    "Recovery retains the exact movement multiplier throughout its duration");
        });
        later(context, 62, () -> {
            check(!struck.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "Recovery slow expires after sixty actual entity ticks");
            near(struck.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), struckSpeed,
                    "Recovery expiry removes only its own movement modifier");
        });

        PigEntity rejected = target(context);
        frozen(rejected);
        rejected.setInvulnerable(true);
        check(!rejected.damage(context.getWorld().getDamageSources().generic(), 2), "An invulnerable target rejects the real damage call");
        check(rejected.hasStatusEffect(SscAddon.FROZEN) && FrozenControl.serverLocked(rejected.getUuid()),
                "Rejected damage cannot shatter ice or release control");
        check(!rejected.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "Rejected damage cannot create recovery slow");

        PigEntity absorbed = target(context);
        absorbed.setAbsorptionAmount(8);
        float absorbedHealth = absorbed.getHealth();
        frozen(absorbed);
        check(absorbed.damage(context.getWorld().getDamageSources().generic(), 2), "Damage wholly paid by absorption is still accepted");
        near(absorbed.getHealth(), absorbedHealth, "Absorption-only damage removes no health");
        near(absorbed.getAbsorptionAmount(), 6, "The original absorption payment is retained");
        released(absorbed, "Accepted absorption damage shatters ice");
        check(absorbed.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "Absorption-only hits also grant the recovery slow");

        PigEntity triggering = target(context);
        check(triggering.damage(context.getWorld().getDamageSources().generic(), 1), "The buildup-triggering hit resolves its original damage first");
        frozen(triggering);
        check(!triggering.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "Freezing after the triggering damage does not immediately shatter itself");
        later(context, 2, () -> check(triggering.hasStatusEffect(SscAddon.FROZEN), "The newly triggered enclosure survives subsequent ordinary world ticks"));

        PigEntity purified = target(context);
        double purifiedSpeed = purified.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        frozen(purified);
        purified.addStatusEffect(new StatusEffectInstance(SscAddon.PURIFIED, 20));
        later(context, 2, () -> {
            released(purified, "The real PurifiedEffect removes ice on its normal update");
            near(purified.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), purifiedSpeed,
                    "Purification restores movement immediately");
            check(!purified.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "Purification is not a breaking damage hit");
        });

        PigEntity unloaded = target(context);
        frozen(unloaded);
        unloaded.discard();
        later(context, 2, () -> {
            check(unloaded.isRemoved(), "The unloaded entity is actually removed from its world");
            released(unloaded, "Real entity unload cleans frozen state");
        });

        PigEntity dead = target(context);
        frozen(dead);
        dead.setHealth(1);
        check(dead.damage(context.getWorld().getDamageSources().generic(), 2), "A lethal breaking hit succeeds");
        check(!dead.isAlive(), "The lethal hit still kills the target normally");
        released(dead, "Death clears frozen state");
        check(!dead.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "Dead targets receive no new recovery slow");

        PigEntity movement = target(context);
        // NoAI makes MobEntity.canMoveVoluntarily false and skips LivingEntity.travel's physics body.
        // Freeze itself stops navigation/AI through isImmobile; leave vanilla physics enabled here.
        movement.setAiDisabled(false);
        check(movement.isLogicalSideForUpdatingMovement(), "The real movement fixture enters vanilla server-side travel physics");
        frozen(movement);
        movement.setVelocity(Vec3d.ZERO);
        Vec3d origin = movement.getPos();
        movement.travel(new Vec3d(1, 0, 1));
        near(movement.getPos().x, origin.x, "Actual travel rejects voluntary horizontal input on X");
        near(movement.getPos().z, origin.z, "Actual travel rejects voluntary horizontal input on Z");
        movement.setYaw(140);
        movement.setPitch(-60);
        movement.forwardSpeed = 1;
        movement.sidewaysSpeed = 1;
        FrozenControl.hold(movement);
        near(movement.getYaw(), 35, "Frozen view returns to the captured yaw");
        near(movement.getPitch(), 12, "Frozen view returns to the captured pitch");
        near(movement.forwardSpeed + movement.sidewaysSpeed, 0, "Control owner clears active movement intent");
        movement.setVelocity(new Vec3d(.2, -.1, 0));
        movement.travel(Vec3d.ZERO);
        check(movement.getX() > origin.x && movement.getY() < origin.y,
                "Frozen travel still applies passive horizontal forces and downward velocity");

        PigEntity falling = target(context);
        falling.setAiDisabled(false);
        check(falling.isLogicalSideForUpdatingMovement(), "The gravity fixture has normal server-side physics enabled");
        BlockPos floor = BlockPos.ofFloored(falling.getPos()).down(6);
        context.getWorld().setBlockState(floor, Blocks.STONE.getDefaultState());
        falling.setNoGravity(false);
        falling.setVelocity(Vec3d.ZERO);
        double initialY = falling.getY();
        frozen(falling);
        later(context, 5, () -> {
            check(falling.getY() < initialY - .1, "A real frozen entity continues to fall under vanilla gravity");
            check(falling.hasStatusEffect(SscAddon.FROZEN), "Gravity alone does not shatter the enclosure before landing");
        });

        later(context, 104, () -> {
            TARGETS.forEach(target -> {
                FrozenControl.clear(target);
                target.discard();
            });
            try {
                Files.writeString(Path.of("frozen-world-result.json"), "{\"checks\":" + checks
                        + ",\"real_server_world\":true,\"freeze_ticks\":100,\"recovery_ticks\":60,\"slow_fraction\":0.25}");
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            System.out.println("FROZEN_WORLD_PASS checks=" + checks
                    + " (actual damage/absorption, natural expiry, exact slow, purification/unload/death, movement and gravity; client/multiplayer NOT tested)");
            context.complete();
        });
    }
}
