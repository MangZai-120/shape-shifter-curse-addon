package net.jackcooper.shapeShifterCurseAddon.balance;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps data/ssc_addon/balance/{directory}/{id}.json to a schema scope. */
public final class BalanceDocuments {
    private BalanceDocuments() {}

    public static Map<String, Object> normalize(String relativePath, String source, String text) {
        var tree = BalanceJson.parse(source, text).tree;
        Object version = tree.get("schema_version");
        if (!(version instanceof Long value) || value != BalanceSchema.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(source + ": missing or incompatible schema_version");
        }
        // A root-level file is an aggregate patch, retained for existing GLM examples.
        if (!relativePath.contains("/")) return tree;
        String[] parts = relativePath.replaceFirst("\\.json$", "").split("/");
        if (parts.length != 2 || !java.util.Set.of("forms", "abilities", "spells", "systems").contains(parts[0])) {
            throw new IllegalArgumentException(source + ": expected <forms|abilities|spells|systems>/<id>.json");
        }
        Map<String, Object> fields = new LinkedHashMap<>(tree);
        fields.remove("schema_version");
        return Map.of("schema_version", version, parts[0], Map.of(parts[1], fields));
    }
}
