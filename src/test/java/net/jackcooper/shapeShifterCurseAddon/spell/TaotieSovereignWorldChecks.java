package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class TaotieSovereignWorldChecks {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static CowEntity cow(ServerPlayerEntity caster, Vec3d position) {
        var world = caster.getServerWorld();
        var cow = EntityType.COW.create(world);
        cow.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
        cow.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40);
        cow.setHealth(cow.getMaxHealth());
        cow.setAiDisabled(true);
        cow.setNoGravity(true);
        check(world.spawnEntity(cow), "spawn Taotie target");
        return cow;
    }

    private static void hit(LivingEntity target, DamageSource source, float amount, float expected) {
        target.timeUntilRegen = 0;
        target.setHealth(target.getMaxHealth());
        float before = target.getHealth();
        check(target.damage(source, amount), "damage accepted: " + source.getName());
        float actual = before - target.getHealth();
        check(Math.abs(actual - expected) < 0.0001, source.getName() + " expected " + expected + " got " + actual);
    }

    static void run(ServerPlayerEntity caster) {
        var world = caster.getServerWorld();
        var spell = SpellRegistry.get("taotie_sovereign");
        var start = caster.getPos();
        var origin = new Vec3d(start.x, 160, start.z);
        caster.refreshPositionAndAngles(origin.x, origin.y, origin.z, 0, 0);
        var target = cow(caster, origin.add(0, 0, 8));
        var bystander = cow(caster, origin.add(3, 0, 7));
        var wall = BlockPos.ofFloored(origin.add(0, 1, 3));
        var previousBlock = world.getBlockState(wall);
        var sources = world.getDamageSources();
        var attributedFire = new DamageSource(sources.inFire().getTypeRegistryEntry(), null, caster);
        SpellRarity[] rarities = {SpellRarity.WHITE, SpellRarity.GREEN, SpellRarity.BLUE, SpellRarity.PURPLE, SpellRarity.ORANGE};
        try {
            world.setBlockState(wall, Blocks.STONE.getDefaultState());
            check(spell.captureCastTarget(caster, 1) == null, "no lock through a wall");
            spell.cast(caster, 0, false, 1);
            check(caster.getFireTicks() <= 0 && target.getFireTicks() <= 0, "miss ignites nobody");
            world.setBlockState(wall, previousBlock);
            target.setPosition(origin.add(0, 0, 25));
            check(spell.captureCastTarget(caster, 1) == null, "Taotie range is capped at 24 blocks");
            target.setPosition(origin.add(0, 0, 8));
            for (int level = 1; level <= 5; level++) {
                var loot = MagicScrollLoot.createNaturalScroll("taotie_sovereign", level);
                check(ScrollData.getSpell(loot) == spell && spell.getRarity(ScrollData.getLevel(loot)) == rarities[level-1], "loot tier " + level);
                caster.setFireTicks(0);
                target.setFireTicks(0);
                check(spell.captureCastTarget(caster, level) != null && spell.getCastingLockEntity(caster) == target, "lock correct target");
                spell.cast(caster, 0, false, level);
                check(caster.getFireTicks() == (5 + level) * 20 && target.getFireTicks() == caster.getFireTicks(), "both burn for tier duration");
                check(bystander.getFireTicks() <= 0, "nearby bystander untouched");
                target.timeUntilRegen = 0;
                target.baseTick();
                check(target.getAttacker() == caster, "Target's burn is attributed to the caster");
                hit(caster, sources.onFire(), 10, 10 - level);
                hit(caster, sources.inFire(), 4, 4);
                hit(target, sources.onFire(), 1, 1);
                hit(target, sources.playerAttack(caster), 4, 4);
                hit(target, attributedFire, 10, 11 + level);
                target.timeUntilRegen = 0;
                target.setHealth(target.getMaxHealth());
                SpellHitHelper.projectileHit(caster, target, 10, FormationElement.FIRE, null, 0);
                check(Math.abs(target.getMaxHealth() - target.getHealth() - 11 - level) < 0.0001, "fire spell hit tier " + level);
                hit(target, SpellDamageSource.of(sources, caster), 4, 4);
            }
            caster.extinguish();
            hit(target, attributedFire, 4, 4);
            caster.setFireTicks(400);
            NeoRuntime.data(caster).taotieUntil = caster.getServer().getOverworld().getTime();
            hit(target, attributedFire, 4, 4);
            hit(caster, sources.onFire(), 4, 4);
        } finally {
            target.discard();
            bystander.discard();
            world.setBlockState(wall, previousBlock);
            caster.setPosition(start);
            caster.extinguish();
            caster.timeUntilRegen = 0;
            caster.setHealth(caster.getMaxHealth());
            NeoRuntime.data(caster).taotieTier = 0;
            NeoRuntime.data(caster).taotieUntil = 0;
            NeoRuntime.dirty(caster);
        }
        System.out.println("TAOTIE_WORLD_PASS five rarities, range, wall, single target, burning, typed damage, extinguish, expiry");
    }
}
