package net.jackcooper.shapeShifterCurseAddon.evolution;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSCA 进化路线数据驱动加载器（datapack reload listener）。
 *
 * <p>扫描 {@code data/<ns>/ssca_evolution/routes/*.json}，每个文件 = 一条进化路线，
 * 文件名（去 {@code .json}）= 路线 id，解析为内存 {@link EvolutionRoute}。
 * 下划线开头的文件（如占位 / 注释样例）会被跳过。</p>
 *
 * <p>服务端只读取 {@link #INSTANCE}；客户端只读取 {@link #CLIENT} 的连接镜像。
 * 单人内置服务端与客户端也必须隔离，延迟同步包不能回写服务端配置。</p>
 */
public final class EvolutionRegistry implements SimpleSynchronousResourceReloadListener {
    public static final EvolutionRegistry INSTANCE = new EvolutionRegistry();
    public static final EvolutionRegistry CLIENT = new EvolutionRegistry();
    private static final Identifier ID = new Identifier("ssc_addon", "ssca_evolution_routes");
    private static final String DIR = "ssca_evolution/routes";

    private record Snapshot(Map<String, EvolutionRoute> routes, Map<String, String> rawJson) {}
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of());

    private EvolutionRegistry() {
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        if (this != INSTANCE) throw new IllegalStateException("Only the server registry loads datapacks");
        Map<String, EvolutionRoute> loaded = new LinkedHashMap<>();
        Map<String, String> loadedRaw = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> entry :
                manager.findResources(DIR, path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier fileId = entry.getKey();
            String path = fileId.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            if (fileName.startsWith("_")) {
                continue; // 跳过下划线开头的占位 / 注释样例文件
            }
            try (InputStream is = entry.getValue().getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject o = JsonHelper.deserialize(content);
                if (loaded.putIfAbsent(fileName, EvolutionRoute.fromJson(fileName, o)) != null) {
                    throw new IllegalArgumentException("Duplicate evolution route id: " + fileName);
                }
                loadedRaw.put(fileName, content);
            } catch (Exception e) {
                // Do not publish a partial registry or report a successful reload.
                throw new IllegalArgumentException("Failed to load evolution route: " + fileId
                        + "; retaining the previous registry", e);
            }
        }
        snapshot = validatedSnapshot(loaded, loadedRaw);
    }

    /** 客户端收到 S2C 同步后重建路线镜像（多人环境下客户端无 datapack 数据）。 */
    public void applyClientSync(Map<String, String> raw) {
        if (this != CLIENT) throw new IllegalStateException("Client sync cannot write the server registry");
        Map<String, EvolutionRoute> rebuilt = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> e : raw.entrySet()) {
                JsonObject o = JsonHelper.deserialize(e.getValue());
                rebuilt.put(e.getKey(), EvolutionRoute.fromJson(e.getKey(), o));
            }
            snapshot = validatedSnapshot(rebuilt, raw);
        } catch (Exception ex) {
            System.err.println("[ssc_addon] Failed to apply synced evolution routes: " + ex);
            // Preserve the complete previous mirror on malformed snapshots.
        }
    }

    public static EvolutionRegistry forWorld(net.minecraft.world.World world) {
        return world.isClient ? CLIENT : INSTANCE;
    }

    public static void clearClient() {
        CLIENT.snapshot = new Snapshot(Map.of(), Map.of());
    }

    private static Snapshot validatedSnapshot(Map<String, EvolutionRoute> routes, Map<String, String> raw) {
        Map<Identifier, String> forms = new LinkedHashMap<>();
        for (EvolutionRoute route : routes.values()) {
            if (route.startForm != null && forms.putIfAbsent(route.startForm, route.routeId) != null)
                throw new IllegalArgumentException("Multiple evolution routes use start_form " + route.startForm);
        }
        return new Snapshot(Collections.unmodifiableMap(new LinkedHashMap<>(routes)),
                Collections.unmodifiableMap(new LinkedHashMap<>(raw)));
    }

    public EvolutionRoute getRoute(String routeId) {
        return routeId == null ? null : snapshot.routes.get(routeId);
    }

    public Map<String, EvolutionRoute> all() {
        return snapshot.routes;
    }

    /** 各路线的原始 JSON 文本（用于 S2C 同步转发）。 */
    public Map<String, String> getRawJson() {
        return snapshot.rawJson;
    }

    /** 按 {@code start_form} 反查路线（判断「当前形态属于哪条进化路线」）。 */
    public EvolutionRoute getRouteByStartForm(Identifier formId) {
        if (formId == null) {
            return null;
        }
        formId = net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration.canonical(formId);
        for (EvolutionRoute r : snapshot.routes.values()) {
            if (formId.equals(r.startForm)) {
                return r;
            }
        }
        return null;
    }

    public EvolutionNode getNode(String routeId, String nodeId) {
        EvolutionRoute r = getRoute(routeId);
        return r == null ? null : r.getNode(nodeId);
    }
}
