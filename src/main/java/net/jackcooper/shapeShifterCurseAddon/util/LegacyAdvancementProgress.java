package net.jackcooper.shapeShifterCurseAddon.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/** Maps only the transformation advancements shipped under the previous namespace. */
public final class LegacyAdvancementProgress {
    private static final Set<String> FORMS = Set.of(
            "allay_sp", "anubis_wolf_sp", "axolotl_aling",
            "axolotl_fluorescent", "axolotl_sp", "bat_desmodus",
            "bat_parasitic_fruit", "fallen_allay_sp", "familiar_fox_mancianima",
            "familiar_fox_red", "familiar_fox_sp", "golden_sandstorm_sp",
            "ocelot_nova", "ocelot_wind_spirit", "snow_fox_frostspine",
            "snow_fox_sp", "spider_moon_weaver", "spider_salticidae",
            "upgrade_axolotl", "upgrade_familiar_fox", "wild_cat_nightmare",
            "wild_cat_sp");

    private LegacyAdvancementProgress() {}

    /** The tracker owns this parse tree, and the vanilla save writes the canonical keys afterwards. */
    public static JsonElement migrate(JsonElement saved) {
        if (saved == null || !saved.isJsonObject()) return saved;
        JsonObject entries = saved.getAsJsonObject();
        for (String form : FORMS) {
            JsonElement previous = entries.remove("my_addon:on_transform_" + form);
            if (previous == null) continue;
            String id = "ssc_addon:on_transform_" + form;
            JsonElement current = entries.get(id);
            if (current == null) {
                entries.add(id, previous);
            } else if (current.isJsonObject() && previous.isJsonObject()) {
                mergeCriteria(current.getAsJsonObject(), previous.getAsJsonObject());
            }
        }
        return saved;
    }

    private static void mergeCriteria(JsonObject current, JsonObject previous) {
        if (!previous.has("criteria") || !previous.get("criteria").isJsonObject()) return;
        JsonObject earned = previous.getAsJsonObject("criteria");
        if (!current.has("criteria")) {
            current.add("criteria", earned);
        } else if (current.get("criteria").isJsonObject()) {
            JsonObject existing = current.getAsJsonObject("criteria");
            earned.entrySet().forEach(entry -> {
                if (!existing.has(entry.getKey())) existing.add(entry.getKey(), entry.getValue());
            });
        }
        // Vanilla computes completion from the merged criteria and the current requirements.
    }
}
