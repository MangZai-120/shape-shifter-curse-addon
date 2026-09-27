package net.jackcooper.shapeShifterCurseAddon.balance;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Reads data/ssc_addon/balance JSON in datapack priority order. Invalid files
 * fail preparation. The sink stages a validated candidate; BalanceIntegration
 * publishes it only after the entire server reload has succeeded.
 */
public final class BalanceLoader implements IdentifiableResourceReloadListener {

    public static final Identifier FABRIC_ID = new Identifier("ssc_addon", "balance");
    /** balance 文件所在目录（相对命名空间根目录）。 */
    public static final String DIRECTORY = "balance";

    /** apply 结果回传：合并层列表（低→高优先级）。 */
    public interface Sink {
        void onLoaded(List<BalanceMerge.Layer> layers);
    }

    private final Sink sink;

    public BalanceLoader(Sink sink) {
        this.sink = sink;
    }

    public static void register(Sink sink) {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new BalanceLoader(sink));
    }

    @Override
    public Identifier getFabricId() {
        return FABRIC_ID;
    }

    @Override
    public CompletableFuture<Void> reload(Synchronizer synchronizer, ResourceManager manager,
                                          Profiler prepareProfiler, Profiler applyProfiler,
                                          Executor prepareExecutor, Executor applyExecutor) {
        // prepare 阶段（工作线程）：读文件 + 解析为层，不触碰活跃状态
        return CompletableFuture.supplyAsync(() -> readLayers(manager), prepareExecutor)
                .thenCompose(synchronizer::whenPrepared)
                .thenAcceptAsync(layers -> sink.onLoaded(layers), applyExecutor);
    }

    /** Resource paths exclude the namespace: data/ssc_addon/balance -> balance. */
    List<BalanceMerge.Layer> readLayers(ResourceManager manager) {
        record Ranked(int rank, String id, BalanceMerge.Layer layer) {}
        Map<String, Integer> packOrder = new HashMap<>();
        manager.streamResourcePacks().forEach(pack -> packOrder.put(pack.getName(), packOrder.size()));
        List<Ranked> ranked = new ArrayList<>();
        for (Identifier fileId : manager.findResources(DIRECTORY,
                id -> id.getNamespace().equals("ssc_addon") && id.getPath().endsWith(".json")).keySet()) {
            String relative = fileId.getPath().substring(DIRECTORY.length() + 1);
            for (Resource resource : manager.getAllResources(fileId)) {
                String source = resource.getResourcePackName() + ":" + fileId;
                try (var input = resource.getInputStream()) {
                    var tree = BalanceDocuments.normalize(relative, source,
                            new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    ranked.add(new Ranked(packOrder.getOrDefault(resource.getResourcePackName(), -1),
                            fileId.toString(), new BalanceMerge.Layer(source, tree)));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid balance resource " + source, e);
                }
            }
        }
        // Pack priority is stronger than file ordering, including different aggregate filenames.
        ranked.sort(java.util.Comparator.comparingInt(Ranked::rank).thenComparing(Ranked::id));
        List<BalanceMerge.Layer> layers = new ArrayList<>();
        layers.add(new BalanceMerge.Layer("builtin", Map.of("schema_version", 1L)));
        ranked.forEach(value -> layers.add(value.layer()));
        return layers;
    }
}
