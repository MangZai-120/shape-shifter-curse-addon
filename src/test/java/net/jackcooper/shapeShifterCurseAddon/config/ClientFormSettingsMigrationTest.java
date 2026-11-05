package net.jackcooper.shapeShifterCurseAddon.config;

import com.google.gson.Gson;
import java.util.HashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

/** Actual old config JSON and SSC color-save NBT fixtures, independent of a world or renderer. */
public final class ClientFormSettingsMigrationTest {
    private static final String[][] RENAMED = {
            {"familiar_fox_sp", "familiar_fox_spirit_realm_lord"},
            {"snow_fox_sp", "snow_fox_frost_spirit"},
            {"allay_sp", "allay_crystal_sound"},
            {"axolotl_sp", "axolotl_abyssal_overlord"},
            {"wild_cat_sp", "wild_cat_moonlight_phantom"},
            {"fallen_allay_sp", "allay_spirit_of_the_fallen"},
            {"anubis_wolf_sp", "anubis_wolf_netherjudge"},
            {"golden_sandstorm_sp", "anubis_wolf_golden_sandstorm"},
            {"upgrade_familiar_fox", "familiar_fox_upgrade"},
            {"upgrade_axolotl", "axolotl_upgrade"}
    };
    private static int checks;

    public static void main(String[] args) {
        keybinds();
        oldColorSaves();
        mixedColorSaves();
        unrelatedSettings();
        if (Boolean.getBoolean("ssca.testMixinHooks")) actualColorLoad();
        System.out.println("Client form settings migration passed (" + checks + " checks).");
    }

    private static void keybinds() {
        Gson gson = new Gson();
        for (String[] alias : RENAMED) {
            String json = "{\"enableColorEditor\":true,\"cdBarPosOffsetX\":123,\"formKeybinds\":{\""
                    + alias[0] + "\":{\"enabled\":true,\"primaryKey\":\"key.keyboard.r\","
                    + "\"secondaryKey\":\"key.mouse.right\"}}}";
            SSCAddonClientConfig config = gson.fromJson(json, SSCAddonClientConfig.class);
            config.validatePostLoad();
            var keys = config.getFormKeybind(alias[1]);
            check(keys != null && keys.enabled, "Old enabled custom binding remains enabled");
            check("key.keyboard.r".equals(keys.primaryKey) && "key.mouse.right".equals(keys.secondaryKey),
                    "Both physical keyboard and mouse bindings survive");
            check(config.getFormKeybind(alias[0]) == keys, "Old and new callers share the same binding");
            check(!config.formKeybinds.containsKey(alias[0]), "Saved bindings use the new form path");
            check(config.enableColorEditor && config.cdBarPosOffsetX == 123, "Unrelated UI choices are retained");
            String saved = gson.toJson(config);
            config.migrateFormKeybinds();
            check(saved.equals(gson.toJson(config)), "Repeated migration is stable and transient cache is not saved");

            var preferred = new SSCAddonClientConfig.FormKeybind();
            preferred.primaryKey = "key.keyboard.t";
            preferred.secondaryKey = "key.mouse.middle";
            config.formKeybinds = new HashMap<>();
            config.formKeybinds.put(alias[0], keys);
            config.formKeybinds.put(alias[1], preferred);
            check(config.getFormKeybind(alias[0]) == preferred && !preferred.enabled,
                    "A replaced map migrates once and an explicit new disabled entry retains priority");
            check("key.keyboard.t".equals(preferred.primaryKey) && "key.mouse.middle".equals(preferred.secondaryKey),
                    "Legacy keys never overwrite newer settings");
        }
        var config = new SSCAddonClientConfig();
        var foreign = new SSCAddonClientConfig.FormKeybind();
        config.formKeybinds.put("other_addon:familiar_fox_sp", foreign);
        config.formKeybinds.put("unknown_form", foreign);
        config.migrateFormKeybinds();
        check(config.formKeybinds.get("other_addon:familiar_fox_sp") == foreign
                        && config.formKeybinds.get("unknown_form") == foreign,
                "Unknown forms and other namespaces remain untouched");
        config.formKeybinds = null;
        check(config.getFormKeybind("familiar_fox_sp") == null, "A null old config map recovers without enabling keys");
        var defaults = config.getOrCreateFormKeybind("familiar_fox_sp");
        check(!defaults.enabled && "key.keyboard.unknown".equals(defaults.primaryKey)
                        && "key.keyboard.unknown".equals(defaults.secondaryKey),
                "Creating a binding preserves the original disabled and unbound defaults");
        check(config.formKeybinds.containsKey("familiar_fox_spirit_realm_lord"), "New settings use the current ID");
    }

