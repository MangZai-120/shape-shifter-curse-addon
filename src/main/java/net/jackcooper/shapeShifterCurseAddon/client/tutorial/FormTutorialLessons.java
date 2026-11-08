package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.jackcooper.shapeShifterCurseAddon.tutorial.FormTutorialCatalog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-only illustrated lessons. Each entry teaches exactly one named mechanic. */
public final class FormTutorialLessons {
    private static final Identifier DATA = new Identifier("my_addon", "tutorial/form_lessons.json");
    private static volatile Map<Identifier, List<Lesson>> forms = Map.of();

    public enum Kind { ACTIVE, PASSIVE, TRAIT }
    public enum Focus { SELF, TARGET, ALLY, MINIONS }
    public enum Diagram {
        PROJECTILE, AREA, DASH, HEAL, SHIELD, SUMMON, RESOURCE, STEALTH,
        MARK, WEB, FLIGHT, MELEE, EVOLUTION, CHAIN, WEATHER, SPELL
    }

    public record Lesson(String id, Kind kind, Identifier icon, Diagram diagram, Focus focus, String power, String node) {
        public String key(String section) { return "tutorial.ssc_addon.form.lesson." + id + "." + section; }
    }

    private FormTutorialLessons() {}

    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("ssc_addon", "form_lessons"); }
            @Override public void reload(ResourceManager manager) { load(manager); }
        });
    }

    public static List<Lesson> forForm(Identifier form) {
        if (forms.isEmpty()) load(MinecraftClient.getInstance().getResourceManager());
        return forms.getOrDefault(form, List.of());
    }

    private static void load(ResourceManager manager) {
        try (var reader = new InputStreamReader(manager.getResource(DATA).orElseThrow().getInputStream(), StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema_version").getAsInt() != 1) throw new IllegalArgumentException("Unknown lesson schema");
            Map<String, Lesson> lessons = new LinkedHashMap<>();
            for (var entry : root.getAsJsonObject("lessons").entrySet()) {
                var data = entry.getValue().getAsJsonObject();
                Identifier icon = new Identifier(data.get("icon").getAsString());
                if (manager.getResource(icon).isEmpty()) throw new IllegalArgumentException("Missing tutorial illustration: " + icon);
                lessons.put(entry.getKey(), new Lesson(entry.getKey(), Kind.valueOf(data.get("kind").getAsString()), icon,
                        Diagram.valueOf(data.get("diagram").getAsString()),
                        data.has("focus") ? Focus.valueOf(data.get("focus").getAsString()) : Focus.TARGET,
                        data.has("power") ? data.get("power").getAsString() : "",
                        data.has("node") ? data.get("node").getAsString() : ""));
            }
            Map<Identifier, List<Lesson>> next = new LinkedHashMap<>();
            for (var entry : root.getAsJsonObject("forms").entrySet()) {
                Identifier id = new Identifier(entry.getKey());
                if (!FormTutorialCatalog.supports(id)) throw new IllegalArgumentException("Unsupported lesson form: " + id);
                List<Lesson> pages = new ArrayList<>();
                for (var value : entry.getValue().getAsJsonArray()) {
                    Lesson lesson = lessons.get(value.getAsString());
                    if (lesson == null || pages.contains(lesson)) throw new IllegalArgumentException("Invalid or duplicate lesson: " + value);
                    pages.add(lesson);
                }
                if (pages.isEmpty()) throw new IllegalArgumentException("Empty form tutorial: " + id);
                next.put(id, List.copyOf(pages));
            }
            if (!next.keySet().equals(FormTutorialCatalog.forms())) throw new IllegalArgumentException("Incomplete form tutorial");
            forms = Map.copyOf(next);
        } catch (Exception error) {
            org.slf4j.LoggerFactory.getLogger("ssc_addon/form_tutorial")
                    .error("Cannot load illustrated form lessons; retaining previous lessons", error);
        }
    }
}
