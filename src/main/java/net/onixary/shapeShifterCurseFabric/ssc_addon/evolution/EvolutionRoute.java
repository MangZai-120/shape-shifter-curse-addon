package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条 SSCA 进化路线（一个形态的完整进化加点设计），由单个 route JSON 解析而来。
 *
 * <p>取代旧的硬编码 {@code FamiliarFoxTree}：节点树、布局、消耗、前置、分支、发点等级
 * 全部数据化，加 / 改一个形态的进化树只需写一个 route JSON。</p>
 */
public class EvolutionRoute {
    /** 路线 id（= 文件名）。 */
    public final String routeId;
    /** 是否开放（false 则开书 / 入口不显示）。 */
    public final boolean enabled;
    /** 路线显示名 lang key。 */
    public final String displayNameKey;
    /** 进入该路线对应的形态 id（如「进化使魔」）；可空。 */
    public final Identifier startForm;
    /** 发放升级点的经验等级里程碑（升序）。 */
    public final int[] levelMilestones;
    /** 自动解锁分支节点的经验等级（<=0 = 关闭）。 */
    public final int autoBranchLevel;
    /** 全部节点（保持 JSON 声明顺序）。 */
    public final List<EvolutionNode> nodes;
    /** 分支表（branch id -> 分支定义）。 */
    public final Map<String, Branch> branches;

    private final Map<String, EvolutionNode> byId;
    private final String baseNodeId;

    /** SP 双分支之一：解锁后融合到对应 SSCA SP 形态。 */
    public static class Branch {
        public final String id;
        public final String displayNameKey;
        public final Identifier spForm;
        public final List<String> requiresNodes;

        public Branch(String id, String displayNameKey, Identifier spForm, List<String> requiresNodes) {
            this.id = id;
            this.displayNameKey = displayNameKey;
            this.spForm = spForm;
            this.requiresNodes = requiresNodes == null ? List.of() : requiresNodes;
        }
    }

    private EvolutionRoute(String routeId, boolean enabled, String displayNameKey, Identifier startForm,
                           int[] levelMilestones, int autoBranchLevel, List<EvolutionNode> nodes,
                           Map<String, Branch> branches, String baseNodeId) {
        this.routeId = routeId;
        this.enabled = enabled;
        this.displayNameKey = displayNameKey;
        this.startForm = startForm;
        this.levelMilestones = levelMilestones;
        this.autoBranchLevel = autoBranchLevel;
        this.nodes = nodes;
        this.branches = branches;
        this.baseNodeId = baseNodeId;
        this.byId = new LinkedHashMap<>();
        for (EvolutionNode n : nodes) {
            this.byId.put(n.id, n);
        }
    }

    public EvolutionNode getNode(String id) {
        return byId.get(id);
    }

    public boolean isValidNode(String id) {
        return byId.containsKey(id);
    }

    /** 初始节点 id（route JSON 的 {@code base_node}，或首个 autoUnlock 主线节点）。 */
    public String getBaseNodeId() {
        return baseNodeId;
    }

    /** 所有分支节点 id（{@code branch} 非空的节点）。 */
    public List<String> getBranchNodeIds() {
        List<String> ids = new ArrayList<>();
        for (EvolutionNode n : nodes) {
            if (!n.branch.isEmpty()) {
                ids.add(n.id);
            }
        }
        return ids;
    }

    public static EvolutionRoute fromJson(String routeId, JsonObject o) {
        boolean enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        String displayNameKey = o.has("display_name") ? o.get("display_name").getAsString()
                : "evolution.my_addon." + routeId + ".name";
        Identifier startForm = o.has("start_form") ? Identifier.tryParse(o.get("start_form").getAsString()) : null;
        int autoBranchLevel = o.has("auto_branch_level") ? o.get("auto_branch_level").getAsInt() : -1;
        int[] milestones = parseIntArray(o, "level_milestones");

        List<EvolutionNode> nodes = new ArrayList<>();
        if (o.has("nodes") && o.get("nodes").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("nodes")) {
                nodes.add(EvolutionNode.fromJson(routeId, e.getAsJsonObject()));
            }
        }

        // base 节点：显式 base_node，否则取第一个 autoUnlock 且 branch 为空的主线节点
        String baseNodeId = o.has("base_node") ? o.get("base_node").getAsString() : null;
        if (baseNodeId == null) {
            for (EvolutionNode n : nodes) {
                if (n.autoUnlock && n.branch.isEmpty()) {
                    baseNodeId = n.id;
                    break;
                }
            }
        }

        Map<String, Branch> branches = new LinkedHashMap<>();
        if (o.has("branches") && o.get("branches").isJsonObject()) {
            JsonObject bo = o.getAsJsonObject("branches");
            for (Map.Entry<String, JsonElement> en : bo.entrySet()) {
                String bid = en.getKey();
                JsonObject b = en.getValue().getAsJsonObject();
                String bName = b.has("display_name") ? b.get("display_name").getAsString()
                        : "evolution.my_addon." + routeId + ".branch." + bid;
                Identifier spForm = b.has("sp_form") ? Identifier.tryParse(b.get("sp_form").getAsString()) : null;
                List<String> req = new ArrayList<>();
                if (b.has("requires_nodes") && b.get("requires_nodes").isJsonArray()) {
                    for (JsonElement e : b.getAsJsonArray("requires_nodes")) {
                        req.add(e.getAsString());
                    }
                }
                branches.put(bid, new Branch(bid, bName, spForm, req));
            }
        }

        return new EvolutionRoute(routeId, enabled, displayNameKey, startForm, milestones, autoBranchLevel,
                nodes, branches, baseNodeId);
    }

    private static int[] parseIntArray(JsonObject o, String key) {
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray arr = o.getAsJsonArray(key);
            int[] out = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                out[i] = arr.get(i).getAsInt();
            }
            return out;
        }
        return new int[0];
    }
}
