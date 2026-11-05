package net.jackcooper.shapeShifterCurseAddon.config;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

/** Migrates only form keys in SSC's color save; palette names and color payloads are opaque. */
public final class FormColorDataMigration {
    private static final String[] FORM_MAPS = {
            "formDefaultSetting", "customSettingByForm",
            "FCS_form_local_setting_names", "FCS_form_default_setting_names"
    };
    private static final Set<String> RENAMED_IDS = FormIdMigration.aliases().values().stream()
            .map(Object::toString).collect(Collectors.toUnmodifiableSet());

    private FormColorDataMigration() { }

    /** Keep the caller's save untouched and return the same tree when no migration is needed. */
    public static NbtCompound migrate(NbtCompound source) {
        NbtCompound result = source;
        for (String field : FORM_MAPS) {
            if (!source.contains(field, NbtElement.COMPOUND_TYPE)) continue;
            NbtCompound forms = source.getCompound(field);
            for (var alias : FormIdMigration.aliases().entrySet()) {
                String oldId = alias.getKey().toString(), newId = alias.getValue().toString();
                if (!forms.contains(oldId)) continue;
                if (result == source) result = source.copy();
                NbtCompound migrated = result.getCompound(field);
                NbtElement oldValue = migrated.get(oldId), newValue = migrated.get(newId);
                if (newValue == null) {
                    migrated.put(newId, oldValue);
                } else if ("customSettingByForm".equals(field)
                        && oldValue instanceof NbtCompound oldSlots && newValue instanceof NbtCompound newSlots) {
                    // A newly saved slot wins, while the old form's other named slots survive.
                    for (String name : oldSlots.getKeys()) {
                        if (!newSlots.contains(name)) newSlots.put(name, oldSlots.get(name));
                    }
                } else if ("FCS_form_local_setting_names".equals(field)
                        && oldValue instanceof NbtList oldNames && newValue instanceof NbtList newNames
                        && oldNames.getHeldType() == NbtElement.STRING_TYPE
                        && (newNames.isEmpty() || newNames.getHeldType() == NbtElement.STRING_TYPE)) {
                    // Slot indices, including deliberately empty names, retain their existing priority.
                    for (int index = newNames.size(); index < oldNames.size(); index++) {
                        newNames.add(oldNames.get(index));
                    }
                }
                migrated.remove(oldId);
            }
        }
        if (source.contains("unlockedForms", NbtElement.LIST_TYPE)) {
            NbtList unlocked = source.getList("unlockedForms", NbtElement.STRING_TYPE);
            boolean changed = false;
            for (int index = 0; index < unlocked.size(); index++) {
                String oldId = unlocked.getString(index), newId = FormIdMigration.canonical(oldId);
                if (!oldId.equals(newId)) changed = true;
            }
            if (changed) {
                NbtList migrated = new NbtList();
                var seen = new HashSet<String>();
                for (int index = 0; index < unlocked.size(); index++) {
                    String id = FormIdMigration.canonical(unlocked.getString(index));
                    if (!RENAMED_IDS.contains(id) || seen.add(id)) migrated.add(NbtString.of(id));
                }
                if (result == source) result = source.copy();
                result.put("unlockedForms", migrated);
            }
        }
        return result;
    }
}
