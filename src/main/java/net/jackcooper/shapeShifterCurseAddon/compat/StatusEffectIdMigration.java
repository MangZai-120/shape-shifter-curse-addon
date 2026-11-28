package net.jackcooper.shapeShifterCurseAddon.compat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import net.minecraft.util.Identifier;

/** Keep the saved numeric IDs used by active effects, hidden effects and potion items on Fabric 1.20.1. */
public final class StatusEffectIdMigration {
    private static final Identifier EFFECT_REGISTRY = new Identifier("minecraft", "mob_effect");
    private static final Map<Identifier, Identifier> RENAMED_EFFECTS = Map.of(
            new Identifier("ssca_neomagic", "disorder"), new Identifier("ssc_addon", "disorder"),
            new Identifier("ssca_neomagic", "disintegration"), new Identifier("ssc_addon", "disintegration"),
            new Identifier("ssca_neomagic", "breakdown"), new Identifier("ssc_addon", "breakdown"));

    private StatusEffectIdMigration() {}

    public static Identifier canonical(Identifier id) {
        return id == null ? null : RENAMED_EFFECTS.getOrDefault(id, id);
    }

    public static void migrate(Map<Identifier, Object2IntMap<Identifier>> registries) {
        Object2IntMap<Identifier> effects = registries.get(EFFECT_REGISTRY);
        if (effects == null) return;

        // Validate first: one registered effect cannot decode two distinct numeric IDs in the same save.
        RENAMED_EFFECTS.forEach((oldId, newId) -> {
            if (effects.containsKey(oldId) && effects.containsKey(newId)
                    && effects.getInt(oldId) != effects.getInt(newId)) {
                throw new IllegalStateException("Conflicting saved status effect IDs in data/fabricRegistry.dat: "
                        + oldId + "=" + effects.getInt(oldId) + ", " + newId + "=" + effects.getInt(newId));
            }
        });
        RENAMED_EFFECTS.forEach((oldId, newId) -> {
            if (effects.containsKey(oldId)) {
                int savedId = effects.removeInt(oldId);
                effects.put(newId, savedId);
            }
        });
    }
}
