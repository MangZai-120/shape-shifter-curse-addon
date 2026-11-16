package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;

/** Exercises SSC's real public lookup API while the selected form changes. */
public final class SscPerkClientDataTest {
    private static int checks;
    private record Case(Identifier form, Identifier tree, String prefix) {}

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        var cases = List.of(
                new Case(FormIdentifiers.UPGRADE_FAMILIAR_FOX, RegPerks.T_FamiliarFox3PerkTree, "upgrade_familiar_fox"),
                new Case(FormIdentifiers.FAMILIAR_FOX_SP, RegPerks.T_FamiliarFox3PerkTree, "familiar_fox_sp"),
                new Case(FormIdentifiers.FAMILIAR_FOX_RED, RegPerks.T_FamiliarFox3PerkTree, "familiar_fox_red"),
                new Case(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA, RegPerks.T_FamiliarFox3PerkTree, "familiar_fox_mancianima"),
                new Case(FormIdentifiers.UPGRADE_AXOLOTL, RegPerks.T_Axolotl3PerkTree, "upgrade_axolotl"),
                new Case(FormIdentifiers.AXOLOTL_SP, RegPerks.T_Axolotl3PerkTree, "axolotl_sp"),
                new Case(FormIdentifiers.AXOLOTL_FLUORESCENT, RegPerks.T_Axolotl3PerkTree, "axolotl_fluorescent"),
                new Case(FormIdentifiers.AXOLOTL_ALING, RegPerks.T_Axolotl3PerkTree, "axolotl_fluorescent"),
                new Case(FormIdentifiers.SPIDER_SALTICIDAE, RegPerks.T_Spider3PerkTree, "spider_salticidae"),
                new Case(FormIdentifiers.SPIDER_MOON_WEAVER, RegPerks.T_Spider3PerkTree, "spider_moon_weaver"),
                new Case(FormIdentifiers.BAT_PARASITIC_FRUIT, RegPerks.T_Bat3PerkTree, "bat_parasitic_fruit"),
                new Case(FormIdentifiers.BAT_DESMODUS, RegPerks.T_Bat3PerkTree, "bat_desmodus"),
                new Case(FormIdentifiers.ANUBIS_WOLF_SP, RegPerks.T_AnubisWolf3PerkTree, "anubis_wolf_sp"),
                new Case(FormIdentifiers.GOLDEN_SANDSTORM_SP, RegPerks.T_AnubisWolf3PerkTree, "golden_sandstorm_sp"),
                new Case(FormIdentifiers.OCELOT_NOVA, RegPerks.T_Ocelot3PerkTree, "ocelot_nova"),
                new Case(FormIdentifiers.OCELOT_SP, RegPerks.T_Ocelot3PerkTree, "wind_spirit"));
        var original = new HashMap<>(RegPerks.PerkClientRegistry);
        var gameplay = new HashMap<>(RegPerks.PerkRegistry);
        var current = new AtomicReference<IForm>();
        SscPerkClientData.register(current::get);
        var installed = new HashMap<>(RegPerks.PerkClientRegistry);
        SscPerkClientData.register(current::get);
        check(installed.equals(RegPerks.PerkClientRegistry), "Repeated registration does not stack wrappers");
        check(gameplay.equals(RegPerks.PerkRegistry), "Gameplay Perks, costs and powers retain their original instances");
        for (var entry : original.entrySet()) {
            var id = entry.getKey();
            check(RegPerks.getPerkDescription(id).equals(entry.getValue().getDesc()), "No player retains original description: " + id);
            check(RegPerks.getPerkName(id).equals(entry.getValue().getName()), "No player retains original name: " + id);
            check(RegPerks.getPerkIcon(id) == entry.getValue().getIcon(), "Icon is preserved: " + id);
        }
        for (var item : cases) {
            current.set(new NormalForm(item.form()));
            for (var perk : RegPerks.getPerkTree(item.tree()).getAllPerks()) {
                check(key(RegPerks.getPerkDescription(perk)).equals("perk.ssc_addon." + item.prefix() + "." + perk.getPath() + ".description"),
                        "Live form switch selects the right description: " + item.form() + " / " + perk);
                if (item.form().equals(FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
                    check(key(RegPerks.getPerkName(perk)).equals("perk.ssc_addon.golden_sandstorm_sp." + perk.getPath() + ".name"),
                            "Golden Sandstorm has its own Perk names");
                } else {
                    check(RegPerks.getPerkName(perk).equals(original.get(perk).getName()), "Other forms retain original names");
                }
            }
        }
        for (var form : List.of(FormIdentifiers.SNOW_FOX_SP, FormIdentifiers.SNOW_FOX_FROSTSPINE)) {
            current.set(new NormalForm(form));
            check(key(RegPerks.getPerkDescription(RegPerks.P_SnowFoxFrostDive)).equals("perk.ssc_addon.snow_fox_sp.snow_fox_frost_dive.description"),
                    "Both snow fox forms retain their frost-dive description");
            check(RegPerks.getPerkDescription(RegPerks.P_AxolotlVegetation).equals(original.get(RegPerks.P_AxolotlVegetation).getDesc()),
                    "Unrelated tree retains original text");
        }
        current.set(new NormalForm(new Identifier("other", "axolotl_sp")));
        check(RegPerks.getPerkDescription(RegPerks.P_AxolotlVegetation).equals(original.get(RegPerks.P_AxolotlVegetation).getDesc()),
                "A foreign form with the same path is unaffected");
        net.jackcooper.shapeShifterCurseAddon.client.hud.HudCustomizationIntegrationTest.run(SscPerkClientDataTest.class.getClassLoader());
        System.out.println("SSC Perk client registry: " + checks + " checks passed");
    }

    private static String key(Text text) { return ((TranslatableTextContent) text.getContent()).getKey(); }
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
}
