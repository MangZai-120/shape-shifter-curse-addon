package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

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
 * <p>服务端经 reload 填充；客户端经 S2C（{@link #applyClientSync}）填充镜像，
 * 故服务端 / 客户端统一用 {@link #INSTANCE} 读取路线定义。</p>
 */
public final class EvolutionRegistry implements SimpleSynchronousResourceReloadListener {
    public static final EvolutionRegistry INSTANCE = new EvolutionRegistry();
    private static final Identifier ID = new Identifier("ssc_addon", "ssca_evolution_routes");
    private static final String DIR = "ssca_evolution/routes";

    private Map<String, EvolutionRoute> routes = new LinkedHashMap<>();
    private Map<String, String> rawJson = new LinkedHashMap<>();

    private EvolutionRegistry() {
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
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
                loaded.put(fileName, EvolutionRoute.fromJson(fileName, o));
                loadedRaw.put(fileName, content);
            } catch (Exception e) {
                System.err.println("[ssc_addon] Failed to load evolution route: " + fileId + " - " + e);
            }
        }
        this.routes = loaded;
        this.rawJson = loadedRaw;
    }

    /** 客户端收到 S2C 同步后重建路线镜像（多人环境下客户端无 datapack 数据）。 */
    public void applyClientSync(Map<String, String> raw) {
        Map<String, EvolutionRoute> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            try {
                JsonObject o = JsonHelper.deserialize(e.getValue());
                rebuilt.put(e.getKey(), EvolutionRoute.fromJson(e.getKey(), o));
            } catch (Exception ex) {
                System.err.println("[ssc_addon] Failed to apply synced evolution route: " + e.getKey() + " - " + ex);
            }
        }
        this.routes = rebuilt;
        this.rawJson = new LinkedHashMap<>(raw);
    }

    public EvolutionRoute getRoute(String routeId) {
        return routeId == null ? null : routes.get(routeId);
    }

    public Map<String, EvolutionRoute> all() {
        return Collections.unmodifiableMap(routes);
    }

    /** 各路线的原始 JSON 文本（用于 S2C 同步转发）。 */
    public Map<String, String> getRawJson() {
        return Collections.unmodifiableMap(rawJson);
    }

    /** 按 {@code start_form} 反查路线（判断「当前形态属于哪条进化路线」）。 */
    public EvolutionRoute getRouteByStartForm(Identifier formId) {
        if (formId == null) {
            return null;
        }
        for (EvolutionRoute r : routes.values()) {
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
