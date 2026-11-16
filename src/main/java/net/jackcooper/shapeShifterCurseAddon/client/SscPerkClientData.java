package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.IPerkClient;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.util.util.ISprite;

/** Form-specific text through SSC's client registry, without replacing gameplay Perks. */
public final class SscPerkClientData {
    private SscPerkClientData() {}

    public static void register() {
        register(() -> {
            var client = MinecraftClient.getInstance();
            return client == null || client.player == null ? null : FormUtils.getPlayerForm(client.player);
        });
    }

    static void register(Supplier<IForm> currentForm) {
        var affected = new HashSet<Identifier>();
        for (var tree : List.of(RegPerks.T_FamiliarFox3PerkTree, RegPerks.T_Axolotl3PerkTree,
                RegPerks.T_Spider3PerkTree, RegPerks.T_Bat3PerkTree,
                RegPerks.T_AnubisWolf3PerkTree, RegPerks.T_Ocelot3PerkTree)) {
            affected.addAll(RegPerks.getPerkTree(tree).getAllPerks());
        }
        affected.add(RegPerks.P_SnowFoxFrostDive);
        for (var id : affected) {
            var original = RegPerks.getPerkClientData(id);
            if (original != null && !(original instanceof FormAwarePerk)) {
                RegPerks.registerPerkClientData(new FormAwarePerk(original, currentForm));
            }
        }
    }

    private record FormAwarePerk(IPerkClient original, Supplier<IForm> currentForm) implements IPerkClient {
        @Override public Identifier getID() { return original.getID(); }
        @Override public ISprite getIcon() { return original.getIcon(); }
        @Override public Text getDesc() {
            Text replacement = description(currentForm.get(), getID());
            return replacement == null ? original.getDesc() : replacement;
        }
        @Override public Text getName() {
            IForm form = currentForm.get();
            if (form != null && FormIdentifiers.GOLDEN_SANDSTORM_SP.equals(form.getFormID())
                    && RegPerks.getPerkTree(RegPerks.T_AnubisWolf3PerkTree).getAllPerks().contains(getID())) {
                return Text.translatable("perk.ssc_addon.golden_sandstorm_sp." + getID().getPath() + ".name");
            }
            return original.getName();
        }
    }

    private record Mapping(Identifier tree, String prefix) {}

    static Text description(IForm form, Identifier perk) {
        if (form == null || !"my_addon".equals(form.getFormID().getNamespace())) return null;
        String path = FormIdMigration.legacy(form.getFormID()).getPath();
        if (path.equals("snow_fox_sp") || path.equals("snow_fox_frostspine")) {
            return RegPerks.P_SnowFoxFrostDive.equals(perk)
                    ? Text.translatable("perk.ssc_addon.snow_fox_sp.snow_fox_frost_dive.description") : null;
        }
        // These branches historically replace only SSC-owned nodes, including in extended trees.
        boolean nativeNodesOnly = switch (path) {
            case "familiar_fox_sp", "familiar_fox_red", "familiar_fox_mancianima",
                    "axolotl_sp", "axolotl_fluorescent", "axolotl_aling" -> true;
            default -> false;
        };
        if (nativeNodesOnly && !"shape-shifter-curse".equals(perk.getNamespace())) return null;
        Mapping mapping = switch (path) {
            case "upgrade_familiar_fox" -> new Mapping(RegPerks.T_FamiliarFox3PerkTree, path);
            case "upgrade_axolotl" -> new Mapping(RegPerks.T_Axolotl3PerkTree, path);
            case "spider_salticidae", "spider_moon_weaver" -> new Mapping(RegPerks.T_Spider3PerkTree, path);
            case "bat_parasitic_fruit", "bat_desmodus" -> new Mapping(RegPerks.T_Bat3PerkTree, path);
            case "golden_sandstorm_sp", "anubis_wolf_sp" -> new Mapping(RegPerks.T_AnubisWolf3PerkTree, path);
            case "ocelot_nova" -> new Mapping(RegPerks.T_Ocelot3PerkTree, path);
            case "ocelot_wind_spirit" -> new Mapping(RegPerks.T_Ocelot3PerkTree, "wind_spirit");
            case "familiar_fox_sp", "familiar_fox_red", "familiar_fox_mancianima" -> new Mapping(RegPerks.T_FamiliarFox3PerkTree, path);
            case "axolotl_sp" -> new Mapping(RegPerks.T_Axolotl3PerkTree, path);
            case "axolotl_fluorescent", "axolotl_aling" -> new Mapping(RegPerks.T_Axolotl3PerkTree, "axolotl_fluorescent");
            default -> null;
        };
        return mapping != null && RegPerks.getPerkTree(mapping.tree()).getAllPerks().contains(perk)
                ? Text.translatable("perk.ssc_addon." + mapping.prefix() + "." + perk.getPath() + ".description") : null;
    }
}
