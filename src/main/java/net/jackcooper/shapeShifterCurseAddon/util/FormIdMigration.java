package net.jackcooper.shapeShifterCurseAddon.util;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;

/** Form names may change; the existing Origins, powers and their saved state must not. */
public final class FormIdMigration {
    private static final Map<Identifier, Identifier> ALIASES;
    private static final Map<Identifier, Identifier> LEGACY;
    private static final Map<String, String> STRINGS;
    private static final Map<String, String> PATHS;
    private static final String[] COMPONENT_FIELDS = {
            "nowFormID", "currentForm", "fallbackFormID", "BeforeCursedMoonAppliedForm",
            "AfterCursedMoonAppliedForm", "transformTargetForm", "customPotionFormID"
    };
    private static final String[] COMPONENT_LISTS = {"formHistory", "free_perk_forms"};

    static {
        Map<Identifier, Identifier> aliases = new LinkedHashMap<>();
        alias(aliases, "familiar_fox_sp", "familiar_fox_spirit_realm_lord");
        alias(aliases, "snow_fox_sp", "snow_fox_frost_spirit");
        alias(aliases, "allay_sp", "allay_crystal_sound");
        alias(aliases, "axolotl_sp", "axolotl_abyssal_overlord");
        alias(aliases, "wild_cat_sp", "wild_cat_moonlight_phantom");
        alias(aliases, "fallen_allay_sp", "allay_spirit_of_the_fallen");
        alias(aliases, "anubis_wolf_sp", "anubis_wolf_netherjudge");
        alias(aliases, "golden_sandstorm_sp", "anubis_wolf_golden_sandstorm");
        alias(aliases, "upgrade_familiar_fox", "familiar_fox_upgrade");
        alias(aliases, "upgrade_axolotl", "axolotl_upgrade");
        ALIASES = Map.copyOf(aliases);
        Map<Identifier, Identifier> legacy = new LinkedHashMap<>();
        Map<String, String> strings = new LinkedHashMap<>();
        Map<String, String> paths = new LinkedHashMap<>();
        aliases.forEach((oldId, newId) -> {
            legacy.put(newId, oldId);
            strings.put(oldId.toString(), newId.toString());
            paths.put(oldId.getPath(), newId.getPath());
        });
        LEGACY = Map.copyOf(legacy);
        STRINGS = Map.copyOf(strings);
        PATHS = Map.copyOf(paths);
    }

    private FormIdMigration() { }

    private static void alias(Map<Identifier, Identifier> aliases, String oldPath, String newPath) {
        aliases.put(new Identifier("my_addon", oldPath), new Identifier("my_addon", newPath));
    }

    public static Map<Identifier, Identifier> aliases() {
        return ALIASES;
    }

    public static Identifier canonical(Identifier id) {
        return id == null ? null : ALIASES.getOrDefault(id, id);
    }

    /** Only complete, exact old IDs are rewritten; unrelated or malformed strings pass through. */
    public static String canonical(String id) {
        return id == null ? null : STRINGS.getOrDefault(id, id);
    }

    /** For SSCA's own path-keyed keybind/color settings, never for other namespaces. */
    public static String canonicalPath(String path) {
        return path == null ? null : PATHS.getOrDefault(path, path);
    }

    /** Stable resource identity: existing Origins/powers, models and cooldowns retain their IDs. */
    public static Identifier legacy(Identifier id) {
        return id == null ? null : LEGACY.getOrDefault(id, id);
    }

    /** Copy on change: callers may also retain the original save or packet for another consumer. */
    public static NbtCompound migrateComponentNbt(NbtCompound source) {
        NbtCompound result = source;
        for (String key : COMPONENT_FIELDS) {
            if (!source.contains(key, NbtElement.STRING_TYPE)) continue;
            String oldId = source.getString(key);
            String newId = canonical(oldId);
            if (oldId.equals(newId)) continue;
            if (result == source) result = source.copy();
            result.putString(key, newId);
        }
        for (String key : COMPONENT_LISTS) {
            if (!source.contains(key, NbtElement.LIST_TYPE)) continue;
            NbtList list = source.getList(key, NbtElement.STRING_TYPE);
            NbtList changed = null;
            for (int index = 0; index < list.size(); index++) {
                String oldId = list.getString(index);
                String newId = canonical(oldId);
                if (oldId.equals(newId)) continue;
                if (changed == null) changed = list.copy();
                changed.set(index, NbtString.of(newId));
            }
            if (changed != null) {
                if (result == source) result = source.copy();
                result.put(key, changed);
            }
        }
        return result;
    }
}
