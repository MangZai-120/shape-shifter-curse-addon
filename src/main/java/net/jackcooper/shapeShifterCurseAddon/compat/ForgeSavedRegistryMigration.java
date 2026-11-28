package net.jackcooper.shapeShifterCurseAddon.compat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

/** Forge 47 saves effects and sounds as K/V lists in its per-world registry snapshots. */
public final class ForgeSavedRegistryMigration {
    private static final Set<String> SOUNDS = Set.of(
            "snow_fox.gather", "snow_fox.frost_storm", "snow_fox.plunge",
            "orcafis.step", "orcafis.gather", "orcafis.break_ground", "orcafis.land", "orcafis.rock_impact",
            "orcafis.sweep", "orcafis.plow", "orcafis.rush", "orcafis.breach", "orcafis.splash",
            "orcafis.slap", "orcafis.slap_snap", "orcafis.slap_double", "orcafis.slap_draw", "orcafis.slap_final",
            "orcafis.tide_pull", "orcafis.crest_pass", "orcafis.backwash", "orcafis.settle", "orcafis.wall",
            "orcafis.undertow_draw", "orcafis.undertow_bite", "orcafis.jet_charge", "orcafis.jet_stream", "orcafis.jet_release");

    private ForgeSavedRegistryMigration() {}

    public static NbtCompound migrate(NbtCompound root) {
        NbtCompound registries = root.getCompound("fml").getCompound("Registries");
        NbtCompound effects = registries.getCompound("minecraft:mob_effect");
        NbtCompound sounds = registries.getCompound("minecraft:sound_event");
        NbtCompound newEffects = migrateSnapshot(effects, StatusEffectIdMigration::canonical);
        NbtCompound newSounds = migrateSnapshot(sounds, ForgeSavedRegistryMigration::sound);
        if (newEffects == effects && newSounds == sounds) return root;
        NbtCompound migrated = root.copy();
        NbtCompound result = migrated.getCompound("fml").getCompound("Registries");
        if (newEffects != effects) result.put("minecraft:mob_effect", newEffects);
        if (newSounds != sounds) result.put("minecraft:sound_event", newSounds);
        return migrated;
    }

    private static Identifier sound(Identifier id) {
        return "my_addon".equals(id.getNamespace()) && SOUNDS.contains(id.getPath())
                ? new Identifier("ssc_addon", id.getPath()) : id;
    }

    private static String canonical(String key, UnaryOperator<Identifier> rename) {
        if (!key.startsWith("my_addon:") && !key.startsWith("ssca_neomagic:")) return key;
        Identifier id = Identifier.tryParse(key);
        return id == null ? key : rename.apply(id).toString();
    }

    private static NbtCompound migrateSnapshot(NbtCompound snapshot, UnaryOperator<Identifier> rename) {
        NbtCompound result = snapshot;
        for (String field : new String[]{"ids", "overrides", "aliases"}) {
            NbtList entries = snapshot.getList(field, NbtElement.COMPOUND_TYPE);
            NbtList migrated = null;
            Set<String> changedKeys = new HashSet<>();
            for (int i = 0; i < entries.size(); i++) {
                NbtCompound row = entries.getCompound(i);
                String key = row.getString("K");
                String mappedKey = canonical(key, rename);
                String target = row.getString("V");
                String mappedTarget = "aliases".equals(field) ? canonical(target, rename) : target;
                if (key.equals(mappedKey) && target.equals(mappedTarget)) continue;
                changedKeys.add(mappedKey);
                if (migrated == null) migrated = entries.copy();
                NbtCompound updated = migrated.getCompound(i);
                updated.putString("K", mappedKey);
                if (!target.equals(mappedTarget)) updated.putString("V", mappedTarget);
            }
            if (migrated == null) continue;
            deduplicate(migrated, field, changedKeys);
            if (result == snapshot) result = snapshot.copy();
            result.put(field, migrated);
        }
        return result;
    }

    private static void deduplicate(NbtList entries, String field, Set<String> changedKeys) {
        Map<String, NbtElement> values = new HashMap<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            NbtCompound row = entries.getCompound(i);
            String key = row.getString("K");
            if (!changedKeys.contains(key)) continue;
            NbtElement value = row.get("V");
            if ("aliases".equals(field) && key.equals(row.getString("V"))) {
                entries.remove(i); // A former alias may now point to its own canonical key.
                continue;
            }
            NbtElement existing = values.putIfAbsent(key, value);
            if (existing == null) continue;
            if (!existing.equals(value)) {
                throw new IllegalStateException("Conflicting saved Forge registry " + field + " for "
                        + key + " in level.dat: " + existing + " and " + value);
            }
            entries.remove(i);
        }
    }
}
