package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.entity.BeepSheepEntity;
import net.jackcooper.shapeShifterCurseAddon.mixin.entity.MobEntityGoalAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** Check a live projectile, saved mob and natural effect expiration in two server processes. */
public final class MobSheepRestartWorldProbe {
    private static final BlockPos CENTER = new BlockPos(56, 160, 56);
    private static final Path BASELINE = Path.of("cow-goals-before.txt");
    private static final Path SHEEP = Path.of("cow-goals-active.txt");
    private static final Path ENTITIES = Path.of("cow-caster-uuids.txt");

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Stream<String> goals(String type, GoalSelector selector) {
        return selector.getGoals().stream().map(goal ->
                type + " " + goal.getPriority() + " " + goal.getGoal().getClass().getName()).sorted();
    }

    private static List<String> goals(CowEntity cow) {
        var access = (MobEntityGoalAccessor) cow;
        return Stream.concat(goals("goal", access.ssca$getGoalSelector()),
                goals("target", access.ssca$getTargetSelector())).toList();
    }

    private static void checkState(CowEntity cow, float health, float max, String phase) {
        String message = phase + " uuid=" + cow.getUuidAsString() + " health=" + cow.getHealth()
                + "/" + cow.getMaxHealth() + " speed=" + cow.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                + " goals=" + goals(cow);
        System.out.println(message);
        require(cow.getHealth() == health && cow.getMaxHealth() == max, message);
        double speed = cow.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        require(Math.abs(speed - (max == 16 ? 5.0 / 43.17 : 0.2)) < 0.00001, message);
        require(!cow.isAiDisabled(), "Unexpected NoAI flag");
    }

    public static void start(TestContext test) {
        try {
            ServerWorld world = test.getWorld();
            world.setChunkForced(3, 3, true);
            world.getChunk(CENTER);
            if (Boolean.getBoolean("neomagic.resume")) {
                reopen(test);
                return;
            }
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    world.setBlockState(CENTER.add(x, -1, z), Blocks.GLASS.getDefaultState());
                    if (Math.abs(x) == 6 || Math.abs(z) == 6) {
                        for (int y = 0; y < 3; y++) world.setBlockState(CENTER.add(x, y, z), Blocks.GLASS.getDefaultState());
                    }
                }
            }
            var cow = Objects.requireNonNull(EntityType.COW.create(world));
            cow.setPersistent();
            cow.setPosition(56.5, 160, 56.5);
            cow.setHealth(5);
            var caster = Objects.requireNonNull(EntityType.ARMOR_STAND.create(world));
            caster.setPosition(56.5, 160, 52.5);
            require(world.spawnEntity(caster) && world.spawnEntity(cow), "Could not spawn caster and target");
            Files.write(BASELINE, goals(cow));
            Files.write(ENTITIES, List.of(cow.getUuidAsString(), caster.getUuidAsString()));
            checkState(cow, 5, 10, "originalCow");
            var projectile = new BeepSheepEntity(world, caster);
            projectile.setSheepDuration(400);
            projectile.setPosition(56.5, 160.65, 55.65);
            projectile.setVelocity(0, 0, 0.9);
            require(world.spawnEntity(projectile), "Could not launch the production sheep projectile");
            test.waitAndRun(24, () -> {
                try {
                    require(cow.hasStatusEffect(SscAddon.SHEEP_FORM), "Projectile did not apply the sheep effect");
                    checkState(cow, 8, 16, "savedCow");
                    require(!goals(cow).equals(Files.readAllLines(BASELINE)), "Goal set did not change");
                    Files.write(SHEEP, goals(cow));
                    world.getServer().save(false, true, true);
                    test.complete();
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void reopen(TestContext test) throws Exception {
        var entities = Files.readAllLines(ENTITIES);
        var target = UUID.fromString(entities.get(0));
        var caster = UUID.fromString(entities.get(1));
        var expectedGoals = Files.readAllLines(BASELINE);
        var expectedSheep = Files.readAllLines(SHEEP);
        boolean expectCaster = !Boolean.getBoolean("ssca.qa.legacyMob");
        test.waitAndRun(15, () -> {
            var world = test.getWorld();
            require(world.getEntity(caster) != null, "Saved caster was not loaded");
            var cow = (CowEntity) Objects.requireNonNull(world.getEntity(target));
            var effect = cow.getStatusEffect(SscAddon.SHEEP_FORM);
            require(effect != null && effect.getDuration() > 0, "Active effect missing on reload; worldTime=" +
                    world.getTime() + ", registeredId=" + net.minecraft.registry.Registries.STATUS_EFFECT.getRawId(SscAddon.SHEEP_FORM)
                    + ", loadedEffects=" + cow.getStatusEffects());
            checkState(cow, 8, 16, "reopenedCow");
            require(goals(cow).equals(expectedSheep), "Sheep goals lost after restart");
            var snapshot = cow.writeNbt(new NbtCompound()).getCompound("SSCA.SheepForm");
            require(snapshot.getBoolean("SheepAi"), "Missing sheep AI snapshot");
            require(expectCaster ? snapshot.containsUuid("Caster") && snapshot.getUuid("Caster").equals(caster)
                    : !snapshot.containsUuid("Caster"), "Incorrect flee target " + snapshot);
            test.waitAndRun(effect.getDuration() + 10, () -> {
                require(!cow.hasStatusEffect(SscAddon.SHEEP_FORM), "Effect did not expire naturally");
                checkState(cow, 5, 10, "expiredCow");
                require(goals(cow).equals(expectedGoals), "Original cow goals were not restored");
                require(!cow.writeNbt(new NbtCompound()).contains("SSCA.SheepForm"), "Expired sheep snapshot still saved");
                test.complete();
            });
        });
    }
}
