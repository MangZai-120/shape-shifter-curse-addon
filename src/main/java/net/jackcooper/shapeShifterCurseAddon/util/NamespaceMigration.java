package net.jackcooper.shapeShifterCurseAddon.util;

import io.github.apace100.apoli.power.PowerTypeRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginLayers;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginRegistry;

/** Migrate identifiers in serialized Origins and Apoli fields; individual power payloads are opaque. */
public final class NamespaceMigration {
    private NamespaceMigration() { }

    /** A converted pack and an independent pack can both define the same path. Prefer a live original ID. */
    private static String registeredId(String saved, Predicate<Identifier> registered) {
        if (!saved.startsWith("my_addon:") && !saved.startsWith("ssca_neomagic:")) return saved;
        Identifier original = Identifier.tryParse(saved);
        if (original == null || registered.test(original)) return saved;
        Identifier target = new Identifier("ssc_addon", original.getPath());
        return registered.test(target) ? target.toString() : saved;
    }

    public static String powerId(String saved) {
        return registeredId(saved, PowerTypeRegistry::contains);
    }

    private static String sourceId(String saved) {
        if (saved.equals("my_addon:snow_fox_talents") && !OriginRegistry.contains(new Identifier(saved))) {
            return "ssc_addon:snow_fox_talents";
        }
        return registeredId(saved, OriginRegistry::contains);
    }

    /** Apoli stores equipment powers separately from an entity's grants. Preserve slot, flags and item data. */
    public static NbtCompound stackPowers(NbtCompound stack) {
        if (stack == null || !(stack.get("tag") instanceof NbtCompound tag)
                || !(tag.get("Powers") instanceof NbtList powers) || powers.getHeldType() != NbtElement.COMPOUND_TYPE) {
            return stack;
        }
        NbtCompound result = stack;
        for (int i = 0; i < powers.size(); i++) {
            NbtCompound entry = powers.getCompound(i);
            if (!entry.contains("Slot", NbtElement.STRING_TYPE)) continue;
            String saved = entry.getString("Power");
            String current = powerId(saved);
            if (current.equals(saved)) continue;
            if (result == stack) result = stack.copy();
            result.getCompound("tag").getList("Powers", NbtElement.COMPOUND_TYPE).getCompound(i).putString("Power", current);
        }
        return result;
    }

    public static NbtCompound powers(NbtCompound tag) {
        NbtCompound result = tag;
        NbtList powers = tag.getList("Powers", NbtElement.COMPOUND_TYPE);
        Set<String> renamed = null;
        for (int i = 0; i < powers.size(); i++) {
            NbtCompound row = powers.getCompound(i);
            String oldType = row.getString("Type");
            String type = powerId(oldType);
            NbtList sources = row.getList("Sources", NbtElement.STRING_TYPE);
            NbtList changedSources = null;
            for (int j = 0; j < sources.size(); j++) {
                String old = sources.getString(j);
                String current = sourceId(old);
                if (!old.equals(current)) {
                    if (changedSources == null) changedSources = sources.copy();
                    changedSources.set(j, NbtString.of(current));
                }
            }
            if (type.equals(oldType) && changedSources == null) continue;
            if (result == tag) result = tag.copy();
            NbtCompound changed = result.getList("Powers", NbtElement.COMPOUND_TYPE).getCompound(i);
            if (!type.equals(oldType)) {
                changed.putString("Type", type);
                if (renamed == null) renamed = new HashSet<>();
                renamed.add(type);
            }
            if (changedSources != null) {
                NbtList unique = new NbtList();
                for (NbtElement source : changedSources) if (!unique.contains(source)) unique.add(source);
                changed.put("Sources", unique);
            }
        }
        if (renamed != null) mergeDuplicatePowers(powers, result, renamed);
        return result;
    }

    /** In a mixed save, keep the canonical power's Data and the union of its grants. */
    private static void mergeDuplicatePowers(NbtList original, NbtCompound result, Set<String> renamed) {
        NbtList rows = result.getList("Powers", NbtElement.COMPOUND_TYPE);
        Map<String, NbtCompound> preferred = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            NbtCompound row = rows.getCompound(i);
            String type = row.getString("Type");
            if (!renamed.contains(type)) continue;
            if (type.equals(original.getCompound(i).getString("Type"))) preferred.put(type, row);
            else preferred.putIfAbsent(type, row);
        }
        for (int i = rows.size() - 1; i >= 0; i--) {
            NbtCompound row = rows.getCompound(i);
            NbtCompound keep = preferred.get(row.getString("Type"));
            if (keep == null || row == keep) continue;
            NbtList incoming = row.getList("Sources", NbtElement.STRING_TYPE);
            if (!incoming.isEmpty()) {
                NbtList sources = keep.getList("Sources", NbtElement.STRING_TYPE);
                for (NbtElement source : incoming) if (!sources.contains(source)) sources.add(source);
                keep.put("Sources", sources);
            }
            rows.remove(i);
        }
    }

    public static NbtCompound origins(NbtCompound tag) {
        NbtCompound result = powers(tag); // Older Origins versions stored their power data here too.
        String old = tag.getString("Origin");
        String migrated = registeredId(old, OriginRegistry::contains);
        if (!old.equals(migrated)) {
            if (result == tag) result = tag.copy();
            result.putString("Origin", migrated);
        }
        NbtList layers = tag.getList("OriginLayers", NbtElement.COMPOUND_TYPE);
        for (int i = layers.size() - 1; i >= 0; i--) {
            NbtCompound entry = layers.getCompound(i);
            String oldOrigin = entry.getString("Origin"), oldLayer = entry.getString("Layer");
            String origin = registeredId(oldOrigin, OriginRegistry::contains);
            String layer = registeredId(oldLayer, NamespaceMigration::hasLayer);
            boolean renamedLayer = !layer.equals(oldLayer);
            if (!renamedLayer && origin.equals(oldOrigin)) continue;
            if (result == tag) result = tag.copy();
            NbtList updated = result.getList("OriginLayers", NbtElement.COMPOUND_TYPE);
            // A selection already saved under the new layer wins regardless of the order of the rows.
            if (renamedLayer && layers.stream().anyMatch(row -> layer.equals(((NbtCompound) row).getString("Layer")))) {
                updated.remove(i);
                continue;
            }
            if (renamedLayer) updated.getCompound(i).putString("Layer", layer);
            if (!origin.equals(oldOrigin)) updated.getCompound(i).putString("Origin", origin);
        }
        return result;
    }

    private static boolean hasLayer(Identifier id) {
        return OriginLayers.getLayers().stream().anyMatch(layer -> id.equals(layer.getIdentifier()));
    }
}
