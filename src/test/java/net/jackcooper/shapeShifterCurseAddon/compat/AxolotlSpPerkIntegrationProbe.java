package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.component.PowerHolderComponentImpl;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.MultiplePowerType;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.power.factory.action.EntityActions;
import io.github.apace100.apoli.power.factory.condition.EntityConditions;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import io.github.apace100.apoli.util.HudRender;
import net.minecraft.entity.LivingEntity;
import net.jackcooper.shapeShifterCurseAddon.ability.VortexChargeManager;
import net.jackcooper.shapeShifterCurseAddon.forms.SscAddonForms;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.config.CommonConfig;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.PerkUtils;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real CCA, Apoli source ownership, SSC unlock loading, applied Mixin and JSON cost actions. */
public final class AxolotlSpPerkIntegrationProbe {
    static sun.misc.Unsafe unsafe;
    static TestWorld world;
    private static int checks;
    private static final Identifier TREE = RegPerks.T_Axolotl3PerkTree;
    private static final PowerFactory<Power> DUMMY = new PowerFactory<>(new Identifier("my_addon", "perk_test"),
            new SerializableData(), data -> Power::new);
    private static final PowerFactory<Power> REFUND_TIMER = new PowerFactory<>(new Identifier("my_addon", "perk_test_refund_timer"),
            new SerializableData().add("cooldown", SerializableDataTypes.INT),
            data -> (type, entity) -> new TestCooldown(type, entity, data.getInt("cooldown")));

    public static void run() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (sun.misc.Unsafe) field.get(null);
        world = allocate(TestWorld.class);
        world.entities = new java.util.HashMap<>();
        world.clock = 100000;
        ShapeShifterCurseFabric.commonConfig = new CommonConfig();
        ShapeShifterCurseFabric.commonConfig.enableInitialForm = false;
        SscAddonForms.register();
        EntityActions.register();
        EntityConditions.register();
        var all = RegPerks.getPerkTree(TREE).getAllPerks();
        check(all.size() == 8, "exactly eight original Perks");
        for (Identifier id : all) {
            var perk = (NormalPerk) RegPerks.getPerk(id);
            for (Identifier power : perk.powerAdd) register(power, json("shape-shifter-curse", power.getPath()));
            for (Identifier power : perk.powerRemove) if (!PowerTypeRegistry.contains(power)) register(power, null);
        }
        register(AxolotlSpPerks.WATER_MAGIC, json("my_addon", AxolotlSpPerks.WATER_MAGIC.getPath()));
        register(AxolotlSpPerks.TIDAL_PULL, json("my_addon", AxolotlSpPerks.TIDAL_PULL.getPath()));
        register(FluorescentAxolotlPerks.WATER_MAGIC, json("my_addon", FluorescentAxolotlPerks.WATER_MAGIC.getPath()));
        register(FluorescentAxolotlPerks.DRY_TOLERANCE, json("my_addon", FluorescentAxolotlPerks.DRY_TOLERANCE.getPath()));
        register(FluorescentAxolotlPerks.MOISTURE_RETURN, json("my_addon", FluorescentAxolotlPerks.MOISTURE_RETURN.getPath()));

        IForm sp = RegPlayerForms.getPlayerForm(FormIdentifiers.AXOLOTL_SP);
        check(sp.getPerkTreeID().equals(TREE), "registered SP form shares the original saved unlocks");
        for (Identifier excluded : List.of(FormIdentifiers.UPGRADE_AXOLOTL, FormIdentifiers.UPGRADE_FAMILIAR_FOX)) {
            check(RegPlayerForms.getPlayerForm(excluded).getPerkTreeID().equals(RegPerks.EMPTY_PERK_TREE),
                    "unapproved form stays unbound: " + excluded);
        }
        TestPlayer player = player();
        player.totalExperience = 1234;
        player.form.formPerkMap.put(TREE, new ArrayList<>(all));
        select(player, RegPlayerForms.AXOLOTL_3);
        check(has(player, original("perks/axolotl_water_magic_efficiency")), "SSC keeps its original water magic effect");
        check(!has(player, AxolotlSpPerks.WATER_MAGIC), "no SP effect on original form");
        select(player, sp);
        check(player.form.formPerkMap.get(TREE).equals(all) && player.totalExperience == 1234,
                "evolution retains purchased Perks without spending experience");
        check(has(player, AxolotlSpPerks.WATER_MAGIC) && has(player, AxolotlSpPerks.WATER_MAGIC_ATTACK),
                "actual onLoad Mixin maps the water magic root and subpower");
        check(!has(player, original("perks/axolotl_water_magic_efficiency"))
                && !has(player, original("perks/axolotl_water_magic_efficiency_explosion")), "original explosion is not reintroduced");
        check(has(player, AxolotlSpPerks.TIDAL_PULL) && !has(player, original("perks/axolotl_tidal_pull")),
                "tidal pull uses the SP key-3 definition");
        check(!has(player, original("form_axolotl_3_sprinting_attack")), "only one wave attack remains");
        check(!has(player, original("perks/axolotl_moisture_return_1"))
                && has(player, original("perks/axolotl_moisture_return_2")), "return II replaces I");
        int count = player.powers.getPowers().size();
        PerkUtils.loadAllPerk(player, TREE);
        check(player.powers.getPowers().size() == count, "reloading Perks is idempotent");

