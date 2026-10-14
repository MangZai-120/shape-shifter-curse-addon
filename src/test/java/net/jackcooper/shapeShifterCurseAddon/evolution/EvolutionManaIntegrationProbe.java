package net.jackcooper.shapeShifterCurseAddon.evolution;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import io.github.apace100.apoli.power.ModifyDamageTakenPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.power.factory.condition.EntityConditions;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.apoli.util.modifier.ModifierOperations;
import io.github.apace100.apoli.util.modifier.ModifierUtil;
import net.jackcooper.shapeShifterCurseAddon.condition.SscAddonConditions;
import net.jackcooper.shapeShifterCurseAddon.power.ConditionalManaAttributePower;
import net.jackcooper.shapeShifterCurseAddon.power.SscAddonPowers;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.additional_power.ManaAttributePower;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.mana.RegManaComponent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Isolated server-player I/O; actual production resources, conditions, modifiers and mana NBT. */
public final class EvolutionManaIntegrationProbe {
    private static final List<String> TALENTS = List.of("spark", "rocket", "buff_immunity", "alchemy", "spirit_vision", "fire_ring");
    private static sun.misc.Unsafe unsafe;
    private static TestWorld world;
    private static int checks;

    public static void run() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        world = allocate(TestWorld.class);
        EntityConditions.register();
        SscAddonConditions.register();
        ModifierOperations.registerAll();
        SscAddonPowers.register();

        TestPlayer player = player();
        Power base = power(ManaAttributePower.createFactory(), json("mana_system").getAsJsonObject("max_mana"), player, "base");
        base.onAdded();
        List<Power> powers = boosts(player);
        for (Power power : powers) power.onAdded();
        near(player.mana.getMaxMana(), 33, "locked talents do not grant max-mana bonuses");
        player.evolution.unlock("mana_system");
        near(player.mana.getMaxMana(), 33, "Mana system baseline is 33");
        double expected = 33;
        for (int i = 0; i < TALENTS.size(); i++) {
            player.evolution.unlock(TALENTS.get(i));
            tick(powers);
            expected += i == TALENTS.size() - 1 ? 20 : 10;
            near(player.mana.getMaxMana(), expected, "unlock applies exactly one bonus: " + TALENTS.get(i));
            tick(powers);
            near(player.mana.getMaxMana(), expected, "repeated ticks do not stack: " + TALENTS.get(i));
        }
        near(expected, 103, "fully unlocked maximum stays 103");

        var stored = player.mana.MaxManaModifierPlayerSide.getModifiers().get(
                new Identifier("my_addon", "upgrade_familiar_fox_spark_mana_boost"));
        tick(powers);
        check(stored == player.mana.MaxManaModifierPlayerSide.getModifiers().get(
                new Identifier("my_addon", "upgrade_familiar_fox_spark_mana_boost")), "stable ticks do not rewrite identical modifiers");

        player.evolution.reset();
        tick(powers);
        near(player.mana.getMaxMana(), 33, "reset revokes every talent bonus");
        player.evolution.unlock("spark");
        tick(powers);
        near(player.mana.getMaxMana(), 43, "revoked talent can be unlocked again");
        powers.get(0).onLost();
        near(player.mana.getMaxMana(), 33, "losing power removes its bonus");
        powers.get(0).onAdded();
        near(player.mana.getMaxMana(), 43, "re-adding unlocked power restores bonus");
        powers.get(0).onRemoved();
        near(player.mana.getMaxMana(), 33, "power removal for reload cleans up");
        Power replacement = boost(player, "spark");
        replacement.onAdded();
        near(player.mana.getMaxMana(), 43, "replacement power preserves exactly one bonus");
        replacement.onAdded();
        near(player.mana.getMaxMana(), 43, "duplicate onAdded is idempotent");

        TestPlayer legacy = player();
        power(ManaAttributePower.createFactory(), json("mana_system").getAsJsonObject("max_mana"), legacy, "base").onAdded();
        for (String talent : TALENTS) power(ManaAttributePower.createFactory(), boostJson(talent), legacy, talent).onAdded();
        near(legacy.mana.getMaxMana(), 103, "old factory reproduces early bonuses with locked nodes");
        NbtCompound oldMana = new NbtCompound();
        legacy.mana.writeToNbt(oldMana);
        legacy.mana.readFromNbt(oldMana);
        for (Power power : boosts(legacy)) power.onAdded();
        near(legacy.mana.getMaxMana(), 33, "loading fixed powers removes persisted locked-node bonuses");

        TestPlayer lateRestore = player();
        List<Power> latePowers = boosts(lateRestore);
        for (Power power : latePowers) power.onAdded();
        lateRestore.mana.readFromNbt(oldMana);
        near(lateRestore.mana.getMaxMana(), 103, "fixture restores old mana after power callbacks");
        tick(latePowers);
        near(lateRestore.mana.getMaxMana(), 33, "next tick repairs late CCA NBT restoration");

