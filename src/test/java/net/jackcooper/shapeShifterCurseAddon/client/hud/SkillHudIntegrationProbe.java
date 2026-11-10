package net.jackcooper.shapeShifterCurseAddon.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.component.PowerHolderComponentImpl;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionTestRoutes;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.additional_power.ChargePower;
import io.github.apace100.apoli.power.Power;

/** Exercise the transformed SSC charge class and real catalog, without a running game world. */
public final class SkillHudIntegrationProbe {
    private static int checks;
    public static void run() throws Exception {
        var routes = EvolutionTestRoutes.production();
        EvolutionTestRoutes.loadServer(routes);
        EvolutionRegistry.CLIENT.applyClientSync(routes);
        var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) f.get(null);
        TestWorld server = (TestWorld) unsafe.allocateInstance(TestWorld.class);
        TestWorld client = (TestWorld) unsafe.allocateInstance(TestWorld.class);
        var clientField = World.class.getDeclaredField("isClient");
        unsafe.putBoolean(client, unsafe.objectFieldOffset(clientField), true);
        server.now = 100; client.now = 500;
        ChargePower authority = power(unsafe, server), mirror = power(unsafe, client);
        check((Object) mirror instanceof ChargeCooldownHud, "new Mixin applied to actual SSC ChargePower");
        authority.nowCooldown = 12;
        var serialized = (NbtCompound) authority.toTag();
        check(serialized.getCompound("ssca_hud_cd").getInt("remaining") == 12, "server sends actual remaining cooldown");
        check(serialized.getCompound("ssca_hud_cd").getInt("total") == 20, "server sends actual tier cooldown");
        mirror.fromTag(serialized);
        var hud = (ChargeCooldownHud) (Object) mirror;
        check(hud.ssca$remainingCooldown() == 12 && hud.ssca$totalCooldown() == 20, "snapshot translated to client clock");
        client.now += 5;
        check(hud.ssca$remainingCooldown() == 7, "HUD countdown advances on the client clock");
        client.now += 30;
        check(hud.ssca$remainingCooldown() == 0, "expired HUD cooldown clamps at zero");
        check(authority.nowCooldown == 12 && mirror.nowCooldown == 0, "HUD never changes server or client gameplay cooldown fields");
        authority.fromTag(serialized);
        check(authority.nowCooldown == 12, "server NBT loading retains previous SSC cooldown semantics");
        mirror.fromTag(new NbtCompound());
        check(hud.ssca$remainingCooldown() == 0 && hud.ssca$totalCooldown() == 0, "legacy tags have no phantom cooldown");

