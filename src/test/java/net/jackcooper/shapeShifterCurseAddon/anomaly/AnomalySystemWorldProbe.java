package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritBoltEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellHitHelper;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Real effects and target lifecycles, independent of the renderer and of human multiplayer acceptance. */
public final class AnomalySystemWorldProbe {
    private static final String[] ELEMENTS = {"fire", "ice", "lunar", "curse", "summon", "void", "space"};
    private static final String[][] PAIRS = {
            {"fire", "ice"}, {"fire", "lunar"}, {"fire", "curse"}, {"fire", "summon"},
            {"fire", "void"}, {"fire", "space"}, {"ice", "lunar"}, {"ice", "curse"},
            {"ice", "summon"}, {"ice", "void"}, {"ice", "space"}, {"lunar", "curse"},
            {"lunar", "summon"}, {"lunar", "void"}, {"lunar", "space"}, {"curse", "summon"},
            {"curse", "void"}, {"curse", "space"}, {"summon", "void"}, {"summon", "space"},
            {"void", "space"}
    };
    private static final AnomalyProbeSupport.Checks CHECK = new AnomalyProbeSupport.Checks();
    private static final List<Entity> FIXTURES = new ArrayList<>();
    private static TestContext test;
    private static ServerPlayerEntity caster;
    private static Vec3d center;
    private static boolean previousEnabled;

    private AnomalySystemWorldProbe() {}

    public static void start(TestContext context) {
        test = context;
        CHECK.count = 0;
        FIXTURES.clear();
        AnomalyProbeSupport.later(context, 70, AnomalySystemWorldProbe::run);
    }

    private static PigEntity pig(Vec3d at) {
        test.getWorld().getChunk(BlockPos.ofFloored(at));
        var target = AnomalyProbeSupport.pig(test, at, 200);
        FIXTURES.add(target);
        return target;
    }

    private static SpellAnomalyContext context(String element, double amount) {
        return new SpellAnomalyContext(new Identifier("ssc_addon", "test_" + element), element, 1,
                amount, caster.getUuid(), UUID.randomUUID());
    }

    private static void add(PigEntity target, String element, double amount) {
        AnomalyManager.enqueue(context(element, amount), target);
        AnomalyManager.drain(test.getWorld().getServer());
    }

    private static double value(PigEntity target, String element) {
        return AnomalyManager.snapshot(target).values().getOrDefault(element, 0.0);
    }

    private static void status(PigEntity target, StatusEffect effect, int amplifier, int duration, String why) {
        var active = target.getStatusEffect(effect);
        CHECK.that(active != null, why + ": status is present");
        CHECK.that(active.getAmplifier() == amplifier, why + ": exact strength");
        CHECK.that(active.getDuration() == duration, why + ": exact duration");
    }

    private static void resetFixtures() {
        AnomalyManager.clearAll();
        for (Entity fixture : List.copyOf(FIXTURES)) fixture.discard();
        FIXTURES.clear();
    }

    private static void run() {
        previousEnabled = AnomalyManager.setEnabledForTesting(true);
        center = test.getAbsolutePos(new BlockPos(4, 30, 4)).toCenterPos();
        caster = test.createMockCreativeServerPlayerInWorld();
        caster.setNoGravity(true);
        caster.getAbilities().invulnerable = true;
        caster.refreshPositionAndAngles(center.x + 12, center.y, center.z, 0, 0);
        try {
            networkCodec();
            baseEffects();
            reactionEffects();
            integratedReactions();
            meterAndOrdering();
            timedBoundaries();
        } catch (Throwable failure) {
            resetFixtures();
            AnomalyManager.setEnabledForTesting(previousEnabled);
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        }
    }

