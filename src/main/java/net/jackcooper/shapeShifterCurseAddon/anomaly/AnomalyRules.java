package net.jackcooper.shapeShifterCurseAddon.anomaly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Data-pack configuration parsed once; damage/impact paths only index immutable snapshots. */
public final class AnomalyRules implements SimpleSynchronousResourceReloadListener {
    private static final Identifier RESOURCE = new Identifier("ssc_addon", "anomaly/defaults.json");
    private static final String[] REQUIRED_SPELLS = {
            "fire_bolt", "flame_nova", "meteor", "explosion", "frost_spike", "ice_barrage", "frost_nova",
            "frost_armor", "moonlight_arrow", "lunar_mend", "lunar_veil", "lunar_phase", "curse_mark",
            "dread_whisper", "corrupt_mist", "summon_lunar_spirit", "companion_resonance", "beep_sheep",
            "void_devour", "void_erosion", "death_finale", "space_blink", "space_stride", "space_recall",
            "pocket_space", "domain", "summon_lunar_spirit_bolt", "domain_initial", "domain_pulse"
    };
    private record Config(Map<String, double[]> amounts, Map<Identifier, double[]> spellAmounts, int pulseTicks, String json) {
        double amount(String id, int level) {
            double[] values = amounts.get(normalize(id));
            return values == null ? 0 : values[Math.max(0, Math.min(values.length - 1, level - 1))];
        }
    }
    private static final List<Runnable> RELOAD_LISTENERS = new ArrayList<>();
    private static volatile Config server = defaults();
    private static volatile Config client = server;

    public static double amount(Spell spell, int level) {
        double[] values = server.spellAmounts().get(spell.getId());
        return values == null ? 0 : values[Math.max(0, Math.min(values.length - 1, level - 1))];
    }
    public static double amount(String spell, int level) { return server.amount(spell, level); }
    public static double syntheticAmount(String id, int level) { return amount(id, level); }
    public static double clientAmount(String spell, int level) { return client.amount(spell, level); }
    public static double domainInitial() { return amount("domain_initial", 1); }
    public static double domainPulse() { return amount("domain_pulse", 1); }
    public static int domainPulseTicks() { return server.pulseTicks(); }
    public static String snapshotJson() { return server.json(); }
    public static void onReload(Runnable listener) { RELOAD_LISTENERS.add(java.util.Objects.requireNonNull(listener)); }

    /** The network adapter invokes this on its client thread after checking the connection/dimension. */
    public static void acceptClientSnapshot(String json) { client = parse(json); }
    public static void clearClientSnapshot() { client = defaults(); }

    @Override public Identifier getFabricId() { return new Identifier("ssc_addon", "anomaly_rules"); }
    @Override public void reload(ResourceManager resources) {
        final Config next;
        try (var reader = new InputStreamReader(resources.getResource(RESOURCE).orElseThrow().getInputStream(), StandardCharsets.UTF_8)) {
            next = parse(JsonParser.parseReader(reader).toString());
        } catch (Exception invalid) {
            throw new IllegalStateException("Invalid SSCA anomaly rules; retaining the previous configuration", invalid);
        }
        server = next;
        for (Runnable listener : RELOAD_LISTENERS) listener.run();
    }

    private static String normalize(String id) {
        if (id == null) return "";
        return id.indexOf(':') < 0 ? "ssc_addon:" + id : id;
    }

    private static Config parse(String json) {
        if (json == null || json.length() > 131072) throw new IllegalArgumentException("Oversized anomaly configuration");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("version") || root.get("version").getAsInt() != 1)
            throw new IllegalArgumentException("Unsupported anomaly configuration version");
        int interval = root.get("domain_pulse_ticks").getAsInt();
        if (interval < 1 || interval > 1200) throw new IllegalArgumentException("Invalid domain anomaly interval");
        JsonObject spells = root.getAsJsonObject("spells");
        if (spells == null || spells.size() > 512) throw new IllegalArgumentException("Invalid anomaly spell table");
        Map<String, double[]> amounts = new LinkedHashMap<>();
        Map<Identifier, double[]> identities = new LinkedHashMap<>();
        for (var entry : spells.entrySet()) {
            String id = normalize(entry.getKey());
            Identifier identity = Identifier.tryParse(id);
            if (id.length() > 160 || identity == null || amounts.containsKey(id))
                throw new IllegalArgumentException("Invalid or duplicate anomaly spell ID: " + id);
            JsonArray array = entry.getValue().getAsJsonArray();
            if (array.isEmpty() || array.size() > 5) throw new IllegalArgumentException("Invalid anomaly levels: " + id);
            double[] values = new double[array.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = array.get(i).getAsDouble();
                if (!Double.isFinite(values[i]) || values[i] < 0 || values[i] > AnomalyLedger.MAXIMUM)
                    throw new IllegalArgumentException("Invalid anomaly amount: " + id);
            }
            amounts.put(id, values);
            identities.put(identity, values);
        }
        for (String id : REQUIRED_SPELLS) if (!amounts.containsKey(normalize(id)))
            throw new IllegalArgumentException("Missing anomaly spell rule: " + id);
        return new Config(Map.copyOf(amounts), Map.copyOf(identities), interval, root.toString());
    }

    private static Config defaults() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("domain_pulse_ticks", 40);
        JsonObject spells = new JsonObject();
        put(spells, "fire_bolt", 120, 153, 185, 218, 250);
        put(spells, "flame_nova", 180, 215, 250, 285, 320);
        put(spells, "meteor", 240, 275, 310, 345, 380);
        put(spells, "explosion", 500);
        put(spells, "frost_spike", 180, 210, 240, 270, 300);
        put(spells, "ice_barrage", 70, 83, 95, 108, 120);
        put(spells, "frost_nova", 280, 315, 350, 385, 420);
        put(spells, "moonlight_arrow", 140, 170, 200, 230, 260);
        put(spells, "curse_mark", 160, 190, 220, 250, 280);
        put(spells, "dread_whisper", 200, 235, 270, 305, 340);
        put(spells, "corrupt_mist", 60, 70, 80, 90, 100);
        put(spells, "beep_sheep", 220, 255, 290, 325, 360);
        put(spells, "void_devour", 210, 245, 280, 315, 350);
        put(spells, "void_erosion", 190, 225, 260, 295, 330);
        put(spells, "death_finale", 500);
        put(spells, "summon_lunar_spirit_bolt", 25, 29, 33, 36, 40);
        put(spells, "domain_initial", 200);
        put(spells, "domain_pulse", 50);
        for (String id : REQUIRED_SPELLS) if (!spells.has(normalize(id))) put(spells, id, 0, 0, 0, 0, 0);
        root.add("spells", spells);
        return parse(root.toString());
    }

    private static void put(JsonObject into, String id, double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(value);
        into.add(normalize(id), array);
    }
}