        var formsField = SkillHudCatalog.class.getDeclaredField("FORMS"); formsField.setAccessible(true);
        @SuppressWarnings("unchecked") var forms = (Map<String, List<SkillHudCatalog.Skill>>) formsField.get(null);
        var perksField = SkillHudCatalog.class.getDeclaredField("PERK_SKILLS"); perksField.setAccessible(true);
        @SuppressWarnings("unchecked") var registrations = (List<SkillHudCatalog.PerkSkill>) perksField.get(null);
        var perks = registrations.stream().map(SkillHudCatalog.PerkSkill::skill).toList();
        var eligible = SkillHudCatalog.class.getDeclaredMethod("extraEligible", SkillHudCatalog.Skill.class); eligible.setAccessible(true);
        java.util.function.Predicate<SkillHudCatalog.Skill> accepts = skill -> {
            try { return (boolean) eligible.invoke(null, skill); } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        };
        var axolotl = forms.get("axolotl_sp");
        var tidal = perks.stream().filter(skill -> skill.cooldown().getPath().equals("perk_axolotl_sp_tidal_pull_charge")).findFirst().orElseThrow();
        var base = SkillHudLayout.select(axolotl, SkillHudCatalog.Skill::primary, accepts, List.of());
        check(base.size() == 3 && base.get(2).skill().cooldown().getPath().equals("form_axolotl_sp_water_ball"), "SP axolotl adds burst only; crafting and movement do not occupy small frames");
        var learned = SkillHudLayout.select(axolotl, SkillHudCatalog.Skill::primary, accepts, List.of(tidal));
        check(learned.size() == 4 && learned.get(3).skill() == tidal && learned.get(3).slot() == SkillHudLayout.Slot.UPPER, "learned tidal pull occupies the upper extra frame");
        check(base.get(0).equals(learned.get(0)) && base.get(1).equals(learned.get(1)), "learning a Perk preserves both original main skills");
        for (String fieldName : List.of("SNOW_MELEE", "SNOW_RANGED")) {
            var snowField = SkillHudCatalog.class.getDeclaredField(fieldName); snowField.setAccessible(true);
            @SuppressWarnings("unchecked") var snow = (List<SkillHudCatalog.Skill>) snowField.get(null);
            var dive = perks.stream().filter(skill -> skill.cooldown().getPath().equals("perk_snow_fox_sp_frost_dive")).findFirst().orElseThrow();
            var slots = SkillHudLayout.select(snow, SkillHudCatalog.Skill::primary, accepts, List.of(dive));
            check(slots.size() == 4 && slots.get(2).skill().cooldown().getPath().equals("form_snow_fox_sp_toggle")
                    && slots.get(3).skill() == dive, "snow fox mode switch and learned dive use lower/upper frames");
        }
        check(perks.size() == 9, "all nine current addon Perk power implementations are represented");
        for (var perk : perks) {
            check(SkillHudIntegrationProbe.class.getClassLoader().getResource("assets/" + perk.icon().getNamespace() + "/" + perk.icon().getPath()) != null,
                    "actual Perk icon packaged: " + perk.icon());
        }
        dynamicCatalog(unsafe, client, registrations);
        perkCooldownParity(unsafe, server, client);
        evolutionEligibility(unsafe, server, client, routes);
        System.out.println("Skill HUD Fabric integration: " + checks + " actual Mixin, NBT cooldown mirror, Perk unlock/revoke/form-switch and SSC/SSCA cooldown checks passed; live gameplay not tested.");
    }

    private static void evolutionEligibility(sun.misc.Unsafe unsafe, TestWorld server, TestWorld client,
                                             Map<String, String> routes) throws Exception {
        var form = EvolutionRegistry.CLIENT.getRoute("familiar_fox").startForm;
        var player = player(unsafe, client, form);
        var authority = player(unsafe, server, form);
        player.evolution.setUnlockAll(false);
        player.evolution.unlock("spark");
        var skills = SkillHudCatalog.forForm(form, player);
        var spark = skills.stream().filter(s -> "spark".equals(s.talent())).findFirst().orElseThrow();
        var ring = skills.stream().filter(s -> "fire_ring".equals(s.talent())).findFirst().orElseThrow();
        check(SkillHudCatalog.isAvailable(spark, player) && !SkillHudCatalog.isAvailable(ring, player), "HUD exposes acquired spark");
        player.evolution.unlock("fire_ring");
        check(!SkillHudCatalog.isAvailable(spark, player) && SkillHudCatalog.isAvailable(ring, player), "active fire ring replaces only the spark skill");
        try {
            EvolutionRegistry.CLIENT.applyClientSync(EvolutionTestRoutes.withoutNode(routes, "familiar_fox", "fire_ring"));
            check(SkillHudCatalog.isAvailable(spark, player) && !SkillHudCatalog.isAvailable(ring, player), "paused ring restores the acquired spark skill");
            check(SkillHudCatalog.isAvailable(ring, authority), "client mirror cannot change server talent eligibility");
            EvolutionRegistry.clearClient();
            check(entries(player).stream().noneMatch(e -> e.skill().talent() != null), "missing client routes hide every unavailable talent skill");
            check(SkillHudCatalog.isAvailable(ring, authority), "disconnect mirror cleanup preserves authority");
        } finally { EvolutionRegistry.CLIENT.applyClientSync(routes); }
        check(entries(player).stream().anyMatch(e -> "fire_ring".equals(e.skill().talent())), "route synchronization restores the skill without new acquisition");
        player.evolution.setUnlockAll(true);
        player.form.nowForm = new NormalForm(new Identifier("my_addon", "axolotl_upgrade"));
        check(!SkillHudCatalog.isAvailable(ring, player), "admin HUD follows the actual form");
        var axolotl = player(unsafe, client, EvolutionRegistry.CLIENT.getRoute("axolotl").startForm);
        var perkId = new Identifier("my_addon", "perk_upgrade_axolotl_tidal_pull_charge");
        var tree = net.onixary.shapeShifterCurseFabric.perk.RegPerks.T_Axolotl3PerkTree;
        axolotl.form.formPerkMap.put(tree, List.of(net.onixary.shapeShifterCurseFabric.perk.RegPerks.P_AxolotlTidalPull));
        axolotl.powers.addPower(PowerTypeRegistry.get(perkId), new Identifier("test", "evolution"));
        check(entries(axolotl).stream().anyMatch(e -> perkId.equals(e.skill().cooldown())), "purchased mapped Perk is visible while its talent is active");
        try {
            EvolutionRegistry.CLIENT.applyClientSync(EvolutionTestRoutes.withoutNode(routes, "axolotl", "vortex_guide"));
            check(entries(axolotl).stream().noneMatch(e -> perkId.equals(e.skill().cooldown())), "paused talent hides its mapped Perk even with purchase and power retained");
        } finally { EvolutionRegistry.CLIENT.applyClientSync(routes); }
        check(entries(axolotl).stream().anyMatch(e -> perkId.equals(e.skill().cooldown())), "restoration automatically reveals the retained mapped Perk");
    }

    private static void perkCooldownParity(sun.misc.Unsafe unsafe, TestWorld server, TestWorld client) throws Exception {
        for (String path : List.of("shape-shifter-curse/powers/perks/axolotl_tidal_pull",
                "my_addon/powers/perk_axolotl_sp_tidal_pull", "my_addon/powers/perk_upgrade_axolotl_tidal_pull")) {
            JsonObject charge = data(path).getAsJsonObject("charge");
            check(charge.get("tier0_cooldown").getAsInt() == 20, "tidal pull uses the intended one second in the actual packaged resource: " + path);
            // Parse the real timing fields with SSC's factory; omit actions that need a running raycast world.
            var timing = new JsonObject();
            for (String key : List.of("tier0_enable", "tier0_charge_time", "tier0_cooldown", "tier1_enable")) timing.add(key, charge.get(key));
            TestOwner owner = (TestOwner) unsafe.allocateInstance(TestOwner.class); owner.testWorld = server;
            var type = new PowerType<>(new Identifier("my_addon", "tidal_timing_probe"), ChargePower.createFactory().read(timing));
            ChargePower power = (ChargePower) type.create(owner);
            power.ChargeTierList.get(0).use(power);
            check(power.nowCooldown == 20, "actual SSC release logic starts 20 ticks: " + path);
            ChargePower mirror = power(unsafe, client);
            mirror.fromTag(power.toTag());
            var hud = (ChargeCooldownHud) (Object) mirror;
            check(hud.ssca$remainingCooldown() == 20 && hud.ssca$totalCooldown() == 20, "HUD receives the same one-second server cooldown");
            for (int i = 0; i < 10; i++) power.tick();
            check(power.nowCooldown == 10, "tidal pull retains half its cooldown after ten ticks");
            power.onUse();
            check(!power.isCharging() && power.ChargeTime == 0, "early recast is rejected by the actual SSC power");
            for (int i = 0; i < 9; i++) power.tick();
            check(power.nowCooldown == 1, "one tick remains immediately before one second");
            power.onUse();
            check(!power.isCharging(), "recast is still blocked at tick 19");
            power.tick();
            check(power.nowCooldown == 0, "cooldown expires exactly at tick 20");
        }
        String[][] pairs = {
                {"snow_fox_frost_dive", "perk_snow_fox_sp_frost_dive", "160"},
                {"anubis_wolf_soul_recall", "perk_anubis_wolf_sp_soul_recall", "200"},
                {"anubis_wolf_soul_recall", "perk_golden_sandstorm_recall", "200"},
                {"ocelot_metabolic_overload", "perk_wind_spirit_metabolic_overload", "300"},
                {"ocelot_metabolic_overload", "perk_ocelot_nova_metabolic_overload", "300"},
                {"bat_wingbeat", "perk_bat_desmodus_wingbeat", "40"},
                {"bat_wingbeat", "perk_bat_parasitic_fruit_wingbeat", "40"}
        };
        for (var pair : pairs) {
            int original = data("shape-shifter-curse/powers/perks/" + pair[0]).get("cooldown").getAsInt();
            int addon = data("my_addon/powers/" + pair[1]).get("cooldown").getAsInt();
            check(original == addon && addon == Integer.parseInt(pair[2]), "active Perk cooldown parity: " + pair[1]);
        }
        check(data("my_addon/powers/perk_golden_sandstorm_recall").get("fail_cooldown").getAsInt() == 0,
                "empty golden recall still has no failure cooldown");
    }
    private static JsonObject data(String path) throws Exception {
        try (var in = SkillHudIntegrationProbe.class.getClassLoader().getResourceAsStream("data/" + path + ".json")) {
            if (in == null) throw new AssertionError("Missing actual Perk resource: " + path);
            return JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void dynamicCatalog(sun.misc.Unsafe unsafe, TestWorld world,
                                       List<SkillHudCatalog.PerkSkill> registrations) throws Exception {
        var factory = new PowerFactory<Power>(new Identifier("my_addon", "hud_probe"), new SerializableData(), data -> Power::new);
        Identifier source = new Identifier("my_addon", "hud_probe");
        for (var registration : registrations) {
            for (var formId : registration.forms()) {
                TestPlayer player = player(unsafe, world, formId);
                var base = entries(player);
                int baseExtras = SkillHudLayout.extraCount(base);
                check(baseExtras <= 1, "no locked fourth slot: " + formId);
                check(SkillHudCatalog.entriesForHud(formId, player) == base, "render frames reuse the current tick's selection");

                player.form.formPerkMap.put(registration.tree(), List.of(registration.perk()));
                check(entries(player).equals(base), "purchase record alone does not reserve a blank slot: " + formId);
                Identifier powerId = registration.skill().cooldown();
                if (!PowerTypeRegistry.contains(powerId)) PowerTypeRegistry.register(powerId, new PowerType<>(powerId, factory.read(new JsonObject())));
                var type = PowerTypeRegistry.get(powerId);
                player.form.formPerkMap.clear();
                player.powers.addPower(type, source);
                check(entries(player).equals(base), "stale/unpurchased power does not reveal a slot: " + formId);

                player.form.formPerkMap.put(registration.tree(), List.of(registration.perk()));
                var learned = entries(player);
                check(SkillHudLayout.extraCount(learned) == baseExtras + 1, "learned Perk adds exactly one frame: " + formId);
                var perkEntry = learned.get(learned.size() - 1);
                check(perkEntry.skill().equals(registration.forForm(formId)) && perkEntry.slot()
                        == (baseExtras == 0 ? SkillHudLayout.Slot.LOWER : SkillHudLayout.Slot.UPPER), "lower first, upper second: " + formId);
                check(learned.subList(0, base.size()).equals(base), "main pair and innate extras retain their assignments: " + formId);
                check(SkillHudLayout.bounds(baseExtras + 1, false).height() == (baseExtras == 0 ? 84 : 100), "background grows with the learned slot");
                check(SkillHudCatalog.entriesForHud(FormIdMigration.legacy(formId), player).equals(learned), "legacy form IDs use the same unlock gate");

                player.powers.removePower(type, source);
                check(entries(player).equals(base), "power removal hides the Perk frame even while purchase data remains: " + formId);
                player.powers.addPower(type, source);
                player.form.formPerkMap.clear();
                check(entries(player).equals(base), "Perk reset removes the frame and background extension: " + formId);
                player.form.formPerkMap.put(registration.tree(), List.of(registration.perk()));
                player.form.nowForm = new NormalForm(new Identifier("my_addon", "wild_cat_moonlight_phantom"));
                var switched = entries(player);
                check(switched.size() == 3 && SkillHudLayout.extraCount(switched) == 1
                        && switched.stream().noneMatch(e -> e.skill().equals(registration.forForm(formId))), "unrelated forms cannot display lingering learned powers");
            }
        }
        // Retained powers from forms sharing the same Perk tree must not create duplicate extras.
        TestPlayer player = player(unsafe, world, new Identifier("my_addon", "ocelot_wind_spirit"));
        for (var registration : registrations) {
            player.form.formPerkMap.computeIfAbsent(registration.tree(), ignored -> new ArrayList<>()).add(registration.perk());
            player.powers.addPower(PowerTypeRegistry.get(registration.skill().cooldown()), source);
        }
        check(entries(player).size() == 3, "all purchased Perks and stale powers still produce only the active form's third skill");
        player.form.nowForm = new NormalForm(new Identifier("my_addon", "ocelot_nova"));
        var nova = entries(player);
        check(nova.size() == 3 && nova.get(2).skill().cooldown().getPath().equals("perk_ocelot_nova_metabolic_overload"),
                "switching between forms in one tree selects the current form's implementation");
        TestPlayer rejoined = player(unsafe, world, new Identifier("my_addon", "ocelot_nova"));
        check(entries(rejoined).size() == 2, "a new player entity does not inherit the previous entity's unlocked frame");

        var tidal = registrations.stream().filter(r -> r.skill().cooldown().getPath().equals("perk_axolotl_sp_tidal_pull_charge"))
                .findFirst().orElseThrow();
        TestPlayer axolotl = player(unsafe, world, net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.AXOLOTL_SP);
        axolotl.form.formPerkMap.put(tidal.tree(), List.of(tidal.perk()));
        axolotl.powers.addPower(PowerTypeRegistry.get(tidal.skill().cooldown()), source);
        var abyssal = entries(axolotl).get(3);
        check(abyssal.skill().icon().getPath().endsWith("/perk_tidal_pull.png"), "abyssal overlord keeps its approved pale-water icon");
        axolotl.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.AXOLOTL_FLUORESCENT);
        var fluorescent = entries(axolotl).get(2);
        check(fluorescent.skill().icon().getPath().endsWith("/perk_tidal_pull_fluorescent.png"), "fluorescent axolotl uses its approved teal/violet variant");
        check(fluorescent.slot() == SkillHudLayout.Slot.LOWER && abyssal.slot() == SkillHudLayout.Slot.UPPER,
                "icon variant does not alter native extra-skill assignments");
        check(fluorescent.skill().cooldown().equals(abyssal.skill().cooldown()) && fluorescent.skill().talent() == null,
                "the two appearances retain the same purchased Perk and actual cooldown power");
        check(SkillHudIntegrationProbe.class.getClassLoader().getResource("assets/my_addon/" + fluorescent.skill().icon().getPath()) != null,
                "fluorescent-specific icon is packaged");
        axolotl.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.AXOLOTL_ALING);
        var aling = entries(axolotl).get(2);
        check(aling.skill().icon().equals(fluorescent.skill().icon()) && aling.slot() == SkillHudLayout.Slot.LOWER,
                "Aling shares its main-skill palette and the approved fluorescent tidal-pull artwork");
        check(aling.skill().cooldown().equals(abyssal.skill().cooldown()), "Aling's shared appearance keeps the original cooldown power");
        axolotl.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.AXOLOTL_SP);
        check(entries(axolotl).get(3).skill().icon().equals(abyssal.skill().icon()), "switching back restores the abyssal appearance without changing powers");

        var frostDive = registrations.stream().filter(r -> r.skill().cooldown().getPath().equals("perk_snow_fox_sp_frost_dive"))
                .findFirst().orElseThrow();
        TestPlayer snow = player(unsafe, world, net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.SNOW_FOX_SP);
        snow.form.formPerkMap.put(frostDive.tree(), List.of(frostDive.perk()));
        snow.powers.addPower(PowerTypeRegistry.get(frostDive.skill().cooldown()), source);
        var spiritDive = entries(snow).get(3);
        check(spiritDive.skill().icon().getPath().endsWith("/perk_frost_dive_frost_spirit.png")
                && spiritDive.slot() == SkillHudLayout.Slot.UPPER, "frost spirit uses the approved ice-claw artwork in its upper frame");
        check(SkillHudIntegrationProbe.class.getClassLoader().getResource("assets/my_addon/" + spiritDive.skill().icon().getPath()) != null,
                "approved frost-spirit Perk artwork is packaged");
        snow.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.SNOW_FOX_FROSTSPINE);
        var spineDive = entries(snow).get(2);
        check(spineDive.skill().icon().equals(spiritDive.skill().icon()) && spineDive.slot() == SkillHudLayout.Slot.LOWER,
                "frostspine reuses the approved ice-claw artwork in its own lower frame");
        check(spineDive.skill().cooldown().equals(spiritDive.skill().cooldown()), "both snow-fox appearances retain the same cooldown power");
        snow.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.SNOW_FOX_SP);
        check(entries(snow).get(3).skill().icon().equals(spiritDive.skill().icon()), "switching back restores the approved frost-spirit artwork");

        var judgeRecall = registrations.stream().filter(r -> r.skill().cooldown().getPath().equals("perk_anubis_wolf_sp_soul_recall"))
                .findFirst().orElseThrow();
        var sandRecall = registrations.stream().filter(r -> r.skill().cooldown().getPath().equals("perk_golden_sandstorm_recall"))
                .findFirst().orElseThrow();
        TestPlayer wolf = player(unsafe, world, net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.ANUBIS_WOLF_SP);
        wolf.form.formPerkMap.put(judgeRecall.tree(), List.of(judgeRecall.perk()));
        wolf.powers.addPower(PowerTypeRegistry.get(judgeRecall.skill().cooldown()), source);
        wolf.powers.addPower(PowerTypeRegistry.get(sandRecall.skill().cooldown()), source);
        var judge = entries(wolf).get(2);
        check(judge.skill().icon().getPath().endsWith("/perk_soul_recall_netherjudge.png")
                && judge.slot() == SkillHudLayout.Slot.LOWER, "Netherjudge shows its approved soul-gathering artwork in the third slot");
        check(SkillHudIntegrationProbe.class.getClassLoader().getResource("assets/my_addon/" + judge.skill().icon().getPath()) != null,
                "approved Netherjudge soul-recall artwork is packaged");
        wolf.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.GOLDEN_SANDSTORM_SP);
        var sand = entries(wolf).get(2);
        check(sand.skill().icon().getPath().endsWith("/perk_brand_recall_golden_sandstorm.png")
                && sand.slot() == SkillHudLayout.Slot.LOWER, "Golden Sandstorm uses its approved brand-absorption artwork in the third slot");
        check(!sand.skill().icon().equals(judge.skill().icon()), "brand recall and soul recall retain their distinct approved appearances");
        check(SkillHudIntegrationProbe.class.getClassLoader().getResource("assets/my_addon/" + sand.skill().icon().getPath()) != null,
                "approved Golden Sandstorm artwork is packaged");
        check(sand.skill().cooldown().equals(sandRecall.skill().cooldown()) && judge.skill().cooldown().equals(judgeRecall.skill().cooldown()),
                "shared purchase records and retained powers still use the correct form-specific recall implementation");
        wolf.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.ANUBIS_WOLF_SP);
        check(entries(wolf).get(2).skill().icon().equals(judge.skill().icon()), "switching back restores the Netherjudge soul-gathering icon");

        var windOverload = registrations.stream().filter(r -> r.skill().cooldown().getPath().equals("perk_wind_spirit_metabolic_overload"))
                .findFirst().orElseThrow();
        var novaOverload = registrations.stream().filter(r -> r.skill().cooldown().getPath().equals("perk_ocelot_nova_metabolic_overload"))
                .findFirst().orElseThrow();
        TestPlayer cat = player(unsafe, world, net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.OCELOT_SP);
        cat.form.formPerkMap.put(windOverload.tree(), List.of(windOverload.perk()));
        cat.powers.addPower(PowerTypeRegistry.get(windOverload.skill().cooldown()), source);
        cat.powers.addPower(PowerTypeRegistry.get(novaOverload.skill().cooldown()), source);
        var wind = entries(cat).get(2);
        check(wind.skill().icon().getPath().endsWith("/perk_metabolic_overload_wind_spirit.png")
                && wind.slot() == SkillHudLayout.Slot.LOWER, "Wind Spirit uses its approved running-cat overload artwork in the third slot");
        check(SkillHudIntegrationProbe.class.getClassLoader().getResource("assets/my_addon/" + wind.skill().icon().getPath()) != null,
                "approved Wind Spirit overload artwork is packaged");
        cat.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.OCELOT_NOVA);
        var novaCat = entries(cat).get(2);
        check(novaCat.skill().icon().getPath().endsWith("/perk_metabolic_overload_ocelot_nova.png")
                && !novaCat.skill().icon().equals(wind.skill().icon()) && novaCat.slot() == SkillHudLayout.Slot.LOWER,
                "Nova uses its approved gold/purple overload variant when both forms' powers remain granted");
        check(wind.skill().cooldown().equals(windOverload.skill().cooldown()) && novaCat.skill().cooldown().equals(novaOverload.skill().cooldown()),
                "shared overload purchase records still select each form's actual cooldown power");
        cat.form.nowForm = new NormalForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.OCELOT_SP);
        check(entries(cat).get(2).skill().icon().equals(wind.skill().icon()), "switching back restores the approved Wind Spirit overload icon");
    }

    private static List<SkillHudLayout.Entry<SkillHudCatalog.Skill>> entries(TestPlayer player) {
        player.testWorld.now++;
        return SkillHudCatalog.entriesForHud(player.form.nowForm.getFormID(), player);
    }
    private static TestPlayer player(sun.misc.Unsafe unsafe, TestWorld world, Identifier form) throws Exception {
        TestPlayer player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
        player.testWorld = world;
        player.form = (PlayerFormComponent) unsafe.allocateInstance(PlayerFormComponent.class);
        player.form.nowForm = new NormalForm(form);
        player.form.formPerkMap = new HashMap<>();
        player.evolution = new EvolutionComponent();
        var route = EvolutionRegistry.forWorld(world).getRouteByStartForm(FormIdMigration.canonical(form));
        if (route != null) player.evolution.setRoute(route.routeId);
        player.evolution.setUnlockAll(true);
        player.powers = new PowerHolderComponentImpl(player);
        player.components = ComponentContainer.Factory.builder(TestPlayer.class)
                .component(RegPlayerFormComponent.PLAYER_FORM, p -> p.form)
                .component(RegEvolutionComponent.EVOLUTION, p -> p.evolution)
                .component(PowerHolderComponent.KEY, p -> p.powers).build().createContainer(player);
        return player;
    }
    private static ChargePower power(sun.misc.Unsafe unsafe, TestWorld world) throws Exception {
        TestOwner owner = (TestOwner) unsafe.allocateInstance(TestOwner.class); owner.testWorld = world;
        ChargePower power = (ChargePower) unsafe.allocateInstance(ChargePower.class);
        var entity = Power.class.getDeclaredField("entity"); entity.setAccessible(true); entity.set(power, owner);
        power.ChargeTierList = new ArrayList<>();
        var tier = (ChargePower.ChargeTier) unsafe.allocateInstance(ChargePower.ChargeTier.class); tier.cooldown = 20;
        power.ChargeTierList.add(tier);
        return power;
    }
    public static final class TestWorld extends ServerWorld {
        long now;
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public long getTime() { return now; }
    }
    public static final class TestOwner extends ArmorStandEntity {
        World testWorld;
        private TestOwner() { super(EntityType.ARMOR_STAND, null); }
        @Override public World getWorld() { return testWorld; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean hasStatusEffect(net.minecraft.entity.effect.StatusEffect effect) { return false; }
    }
    public static final class TestPlayer extends ServerPlayerEntity implements ComponentProvider {
        TestWorld testWorld;
        PlayerFormComponent form;
        EvolutionComponent evolution;
        PowerHolderComponentImpl powers;
        ComponentContainer components;
        private TestPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "hud-test")); }
        @Override public World getWorld() { return testWorld; }
        @Override public ComponentContainer getComponentContainer() { return components; }
        @Override public Iterable<ServerPlayerEntity> getRecipientsForComponentSync() { return List.of(); }
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