        TestPlayer respawn = player();
        respawn.mana.readFromNbt(oldMana);
        respawn.evolution.unlock("spark");
        for (Power power : boosts(respawn)) power.onRespawn();
        near(respawn.mana.getMaxMana(), 43, "respawn reconciles copied modifiers against copied talent state");
        ManaUtils.gainPlayerMana(respawn, 1000);
        near(respawn.mana.getMana(), 43, "mana gain clamps to corrected max");
        var unrelated = new Identifier("other_mod", "mana_bonus");
        ManaUtils.addMaxManaModifier(respawn, unrelated, new ManaUtils.Modifier(0, 1, 7), true);
        for (Power power : boosts(respawn)) power.onRemoved();
        near(respawn.mana.getMaxMana(), 40, "cleanup preserves base and unrelated modifiers");

        TestPlayer shieldPlayer = player();
        power(ManaAttributePower.createFactory(), json("mana_system").getAsJsonObject("max_mana"), shieldPlayer, "base").onAdded();
        var shield = (ModifyDamageTakenPower) power(ModifyDamageTakenPower.createFactory(), json("mana_shield"), shieldPlayer, "shield");
        shieldPlayer.mana.setMana(33);
        near(damage(shieldPlayer, shield, 10), 10, "shield requires Mana-system unlock");
        shieldPlayer.evolution.unlock("mana_system");
        shieldPlayer.mana.setMana(16.49);
        near(damage(shieldPlayer, shield, 10), 10, "shield off below 50 percent mana");
        shieldPlayer.mana.setMana(16.5);
        near(damage(shieldPlayer, shield, 10), 8.5, "shield reduces damage by15percent at exact threshold");
        shieldPlayer.mana.setMana(33);
        near(damage(shieldPlayer, shield, 20), 17, "shield stays15percent at full mana");
        shieldPlayer.evolution.reset();
        near(damage(shieldPlayer, shield, 10), 10, "shield disabled after talent reset");
        System.out.println("Evolution mana integration: " + checks + " checks passed. Live gameplay NOT tested.");
    }

    private static List<Power> boosts(TestPlayer player) {
        List<Power> powers = new ArrayList<>();
        for (String talent : TALENTS) powers.add(boost(player, talent));
        return powers;
    }

    private static Power boost(TestPlayer player, String talent) {
        JsonObject json = boostJson(talent);
        var factory = ApoliRegistries.POWER_FACTORY.get(new Identifier(json.get("type").getAsString()));
        check(factory != null, "resource references registered factory: " + talent);
        Power power = power(factory, json, player, talent);
        check(power instanceof ConditionalManaAttributePower, "resource uses condition-aware power: " + talent);
        check(power.shouldTickWhenInactive(), "inactive power can remove old modifiers: " + talent);
        return power;
    }

    private static JsonObject boostJson(String talent) {
        return talent.equals("buff_immunity") ? json(talent).getAsJsonObject("max_mana_boost") : json(talent + "_mana_boost");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Power power(PowerFactory factory, JsonObject json, TestPlayer player, String id) {
        var instance = factory.read(json);
        var type = new PowerType(new Identifier("my_addon", "evolution_mana_test_" + id), instance);
        return (Power) instance.apply(type, player);
    }

    private static JsonObject json(String name) {
        String path = "/data/my_addon/powers/form_upgrade_familiar_fox_" + name + ".json";
        try (var stream = EvolutionManaIntegrationProbe.class.getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void tick(List<Power> powers) { powers.forEach(Power::tick); }
    private static double damage(TestPlayer player, ModifyDamageTakenPower shield, double amount) {
        return shield.isActive() ? ModifierUtil.applyModifiers(player, shield.getModifiers(), amount) : amount;
    }
    private static void near(double actual, double expected, String label) {
        check(Math.abs(actual - expected) < 1.0e-8, label + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static TestPlayer player() throws InstantiationException {
        TestPlayer player = allocate(TestPlayer.class);
        player.mana = new ManaComponent(player);
        player.evolution = new EvolutionComponent();
        player.components = ComponentContainer.Factory.builder(TestPlayer.class)
                .component(RegManaComponent.MANA, p -> p.mana)
                .component(RegEvolutionComponent.EVOLUTION, p -> p.evolution)
                .build().createContainer(player);
        player.mana.setManaTypeID(net.onixary.shapeShifterCurseFabric.mana.ManaRegistries.FAMILIAR_FOX_MANA);
        return player;
    }
    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws InstantiationException {
        return (T) unsafe.allocateInstance(type);
    }
    public static final class TestWorld extends ServerWorld {
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
    }
    public static final class TestPlayer extends ServerPlayerEntity implements ComponentProvider {
        ManaComponent mana;
        EvolutionComponent evolution;
        ComponentContainer components;
        private TestPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "evolution-mana-test")); }
        @Override public World getWorld() { return world; }
        @Override public ServerWorld getServerWorld() { return world; }
        @Override public ComponentContainer getComponentContainer() { return components; }
        @Override public EntityAttributeInstance getAttributeInstance(EntityAttribute attribute) { return null; }
    }
}