    private static void networkCodec() {
        var snapshot = new AnomalySnapshot(500, Map.of("ice", 499.0, "fire", 123.0), 320,
                "ice", 87, List.of(new AnomalyEffects.EffectView("lunar_eclipse", 42, .75)));
        var original = new AnomalyNetwork.Message(test.getWorld().getRegistryKey().getValue(), 7531,
                UUID.randomUUID(), snapshot);
        var buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            AnomalyNetwork.write(buffer, original);
            var decoded = AnomalyNetwork.read(buffer);
            CHECK.that(decoded.equals(original) && buffer.readableBytes() == 0,
                    "The actual packet codec round-trips target identity, per-element values, timers and active effects");
            boolean immutable = false;
            try { decoded.snapshot().values().put("ice", 0.0); }
            catch (UnsupportedOperationException expected) { immutable = true; }
            CHECK.that(immutable && decoded.snapshot().values().get("ice") == 499,
                    "The decoded display snapshot cannot mutate its authoritative meter data");
        } finally { buffer.release(); }
        rejectedPacket(new AnomalySnapshot(Double.NaN, Map.of(), 0, "", 0, List.of()),
                "The packet codec rejects a non-finite maximum");
        rejectedPacket(new AnomalySnapshot(500, Map.of("ice", Double.NaN), 20, "", 0, List.of()),
                "The packet codec rejects a non-finite meter value");
        rejectedPacket(new AnomalySnapshot(500, Map.of("ice", 501.0), 20, "", 0, List.of()),
                "The packet codec rejects values above the transmitted maximum");
        rejectedPacket(new AnomalySnapshot(500, Map.of("ice", 1.0), 401, "", 0, List.of()),
                "The packet codec rejects accumulation time beyond twenty seconds");
        rejectedPacket(new AnomalySnapshot(500, Map.of(), 0, "ice", 101, List.of()),
                "The packet codec rejects a link window longer than five seconds");
        rejectedPacket(new AnomalySnapshot(500, Map.of(), 0, "", 0,
                        List.of(new AnomalyEffects.EffectView("invalid", 10, Double.NaN))),
                "The packet codec rejects non-finite effect intensity");
    }

    private static void rejectedPacket(AnomalySnapshot snapshot, String why) {
        var buffer = new PacketByteBuf(Unpooled.buffer());
        boolean rejected = false;
        try {
            AnomalyNetwork.write(buffer, new AnomalyNetwork.Message(test.getWorld().getRegistryKey().getValue(),
                    1, UUID.randomUUID(), snapshot));
            try { AnomalyNetwork.read(buffer); }
            catch (IllegalArgumentException expected) { rejected = true; }
        } finally { buffer.release(); }
        CHECK.that(rejected, why);
    }

    private static LunarSpiritBoltEntity summonSource(PigEntity victim) {
        var spirit = new LunarSpiritEntity(SscAddon.LUNAR_SPIRIT_ENTITY, test.getWorld());
        spirit.setOwnerUuid(caster.getUuid());
        var source = new SpellAnomalyContext(new Identifier("ssc_addon", "summon_lunar_spirit_bolt"),
                "summon", 1, 25, caster.getUuid(), UUID.randomUUID());
        ((SpellAnomalyHits.SummonedData) spirit).ssca$summonAnomaly(source);
        var bolt = new LunarSpiritBoltEntity(test.getWorld(), spirit);
        ((SpellAnomalyHits.ProjectileData) bolt).ssca$anomaly(source);
        bolt.setPosition(victim.getPos());
        return bolt;
    }

    private static void summonDamage(PigEntity victim, LunarSpiritBoltEntity source, float amount) {
        victim.timeUntilRegen = 0;
        CHECK.that(victim.damage(SpellDamageSource.of(test.getWorld().getDamageSources(), source, caster), amount),
                "Real summon-source damage is accepted");
        AnomalyEffects.drain();
    }

    private static void baseEffects() {
        CHECK.that(AnomalyEffects.registeredBases().size() == 7, "All seven base effects are registered");
        CHECK.that(AnomalyEffects.registeredReactions().size() == 21, "All twenty-one unordered pairs are registered");

        var target = pig(center);
        AnomalyEffects.activate("fire", target, context("fire", 500));
        CHECK.near(target.getHealth(), 192, "Fire anomaly adds exactly eight unarmoured fire damage");
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200));
        AnomalyEffects.activate("fire", target, context("fire", 500));
        CHECK.near(target.getHealth(), 192, "Fire anomaly retains vanilla fire resistance");
        resetFixtures();

        target = pig(center);
        AnomalyEffects.activate("ice", target, context("ice", 500));
        status(target, SscAddon.FROZEN, 0, 100, "Ice enclosure");
        CHECK.that(target.damage(test.getWorld().getDamageSources().generic(), 1), "An actual subsequent hit succeeds");
        status(target, SscAddon.FROZEN_RECOVERY, 0, 60, "Broken ice recovery");
        CHECK.that(!target.hasStatusEffect(SscAddon.FROZEN), "The successful later hit shatters existing ice");
        resetFixtures();

        target = pig(center);
        target.setHealth(100);
        target.setAbsorptionAmount(5);
        AnomalyEffects.activate("lunar", target, context("lunar", 500));
        status(target, AnomalyEffects.LUNAR_ECLIPSE, 0, 120, "Lunar healing suppression");
        target.heal(10);
        CHECK.near(target.getHealth(), 105, "Lunar anomaly halves a real healing call");
        CHECK.near(target.getAbsorptionAmount(), 5, "Lunar anomaly leaves absorption unchanged");
        CHECK.near(target.getMaxHealth(), 200, "Lunar anomaly leaves maximum health unchanged");
        resetFixtures();

        var attacker = pig(center);
        var victim = pig(center.add(2, 0, 0));
        AnomalyEffects.activate("curse", attacker, context("curse", 500));
        status(attacker, AnomalyEffects.CURSE_WEAKNESS, 0, 120, "Curse outgoing weakness");
        CHECK.that(victim.damage(SpellDamageSource.of(test.getWorld().getDamageSources(), attacker), 10),
                "A cursed real living source can deal damage");
        CHECK.near(victim.getHealth(), 192, "Curse reduces actual outgoing damage by twenty percent");
        resetFixtures();

        target = pig(center);
        var bolt = summonSource(target);
        AnomalyEffects.activate("summon", target, context("summon", 500));
        status(target, AnomalyEffects.SUMMON_MARK, 0, 160, "Summon vulnerability");
        summonDamage(target, bolt, 8);
        CHECK.near(target.getHealth(), 190, "Summon mark adds twenty-five percent to a real summon source");
        target.timeUntilRegen = 0;
        CHECK.that(target.damage(SpellDamageSource.of(test.getWorld().getDamageSources(), caster), 8),
                "An ordinary caster can still hit a summon-marked target");
        CHECK.near(target.getHealth(), 182, "Summon vulnerability does not boost ordinary caster hits");
        resetFixtures();

        target = pig(center);
        AnomalyEffects.activate("void", target, context("void", 500));
        status(target, AnomalyEffects.VOID_EROSION, 0, 120, "Void spell vulnerability");
        CHECK.that(target.damage(SpellDamageSource.of(test.getWorld().getDamageSources(), caster), 8),
                "A real named-spell damage source is accepted");
        CHECK.near(target.getHealth(), 190.8, "Void adds fifteen percent to actual spell damage");
        target.timeUntilRegen = 0;
        CHECK.that(target.damage(test.getWorld().getDamageSources().generic(), 8), "Ordinary damage is accepted");
        CHECK.near(target.getHealth(), 182.8, "Void vulnerability does not boost ordinary damage");
        resetFixtures();

        target = pig(center);
        double speed = target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        AnomalyEffects.activate("space", target, context("space", 500));
        status(target, AnomalyEffects.SPACE_BIND, 0, 100, "Spatial bind");
        CHECK.near(target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), speed * .7,
                "Spatial bind is exactly thirty-percent slow");
        CHECK.that(AnomalyEffects.teleportBlocked(target), "Spatial bind denies SSCA active teleportation");
        resetFixtures();
    }

    private static void reactionEffects() {
        for (String[] pair : PAIRS) for (boolean reverse : new boolean[]{false, true}) {
            var target = pig(center);
            var peer = pig(center.add(2, 0, 0));
            var protectedTarget = pig(center.add(-2, 0, 0));
            var outside = pig(center.add(6, 0, 0));
            caster.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + protectedTarget.getUuid());
            String first = reverse ? pair[1] : pair[0];
            String second = reverse ? pair[0] : pair[1];
            AnomalyEffects.react(first, second, target, context(second, 500));
            switch (pair[0] + "/" + pair[1]) {
                case "fire/ice" -> {
                    CHECK.near(target.getHealth(), 192, "Frostfire primary one-time damage");
                    CHECK.near(peer.getHealth(), 192, "Frostfire hits an eligible nearby target once");
                    status(target, AnomalyEffects.FROST_SLOW, 0, 100, "Frostfire slow");
                    CHECK.near(target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), .125,
                            "Frostfire slow is exactly half the pig's default speed");
                }
                case "fire/lunar" -> {
                    status(target, AnomalyEffects.LUNAR_ECLIPSE, 1, 120, "Scorched moon");
                    target.setHealth(100);
                    target.heal(8);
                    CHECK.near(target.getHealth(), 102, "Scorched moon permits only one quarter of real healing");
                }
                case "fire/curse" -> {
                    status(target, AnomalyEffects.CURSE_WEAKNESS, 0, 100, "Burning curse");
                    status(peer, AnomalyEffects.CURSE_WEAKNESS, 0, 100, "Burning curse spreads locally");
                }
                case "fire/summon" -> {
                    CHECK.that(AnomalyEffects.budgetSnapshot(target).fireHits() == 3, "Flame spirit starts with exactly three hits");
                    var source = summonSource(target);
                    for (int i = 0; i < 4; i++) summonDamage(target, source, 4);
                    CHECK.near(target.getHealth(), 178, "Flame spirit adds only six damage over four ordinary hits");
                    CHECK.that(AnomalyEffects.budgetSnapshot(target).fireHits() == 0, "Flame spirit cannot exceed its hit budget");
                }
                case "fire/void" -> CHECK.near(target.getHealth(), 196, "Void flame adds four damage once");
                case "fire/space" -> CHECK.that(AnomalyEffects.diagnostics().zones() == 1, "Flame field creates one finite zone");
                case "ice/lunar" -> status(target, AnomalyEffects.LUNAR_ECLIPSE, 0, 180, "Cold moon");
                case "ice/curse" -> status(target, AnomalyEffects.CURSE_WEAKNESS, 2, 120, "Cold curse");
                case "ice/summon" -> {
                    CHECK.that(AnomalyEffects.budgetSnapshot(target).frostHits() == 1, "Frost hunt has one use");
                    var source = summonSource(target);
                    summonDamage(target, source, 4);
                    summonDamage(target, source, 4);
                    CHECK.near(target.getHealth(), 186, "Frost hunt adds only six damage once");
                    CHECK.that(AnomalyEffects.budgetSnapshot(target).frostHits() == 0, "Frost hunt consumes its one use");
                }
                case "ice/void" -> CHECK.near(target.getHealth(), 194, "Shattered frost adds six damage once");
                case "ice/space" -> {
                    status(target, AnomalyEffects.NO_SPRINT, 0, 100, "Ice lock");
                    CHECK.that(AnomalyEffects.sprintBlocked(target), "Ice lock denies sprinting");
                }
                case "lunar/curse" -> {
                    status(target, AnomalyEffects.LUNAR_ECLIPSE, 0, 180, "Dark moon lunar duration");
                    status(target, AnomalyEffects.CURSE_WEAKNESS, 0, 180, "Dark moon curse duration");
                }
                case "lunar/summon" -> {
                    caster.setHealth(5);
                    var source = summonSource(target);
                    for (int i = 0; i < 5; i++) summonDamage(target, source, 2);
                    CHECK.near(caster.getHealth(), 9, "Spirit moon heals its summoner at most four health");
                    CHECK.near(AnomalyEffects.budgetSnapshot(target).healingRemaining(), 0, "Spirit moon healing budget is exhausted");
                }
                case "lunar/void" -> status(target, AnomalyEffects.VOID_EROSION, 1, 120, "Eclipsed moon");
                case "lunar/space" -> {
                    status(target, AnomalyEffects.LUNAR_ECLIPSE, 0, 100, "Moon field primary");
                    status(peer, AnomalyEffects.LUNAR_ECLIPSE, 0, 100, "Moon field nearby target");
                }
                case "curse/summon" -> {
                    status(target, AnomalyEffects.SUMMON_WEAKNESS, 0, 120, "Doomed spirit");
                    var summon = new LunarSpiritEntity(SscAddon.LUNAR_SPIRIT_ENTITY, test.getWorld());
                    summon.setOwnerUuid(caster.getUuid());
                    ((SpellAnomalyHits.SummonedData) summon).ssca$summonAnomaly(context("summon", 25));
                    summon.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
                    summon.setHealth(summon.getMaxHealth());
                    var ordinaryAttacker = pig(center.add(0, 0, -4));
                    float baselineHealth = summon.getHealth();
                    CHECK.that(summon.damage(SpellDamageSource.of(test.getWorld().getDamageSources(), ordinaryAttacker), 8),
                            "An unmarked real attacker establishes the same summon's damage baseline");
                    float baselineDamage = baselineHealth - summon.getHealth();
                    CHECK.that(baselineDamage > 0 && summon.isAlive(), "The unmarked baseline is real nonlethal health loss");
                    summon.setHealth(summon.getMaxHealth());
                    summon.timeUntilRegen = 0;
                    float summonBefore = summon.getHealth();
                    CHECK.that(summon.damage(SpellDamageSource.of(test.getWorld().getDamageSources(), target), 8),
                            "A marked attacker can hit a real summon entity");
                    CHECK.near(summonBefore - summon.getHealth(), baselineDamage * .5,
                            "Doomed spirit halves the same summon's actual unmarked damage baseline");
                    CHECK.that(peer.damage(SpellDamageSource.of(test.getWorld().getDamageSources(), target), 8),
                            "The same marked attacker can still hit an ordinary target");
                    CHECK.near(peer.getHealth(), 192, "Summon-specific weakness leaves ordinary targets unchanged");
                }
                case "curse/void" -> {
                    status(target, AnomalyEffects.CURSE_WEAKNESS, 1, 120, "Withering outgoing weakness");
                    status(target, AnomalyEffects.VOID_EROSION, 1, 120, "Withering spell vulnerability");
                }
                case "curse/space" -> {
                    status(peer, AnomalyEffects.CURSE_WEAKNESS, 0, 100, "Forbidden field spreads curse");
                    status(peer, AnomalyEffects.NO_SPRINT, 0, 100, "Forbidden field spreads sprint denial");
                }
                case "summon/void" -> status(target, AnomalyEffects.SUMMON_MARK, 1, 160, "Devouring spirit");
                case "summon/space" -> status(peer, AnomalyEffects.SUMMON_MARK, 0, 160, "Spirit net spreads summon mark");
                case "void/space" -> status(peer, AnomalyEffects.VOID_EROSION, 0, 100, "Rift spreads spell vulnerability");
                default -> throw new AssertionError("Unspecified reaction fixture");
            }
            CHECK.near(protectedTarget.getHealth(), 200, "Area reaction preserves a protected target's health");
            CHECK.that(protectedTarget.getStatusEffects().isEmpty(), "Area reaction never adds statuses to a protected target");
            CHECK.near(outside.getHealth(), 200, "Area reaction cannot damage beyond its spherical radius");
            CHECK.that(outside.getStatusEffects().isEmpty(), "Area reaction cannot apply statuses beyond its radius");
            caster.removeScoreboardTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + protectedTarget.getUuid());
            resetFixtures();
        }
    }

    /** Every pair also traverses actual accepted spell damage, accumulation and threshold dispatch. */
    private static void integratedReactions() {
        for (String[] pair : PAIRS) for (boolean reverse : new boolean[]{false, true}) {
            var target = pig(center);
            var peer = pig(center.add(2, 0, 0));
            var protectedTarget = pig(center.add(-2, 0, 0));
            var outside = pig(center.add(6, 0, 0));
            String protectedTag = WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + protectedTarget.getUuid();
            caster.addCommandTag(protectedTag);
            String first = reverse ? pair[1] : pair[0];
            String second = reverse ? pair[0] : pair[1];
            String label = first + " -> " + second;
            AnomalyManager.resetMetrics();

            actualHit(target, first, 250);
            CHECK.near(value(target, first), 250, label + ": first actual hit retains a partial meter");
            CHECK.that(AnomalyManager.metrics().triggers() == 0, label + ": partial meter has no base effect");
            float beforeBase = actualHit(target, first, 250);
            CHECK.near(value(target, first), 0, label + ": first threshold consumes its own meter");
            CHECK.that(AnomalyManager.metrics().triggers() == 1 && AnomalyManager.metrics().reactions() == 0,
                    label + ": the first real threshold activates one base without a reaction");
            if (first.equals("fire")) {
                CHECK.near(target.getHealth(), beforeBase - 8, label + ": first fire base deals eight damage");
            } else if (first.equals("ice")) {
                status(target, SscAddon.FROZEN, 0, 100, label + ": first ice base freezes for five seconds");
            } else {
                CHECK.that(target.hasStatusEffect(baseMarker(first)), label + ": first base marker is actually active");
            }

            actualHit(target, second, 250);
            CHECK.near(value(target, second), 250, label + ": another element still needs its own full meter");
            CHECK.that(AnomalyManager.metrics().triggers() == 1 && AnomalyManager.metrics().reactions() == 0,
                    label + ": a partial second element cannot react early");
            float beforeSecondBase = actualHit(target, second, 250);
            var metrics = AnomalyManager.metrics();
            CHECK.that(metrics.enqueued() == 4 && metrics.processed() == 4 && metrics.pending() == 0,
                    label + ": four original hits settle once without recursive accumulation");
            CHECK.that(metrics.triggers() == 2 && metrics.reactions() == 1,
                    label + ": the second base and exactly one unordered reaction both activate");
            CHECK.that(AnomalyManager.snapshot(target).values().isEmpty(),
                    label + ": both full meters are consumed without overflow");
            if (!second.equals("fire") && !second.equals("ice")) {
                CHECK.that(target.hasStatusEffect(baseMarker(second)),
                        label + ": the second single-element effect remains alongside its reaction");
            }
            double additional = second.equals("fire") ? (first.equals("void") ? 9.2 : 8) : 0;
            additional += switch (pair[0] + "/" + pair[1]) {
                case "fire/ice" -> 8;
                case "fire/void" -> 4.6; // The real void base also amplifies reaction damage by fifteen percent.
                case "ice/void" -> 6.9;
                default -> 0;
            };
            CHECK.near(target.getHealth(), beforeSecondBase - additional,
                    label + ": second-base damage and reaction damage settle in the declared order");
            integratedSignature(pair, target, peer, label);
            CHECK.near(peer.getHealth(), pair[0].equals("fire") && pair[1].equals("ice") ? 192 : 200,
                    label + ": only frostfire deals immediate area damage");
            CHECK.near(protectedTarget.getHealth(), 200, label + ": the complete chain respects protected health");
            CHECK.that(protectedTarget.getStatusEffects().isEmpty(), label + ": the complete chain respects protected statuses");
            CHECK.near(outside.getHealth(), 200, label + ": the complete chain cannot damage outside its area");
            CHECK.that(outside.getStatusEffects().isEmpty(), label + ": the complete chain cannot mark outside its area");
            caster.removeScoreboardTag(protectedTag);
            resetFixtures();
        }
    }

    /** Returns health after the original hit but before the deferred base/reaction effects. */
    private static float actualHit(PigEntity target, String element, double contribution) {
        target.timeUntilRegen = 0;
        CHECK.that(SpellHitHelper.hitRaw(caster, target, 1, 0, context(element, contribution)) == SpellHitHelper.HitResult.HIT,
                "An actual " + element + " spell hit is accepted");
        float beforeEffects = target.getHealth();
        AnomalyManager.drain(test.getWorld().getServer());
        return beforeEffects;
    }

    private static StatusEffect baseMarker(String element) {
        return switch (element) {
            case "lunar" -> AnomalyEffects.LUNAR_ECLIPSE;
            case "curse" -> AnomalyEffects.CURSE_WEAKNESS;
            case "summon" -> AnomalyEffects.SUMMON_MARK;
            case "void" -> AnomalyEffects.VOID_EROSION;
            case "space" -> AnomalyEffects.SPACE_BIND;
            default -> throw new AssertionError("This base has no persistent marker: " + element);
        };
    }

    private static void integratedSignature(String[] pair, PigEntity target, PigEntity peer, String label) {
        switch (pair[0] + "/" + pair[1]) {
            case "fire/ice" -> status(peer, AnomalyEffects.FROST_SLOW, 0, 100, label + ": frostfire area slow");
            case "fire/lunar" -> status(target, AnomalyEffects.LUNAR_ECLIPSE, 1, 120, label + ": scorched moon");
            case "fire/curse" -> status(peer, AnomalyEffects.CURSE_WEAKNESS, 0, 100, label + ": burning curse spreads");
            case "fire/summon" -> CHECK.that(AnomalyEffects.budgetSnapshot(target).fireHits() == 3,
                    label + ": flame spirit has its exact three-hit budget");
            case "fire/void", "ice/void" -> status(target, AnomalyEffects.VOID_EROSION, 0, 120,
                    label + ": void base remains after the additional reaction damage");
            case "fire/space" -> CHECK.that(AnomalyEffects.diagnostics().zones() == 1,
                    label + ": exactly one finite fire zone is created");
            case "ice/lunar" -> status(target, AnomalyEffects.LUNAR_ECLIPSE, 0, 180, label + ": cold moon extends lunar");
            case "ice/curse" -> status(target, AnomalyEffects.CURSE_WEAKNESS, 2, 120, label + ": cold curse strengthens weakness");
            case "ice/summon" -> CHECK.that(AnomalyEffects.budgetSnapshot(target).frostHits() == 1,
                    label + ": frost hunt has one follow-up");
            case "ice/space" -> status(target, AnomalyEffects.NO_SPRINT, 0, 100, label + ": ice lock prevents sprinting");
            case "lunar/curse" -> {
                status(target, AnomalyEffects.LUNAR_ECLIPSE, 0, 180, label + ": dark moon lunar extension");
                status(target, AnomalyEffects.CURSE_WEAKNESS, 0, 180, label + ": dark moon curse extension");
            }
            case "lunar/summon" -> CHECK.near(AnomalyEffects.budgetSnapshot(target).healingRemaining(), 4,
                    label + ": spirit moon starts with four healing points");
            case "lunar/void" -> status(target, AnomalyEffects.VOID_EROSION, 1, 120, label + ": eclipsed moon strengthens erosion");
            case "lunar/space" -> status(peer, AnomalyEffects.LUNAR_ECLIPSE, 0, 100, label + ": moon field reaches peers");
            case "curse/summon" -> status(target, AnomalyEffects.SUMMON_WEAKNESS, 0, 120, label + ": doomed spirit");
            case "curse/void" -> {
                status(target, AnomalyEffects.CURSE_WEAKNESS, 1, 120, label + ": withering weakness");
                status(target, AnomalyEffects.VOID_EROSION, 1, 120, label + ": withering vulnerability");
            }
            case "curse/space" -> {
                status(peer, AnomalyEffects.CURSE_WEAKNESS, 0, 100, label + ": forbidden field weakness");
                status(peer, AnomalyEffects.NO_SPRINT, 0, 100, label + ": forbidden field sprint denial");
            }
            case "summon/void" -> status(target, AnomalyEffects.SUMMON_MARK, 1, 160, label + ": devouring spirit");
            case "summon/space" -> status(peer, AnomalyEffects.SUMMON_MARK, 0, 160, label + ": spirit net reaches peers");
            case "void/space" -> status(peer, AnomalyEffects.VOID_EROSION, 0, 100, label + ": rift reaches peers");
            default -> throw new AssertionError("Missing integrated reaction assertion: " + label);
        }
    }

    private static void meterAndOrdering() {
        var target = pig(center);
        AnomalyManager.resetMetrics();
        add(target, "curse", 499);
        CHECK.near(value(target, "curse"), 499, "Below threshold remains accumulated");
        CHECK.that(AnomalyManager.metrics().triggers() == 0, "Below threshold cannot activate");
        add(target, "ice", 120);
        var display = AnomalyManager.snapshot(target);
        add(target, "curse", 1);
        CHECK.near(value(target, "curse"), 0, "Exactly five hundred consumes only that element");
        CHECK.near(value(target, "ice"), 120, "Trigger consumption retains another element");
        CHECK.that(AnomalyManager.metrics().triggers() == 1, "Exactly five hundred triggers once");
        CHECK.near(display.values().get("curse"), 499, "A previously exported display snapshot cannot be mutated by a new hit");
        add(target, "void", 10000);
        CHECK.near(value(target, "void"), 0, "Excess contribution is capped and consumed without carrying overflow");
        CHECK.that(AnomalyManager.metrics().triggers() == 2, "Excess contribution cannot trigger several times");
        resetFixtures();

        target = pig(center);
        AnomalyManager.resetMetrics();
        var payload = context("fire", 500);
        CHECK.that(SpellHitHelper.hitRaw(caster, target, 3, 0, payload) == SpellHitHelper.HitResult.HIT,
                "Real original spell damage succeeds before queued reaction damage");
        CHECK.near(target.getHealth(), 197, "Original hit is measured before the anomaly queue drains");
        AnomalyManager.drain(test.getWorld().getServer());
        CHECK.near(target.getHealth(), 189, "Additional fire damage bypasses the original hit's cooldown without replacing it");
        CHECK.that(AnomalyManager.metrics().processed() == 1 && AnomalyManager.metrics().pending() == 0,
                "Reaction damage cannot enqueue its own anomaly recursively");
        resetFixtures();

        target = pig(center);
        CHECK.that(SpellHitHelper.hitRaw(caster, target, 2, 0, context("ice", 500)) == SpellHitHelper.HitResult.HIT,
                "An original ice buildup hit succeeds");
        AnomalyManager.drain(test.getWorld().getServer());
        status(target, SscAddon.FROZEN, 0, 100, "A single original buildup hit cannot shatter its own deferred freeze");
        CHECK.that(!target.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "The first ice-triggering hit does not create recovery slow");
        resetFixtures();

        target = pig(center);
        double sameTickSpeed = target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        AnomalyManager.resetMetrics();
        CHECK.that(SpellHitHelper.hitRaw(caster, target, 2, 0, context("ice", 500)) == SpellHitHelper.HitResult.HIT,
                "First same-tick ice buildup hit is real damage");
        target.timeUntilRegen = 0;
        CHECK.that(SpellHitHelper.hitRaw(caster, target, 1, 0, context("fire", 100)) == SpellHitHelper.HitResult.HIT,
                "Second same-tick non-triggering fire hit is real damage");
        AnomalyManager.drain(test.getWorld().getServer());
        CHECK.that(!target.hasStatusEffect(SscAddon.FROZEN), "A later same-tick hit shatters the logically earlier pending ice activation");
        status(target, SscAddon.FROZEN_RECOVERY, 0, 60, "Same-tick shattering retains the exact recovery duration");
        CHECK.near(target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), sameTickSpeed * .75,
                "Same-tick shattering grants exact twenty-five-percent slow");
        CHECK.near(value(target, "fire"), 100, "The later non-triggering fire hit still contributes exactly once");
        CHECK.that(AnomalyManager.metrics().enqueued() == 2 && AnomalyManager.metrics().processed() == 2,
                "Internal damage-order observations do not become extra spell hits");
        resetFixtures();

        target = pig(center);
        AnomalyManager.resetMetrics();
        CHECK.that(SpellHitHelper.hitRaw(caster, target, 2, 0, context("ice", 500)) == SpellHitHelper.HitResult.HIT,
                "Ice buildup can precede an ordinary physical hit in the same server tick");
        target.timeUntilRegen = 0;
        CHECK.that(target.damage(test.getWorld().getDamageSources().generic(), 1),
                "The subsequent ordinary damage call is accepted before the queue drains");
        AnomalyManager.drain(test.getWorld().getServer());
        CHECK.that(!target.hasStatusEffect(SscAddon.FROZEN) && target.hasStatusEffect(SscAddon.FROZEN_RECOVERY),
                "Same-tick ordinary physical damage also shatters an earlier pending ice activation");
        CHECK.that(AnomalyManager.metrics().enqueued() == 1 && AnomalyManager.metrics().processed() == 1
                        && AnomalyManager.snapshot(target).values().isEmpty(),
                "Ordinary physical damage never adds an elemental hit or accumulation");
        resetFixtures();

        target = pig(center);
        double reactionShatterSpeed = target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        AnomalyManager.resetMetrics();
        CHECK.that(SpellHitHelper.hitRaw(caster, target, 2, 0, context("ice", 500)) == SpellHitHelper.HitResult.HIT,
                "Ice buildup can precede an old reaction pulse in the same tick");
        CHECK.that(AnomalyEffects.reactionDamage(target, context("fire", 500), 1, true),
                "An old reaction pulse is accepted before pending ice drains");
        AnomalyManager.drain(test.getWorld().getServer());
        CHECK.that(!target.hasStatusEffect(SscAddon.FROZEN), "Old reaction damage shatters logically earlier pending ice");
        status(target, SscAddon.FROZEN_RECOVERY, 0, 60, "Old reaction shatter retains sixty recovery ticks");
        CHECK.near(target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), reactionShatterSpeed * .75,
                "Old reaction shatter applies exact twenty-five-percent slow");
        CHECK.that(AnomalyManager.metrics().enqueued() == 1 && AnomalyManager.metrics().processed() == 1
                        && AnomalyManager.snapshot(target).values().isEmpty()
                        && AnomalyManager.snapshot(target).remainingTicks() == 0,
                "Old reaction observation never adds accumulation or creates a new meter timeout");
        resetFixtures();

        target = pig(center);
        AnomalyManager.resetMetrics();
        double speed = target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        add(target, "ice", 500);
        CHECK.that(target.hasStatusEffect(SscAddon.FROZEN), "Ice reached through the actual meter freezes its target");
        target.timeUntilRegen = 0;
        CHECK.that(target.damage(test.getWorld().getDamageSources().generic(), 1), "A later real hit breaks meter-triggered ice");
        CHECK.that(!target.hasStatusEffect(SscAddon.FROZEN), "Ice is already broken before the second element activates");
        add(target, "fire", 500);
        CHECK.that(AnomalyManager.metrics().reactions() == 1, "Broken ice still qualifies for the five-second reaction window");
        CHECK.near(target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), speed * .5,
                "Frostfire's fifty-percent slow takes precedence over twenty-five-percent recovery");
        CHECK.that(!target.hasStatusEffect(SscAddon.FROZEN), "Frostfire damage shatters ice without secretly reapplying it");
        resetFixtures();

        target = pig(center);
        AnomalyManager.resetMetrics();
        add(target, "fire", 500);
        add(target, "fire", 500);
        CHECK.that(AnomalyManager.metrics().reactions() == 0, "Repeating the same element cannot react with itself");
        add(target, "curse", 500);
        add(target, "ice", 500);
        CHECK.that(AnomalyManager.metrics().reactions() == 2, "Every new trigger resolves at most one nearest different-element pair");
        status(target, AnomalyEffects.CURSE_WEAKNESS, 2, 120, "The nearest curse produces cold curse rather than an older fire pair");
        CHECK.that(!target.hasStatusEffect(AnomalyEffects.FROST_SLOW), "An older fire activation does not create a second simultaneous frostfire pair");
        resetFixtures();

        target = pig(center);
        UUID secondCaster = UUID.randomUUID();
        AnomalyManager.enqueue(context("ice", 250), target);
        AnomalyManager.enqueue(new SpellAnomalyContext(new Identifier("ssc_addon", "test_ice"), "ice", 1,
                250, secondCaster, UUID.randomUUID()), target);
        AnomalyManager.drain(test.getWorld().getServer());
        CHECK.that(target.hasStatusEffect(SscAddon.FROZEN) && value(target, "ice") == 0,
                "Different caster identities share the same target's accumulation");
        resetFixtures();

        target = pig(center);
        add(target, "ice", 100);
        UUID previousIdentity = target.getUuid();
        target.discard();
        var replacement = new net.minecraft.entity.passive.PigEntity(net.minecraft.entity.EntityType.PIG, test.getWorld());
        replacement.setUuid(previousIdentity);
        replacement.setAiDisabled(true);
        replacement.setNoGravity(true);
        replacement.refreshPositionAndAngles(center.x, center.y, center.z, 0, 0);
        CHECK.that(test.getWorld().spawnEntity(replacement), "A new live instance can reuse the old unloaded UUID");
        FIXTURES.add(replacement);
        add(replacement, "ice", 75);
        CHECK.near(value(replacement, "ice"), 75, "A replacement instance never inherits an unloaded entity's meter");
        AnomalyManager.clear(target);
        CHECK.near(value(replacement, "ice"), 75, "Late cleanup of an old instance cannot clear a new instance's meter");
        resetFixtures();
    }

    private static void timedBoundaries() {
        var expiry = pig(center);
        var refreshed = pig(center.add(0, 12, 0));
        var earlyLink = pig(center.add(0, 24, 0));
        var expiredLink = pig(center.add(0, 36, 0));
        add(expiry, "ice", 120);
        add(refreshed, "ice", 120);
        add(earlyLink, "ice", 500);
        add(expiredLink, "ice", 500);
        var fireField = pig(center.add(0, 48, 0));
        var firePeer = pig(fireField.getPos().add(2.9, 0, 0));
        var fireOutside = pig(fireField.getPos().add(3.1, 0, 0));
        var boxCorner = pig(fireField.getPos().add(2.5, 2.5, 0));
        AnomalyEffects.react("fire", "space", fireField, context("space", 500));
        var overlappingField = pig(fireField.getPos().add(-.1, 0, 0));
        AnomalyEffects.react("fire", "space", overlappingField, context("space", 500));
        var slowTarget = pig(center.add(0, 60, 0));
        double slowBase = slowTarget.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        AnomalyEffects.activate("ice", slowTarget, context("ice", 500));
        CHECK.that(slowTarget.damage(test.getWorld().getDamageSources().generic(), 1), "A hit creates the weaker recovery slow");
        AnomalyEffects.react("fire", "ice", slowTarget, context("fire", 500));
        CHECK.near(slowTarget.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), slowBase * .5,
                "The stronger reaction slow immediately replaces weaker recovery slow");
        var purified = pig(center.add(0, 72, 0));
        AnomalyEffects.react("fire", "summon", purified, context("summon", 500));
        AnomalyEffects.react("fire", "space", purified, context("space", 500));
        purified.addStatusEffect(new StatusEffectInstance(SscAddon.PURIFIED, 40));
        long beforeEmpty = AnomalyManager.metrics().tracked();
        var empty = pig(center.add(0, 84, 0));
        add(empty, "space", 0);
        CHECK.that(AnomalyManager.metrics().tracked() == beforeEmpty, "A zero-contribution hit without previous state allocates no target meter");

        // Use actual hits for the radius/duration acceptance, instead of calling the executor directly.
        var frostOrigin = pig(center.add(0, 108, 0));
        var frostBoundary = pig(frostOrigin.getPos().add(5, 0, 0));
        var frostInside = pig(frostOrigin.getPos().add(4.99, 0, 0));
        var frostVertical = pig(frostOrigin.getPos().add(0, 4.99, 0));
        var frostOutside = pig(frostOrigin.getPos().add(5.01, 0, 0));
        var frostCorner = pig(frostOrigin.getPos().add(4, 4, 0));
        actualHit(frostOrigin, "ice", 500);
        actualHit(frostOrigin, "fire", 500);
        List<PigEntity> frostVictims = List.of(frostBoundary, frostInside, frostVertical);
        for (PigEntity victim : frostVictims) {
            CHECK.near(victim.getHealth(), 192, "Actual frostfire reaches the inclusive five-block sphere once");
            status(victim, AnomalyEffects.FROST_SLOW, 0, 100, "Actual frostfire has a five-second slow");
            CHECK.near(victim.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), .125,
                    "Actual frostfire halves speed independently of vanilla slowness rounding");
        }
        CHECK.near(frostOutside.getHealth(), 200, "Actual frostfire excludes a point just outside five blocks");
        CHECK.near(frostCorner.getHealth(), 200, "Actual frostfire excludes an in-box point outside the five-block sphere");
        CHECK.that(frostOutside.getStatusEffects().isEmpty() && frostCorner.getStatusEffects().isEmpty(),
                "Outside targets receive neither damage nor slow");
        long queriesBefore = AnomalyEffects.diagnostics().localQueries();
        AnomalyProbeSupport.later(test, 98, () -> {
            for (PigEntity victim : frostVictims) {
                CHECK.near(victim.getHealth(), 192, "Frostfire's five-second slow never repeats its damage");
                CHECK.that(victim.hasStatusEffect(AnomalyEffects.FROST_SLOW), "Frostfire slow remains before five seconds");
                CHECK.near(victim.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), .125,
                        "Frostfire retains the exact fifty-percent slow until expiry");
            }
        });
        AnomalyProbeSupport.later(test, 102, () -> {
            for (PigEntity victim : frostVictims) {
                CHECK.near(victim.getHealth(), 192, "Frostfire causes exactly one eight-point hit over its full lifetime");
                CHECK.that(!victim.hasStatusEffect(AnomalyEffects.FROST_SLOW), "Frostfire slow expires after five seconds");
                CHECK.near(victim.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), .25,
                        "Frostfire expiry restores the original movement speed");
            }
        });

        AnomalyProbeSupport.later(test, 2, () -> {
            CHECK.that(!purified.hasStatusEffect(AnomalyEffects.FLAME_SPIRIT)
                            && !purified.hasStatusEffect(AnomalyEffects.FIRE_ZONE),
                    "Actual purification removes both proc and field markers");
            CHECK.that(AnomalyEffects.budgetSnapshot(purified).fireHits() == 0,
                    "Purification invalidates the finite summon proc budget");
        });
        AnomalyProbeSupport.later(test, 19, () -> CHECK.near(fireField.getHealth(), 200,
                "A fire field does not deal its first periodic hit before twenty ticks"));
        for (int pulse = 1; pulse <= 5; pulse++) {
            int count = pulse;
            AnomalyProbeSupport.later(test, pulse * 20L + 1, () -> {
                CHECK.near(fireField.getHealth(), 200 - count, "Fire field primary has exactly the scheduled pulse damage");
                CHECK.near(firePeer.getHealth(), 200 - count, "Fire field reaches a target within the exact three-block sphere");
                CHECK.near(fireOutside.getHealth(), 200, "Fire field excludes a target just beyond its radius");
                CHECK.near(boxCorner.getHealth(), 200, "Fire field excludes a box corner outside the spherical radius");
                if (count < 5) CHECK.that(AnomalyEffects.diagnostics().localQueries() == queriesBefore + count,
                        "Overlapping fields in one cell reuse one local query for the same pulse tick");
            });
        }
        AnomalyProbeSupport.later(test, 62, () -> {
            CHECK.that(!slowTarget.hasStatusEffect(SscAddon.FROZEN_RECOVERY), "The weaker recovery slow expires after sixty ticks");
            CHECK.near(slowTarget.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), slowBase * .5,
                    "Removing the weaker slow cannot remove the stronger active reaction slow");
            AnomalyEffects.activate("space", slowTarget, context("space", 500));
            CHECK.near(slowTarget.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), slowBase * .5,
                    "Applying a weaker spatial slow cannot overwrite fifty-percent frostfire slow");
        });
        AnomalyProbeSupport.later(test, 103, () -> {
            CHECK.near(slowTarget.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), slowBase * .7,
                    "When the stronger slow expires the remaining weaker spatial slow is restored");
            CHECK.that(AnomalyEffects.diagnostics().zones() == 0, "All five field pulses end without retaining a zone");
            CHECK.near(purified.getHealth(), 200, "A purified field never emits a delayed periodic hit");
        });
        AnomalyProbeSupport.later(test, 164, () -> CHECK.near(
                slowTarget.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), slowBase,
                "After all slow sources expire the original speed is restored"));

        AnomalyProbeSupport.later(test, 99, () -> {
            long before = AnomalyManager.metrics().reactions();
            add(earlyLink, "fire", 500);
            CHECK.that(AnomalyManager.metrics().reactions() == before + 1, "Tick ninety-nine remains inside the five-second link window");
        });
        AnomalyProbeSupport.later(test, 100, () -> {
            long before = AnomalyManager.metrics().reactions();
            add(expiredLink, "fire", 500);
            CHECK.that(AnomalyManager.metrics().reactions() == before, "Tick one hundred is outside the expired link window");
        });
        AnomalyProbeSupport.later(test, 399, () -> {
            CHECK.near(value(expiry, "ice"), 120, "Accumulation does not decay during the first three hundred ninety-nine ticks");
            CHECK.that(AnomalyManager.snapshot(expiry).remainingTicks() == 1, "The shared expiry exposes its last retained tick");
            expiry.timeUntilRegen = 0;
            CHECK.that(expiry.damage(test.getWorld().getDamageSources().generic(), 1), "An ordinary physical hit near expiry succeeds");
            CHECK.that(AnomalyManager.snapshot(expiry).remainingTicks() == 1,
                    "An ordinary physical hit does not refresh the elemental accumulation countdown");
            add(refreshed, "space", 0);
            CHECK.that(AnomalyManager.snapshot(refreshed).remainingTicks() == 400, "A different zero-contribution element resets the full twenty-second timer");
        });
        AnomalyProbeSupport.later(test, 400, () -> {
            CHECK.that(AnomalyManager.snapshot(expiry).values().isEmpty(), "Tick four hundred clears all expired accumulation directly");
            CHECK.near(value(refreshed, "ice"), 120, "A cross-element refresh retains the older ice accumulation");
        });
        AnomalyProbeSupport.later(test, 798, () -> {
            CHECK.near(value(refreshed, "ice"), 120, "The refreshed timer retains accumulation until its own final tick");
            CHECK.that(AnomalyManager.snapshot(refreshed).remainingTicks() == 1, "Refreshed shared expiry retains the exact remaining tick");
        });
        AnomalyProbeSupport.later(test, 799, () -> CHECK.that(AnomalyManager.snapshot(refreshed).values().isEmpty(),
                "The refreshed timer also clears directly at its own four hundredth tick"));
        AnomalyProbeSupport.later(test, 802, () -> {
            resetFixtures();
            CHECK.that(AnomalyManager.metrics().tracked() == 0 && AnomalyManager.metrics().pending() == 0,
                    "Final cleanup retains no meter or hit queue");
            CHECK.that(AnomalyEffects.diagnostics().activeTargets() == 0
                            && AnomalyEffects.diagnostics().zones() == 0
                            && AnomalyEffects.diagnostics().queuedProcs() == 0,
                    "Final cleanup retains no active effect, area or proc budget");
            AnomalyManager.setEnabledForTesting(previousEnabled);
            AnomalyProbeSupport.report("anomaly-system-world-result.json", Map.of("checks", CHECK.count,
                    "real_server_world", true, "base_effects", 7, "unordered_reactions", 21,
                    "reaction_orders_tested", 42, "real_hit_reaction_orders_tested", 42,
                    "frostfire_radius_duration_verified", true, "client_rendering_or_network_multiplayer_tested", false));
            System.out.println("ANOMALY_SYSTEM_WORLD_PASS checks=" + CHECK.count
                    + " (seven base effects, forty-two executor and real-hit reaction orders, exact five-block/five-second frostfire, exact timers, shared target state and cleanup; client/network multiplayer NOT tested)");
            test.complete();
        });
    }
}