    private static void oldColorSaves() {
        for (String[] alias : RENAMED) {
            String oldId = "my_addon:" + alias[0], newId = "my_addon:" + alias[1];
            NbtCompound source = new NbtCompound();
            source.putBoolean("enableDefaultFormColor", false);
            source.put("formDefaultSetting", entry(oldId, color(0x12345678)));
            source.put("customSettingByForm", entry(oldId, entry("fcs_0", color(0xAABBCCDD))));
            source.put("FCS_form_local_setting_names", entry(oldId, names("first", "", "third")));
            source.put("FCS_form_default_setting_names", entry(oldId, NbtString.of("my favorite")));
            source.put("unlockedForms", names("shape-shifter-curse:original", oldId, "other_addon:allay_sp"));
            NbtCompound before = source.copy();
            NbtCompound migrated = FormColorDataMigration.migrate(source);
            check(migrated != source && source.equals(before), "Color migration copies without mutating the loaded save");
            check(migrated.getCompound("formDefaultSetting").getCompound(newId).equals(color(0x12345678)),
                    "All default color channels, opacity and reverse flags survive");
            check(migrated.getCompound("customSettingByForm").getCompound(newId).getCompound("fcs_0")
                    .equals(color(0xAABBCCDD)), "Local preset colors retain their slots");
            check(migrated.getCompound("FCS_form_local_setting_names").get(newId).equals(names("first", "", "third")),
                    "Preset names and deliberately empty names keep their indices");
            check("my favorite".equals(migrated.getCompound("FCS_form_default_setting_names").getString(newId)),
                    "The default preset name is retained");
            check(migrated.getList("unlockedForms", NbtElement.STRING_TYPE).equals(
                    names("shape-shifter-curse:original", newId, "other_addon:allay_sp")),
                    "Unlock history preserves order and unrelated namespaces");
            check(!migrated.getBoolean("enableDefaultFormColor"), "Migration never enables automatic colors");
            check(FormColorDataMigration.migrate(migrated) == migrated, "Current saves do not get copied again");
        }
    }

    private static void mixedColorSaves() {
        String oldId = "my_addon:allay_sp", newId = "my_addon:allay_crystal_sound";
        NbtCompound source = new NbtCompound();
        NbtCompound defaults = entry(oldId, color(1));
        defaults.put(newId, color(2));
        source.put("formDefaultSetting", defaults);
        NbtCompound oldSlots = entry("fcs_0", color(10));
        oldSlots.put("fcs_1", color(11));
        oldSlots.put("named by player", color(12));
        NbtCompound allSlots = entry(oldId, oldSlots);
        allSlots.put(newId, entry("fcs_0", color(20)));
        source.put("customSettingByForm", allSlots);
        NbtCompound slotNames = entry(oldId, names("old first", "old second", "old third"));
        slotNames.put(newId, names(""));
        source.put("FCS_form_local_setting_names", slotNames);
        NbtCompound defaultNames = entry(oldId, NbtString.of("old name"));
        defaultNames.put(newId, NbtString.of("new name"));
        source.put("FCS_form_default_setting_names", defaultNames);
        source.put("unlockedForms", names(oldId, "other_addon:form", newId, "other_addon:form"));
        NbtCompound before = source.copy();
        NbtCompound migrated = FormColorDataMigration.migrate(source);
        check(source.equals(before), "Merging never alters the caller's original data");
        check(migrated.getCompound("formDefaultSetting").getCompound(newId).equals(color(2)),
                "A new default color wins as one complete setting");
        NbtCompound slots = migrated.getCompound("customSettingByForm").getCompound(newId);
        check(slots.getCompound("fcs_0").equals(color(20)), "New local slots retain priority");
        check(slots.getCompound("fcs_1").equals(color(11)) && slots.getCompound("named by player").equals(color(12)),
                "Old local slots absent from the new form are merged instead of discarded");
        check(migrated.getCompound("FCS_form_local_setting_names").get(newId)
                        .equals(names("", "old second", "old third")),
                "New slot names, including an intentional blank, win; missing slots retain old names");
        check("new name".equals(migrated.getCompound("FCS_form_default_setting_names").getString(newId)),
                "A newly edited default name wins");
        check(migrated.getList("unlockedForms", NbtElement.STRING_TYPE)
                        .equals(names(newId, "other_addon:form", "other_addon:form")),
                "Equivalent SSCA unlocks collapse without reordering or editing unrelated records");
        check(FormColorDataMigration.migrate(migrated) == migrated, "Merged saves are idempotent");
    }

