package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.entity.GhostCatEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;

/** Real spawn and tick paths, including movement of a victim looking in unrelated directions. */
public final class NeoNightmareGhostFacingProbe {
    private static int checks;

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static Vec3d forward(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(angle), 0, Math.cos(angle));
    }

    private static void facing(MobEntity ghost, LivingEntity target, String stage) {
        Vec3d toward = target.getPos().subtract(ghost.getPos()).multiply(1, 0, 1).normalize();
        check(forward(ghost.getYaw()).dotProduct(toward) > .99999, stage + " entity faces victim");
        check(forward(ghost.bodyYaw).dotProduct(toward) > .99999, stage + " body faces victim");
        check(forward(ghost.headYaw).dotProduct(toward) > .99999, stage + " head faces victim");
        Vec3d eyeDirection = target.getEyePos().subtract(ghost.getEyePos()).normalize();
        double alignment = ghost.getRotationVector().dotProduct(eyeDirection);
        check(alignment > .9999, stage + " looks toward the victim's eyes: alignment=" + alignment
                + ", actual=" + ghost.getRotationVector() + ", expected=" + eyeDirection
                + ", pitch=" + ghost.getPitch());
    }

    public static void start(TestContext context) {
        context.waitAndRun(70, () -> {
            try {
                ServerPlayerEntity caster = NeoFormCastingProbe.player(context, FormIdentifiers.WILD_CAT_NIGHTMARE, 0);
                ServerPlayerEntity target = NeoFormCastingProbe.player(context, RegPlayerForms.ORIGINAL_SHIFTER.getFormID(), 0);
                ServerWorld world = context.getWorld();
                BlockPos center = context.getAbsolutePos(new BlockPos(4, 3, 4));
                for (BlockPos pos : BlockPos.iterate(center.add(-10, -1, -10), center.add(10, -1, 10)))
                    world.setBlockState(pos, Blocks.STONE.getDefaultState());
                caster.setPosition(center.getX() + 6.5, center.getY(), center.getZ() + .5);
                Method spawn = NightmareSpookManager.class.getDeclaredMethod("spawnGhostCreeper",
                        ServerWorld.class, ServerPlayerEntity.class, LivingEntity.class);
                spawn.setAccessible(true);
                List<Float> spawnHeadings = new ArrayList<>();
                for (int angle = 0; angle < 360; angle += 45) {
                    NightmareSpookManager.clearAll(world.getServer());
                    target.refreshPositionAndAngles(center.getX() + .5, center.getY(), center.getZ() + .5, angle, 0);
                    target.setHeadYaw(angle);
                    check((boolean) spawn.invoke(null, world, caster, target), "Ghost spawns for victim heading " + angle);
                    CreeperEntity creeper = world.getEntitiesByClass(CreeperEntity.class, target.getBoundingBox().expand(8),
                            NightmareSpookManager::isGhostCreeperEntity).stream().findFirst().orElseThrow();
                    facing(creeper, target, "Creeper spawn " + angle);
                    check(creeper.prevBodyYaw == creeper.bodyYaw && creeper.prevHeadYaw == creeper.headYaw,
                            "Spawn interpolation starts from the correct heading");
                    spawnHeadings.add(creeper.getYaw());
                    target.setPosition(target.getX() + 1.1, target.getY(), target.getZ() - .7);
                    target.setYaw(angle + 113);
                    target.setHeadYaw(angle + 113);
                    NightmareSpookManager.tick(target);
                    facing(creeper, target, "Moving victim creeper " + angle);
                    NightmareSpookManager.onGhostCreeperAttacked(target, creeper);
                    GhostCatEntity cat = world.getEntitiesByClass(GhostCatEntity.class, target.getBoundingBox().expand(10),
                            entity -> entity.isAlive()).stream().findFirst().orElseThrow();
                    facing(cat, target, "Cat spawn " + angle);
                    check(cat.prevBodyYaw == cat.bodyYaw && cat.prevHeadYaw == cat.headYaw,
                            "Cat first frame has its correct body and head heading");
                    target.setPosition(target.getX() - .8, target.getY(), target.getZ() + .9);
                    NightmareSpookManager.tick(target);
                    check(cat.isAlive(), "Cat remains visible during its initial pursuit");
                    facing(cat, target, "Moving victim cat " + angle);
                }
                check(spawnHeadings.stream().map(yaw -> Math.round(yaw / 20)).distinct().count() >= 4,
                        "Different relative positions produce different headings instead of a fixed direction");
                NightmareSpookManager.clearAll(world.getServer());
                System.out.println("NIGHTMARE_GHOST_FACING_PASS " + checks + " checks");
                context.complete();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }
}