        NbtCompound saved = new NbtCompound();
        player.form.writeToNbt(saved);
        player.form.readFromNbt(saved);
        check(player.form.formPerkMap.get(TREE).equals(all), "real SSC CCA NBT retains the unlock list");
        NbtCompound powerNbt = new NbtCompound();
        player.powers.writeToNbt(powerNbt);
        player.powers.readFromNbt(powerNbt);
        check(AxolotlSpPerks.hasWaterMagicEfficiency(player), "Apoli source/power NBT retains the mapped effect");
        check(VortexChargeManager.airPerHitForHud(player) == 5, "discounted HUD/start threshold is five air");
        checkBurstActions(player);
        checkCosts();
        checkValues();
        checkFluorescentForms(player, all);
        select(player, RegPlayerForms.AXOLOTL_3);
        check(!AxolotlSpPerks.hasWaterMagicEfficiency(player)
                && VortexChargeManager.airPerHitForHud(player) == 8, "switching back restores original cost rules");
        check(has(player, original("perks/axolotl_water_magic_efficiency")), "switching back restores original Perk effect");
        check(player.form.formPerkMap.get(TREE).equals(all), "switching forms never erases source unlocks");
        TestPlayer fresh = player();
        select(fresh, sp);
        check(!has(fresh, AxolotlSpPerks.WATER_MAGIC), "unlearned Perks are not granted by evolution");
        checkFamiliarForms();
        checkSnowFoxForm();
        checkNovaForm();
        checkWindSpiritForm();
        checks += AnubisWolfSpPerkChecks.run();
        System.out.println("Axolotl SP Perk integration: " + checks + " checks passed; live gameplay NOT tested.");
    }

    private static void checkWindSpiritForm() throws Exception {
        Identifier tree = RegPerks.T_Ocelot3PerkTree;
        var all = RegPerks.getPerkTree(tree).getAllPerks();
        check(all.size() == 8, "eight original ocelot purchases");
        for (Identifier id : all) {
            var perk = (NormalPerk) RegPerks.getPerk(id);
            for (Identifier power : perk.powerAdd) register(power, json("shape-shifter-curse", power.getPath()));
            for (Identifier power : perk.powerRemove) if (!PowerTypeRegistry.contains(power)) register(power, null);
        }
        for (Identifier id : List.of(WindSpiritPerks.METABOLISM_1, WindSpiritPerks.METABOLISM_2, WindSpiritPerks.OVERLOAD, WindSpiritPerks.LONG_POUNCE))
            register(id, json("my_addon", id.getPath()));
        TestPlayer player = player();
        IForm form = RegPlayerForms.getPlayerForm(FormIdentifiers.OCELOT_SP);
        check(form.getPerkTreeID().equals(tree), "Wind Spirit shares the ocelot tree");
        player.form.formPerkMap.put(tree, new ArrayList<>(all)); player.totalExperience = 1234;
        select(player, RegPlayerForms.getPlayerForm(original("ocelot_3")));
        check(has(player, original("perks/ocelot_metabolism_1")), "SSC metabolism remains original");
        select(player, form);
        check(player.form.formPerkMap.get(tree).equals(all) && player.totalExperience == 1234, "Wind Spirit inherits all eight without another payment");
        check(WindSpiritPerks.hungerDiscount(player) == 25 && !has(player, WindSpiritPerks.METABOLISM_1), "rank II replaces rank I, total reduction 25 percent");
        check(!has(player, original("perks/ocelot_armor_1")) && has(player, original("perks/ocelot_armor_2")), "armor totals eight, not twelve");
        check(has(player, WindSpiritPerks.LONG_POUNCE) && !has(player, original("perks/ocelot_long_pounce_flight")), "long pounce enhances dash without another active flight");
        for (String suffix : List.of("metabolism_1", "metabolism_2", "metabolic_overload", "long_pounce"))
            check(!has(player, original("perks/ocelot_" + suffix)), "native conflicting effect is absent: " + suffix);
        check(has(player, original("perks/ocelot_ambush")) && has(player, original("perks/ocelot_hungry_pounce")), "unmodified native combat Perks remain");
        var expected = json("shape-shifter-curse", "perks/ocelot_metabolic_overload").deepCopy();
        expected.getAsJsonObject("key").addProperty("key", "key.shape-shifter-curse.active_skill_3");
        expected.addProperty("description", "perk.ssc_addon.wind_spirit.ocelot_metabolic_overload.description");
        check(expected.equals(json("my_addon", WindSpiritPerks.OVERLOAD.getPath())), "overload changes only key and description");
        for (int rank : new int[]{1, 2}) {
            player.form.formPerkMap.put(tree, new ArrayList<>(rank == 1 ? List.of(RegPerks.P_OcelotMetabolism_1)
                    : List.of(RegPerks.P_OcelotMetabolism_1, RegPerks.P_OcelotMetabolism_2)));
            select(player, form);
            int discount = rank == 1 ? 15 : 25;
            player.hunger = new net.minecraft.entity.player.HungerManager();
            ((WindSpiritHungerState) player.hunger).ssca$setOwner(player);
            player.hunger.addExhaustion(10);
            check(Math.abs(player.hunger.getExhaustion() - (rank == 1 ? 8.5 : 7.5)) < 0.0001, "real HungerManager exhaustion discount rank " + rank);
            int paid = 0;
            for (int i = 0; i < 20; i++) {
                player.hunger.setFoodLevel(20); player.hunger.setFoodLevel(19); paid += 20 - player.hunger.getFoodLevel();
                if (i == 0) check(player.hunger.getFoodLevel() == 19, "first one-point bill is never rounded down to free");
            }
            check(paid == 20 * (100 - discount) / 100, "one-point bills retain the full cumulative reduction");
            player.hunger = new net.minecraft.entity.player.HungerManager(); ((WindSpiritHungerState) player.hunger).ssca$setOwner(player);
            paid = 0;
            for (int i = 0; i < 5; i++) {
                player.hunger.setFoodLevel(20); player.hunger.setFoodLevel(12); paid += 20 - player.hunger.getFoodLevel();
                var nbt = new NbtCompound(); player.hunger.writeNbt(nbt);
                player.hunger = new net.minecraft.entity.player.HungerManager(); player.hunger.readNbt(nbt);
                ((WindSpiritHungerState) player.hunger).ssca$setOwner(player);
            }
            check(paid == 40 * (100 - discount) / 100, "fractional eight-point bills survive real HungerManager NBT reloads");
            paid = 0;
            for (int i = 0; i < 20; i++) { player.hunger.setFoodLevel(20); player.hunger.add(-1, 0); paid += 20 - player.hunger.getFoodLevel(); }
            check(paid == 20 * (100 - discount) / 100, "negative food additions use the same consumption discount");
        }
        var reversed = new ArrayList<>(all); java.util.Collections.reverse(reversed); player.form.formPerkMap.put(tree, reversed);
        select(player, form);
        check(WindSpiritPerks.hungerDiscount(player) == 25 && !has(player, WindSpiritPerks.METABOLISM_1)
                && !has(player, original("perks/ocelot_armor_1")), "reversed save ordering cannot reintroduce replaced ranks");
        check(WindSpiritPerks.dashRange(16, true) == 24 && WindSpiritPerks.landingRadius(3, true) == 4
                && WindSpiritPerks.landingDamage(12, true) == 16, "enhanced dash default values 24 / 4 / 16");
        NbtCompound saved = new NbtCompound(); player.form.writeToNbt(saved); player.form.readFromNbt(saved);
        check(player.form.formPerkMap.get(tree).equals(reversed), "all eight Wind Spirit purchases survive CCA NBT");
        for (Identifier id : List.of(FormIdentifiers.OCELOT_NOVA, original("ocelot_3"), FormIdentifiers.OCELOT_SP, FormIdentifiers.AXOLOTL_SP)) {
            select(player, RegPlayerForms.getPlayerForm(id));
            check(WindSpiritPerks.hungerDiscount(player) == (id.equals(FormIdentifiers.OCELOT_SP) ? 25 : 0), "hunger reduction is isolated: " + id);
            check(WindSpiritPerks.hasLongPounce(player) == id.equals(FormIdentifiers.OCELOT_SP), "dash enhancement is isolated: " + id);
        }
        TestPlayer fresh = player(); select(fresh, form);
        check(WindSpiritPerks.hungerDiscount(fresh) == 0 && !WindSpiritPerks.hasLongPounce(fresh), "unlearned Wind Spirit receives no free effects");
        check(RegPlayerForms.getPlayerForm(FormIdentifiers.OCELOT_NOVA).getPerkTreeID().equals(tree), "Nova shares purchases with distinct mapped effects");
    }

    private static void checkNovaForm() throws Exception {
        Identifier tree = RegPerks.T_Ocelot3PerkTree;
        var all = RegPerks.getPerkTree(tree).getAllPerks();
        for (Identifier id : all) {
            var perk = (NormalPerk) RegPerks.getPerk(id);
            for (Identifier power : perk.powerAdd) register(power, json("shape-shifter-curse", power.getPath()));
            for (Identifier power : perk.powerRemove) if (!PowerTypeRegistry.contains(power)) register(power, null);
        }
        for (Identifier id : List.of(NovaPerks.METABOLISM_1, NovaPerks.METABOLISM_2, NovaPerks.OVERLOAD, NovaPerks.LONG_POUNCE,
                WindSpiritPerks.METABOLISM_1, WindSpiritPerks.METABOLISM_2, WindSpiritPerks.OVERLOAD, WindSpiritPerks.LONG_POUNCE))
            register(id, json("my_addon", id.getPath()));
        TestPlayer player = player();
        IForm form = RegPlayerForms.getPlayerForm(FormIdentifiers.OCELOT_NOVA);
        check(form.getPerkTreeID().equals(tree), "Nova shares original ocelot purchase tree");
        player.form.formPerkMap.put(tree, new ArrayList<>(all)); player.totalExperience = 1234;
        select(player, RegPlayerForms.getPlayerForm(original("ocelot_3")));
        check(has(player, original("perks/ocelot_long_pounce_flight")), "original ocelot retains independent pounce");
        select(player, form);
        check(player.form.formPerkMap.get(tree).equals(all) && player.totalExperience == 1234, "Nova inherits all eight purchases without another payment");
        check(has(player, original("perks/ocelot_armor_2")) && !has(player, original("perks/ocelot_armor_1")), "Nova armor II replaces I");
        check(NovaPerks.hungerDiscount(player) == 25 && !has(player, NovaPerks.METABOLISM_1), "Nova metabolism II replaces I");
        check(NovaPerks.hasLongPounce(player) && !has(player, original("perks/ocelot_long_pounce_flight")), "Nova passive replaces independent pounce");
        check(has(player, original("perks/ocelot_ambush")) && has(player, original("perks/ocelot_hungry_pounce")), "Nova keeps native combat Perks");
        check(!WindSpiritPerks.hasLongPounce(player) && WindSpiritPerks.hungerDiscount(player) == 0, "Nova does not use Wind Spirit effects");
        var expected = json("shape-shifter-curse", "perks/ocelot_metabolic_overload").deepCopy();
        expected.getAsJsonObject("key").addProperty("key", "key.shape-shifter-curse.active_skill_3");
        expected.addProperty("description", "perk.ssc_addon.ocelot_nova.ocelot_metabolic_overload.description");
        check(expected.equals(json("my_addon", NovaPerks.OVERLOAD.getPath())), "Nova overload changes only key and description");
        for (int rank : new int[]{0, 1, 2}) {
            var learned = new ArrayList<Identifier>();
            if (rank > 0) learned.add(RegPerks.P_OcelotMetabolism_1);
            if (rank == 2) learned.add(RegPerks.P_OcelotMetabolism_2);
            player.form.formPerkMap.put(tree, learned); select(player, form);
            int discount = rank == 0 ? 0 : rank == 1 ? 15 : 25;
            player.hunger = new net.minecraft.entity.player.HungerManager(); ((WindSpiritHungerState) player.hunger).ssca$setOwner(player);
            player.hunger.addExhaustion(10);
            check(Math.abs(player.hunger.getExhaustion() - 10 * (100 - discount) / 100f) < 0.0001, "Nova exhaustion route rank " + rank);
            int paid = 0;
            for (int i = 0; i < 20; i++) {
                player.hunger.setFoodLevel(20); player.hunger.setFoodLevel(19); paid += 20 - player.hunger.getFoodLevel();
                var nbt = new NbtCompound(); player.hunger.writeNbt(nbt);
                player.hunger = new net.minecraft.entity.player.HungerManager(); player.hunger.readNbt(nbt);
                ((WindSpiritHungerState) player.hunger).ssca$setOwner(player);
            }
            check(paid == 20 * (100 - discount) / 100, "Nova one-point costs retain discount across real NBT");
            paid = 0;
            for (int i = 0; i < 20; i++) { player.hunger.setFoodLevel(20); player.hunger.add(-1, 0); paid += 20 - player.hunger.getFoodLevel(); }
            check(paid == 20 * (100 - discount) / 100, "Nova negative additions use same discount");
            player.hunger.setFoodLevel(10); int before = player.hunger.getFoodLevel(); player.hunger.add(2, 0);
            check(player.hunger.getFoodLevel() == Math.min(20, before + 2), "Nova positive food is not discounted");
        }
        check(NovaPerks.horizontalSpeed(1.2, true) == 1.5 && NovaPerks.fullCooldown(200, true) == 160
                && NovaPerks.singleCooldown(120, true) == 100, "Nova enhanced defaults are 1.5 horizontal / 8s / 5s");
        check(NovaPerks.horizontalSpeed(2, true) == 2.5 && NovaPerks.fullCooldown(400, true) == 320
                && NovaPerks.singleCooldown(240, true) == 200, "Nova enhancement respects configured bases");
        check(NovaPerks.horizontalSpeed(1.2, false) == 1.2 && NovaPerks.fullCooldown(200, false) == 200
                && NovaPerks.singleCooldown(120, false) == 120, "unlearned Nova keeps baseline leap parameters");
        var reversed = new ArrayList<>(all); java.util.Collections.reverse(reversed); player.form.formPerkMap.put(tree, reversed);
        select(player, form); PerkUtils.loadAllPerk(player, tree);
        check(NovaPerks.hungerDiscount(player) == 25 && !has(player, NovaPerks.METABOLISM_1)
                && !has(player, original("perks/ocelot_armor_1")), "reversed/repeated Nova loading cannot stack replaced ranks");
        NbtCompound saved = new NbtCompound(); player.form.writeToNbt(saved); player.form.readFromNbt(saved);
        check(player.form.formPerkMap.get(tree).equals(reversed), "Nova eight purchases survive real CCA NBT");
        for (Identifier id : List.of(FormIdentifiers.OCELOT_SP, original("ocelot_3"), FormIdentifiers.OCELOT_NOVA, FormIdentifiers.AXOLOTL_SP)) {
            select(player, RegPlayerForms.getPlayerForm(id));
            check(NovaPerks.hungerDiscount(player) == (id.equals(FormIdentifiers.OCELOT_NOVA) ? 25 : 0), "Nova hunger effects isolated: " + id);
            check(NovaPerks.hasLongPounce(player) == id.equals(FormIdentifiers.OCELOT_NOVA), "Nova leap enhancement isolated: " + id);
            check(player.form.formPerkMap.get(tree).equals(reversed), "shared ocelot purchases preserved: " + id);
        }
        TestPlayer fresh = player(); select(fresh, form);
        check(NovaPerks.hungerDiscount(fresh) == 0 && !NovaPerks.hasLongPounce(fresh), "unlearned Nova receives no free effects");
    }

    private static void checkSnowFoxForm() throws Exception {
        Identifier tree = RegPerks.T_SnowFox3PerkTree;
        Identifier nativeDive = original("perks/snow_fox_frost_dive");
        var all = RegPerks.getPerkTree(tree).getAllPerks();
        check(all.size() == 8, "exactly eight registered snow fox Perks");
        for (Identifier id : all) {
            var perk = (NormalPerk) RegPerks.getPerk(id);
            for (Identifier power : perk.powerAdd) register(power, json("shape-shifter-curse", power.getPath()));
            for (Identifier power : perk.powerRemove) if (!PowerTypeRegistry.contains(power)) register(power, null);
        }
        register(SnowFoxSpPerks.FROST_DIVE, json("my_addon", SnowFoxSpPerks.FROST_DIVE.getPath()));
        register(SnowFoxSpPerks.ELUSIVE_PAWS, json("my_addon", SnowFoxSpPerks.ELUSIVE_PAWS.getPath()));
        JsonObject nativeJson = json("shape-shifter-curse", nativeDive.getPath());
        JsonObject mappedJson = json("my_addon", SnowFoxSpPerks.FROST_DIVE.getPath());
        check(nativeJson.getAsJsonObject("key").get("key").getAsString().equals("key.shape-shifter-curse.active_skill_2"),
                "SSC dive retains its original key 2");
        check(mappedJson.getAsJsonObject("key").get("key").getAsString().equals("key.shape-shifter-curse.active_skill_3"),
                "SP dive uses key 3 rather than either SP combat key");
        JsonObject expected = nativeJson.deepCopy();
        expected.getAsJsonObject("key").addProperty("key", "key.shape-shifter-curse.active_skill_3");
        expected.addProperty("description", "perk.ssc_addon.snow_fox_sp.snow_fox_frost_dive.description");
        check(expected.equals(mappedJson), "only the dive key and localized description differ; all effects and costs remain original");

        for (Identifier formId : List.of(FormIdentifiers.SNOW_FOX_SP, FormIdentifiers.SNOW_FOX_FROSTSPINE)) {
            IForm sp = RegPlayerForms.getPlayerForm(formId);
            IForm vanilla = RegPlayerForms.getPlayerForm(original("snow_fox_3"));
            check(sp.getPerkTreeID().equals(tree), "SP snow fox shares SSC's saved tree");
            TestPlayer player = player(); player.totalExperience = 1234;
            player.form.formPerkMap.put(tree, new ArrayList<>(all));
            select(player, vanilla);
            check(has(player, nativeDive) && !has(player, SnowFoxSpPerks.FROST_DIVE), "SSC uses only the native dive");
            select(player, sp);
            check(player.form.formPerkMap.get(tree).equals(all) && player.totalExperience == 1234,
                    "all eight purchases survive evolution without payment or deletion");
            check(has(player, SnowFoxSpPerks.FROST_DIVE) && !has(player, nativeDive), "onLoad Mixin installs only the SP key-3 dive");
            check(SnowFoxSpPerks.hasElusivePaws(player) && !has(player, original("perks/snow_fox_elusive_paws")),
                    "SP reach uses a passive marker without duplicating SSC's bonus-only multiplier");
            for (Identifier id : all) {
                var perk = (NormalPerk) RegPerks.getPerk(id);
                check(perk.getID().equals(id), "original Perk identity remains intact: " + id);
                for (Identifier power : perk.powerAdd) check(has(player, power.equals(nativeDive) ? SnowFoxSpPerks.FROST_DIVE
                        : power.equals(original("perks/snow_fox_elusive_paws")) ? SnowFoxSpPerks.ELUSIVE_PAWS : power),
                        "learned snow fox effect is present: " + power);
                for (Identifier power : perk.powerRemove) check(!has(player, power), "fire training removes its original weakness: " + power);
            }
            var unchanged = new ArrayList<>(List.of(original("form_snow_fox_3_keep_burn"), original("form_snow_fox_3_triple_jump")));
            if (formId.equals(FormIdentifiers.SNOW_FOX_SP)) {
                unchanged.add(new Identifier("my_addon", "form_snow_fox_sp_hot_weakness"));
                unchanged.add(new Identifier("my_addon", "form_snow_fox_sp_cold_strength"));
            } else {
                for (String path : List.of("form_snow_fox_frostspine_cold_metabolism", "form_snow_fox_frostspine_snow_stride",
                        "form_snow_fox_frostspine_frost_spike", "form_snow_fox_frostspine_frost_forge")) unchanged.add(new Identifier("my_addon", path));
                unchanged.add(original("form_snow_fox_3_desert_health"));
                unchanged.add(original("form_snow_fox_3_plain_health"));
            }
            for (Identifier power : unchanged) {
                check(has(player, power), "unmodified base mechanic stays present: " + power);
            }
            check(has(player, original("perks/snow_fox_cold_recovery_1")) && has(player, original("perks/snow_fox_cold_recovery_2")),
                    "cold recovery II retains rank I as an independent effect");
            int count = player.powers.getPowers().size();
            PerkUtils.loadAllPerk(player, tree);
            check(player.powers.getPowers().size() == count, "snow fox Perk reload does not duplicate powers");
            NbtCompound saved = new NbtCompound(); player.form.writeToNbt(saved);
            TestPlayer restored = player(); restored.form.readFromNbt(saved);
            check(restored.form.formPerkMap.get(tree).equals(all), "CCA NBT preserves all eight snow fox purchases");
            select(restored, sp);
            check(has(restored, SnowFoxSpPerks.FROST_DIVE) && !has(restored, nativeDive), "restoring purchases reloads the mapped dive");
            NbtCompound powers = new NbtCompound(); player.powers.writeToNbt(powers); player.powers.readFromNbt(powers);
            check(has(player, SnowFoxSpPerks.FROST_DIVE) && !has(player, nativeDive), "Apoli NBT preserves only the mapped dive");
            for (IForm form : List.of(vanilla, RegPlayerForms.getPlayerForm(FormIdentifiers.SNOW_FOX_FROSTSPINE),
                    RegPlayerForms.getPlayerForm(FormIdentifiers.AXOLOTL_SP), sp, vanilla, sp)) {
                select(player, form);
                check(has(player, SnowFoxSpPerks.FROST_DIVE) == SnowFoxSpPerks.isSupported(form), "dive mapping is isolated across form changes: " + form.getFormID());
                check(SnowFoxSpPerks.hasElusivePaws(player) == SnowFoxSpPerks.isSupported(form), "reach mapping is isolated across form changes: " + form.getFormID());
                check(player.form.formPerkMap.get(tree).equals(all), "snow fox purchases persist across form changes");
                if (form == vanilla) check(has(player, nativeDive), "switching back restores SSC's native dive");
            }
            check(RegPlayerForms.getPlayerForm(FormIdentifiers.UPGRADE_AXOLOTL).getPerkTreeID().equals(RegPerks.EMPTY_PERK_TREE),
                    "special evolution stays unbound");
            TestPlayer fresh = player(); select(fresh, sp);
            check(!has(fresh, SnowFoxSpPerks.FROST_DIVE) && !has(fresh, original("perks/snow_fox_air_jump")),
                    "unlearned snow fox gains no Perks automatically");
            check(!SnowFoxSpPerks.hasElusivePaws(fresh), "unlearned snow fox has no reach multiplier");
            for (String suffix : List.of("near_lava_damage", "near_fire_damage", "burn_damage_up"))
                check(has(fresh, original("form_snow_fox_3_" + suffix)), "unlearned fire training leaves the weakness intact: " + suffix);
            RegPerks.getPerk(RegPerks.P_SnowFoxFrostDive).onGain(fresh, sp);
            check(has(fresh, SnowFoxSpPerks.FROST_DIVE) && !has(fresh, nativeDive), "buying dive directly as SP uses the same mapping as inheritance");
        }
    }

    private static void checkFamiliarForms() throws Exception {
        Identifier tree = RegPerks.T_FamiliarFox3PerkTree;
        var all = RegPerks.getPerkTree(tree).getAllPerks();
        check(all.size() == 9, "exactly nine registered familiar Perks");
        for (Identifier id : all) {
            var perk = (NormalPerk) RegPerks.getPerk(id);
            for (Identifier power : perk.powerAdd) register(power, json("shape-shifter-curse", power.getPath()));
            for (Identifier power : perk.powerRemove) if (!PowerTypeRegistry.contains(power)) register(power, null);
        }
        for (Identifier id : List.of(FamiliarFoxSpPerks.SIPHON_1, FamiliarFoxSpPerks.SIPHON_2,
                FamiliarFoxSpPerks.FIELD_1, FamiliarFoxSpPerks.FIELD_2)) register(id, json("my_addon", id.getPath()));
        for (Identifier familiarForm : List.of(FormIdentifiers.FAMILIAR_FOX_SP, FormIdentifiers.FAMILIAR_FOX_RED,
                FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
            IForm sp = RegPlayerForms.getPlayerForm(familiarForm);
            IForm vanilla = RegPlayerForms.getPlayerForm(original("familiar_fox_3"));
            check(sp.getPerkTreeID().equals(tree), "SP familiar is registered with the original tree");
            TestPlayer player = player(); player.totalExperience = 1234;
            player.form.formPerkMap.put(tree, new ArrayList<>(all));
            select(player, vanilla);
            check(has(player, original("perks/familiar_fox_siphon_2")), "SSC familiar retains original siphon");
            check(!has(player, FamiliarFoxSpPerks.SIPHON_2), "SSC familiar has no SP damage hook marker");
            select(player, sp);
            check(player.totalExperience == 1234 && player.form.formPerkMap.get(tree).equals(all), "all nine purchases survive evolution without an extra payment");
            check(FamiliarFoxSpPerks.siphonAmount(player) == 4 && !has(player, FamiliarFoxSpPerks.SIPHON_1), "siphon II replaces I and returns four, never six");
            check(!has(player, original("perks/familiar_fox_siphon_1")) && !has(player, original("perks/familiar_fox_siphon_2")), "original on-hit refunds are not duplicated");
            check(has(player, FamiliarFoxSpPerks.FIELD_2) && !has(player, FamiliarFoxSpPerks.FIELD_1)
                    && !has(player, FamiliarFoxSpPerks.baseImmunity(sp)), "field II removes the current form's base immunity and rank I");
            if (familiarForm.equals(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
                check(!has(player, original("form_familiar_fox_3_no_buff_effect")), "Mancianima also removes its redundant original immunity");
            }
            for (String suffix : List.of("deflection", "return_shield", "mana_capacity_2", "potion_charms"))
                check(has(player, original("perks/familiar_fox_" + suffix)), "unaltered familiar Perk is inherited: " + suffix);
            check(!has(player, original("perks/familiar_fox_mana_capacity_1")), "capacity II replaces I");
            var firstEffects = List.of(net.minecraft.entity.effect.StatusEffects.SPEED,
                    net.minecraft.entity.effect.StatusEffects.HASTE, net.minecraft.entity.effect.StatusEffects.INVISIBILITY);
            var secondEffects = List.of(net.minecraft.entity.effect.StatusEffects.STRENGTH,
                    net.minecraft.entity.effect.StatusEffects.WATER_BREATHING, net.minecraft.entity.effect.StatusEffects.NIGHT_VISION);
            for (var effect : firstEffects) check(FamiliarFoxSpPerks.preservesEffectDuration(player, effect), "field II preserves rank-I buff duration");
            for (var effect : secondEffects) check(FamiliarFoxSpPerks.preservesEffectDuration(player, effect), "field II preserves its additional buff duration");
            check(!FamiliarFoxSpPerks.preservesEffectDuration(player, net.minecraft.entity.effect.StatusEffects.REGENERATION), "unapproved buffs do not bypass the existing rules");
            for (int amplifier : new int[]{0, 1, 2, 3, 10}) for (int duration : new int[]{-1, 1, 20, 2400}) {
                var effect = new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, duration, amplifier, true, false, false);
                var adjusted = FamiliarFoxSpPerks.levelOneWithOriginalDuration(effect);
                check(adjusted.getAmplifier() == 0 && adjusted.getDuration() == duration, "full duration and level I: " + amplifier + "/" + duration);
                check(adjusted.isAmbient() && !adjusted.shouldShowParticles() && !adjusted.shouldShowIcon(), "effect display flags survive adjustment");
            }
            NbtCompound saved = new NbtCompound(); player.form.writeToNbt(saved);
            TestPlayer restored = player(); restored.form.readFromNbt(saved);
            check(restored.form.formPerkMap.get(tree).equals(all), "CCA NBT preserves the familiar purchase records");
            NbtCompound powers = new NbtCompound(); player.powers.writeToNbt(powers); player.powers.readFromNbt(powers);
            check(FamiliarFoxSpPerks.siphonAmount(player) == 4 && has(player, FamiliarFoxSpPerks.FIELD_2), "mapped powers survive Apoli NBT");

            var baseImmunity = new java.util.HashSet<String>();
            for (var effect : json("my_addon", FamiliarFoxSpPerks.baseImmunity(sp).getPath()).getAsJsonArray("effects")) baseImmunity.add(effect.getAsString());
            for (int rank : new int[]{1, 2}) {
                var expected = new java.util.HashSet<>(baseImmunity);
                expected.removeAll(List.of("minecraft:speed", "minecraft:haste", "minecraft:invisibility"));
                if (rank == 2) expected.removeAll(List.of("minecraft:strength", "minecraft:water_breathing", "minecraft:night_vision"));
                var actual = new java.util.HashSet<String>();
                for (var effect : json("my_addon", "perk_familiar_fox_sp_permeable_field_" + rank).getAsJsonArray("effects")) actual.add(effect.getAsString());
                check(actual.equals(expected), "field rank " + rank + " preserves every other SP immunity");
            }
            player.form.formPerkMap.put(tree, new ArrayList<>(List.of(RegPerks.P_FamiliarFoxSiphon_1, RegPerks.P_FamiliarFoxManaCapacity_1, RegPerks.P_FamiliarFoxPermeableField_1)));
            select(player, sp);
            check(FamiliarFoxSpPerks.siphonAmount(player) == 2, "rank I alone restores two mana");
            for (var effect : firstEffects) check(FamiliarFoxSpPerks.preservesEffectDuration(player, effect), "field I preserves exactly its first three durations");
            for (var effect : secondEffects) check(!FamiliarFoxSpPerks.preservesEffectDuration(player, effect), "rank I does not unlock rank-II buffs");
            select(player, vanilla);
            check(has(player, original("perks/familiar_fox_siphon_1")) && FamiliarFoxSpPerks.siphonAmount(player) == 0, "switching back restores SSC siphon without the SP hook");
            for (Identifier excluded : List.of(FormIdentifiers.UPGRADE_FAMILIAR_FOX)) {
                IForm form = RegPlayerForms.getPlayerForm(excluded);
                check(form.getPerkTreeID().equals(RegPerks.EMPTY_PERK_TREE), "other familiar forms remain unbound: " + excluded);
                select(player, form);
                check(FamiliarFoxSpPerks.siphonAmount(player) == 0, "SP siphon does not leak to " + excluded);
                check(!FamiliarFoxSpPerks.canUsePotionCharm(excluded), "potion charm eligibility stays unchanged for " + excluded);
            }
            check(FamiliarFoxSpPerks.canUsePotionCharm(FormIdentifiers.FAMILIAR_FOX_SP), "SP charm use extends the original form eligibility branch");
            check(FamiliarFoxSpPerks.canUsePotionCharm(FormIdentifiers.FAMILIAR_FOX_RED), "Red shares SP charm eligibility");
            check(FamiliarFoxSpPerks.canUsePotionCharm(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA), "Mancianima can use original potion charms");
            check(FamiliarFoxSpPerks.canUsePotionCharm(original("familiar_fox_3")), "original charm users remain allowed");
            TestPlayer fresh = player(); select(fresh, sp);
            check(FamiliarFoxSpPerks.siphonAmount(fresh) == 0 && has(fresh, FamiliarFoxSpPerks.baseImmunity(sp)), "unlearned familiar gains no Perks and retains its own immunity");
        }
        TestPlayer switching = player();
        switching.form.formPerkMap.put(tree, new ArrayList<>(all));
        for (Identifier id : List.of(FormIdentifiers.FAMILIAR_FOX_SP, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA, FormIdentifiers.FAMILIAR_FOX_RED,
                FormIdentifiers.FAMILIAR_FOX_SP, FormIdentifiers.FAMILIAR_FOX_RED, original("familiar_fox_3"), FormIdentifiers.FAMILIAR_FOX_SP)) {
            select(switching, RegPlayerForms.getPlayerForm(id));
            check(switching.form.formPerkMap.get(tree).equals(all), "familiar round trip retains all purchases: " + id);
            boolean supported = !id.equals(original("familiar_fox_3"));
            check(FamiliarFoxSpPerks.siphonAmount(switching) == (supported ? 4 : 0), "familiar round trip has the correct siphon: " + id);
            check(has(switching, FamiliarFoxSpPerks.FIELD_2) == supported && !has(switching, FamiliarFoxSpPerks.FIELD_1), "familiar round trip has no duplicate field: " + id);
        }
    }

    private static void checkFluorescentForms(TestPlayer player, List<Identifier> all) {
        for (Identifier id : List.of(FormIdentifiers.AXOLOTL_FLUORESCENT, FormIdentifiers.AXOLOTL_ALING)) {
            IForm form = RegPlayerForms.getPlayerForm(id);
            check(form.getPerkTreeID().equals(TREE), "fluorescent family shares saved unlocks: " + id);
            select(player, form);
            check(player.form.formPerkMap.get(TREE).equals(all), "SP evolution keeps all purchased unlocks: " + id);
            check(FluorescentAxolotlPerks.hasWaterMagicEfficiency(player), "fluorescent efficiency marker is active: " + id);
            check(!has(player, AxolotlSpPerks.WATER_MAGIC) && !AxolotlSpPerks.hasWaterMagicEfficiency(player),
                    "SP-only vortex discount does not leak into fluorescent family");
            check(!has(player, original("perks/axolotl_water_magic_efficiency_explosion")), "fluorescent family has no old explosion");
            check(has(player, AxolotlSpPerks.TIDAL_PULL), "fluorescent tidal pull keeps approved key-3 effect");
            check(has(player, FluorescentAxolotlPerks.DRY_TOLERANCE)
                    && !has(player, original("perks/axolotl_dry_tolerance")), "dry tolerance maps to unconditional health");
            for (int tier = 0; tier <= 9; tier++) check(has(player, original("form_axolotl_2_new_oxygen_health_" + tier)),
                    "dry tolerance preserves moisture health tier " + tier);
            var health = json("my_addon", FluorescentAxolotlPerks.DRY_TOLERANCE.getPath());
            check("apoli:attribute".equals(health.get("type").getAsString()) && !health.has("condition")
                    && health.getAsJsonObject("modifier").get("value").getAsInt() == 6, "permanent plus six has no moisture condition");
            checkRefunds(player);
            String drainId = id.equals(FormIdentifiers.AXOLOTL_ALING)
                    ? "form_axolotl_aling_moisture_drain" : "form_axolotl_fluorescent_moisture_drain";
            check(json("my_addon", drainId).get("interval").getAsInt() == (id.equals(FormIdentifiers.AXOLOTL_ALING) ? 27 : 20),
                    "each form retains its own ground-drain interval");
            var drain = ApoliDataTypes.ENTITY_ACTION.read(json("my_addon", drainId).getAsJsonObject("entity_action"));
            player.air = 100;
            drain.accept(player);
            check(player.air == 99, "vegetation Perk reduces dry ground drain to one");
            player.powers.removePower(PowerTypeRegistry.get(original("perks/axolotl_vegetation")), form.getFormLayer().getRight());
            player.air = 100;
            drain.accept(player);
            check(player.air == 97, "unlearned vegetation keeps original ground drain of three");
            RegPerks.getPerk(RegPerks.P_AxolotlVegetation).onLoad(player, form);
            check(FluorescentAxolotlPerks.laserChargeTicks(140, true) == 100, "normal laser charge 7 to 5 seconds");
            check(FluorescentAxolotlPerks.laserChargeTicks(140, false) == 140, "unlearned charge stays seven seconds");
            check(FluorescentAxolotlPerks.laserChargeTicks(20, true) == 1, "configured short charge never goes below one tick");
            check(Math.abs(FluorescentAxolotlPerks.tidalFlightSpeed(0.2, true) - 0.25) < 1e-9, "tidal base speed gains 25 percent");
            check(Math.abs(FluorescentAxolotlPerks.tidalFlightSpeed(0.31, true) - 0.3875) < 1e-9, "tidal accessory bonus composes with Perk");
            check(Math.abs(FluorescentAxolotlPerks.enhancedLaserDamage(12, true) - 15) < 1e-5, "enhanced fluorescent shot damage 12 to 15");
            check(Math.abs(FluorescentAxolotlPerks.enhancedLaserDamage(14.4f, true) - 18) < 1e-5, "innate Aling shot damage 14.4 to 18");
        }
    }

    private static void checkRefunds(TestPlayer player) {
        world.clock += 100;
        player.air = 100;
        check(FluorescentAxolotlPerks.onLaserHit(player) && player.air == 110, "laser first hit restores ten");
        for (int target = 0; target < 12; target++) check(!FluorescentAxolotlPerks.onLaserHit(player)
                && player.air == 110, "multiple targets/segments do not multiply the laser refund");
        check(FluorescentAxolotlPerks.onTidalHit(player) && player.air == 113, "tidal refund has an independent three-point budget");
        for (int target = 0; target < 12; target++) check(!FluorescentAxolotlPerks.onTidalHit(player)
                && player.air == 113, "multiple tether targets do not multiply the tidal refund");
        NbtCompound saved = new NbtCompound();
        player.powers.writeToNbt(saved); player.powers.readFromNbt(saved);
        check(!FluorescentAxolotlPerks.onLaserHit(player) && !FluorescentAxolotlPerks.onTidalHit(player),
                "Apoli NBT retains both refund clocks");
        world.clock += 19;
        check(!FluorescentAxolotlPerks.onLaserHit(player) && !FluorescentAxolotlPerks.onTidalHit(player), "refunds stay blocked at tick nineteen");
        world.clock++;
        check(FluorescentAxolotlPerks.onLaserHit(player) && player.air == 123, "laser restores again at tick twenty");
        check(FluorescentAxolotlPerks.onTidalHit(player) && player.air == 126, "tidal restores again at tick twenty");
        world.clock += 20; player.air = 299;
        check(FluorescentAxolotlPerks.onLaserHit(player) && player.air == 300, "skill refunds cap at maximum moisture");
        player.powers.removePower(PowerTypeRegistry.get(FluorescentAxolotlPerks.MOISTURE_RETURN), player.form.nowForm.getFormLayer().getRight());
        world.clock += 20; player.air = 100;
        check(!FluorescentAxolotlPerks.onLaserHit(player) && !FluorescentAxolotlPerks.onTidalHit(player) && player.air == 100,
                "no skill refund without a learned return Perk");
        RegPerks.getPerk(RegPerks.P_AxolotlMoistureReturn_1).onLoad(player, player.form.nowForm);
        check(FluorescentAxolotlPerks.onLaserHit(player), "rank I also grants the skill refund");
        RegPerks.getPerk(RegPerks.P_AxolotlMoistureReturn_2).onLoad(player, player.form.nowForm);
        check(!FluorescentAxolotlPerks.onLaserHit(player), "upgrading to rank II does not reset a running refund clock");
    }

    private static void checkBurstActions(TestPlayer player) {
        for (String name : List.of("form_axolotl_sp_water_ball", "form_axolotl_sp_fountain_belt_trinket", "form_axolotl_sp_fountain_belt_trinket_plus")) {
            JsonObject root = json("my_addon", name);
            JsonObject power = root.has("fountain") ? root.getAsJsonObject("fountain") : root;
            var actions = power.getAsJsonObject("entity_action").getAsJsonArray("actions");
            JsonObject payment = null;
            for (var node : actions) if (node.getAsJsonObject().get("type").getAsString().equals("apoli:if_else")) payment = node.getAsJsonObject();
            check(payment != null, name + " has a Perk-aware cost");
            var action = ApoliDataTypes.ENTITY_ACTION.read(payment);
            player.air = 100;
            action.accept(player);
            check(player.air == 88, name + " actually spends 12 air through Apoli");
            player.powers.removePower(PowerTypeRegistry.get(AxolotlSpPerks.WATER_MAGIC), player.form.nowForm.getFormLayer().getRight());
            player.air = 100;
            action.accept(player);
            check(player.air == 80, name + " actually spends 20 air without the Perk");
            RegPerks.getPerk(RegPerks.P_AxolotlWaterMagicEfficiency).onLoad(player, player.form.nowForm);
            check(power.getAsJsonObject("key").get("key").getAsString().equals("key.ssc_addon.sp_secondary")
                    && power.get("cooldown").getAsInt() == 60, name + " retains secondary input and 3s cooldown");
        }
    }

    private static void checkCosts() {
        int paid = 0;
        int[] expected = {5, 5, 5, 5, 4, 5, 5, 2};
        for (int pulse = 1; pulse <= 8; pulse++) {
            int quote = AxolotlSpPerks.vortexAirCost(Math.min(60, pulse * 8), true);
            check(quote - paid == expected[pulse - 1], "cumulative vortex payment " + pulse);
            paid = quote;
        }
        check(paid == 36, "full default vortex spends 36 rather than 60, with all eight damage pulses");
        for (int base = 0; base <= 1000; base++) {
            int quote = AxolotlSpPerks.vortexAirCost(base, true);
            check(quote * 5 >= base * 3 && (quote == 0 || (quote - 1) * 5 < base * 3), "discount rounds only the cumulative bill");
            check(AxolotlSpPerks.vortexAirCost(base, false) == base, "unlearned costs stay unchanged");
        }
    }

    private static void checkValues() {
        JsonObject vegetation = json("shape-shifter-curse", "perks/axolotl_vegetation");
        check(vegetation.getAsJsonObject("normal").getAsJsonObject("modifier").get("value").getAsDouble() == -0.3
                && vegetation.getAsJsonObject("vegetation").getAsJsonObject("modifier").get("value").getAsDouble() == -0.15,
                "vegetation walking penalties remain 30% / 15%");
        JsonObject dry = json("shape-shifter-curse", "perks/axolotl_dry_tolerance");
        check(dry.getAsJsonObject("modifier").get("value").getAsInt() == 6
                && dry.getAsJsonObject("condition").get("compare_to").getAsInt() == 120,
                "dry tolerance remains six health above the 120-air threshold");
        JsonObject first = json("shape-shifter-curse", "perks/axolotl_moisture_return_1");
        check(first.get("event").getAsString().equals("critical") && first.get("min_damage").getAsInt() == 7
                && first.getAsJsonObject("bientity_action").getAsJsonObject("action").get("value").getAsInt() == 15,
                "return I remains critical / seven-damage threshold / fifteen air");
        JsonObject second = json("shape-shifter-curse", "perks/axolotl_moisture_return_2")
                .getAsJsonObject("bientity_action").getAsJsonObject("action");
        check(second.getAsJsonObject("condition").get("compare_to").getAsInt() == 150
                && second.getAsJsonObject("if_action").get("value").getAsInt() == 30
                && second.getAsJsonObject("else_action").get("value").getAsInt() == 15,
                "return II remains 30 below 150 air, 15 otherwise");
        JsonObject propulsion = json("shape-shifter-curse", "perks/axolotl_propulsion_efficiency");
        check(propulsion.getAsJsonObject("entity_action").getAsJsonArray("actions").get(2).getAsJsonObject()
                .get("value").getAsInt() == -1, "sprint jump propulsion still spends one air");
        JsonObject wave = json("shape-shifter-curse", "perks/axolotl_wave_efficiency");
        check(wave.get("interval").getAsInt() == 30 && wave.getAsJsonObject("entity_action").get("value").getAsInt() == -1,
                "ongoing wave cost stays one per thirty ticks");
        JsonObject root = json("my_addon", AxolotlSpPerks.TIDAL_PULL.getPath());
        JsonObject charge = root.getAsJsonObject("charge");
        check(charge.getAsJsonObject("key").get("key").getAsString().equals("key.shape-shifter-curse.active_skill_3"), "tidal pull is key 3");
        check(charge.getAsJsonObject("start_action").get("value").getAsInt() == -20, "tidal pull prepays 20");
        check(charge.getAsJsonObject("start_condition").get("compare_to").getAsInt() == 20, "tidal pull requires 20");
        check(charge.getAsJsonObject("tier0_use_action").get("distance").getAsInt() == 20
                && charge.get("tier0_cooldown").getAsInt() == 20, "tidal pull is 20 blocks / 1 second");
        check(root.getAsJsonObject("glow").getAsJsonObject("condition").get("power").getAsString()
                .equals(AxolotlSpPerks.TIDAL_PULL + "_charge"), "tidal highlighting tracks the mapped charge power");
        JsonObject water = json("my_addon", AxolotlSpPerks.WATER_MAGIC.getPath());
        check(!water.has("explosion"), "mapped Perk contains no competing original explosion");
        for (var node : water.getAsJsonObject("attack").getAsJsonObject("bientity_action").getAsJsonArray("actions")) {
            var object = node.getAsJsonObject();
            if (object.get("type").getAsString().equals("apoli:actor_action"))
                check(object.getAsJsonObject("action").get("value").getAsInt() == -6, "wave attack still spends six");
        }
    }

    static void select(TestPlayer player, IForm form) {
        if (player.form.nowForm != null) player.powers.removeAllPowersFromSource(player.form.nowForm.getFormLayer().getRight());
        player.form.setForm(form);
        player.form.nowPerkTree = form.getPerkTreeID();
        var base = resource(form.getFormID().getNamespace(), "origins/form_" + form.getFormID().getPath());
        for (var id : base.getAsJsonArray("powers")) {
            Identifier key = new Identifier(id.getAsString());
            if (!PowerTypeRegistry.contains(key)) register(key, null);
            player.powers.addPower(PowerTypeRegistry.get(key), form.getFormLayer().getRight());
        }
        PerkUtils.loadAllPerk(player, form.getPerkTreeID());
    }

    static TestPlayer player() throws Exception {
        TestPlayer player = allocate(TestPlayer.class);
        player.hunger = new net.minecraft.entity.player.HungerManager();
        player.id = UUID.randomUUID(); player.air = 300; player.abilities = new PlayerAbilities();
        player.form = new TestFormComponent(player);
        player.effects = new java.util.HashMap<>();
        player.minions = new java.util.concurrent.ConcurrentHashMap<>();
        player.powers = new PowerHolderComponentImpl(player);
        player.components = ComponentContainer.Factory.builder(TestPlayer.class)
                .component(RegPlayerFormComponent.PLAYER_FORM, p -> p.form)
                .component(PowerHolderComponent.KEY, p -> p.powers).build().createContainer(player);
        return player;
    }

    static void register(Identifier id, JsonObject json) {
        if (PowerTypeRegistry.contains(id)) return;
        var factory = DUMMY.read(new JsonObject());
        if (json != null && "apoli:cooldown".equals(json.get("type").getAsString())) factory = REFUND_TIMER.read(json);
        if (json != null && "shape-shifter-curse:wolf_minion_bonus".equals(json.get("type").getAsString())) {
            PowerTypeRegistry.register(id, new PowerType<>(id,
                    net.onixary.shapeShifterCurseFabric.additional_power.WolfMinionPower.createFactory().read(json)));
            return;
        }
        if (json != null && "apoli:multiple".equals(json.get("type").getAsString())) {
            var children = new ArrayList<Identifier>();
            for (var entry : json.entrySet()) if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("type")) {
                Identifier child = new Identifier(id.getNamespace(), id.getPath() + "_" + entry.getKey());
                register(child, entry.getValue().getAsJsonObject()); children.add(child);
            }
            var type = new MultiplePowerType<Power>(id, factory); type.setSubPowers(children);
            PowerTypeRegistry.register(id, type);
        } else PowerTypeRegistry.register(id, new PowerType<>(id, factory));
    }

    static boolean has(TestPlayer player, Identifier id) {
        return PowerTypeRegistry.contains(id) && player.powers.hasPower(PowerTypeRegistry.get(id));
    }
    private static Identifier original(String path) { return new Identifier("shape-shifter-curse", path); }
    static JsonObject json(String namespace, String path) { return resource(namespace, "powers/" + path); }
    private static JsonObject resource(String namespace, String path) {
        try (var in = AxolotlSpPerkIntegrationProbe.class.getResourceAsStream("/data/" + namespace + "/" + path + ".json")) {
            if (in == null) throw new AssertionError(path);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    @SuppressWarnings("unchecked") static <T> T allocate(Class<T> type) throws Exception { return (T) unsafe.allocateInstance(type); }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    public static final class TestFormComponent extends PlayerFormComponent {
        TestFormComponent(PlayerEntity p) { super(p); }
        @Override public void sync() { }
    }
    public static final class TestWorld extends ServerWorld {
        long clock;
        net.minecraft.server.MinecraftServer server;
        java.util.Map<UUID, net.minecraft.entity.Entity> entities;
        net.minecraft.world.PersistentStateManager states;
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public long getTime() { return clock; }
        @Override public net.minecraft.server.MinecraftServer getServer() { return server; }
        @Override public net.minecraft.entity.Entity getEntity(UUID id) { return entities.get(id); }
        @Override public net.minecraft.world.PersistentStateManager getPersistentStateManager() { return states; }
    }
    /** Keeps real CooldownPower timing/NBT; suppresses its network send in this no-connection fixture. */
    public static final class TestCooldown extends CooldownPower {
        TestCooldown(PowerType<?> type, LivingEntity entity, int duration) { super(type, entity, duration, HudRender.DONT_RENDER); }
        @Override public void use() { lastUseTime = entity.getWorld().getTime(); }
    }
    public static final class TestPlayer extends ServerPlayerEntity implements ComponentProvider {
        UUID id; int air; PlayerAbilities abilities; TestFormComponent form;
        ComponentContainer components; PowerHolderComponentImpl powers;
        net.minecraft.entity.player.HungerManager hunger;
        java.util.Map<net.minecraft.entity.effect.StatusEffect, net.minecraft.entity.effect.StatusEffectInstance> effects;
        java.util.concurrent.ConcurrentHashMap<Identifier, ArrayList<UUID>> minions;
        float healed;
        @Override public net.minecraft.entity.player.HungerManager getHungerManager() { return hunger; }
        private TestPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "perk-test")); }
        @Override public World getWorld() { return world; }
        @Override public ServerWorld getServerWorld() { return world; }
        @Override public net.minecraft.server.MinecraftServer getServer() { return world.server; }
        @Override public net.minecraft.entity.attribute.EntityAttributeInstance getAttributeInstance(net.minecraft.entity.attribute.EntityAttribute attribute) { return null; }
        @Override public boolean hasStatusEffect(net.minecraft.entity.effect.StatusEffect effect) { return effects.containsKey(effect); }
        @Override public boolean removeStatusEffect(net.minecraft.entity.effect.StatusEffect effect) { return effects.remove(effect) != null; }
        @Override public void heal(float amount) { healed += amount; }
        // The real minion interface is mixed into PlayerEntity; suppress its network sync in this fixture.
        public java.util.concurrent.ConcurrentHashMap<Identifier, ArrayList<UUID>> shape_shifter_curse$getAllMinions() { return minions; }
        public void shape_shifter_curse$clearMinions(Identifier id) { minions.remove(id); }
        public boolean shape_shifter_curse$removeMinion(Identifier id, UUID uuid) { return minions.getOrDefault(id, new ArrayList<>()).remove(uuid); }
        @Override public ComponentContainer getComponentContainer() { return components; }
        @Override public UUID getUuid() { return id; }
        @Override public Text getName() { return Text.literal("perk-test"); }
        @Override public PlayerAbilities getAbilities() { return abilities; }
        @Override public int getAir() { return air; }
        @Override public void setAir(int air) { this.air = air; }
        @Override public int getMaxAir() { return 300; }
        @Override public boolean isAlive() { return true; }
    }
}