    private static void unrelatedSettings() {
        NbtCompound source = new NbtCompound();
        source.put("formDefaultSetting", entry("other_addon:allay_sp", color(1)));
        source.put("customSetting", entry("my_addon:allay_sp", color(2)));
        source.put("FCS_global_setting_names", names("my_addon:allay_sp"));
        source.put("V2_FCS_global_setting_names", names("my_addon:allay_sp"));
        source.put("unlockedForms", names("other_addon:allay_sp", "other_addon:allay_sp"));
        source.putString("externalData", "my_addon:allay_sp");
        check(FormColorDataMigration.migrate(source) == source,
                "Global palette names, third-party forms and arbitrary strings are never treated as form IDs");
        source.put("customSettingByForm", NbtString.of("bad data"));
        source.put("unlockedForms", names());
        check(FormColorDataMigration.migrate(source) == source, "Unrecognized or absent sections pass through intact");
    }

    /** Run through Fabric/Knot to verify the production injection, rather than just its helper. */
    private static void actualColorLoad() {
        String oldId = "my_addon:allay_sp", newId = "my_addon:allay_crystal_sound";
        NbtCompound source = new NbtCompound();
        source.putBoolean("enableDefaultFormColor", false);
        source.put("formDefaultSetting", entry(oldId, color(0x12345678)));
        source.put("customSettingByForm", entry(oldId, entry("fcs_0", color(0x87654321))));
        source.put("FCS_form_local_setting_names", entry(oldId, names("my old slot")));
        source.put("FCS_form_default_setting_names", entry(oldId, NbtString.of("my default")));
        source.put("unlockedForms", names(oldId));
        NbtCompound before = source.copy();
        var data = new net.onixary.shapeShifterCurseFabric.util.FormColorData();
        data.loadCompound(source);
        var oldForm = new net.minecraft.util.Identifier(oldId);
        var newForm = new net.minecraft.util.Identifier(newId);
        check(data.formDefaultSetting.containsKey(newForm) && !data.formDefaultSetting.containsKey(oldForm),
                "The real SSC loadCompound injection uses the new default-color key");
        check(data.formDefaultSetting.get(newForm).getPrimaryColor() == 0x12345678
                        && data.customSettingByForm.get(newForm).get("fcs_0").getPrimaryColor() == 0x87654321,
                "The real SSC loader retains default and local colors exactly");
        check("my old slot".equals(data.getName_LocalFormSlot(newForm, 0))
                        && "my default".equals(data.getName_DefaultSlot(newForm)),
                "The real menu-name lookup finds migrated labels");
        check(data.isUnlock(newForm) && !data.enableDefaultFormColor, "Unlocks and the disabled switch survive real loading");
        check(source.equals(before), "The load hook never changes the caller's NBT");
        NbtCompound saved = data.saveCompound();
        check(saved.getCompound("formDefaultSetting").contains(newId)
                        && !saved.getCompound("formDefaultSetting").contains(oldId),
                "SSC's normal saver persists the migrated ID without a separate save format");
        var reloaded = new net.onixary.shapeShifterCurseFabric.util.FormColorData();
        reloaded.loadCompound(saved);
        check(reloaded.saveCompound().equals(saved), "A complete save/load cycle retains all migrated settings");
    }

    private static NbtCompound color(int primary) {
        NbtCompound color = new NbtCompound();
        color.putInt("primaryColor", primary);
        color.putInt("accentColor1", 0x23456789);
        color.putInt("accentColor2", 0x3456789A);
        color.putInt("eyeColorA", 0x456789AB);
        color.putInt("eyeColorB", 0x56789ABC);
        color.putBoolean("primaryGreyReverse", true);
        color.putBoolean("accent1GreyReverse", false);
        color.putBoolean("accent2GreyReverse", true);
        return color;
    }

    private static NbtCompound entry(String key, NbtElement value) {
        NbtCompound compound = new NbtCompound();
        compound.put(key, value);
        return compound;
    }

    private static NbtList names(String... values) {
        NbtList list = new NbtList();
        for (String value : values) list.add(NbtString.of(value));
        return list;
    }

    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
}
