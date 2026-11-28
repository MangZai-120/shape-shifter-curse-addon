package net.jackcooper.shapeShifterCurseAddon.ability;

import io.github.apace100.apoli.Apoli;
import io.github.apace100.apoli.util.ApoliConfig;
import java.nio.file.Files;
import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Exercises both sides of a journal failure in the disposable GameTest save. */
public final class EnhancedDomainRecoveryWorldProbe {
    private static final String DOMAIN = "ssc_addon:form_anubis_wolf_sp_death_domain";
    private static final String SUMMON = AnubisWolfSpSummonWolves.SKILL_ID;

    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            try {
                run(test);
                System.out.println("ENHANCED_DOMAIN_RECOVERY_PASS");
                test.complete();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void run(TestContext test) throws Exception {
        if (Apoli.config == null) Apoli.config = new ApoliConfig();
        var world = test.getWorld();
        var active = caster(test, 0, Blocks.STONE);
        var opening = caster(test, 48, Blocks.SOUL_SAND);
        var retry = caster(test, 96, Blocks.STONE);
        var natural = caster(test, 144, Blocks.SOUL_SAND);
        BlockPos floor = active.getBlockPos().down();
        var journal = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data/ssc_addon_death_domain_recovery");

        charge(natural);
        AnubisWolfSpDeathDomain.tick(natural);
        check(AnubisWolfSpDeathDomain.hasActiveDomain(natural.getUuid()), "natural soul sand accepts a successful empty first ring");
        try (var files = Files.list(journal)) {
            check(files.anyMatch(p -> p.toString().endsWith(".nbt")), "empty first ring wrote an actual journal");
        }
        AnubisWolfSpDeathDomain.clearPlayer(natural);
        AnubisWolfSpDeathDomain.tickCleanup();
        world.save(null, true, false);
        try (var files = Files.list(journal)) {
            check(files.noneMatch(p -> p.toString().endsWith(".nbt")), "empty opening journal released after flush");
        }

        charge(active);
        for (int i = 0; i < 4; i++) AnubisWolfSpDeathDomain.tick(active);
        check(world.getBlockState(floor).isOf(Blocks.SOUL_SAND), "first ring converted");
        check(world.getBlockState(floor.east(2)).isOf(Blocks.STONE), "second ring has not started");
        check(SkillCooldowns.remaining(active, DOMAIN) == SkillCooldowns.cooldown(active, DOMAIN), "normal domain cooldown started");
        int summonCooldown = SkillCooldowns.remaining(active, SUMMON);
        check(summonCooldown == SkillCooldowns.cooldown(active, SUMMON) && summonCooldown > 0, "summon cooldown started on first commit");
        for (int i = 0; i < 35; i++) AnubisWolfSpSummonWolves.tick(active);
        var summoned = wolves(active);
        check(!summoned.isEmpty(), "a successful opening produced real wolves");
        world.setBlockState(floor.west(), Blocks.OBSIDIAN.getDefaultState());

        var priorWolf = AnubisWolfSpSummonWolves.spawnSpellWolf(opening, opening.getBlockPos().east(), false, false);
        check(priorWolf != null, "pre-existing wolf");
        SkillCooldowns.force(opening, SUMMON, 45);
        charge(opening);
        charge(retry);
        var parked = journal.resolveSibling("saved-journals-for-test");
        Files.move(journal, parked);
        try {
            Files.writeString(journal, "GameTest: this file blocks journal creation");
            AnubisWolfSpDeathDomain.tick(opening);
            check(!AnubisWolfSpDeathDomain.hasActiveDomain(opening.getUuid()), "failed opening is inactive");
            check(AnubisWolfSpSoulEnergy.getEnergy(opening) == 100 && SkillCooldowns.ready(opening, DOMAIN), "full refund and immediate retry");
            check(SkillCooldowns.remaining(opening, SUMMON) == 45, "pre-existing summon cooldown preserved");
            for (int i = 0; i < 35; i++) AnubisWolfSpSummonWolves.tick(opening);
            check(wolves(opening).equals(List.of(priorWolf)), "failed opening preserves the existing wolf and summons no others");

            AnubisWolfSpDeathDomain.tick(retry);
            check(AnubisWolfSpSoulEnergy.getEnergy(retry) == 100 && SkillCooldowns.remaining(retry, SUMMON) == 0
                    && SkillCooldowns.ready(retry, DOMAIN), "already-paused journal charges neither skill");
            check(world.getBlockState(retry.getBlockPos().down()).isOf(Blocks.STONE), "failed first ring never changed terrain");

            AnubisWolfSpDeathDomain.tick(active);
            check(!AnubisWolfSpDeathDomain.hasActiveDomain(active.getUuid()), "partial domain immediately left expansion");
            check(world.getBlockState(floor.east(2)).isOf(Blocks.STONE), "failed ring never changed terrain");
            check(world.getBlockState(floor).isOf(Blocks.SOUL_SAND), "committed center remains until retraction");
            for (int i = 0; i < 4; i++) AnubisWolfSpDeathDomain.tick(active);
            check(world.getBlockState(floor).isOf(Blocks.STONE) && world.getBlockState(floor.east()).isOf(Blocks.STONE), "committed terrain restored");
            check(world.getBlockState(floor.west()).isOf(Blocks.OBSIDIAN), "later terrain edit preserved");
            check(!SkillCooldowns.isCasting(active, DOMAIN) && SkillCooldowns.remaining(active, DOMAIN) == SkillCooldowns.cooldown(active, DOMAIN),
                    "retraction closed the cast with normal cooldown");
            check(AnubisWolfSpSoulEnergy.getEnergy(active) == 0 && SkillCooldowns.remaining(active, SUMMON) == summonCooldown,
                    "no mid-cast refund or summon cooldown reset");
            check(wolves(active).containsAll(summoned), "committed wolves preserved after retraction");
        } finally {
            Files.deleteIfExists(journal);
            Files.move(parked, journal);
        }
        world.save(null, true, false);
        try (var files = Files.list(journal)) {
            check(files.noneMatch(p -> p.toString().endsWith(".nbt")), "restored rings released after flush");
        }
    }

    private static ServerPlayerEntity caster(TestContext test, int offset, Block ground) throws Exception {
        var player = NeoFormCastingProbe.player(test, FormIdentifiers.ANUBIS_WOLF_SP, 0);
        FormUtils.setForm(player, RegPlayerForms.playerForms.get(FormIdentifiers.ANUBIS_WOLF_SP));
        BlockPos pos = test.getAbsolutePos(new BlockPos(3, 45, 3)).add(offset, 0, 0);
        player.getServerWorld().getChunk(pos);
        player.refreshPositionAndAngles(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0, 0);
        for (var p : BlockPos.iterate(pos.add(-4,-1,-4), pos.add(4,-1,4))) {
            player.getWorld().setBlockState(p, ground.getDefaultState());
        }
        AnubisWolfSpSoulEnergy.setEnergy(player, 100);
        return player;
    }

    private static void charge(ServerPlayerEntity player) {
        check(AnubisWolfSpDeathDomain.execute(player), "accepted cast");
        check(AnubisWolfSpSoulEnergy.getEnergy(player) == 0, "enhanced cast paid its energy");
        for (int i = 0; i < 20; i++) AnubisWolfSpDeathDomain.tick(player);
        check(!AnubisWolfSpDeathDomain.hasActiveDomain(player.getUuid()), "charging alone cannot activate the domain");
        check(SkillCooldowns.remaining(player, DOMAIN) == 0, "on-release cooldown waits for journal commit");
    }

    private static List<AnubisWolfMinionEntity> wolves(ServerPlayerEntity player) {
        return player.getServerWorld().getEntitiesByClass(AnubisWolfMinionEntity.class,
                player.getBoundingBox().expand(16), wolf -> wolf.isAlive());
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        System.out.println("ENHANCED_DOMAIN_CHECK " + message);
    }
}
