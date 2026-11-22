package net.jackcooper.shapeShifterCurseAddon.spell;

import java.util.List;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.onixary.shapeShifterCurseFabric.minion.MinionRegister;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public final class CalamityPactWorldProbe {
    private static final String ID = "calamity_pact";

    public static void start(TestContext t) {
        t.waitAndRun(70, () -> run(t));
    }

    private static void run(TestContext t) {
        var player = SpellBacklashWorldProbe.player(t);
        var world = t.getWorld();
        var at = t.getAbsolutePos(new BlockPos(3, 2, 3));
        player.teleport(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        var spell = SpellRegistry.get(ID);
        var book = NeoBookWorldProbe.book(player);
        SpellbookData.setScroll(book, 0, ScrollData.create(ID));
        var outsider = summon(player, UUID.randomUUID());
        var pet = EntityType.WOLF.create(world);
        pet.setTamed(true);
        pet.setOwnerUuid(player.getUuid());
        pet.setAiDisabled(true);
        pet.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0, 0);
        world.spawnEntity(pet);
        require(!spell.canCast(player), "pets and another player's summons do not qualify");
        SpellCastManager.cast(player, 0);
        require(NeoRuntime.data(player).records.isEmpty(), "no eligible summon, no paid casting session");

        checkSummonTypes(player, spell);
        var first = summon(player, player.getUuid());
        var second = summon(player, player.getUuid());
        second.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1000, 1));
        for (int tier = 1; tier <= 4; tier++) {
            first.setHealth(100);
            second.setHealth(100);
            spell.cast(player, 0, false, tier);
            player.setHealth(100);
            float each = (3 + tier) / 2F;
            hit(player, 10);
            near(first.getHealth(), 100 - each, "first share at tier " + tier);
            near(second.getHealth(), 100 - each, "second share with resistance at tier " + tier);
            hit(player, 10);
            near(first.getHealth(), 100 - 2 * each, "consecutive transfers both count");
            near(player.getHealth(), 100 - 2 * (7 - tier), "caster pays only the remainder at tier " + tier);
            require(player.hurtTime > 0 && first.hurtTime > 0 && second.hurtTime > 0, "vanilla hurt animation starts for all recipients");
        }
        near(outsider.getHealth(), 100, "other owner's summon is untouched");
        near(pet.getHealth(), pet.getMaxHealth(), "ordinary pet is untouched");
        player.setHealth(100);
        first.setHealth(0.5F);
        hit(player, 10);
        require(!first.isAlive(), "first summon died from its share");
        float before = second.getHealth();
        hit(player, 10);
        near(second.getHealth(), before - 7, "last living summon takes the whole share");
        var late = summon(player, player.getUuid());
        hit(player, 10);
        near(late.getHealth(), 96.5F, "newly summoned ally joins the active pact");
        second.discard();
        late.setHealth(1);
        hit(player, 10);
        require(!late.isAlive(), "last summon dies absorbing the fatal transfer");
        near(player.getHealth(), 88, "caster pays three on all four orange hits, including the one that breaks the pact");
        var replacement = summon(player, player.getUuid());
        hit(player, 10);
        near(player.getHealth(), 78, "a replacement in the same tick does not revive the broken pact");
        near(replacement.getHealth(), 100, "replacement did not absorb the next hit");

        player.setHealth(100);
        SpellCastManager.cast(player, 0);
        t.waitAndRun(60, () -> {
            var record = NeoRuntime.data(player).records.get(0);
            require(record.spell.endsWith(ID) && record.result.equals("released") && record.paid > 0
                    && Math.abs(record.paid - record.actual) < 0.00001, "book cast releases and pays its collapse quote");
            require(SharedSpellCooldowns.getEffectiveCooldownEnd(player, SpellbookData.getScroll(book, 0))
                    > world.getTime(), "book cast starts cooldown");
            float hp = replacement.getHealth();
            hit(player, 10);
            near(replacement.getHealth(), hp - 4, "green book cast transfers four through the real pipeline");
            checkExpiry(t, player, replacement, 1);
        });
    }

    private static void checkExpiry(TestContext t, ServerPlayerEntity p, LunarSpiritEntity summon, int tier) {
        SpellRegistry.get(ID).cast(p, 0, false, tier);
        int duration = (7 + tier) * 20;
        t.waitAndRun(duration - 1, () -> {
            float hp = summon.getHealth();
            hit(p, 10);
            near(summon.getHealth(), hp - (3 + tier), "pact active one tick before expiry at tier " + tier);
        });
        t.waitAndRun(duration, () -> {
            float hp = p.getHealth();
            hit(p, 10);
            near(p.getHealth(), hp - 10, "normal damage on the expiry tick at tier " + tier);
            if (tier < 4) {
                p.setHealth(100);
                checkExpiry(t, p, summon, tier + 1);
            } else {
                var form = RegPlayerFormComponent.PLAYER_FORM.get(p);
                form.nowForm = RegPlayerForms.playerForms.get(FormIdentifiers.ALLAY_SP);
                var spell = SpellRegistry.get(ID);
                require(FormAffinity.effectiveSpellLevel(p, spell, 3) == 4 && FormAffinity.effectiveSpellLevel(p, spell, 4) == 4,
                        "summon affinity caps the pact at orange");
                var book = SpellCastManager.getEquippedBook(p);
                int purple = SpellNumbers.finalManaCost(spell, book, p, 3);
                int orange = SpellNumbers.finalManaCost(spell, book, p, 4);
                require(purple > 0 && orange == purple, "two equivalent affinity tiers have the same quote");
                System.out.println("CALAMITY_PACT_EQUAL_SHARES_AND_EXPIRY_WORLD_PASS");
                t.complete();
            }
        });
    }

    private static void checkSummonTypes(ServerPlayerEntity p, Spell spell) {
        var world = p.getServerWorld();
        var blueprint = new NeoModel.Blueprint("summon", NeoModel.Core.SUMMON, NeoModel.Carrier.AREA,
                List.of(), NeoModel.Core.FROST, 1, 0, 6);
        var cast = NeoWorldProbe.active(p, blueprint, p.getEyePos().add(0, 0, 4), null);
        require(NeoEffects.release(p, cast), "summoned wolf released through the blueprint");
        var wolf = world.getEntitiesByClass(WolfEntity.class, p.getBoundingBox().expand(8),
                ally -> ally.getCommandTags().contains(NeoEffects.SUMMON_TAG)).get(0);
        var vex = EntityType.VEX.create(world);
        vex.addCommandTag("ssc_fallen_allay_vex");
        vex.addCommandTag("owner:" + p.getUuid());
        var minion = MinionRegister.ANUBIS_WOLF_MINION.create(world);
        minion.setMinionOwnerUUID(p.getUuid());
        var allies = new MobEntity[]{wolf, vex, minion};
        for (var ally : new MobEntity[]{vex, minion}) {
            ally.setAiDisabled(true);
            ally.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), 0, 0);
            require(world.spawnEntity(ally), "ally spawned");
        }
        for (var ally : allies) {
            ally.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40);
            ally.setHealth(40);
        }
        tether(p, wolf);
        require(SpiderMoonWeaverSwingManager.isTethering(p, wolf) && WhitelistUtils.isProtected(p, wolf),
                "friendly spider tether is actually active on a pact recipient");
        spell.cast(p, 0, false, 2);
        hit(p, 10);
        near(p.getHealth(), 95, "blue caster pays five alongside mixed summons");
        for (var ally : allies) {
            near(ally.getHealth(), 40 - 5F / 3, ally.getType() + " takes one third of five points");
            ally.discard();
        }
        SpiderMoonWeaverSwingManager.onDisconnect(p);
        require(!spell.canCast(p), "unloaded summons cannot renew the pact");
    }

    // Seed an existing friendly tether and exercise both production damage handlers in one hit.
    private static void tether(ServerPlayerEntity player, MobEntity target) {
        try {
            var method = SpiderMoonWeaverSwingManager.class.getDeclaredMethod("state", ServerPlayerEntity.class);
            method.setAccessible(true);
            var link = method.invoke(null, player);
            var state = link.getClass().getDeclaredField("state");
            var entityId = link.getClass().getDeclaredField("tetherEntityId");
            state.setAccessible(true);
            entityId.setAccessible(true);
            state.setInt(link, SpiderMoonWeaverSwingManager.STATE_TETHER);
            entityId.setInt(link, target.getId());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LunarSpiritEntity summon(ServerPlayerEntity p, UUID owner) {
        var spirit = SscAddon.LUNAR_SPIRIT_ENTITY.create(p.getWorld());
        spirit.setOwnerUuid(owner);
        spirit.setAiDisabled(true);
        spirit.setLifeTicks(2400);
        spirit.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
        spirit.setHealth(100);
        spirit.refreshPositionAndAngles(p.getX(), p.getY() + 1, p.getZ(), 0, 0);
        require(p.getServerWorld().spawnEntity(spirit), "summon spawned");
        return spirit;
    }

    private static void hit(ServerPlayerEntity p, float amount) {
        p.timeUntilRegen = 0;
        p.damage(p.getDamageSources().generic(), amount);
    }

    private static void near(float actual, float expected, String why) {
        require(Math.abs(actual - expected) < 0.001F, why + ": " + actual + " != " + expected);
    }

    private static void require(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
}
