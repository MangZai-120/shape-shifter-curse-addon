package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.entity.SpellFireBoltEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;

/** Spell release and entity tracking can take place on different ticks after a teleport. */
public final class NeoWeaveTrackingProbe {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            try {
                castBeforeTracking(test);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private static void castBeforeTracking(TestContext test) throws Exception {
        var caster = NeoFormCastingProbe.player(test, FormIdentifiers.SPIDER_MOON_WEAVER, 24);
        ManaUtils.setManaTypeID(caster, new Identifier("shape-shifter-curse", "web_resource"));
        ManaUtils.setPlayerMana(caster, 24);
        var world = caster.getServerWorld();
        var origin = caster.getPos();
        var destination = new BlockPos(8104, 120, 8104);
        world.getChunk(destination);
        caster.refreshPositionAndAngles(destination.getX() + .5, destination.getY(), destination.getZ() + .5, 0, 0);
        var fireBolt = SpellRegistry.get("fire_bolt");
        var style = FormSpellStyle.plan(caster, fireBolt, 1, 8, 8);
        check(style.pay() && style.moonWeaver, "A paid moon-weaver cast is available");
        style.effect(() -> fireBolt.cast(caster, 1, false, 1));
        fireBolt.cast(caster, 1, false, 2);
        double reserve = ManaUtils.getPlayerMana(caster);
        check(reserve == 16, "Only the paid cast spends eight web energy");
        var area = new Box(destination).expand(40);
        check(world.getEntitiesByClass(SpellFireBoltEntity.class, area, bolt -> bolt.getOwner() == caster).isEmpty(),
                "The newly loaded destination has not started tracking either projectile");
        caster.refreshPositionAndAngles(origin.x, origin.y, origin.z, 0, 0);
        world.setChunkForced(destination.getX() >> 4, destination.getZ() >> 4, true);
        test.createTimedTaskRunner().createAndAdd(() -> {
            var bolts = world.getEntitiesByClass(SpellFireBoltEntity.class, area, bolt -> bolt.getOwner() == caster);
            if (bolts.size() < 2) test.throwGameTestException("Waiting for both deferred fire bolts to enter tracking");
            check(bolts.size() == 2, "Both original fire bolts are tracked exactly once");
            var paid = bolts.stream().filter(b -> b.getSpellLevel() == 1).findFirst().orElseThrow();
            var plain = bolts.stream().filter(b -> b.getSpellLevel() == 2).findFirst().orElseThrow();
            var paidData = paid.writeNbt(new NbtCompound());
            check(paidData.getBoolean("SscaWeaveEnabled") && style.weave.id.equals(paidData.getUuid("SscaWeaveCast")),
                    "The paid projectile retains its original weaving group when tracking starts");
            check(!((SpiderSpellLink.WebProjectile) plain).ssca$weave().enabled(),
                    "A subsequent unstyled projectile from the same owner remains ordinary");
            check(ManaUtils.getPlayerMana(caster) == reserve, "Starting tracking never charges the caster a second time");
            world.setChunkForced(destination.getX() >> 4, destination.getZ() >> 4, false);
            System.out.println("DEFERRED_WEAVE_PASS projectiles=2 paid=1 ordinary=1 energy=16");
        }).completeIfSuccessful();
    }
}
