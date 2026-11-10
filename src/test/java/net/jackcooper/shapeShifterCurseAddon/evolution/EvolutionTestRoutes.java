package net.jackcooper.shapeShifterCurseAddon.evolution;

import com.google.gson.JsonParser;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Real route resources shared by the isolated probes and the in-game lifecycle probe. */
public final class EvolutionTestRoutes {
    private EvolutionTestRoutes() { }

    public static Map<String, String> production() throws java.io.IOException {
        Map<String, String> raw = new LinkedHashMap<>();
        for (String route : List.of("familiar_fox", "axolotl")) {
            try (var input = EvolutionTestRoutes.class.getResourceAsStream(
                    "/data/my_addon/ssca_evolution/routes/" + route + ".json")) {
                raw.put(route, new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return raw;
    }

    public static void loadServer(Map<String, String> raw) {
        Map<Identifier, Resource> resources = new LinkedHashMap<>();
        raw.forEach((id, json) -> resources.put(new Identifier("my_addon", "ssca_evolution/routes/" + id + ".json"),
                new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))));
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class[]{ResourceManager.class}, (proxy, method, args) -> method.getName().equals("findResources") ? resources : null);
        EvolutionRegistry.INSTANCE.reload(manager);
    }

    public static Map<String, String> withoutNode(Map<String, String> raw, String routeId, String nodeId) {
        var route = JsonParser.parseString(raw.get(routeId)).getAsJsonObject();
        route.getAsJsonArray("nodes").asList().removeIf(node -> node.getAsJsonObject().get("id").getAsString().equals(nodeId));
        for (var node : route.getAsJsonArray("nodes")) {
            var prerequisites = node.getAsJsonObject().getAsJsonArray("prereqs");
            if (prerequisites != null) prerequisites.asList().removeIf(id -> id.getAsString().equals(nodeId));
        }
        for (var branch : route.getAsJsonObject("branches").entrySet()) {
            branch.getValue().getAsJsonObject().getAsJsonArray("requires_nodes").asList()
                    .removeIf(id -> id.getAsString().equals(nodeId));
        }
        Map<String, String> changed = new LinkedHashMap<>(raw);
        changed.put(routeId, route.toString());
        return changed;
    }
}
