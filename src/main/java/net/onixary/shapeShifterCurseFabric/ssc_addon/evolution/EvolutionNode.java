package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * SSCA 进化树的单个节点（能力）定义。纯数据，服务端与客户端共用。
 *
 * <p>图标用 vanilla/mod 物品（客户端渲染时 {@code new ItemStack(icon)}），零美术成本。</p>
 * <p>节点 {@link #id} 即解锁状态 / 能力门控（{@code ssc_addon:has_talent}）所用的 talent id。</p>
 * <p>数据驱动：节点由 route JSON 的 {@code nodes} 数组经 {@link #fromJson} 解析，不再硬编码。</p>
 */
public class EvolutionNode {
    /** 节点唯一 id（= talent id）。 */
    public final String id;
    /** 名称 lang key。 */
    public final String nameKey;
    /** 描述 lang key（tooltip 正文）。 */
    public final String descKey;
    /** 图标物品。 */
    public final Item icon;
    /** 解锁消耗点数。 */
    public final int cost;
    /** 前置节点 id 列表（全部已解锁才满足，AND）。 */
    public final List<String> prereqs;
    /** 布局列（进化层级，越大越靠后）。 */
    public final int col;
    /** 布局行（同层内的纵向位置）。 */
    public final int row;
    /** true = 满足前置即自动解锁、不消耗点数（如初始形态、分支节点）。 */
    public final boolean autoUnlock;
    /** 所属分支标记（""=主线）。 */
    public final String branch;
    /** 该节点解锁后关联生效的 power id 列表（元数据；能力门控仍由 power JSON 的 has_talent 驱动）。 */
    public final List<String> grantsPowers;

    /** 兼容旧的硬编码构造（无 grants_powers）。 */
    public EvolutionNode(String id, String nameKey, String descKey, Item icon, int cost,
                         List<String> prereqs, int col, int row, boolean autoUnlock, String branch) {
        this(id, nameKey, descKey, icon, cost, prereqs, col, row, autoUnlock, branch, List.of());
    }

    public EvolutionNode(String id, String nameKey, String descKey, Item icon, int cost,
                         List<String> prereqs, int col, int row, boolean autoUnlock, String branch,
                         List<String> grantsPowers) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.icon = icon;
        this.cost = cost;
        this.prereqs = prereqs == null ? List.of() : prereqs;
        this.col = col;
        this.row = row;
        this.autoUnlock = autoUnlock;
        this.branch = branch == null ? "" : branch;
        this.grantsPowers = grantsPowers == null ? List.of() : grantsPowers;
    }

    /**
     * 从 route JSON 的单个节点对象解析。
     *
     * <p>字段：{@code id}(必填)；{@code name}/{@code desc}(lang key，缺省自动按
     * {@code evolution.my_addon.<route>.node.<id>.name/.desc} 生成)；{@code icon}(物品 id，缺省屏障)；
     * {@code cost}(默认 1)；{@code prereqs}(默认空)；{@code col}/{@code row}(默认 0)；
     * {@code auto_unlock}(默认 false)；{@code branch}(默认空)；{@code grants_powers}(默认空)。</p>
     *
     * @param routeId 所属路线 id，用于生成缺省 lang key
     */
    public static EvolutionNode fromJson(String routeId, JsonObject o) {
        String id = o.get("id").getAsString();
        String nameKey = o.has("name") ? o.get("name").getAsString()
                : "evolution.my_addon." + routeId + ".node." + id + ".name";
        String descKey = o.has("desc") ? o.get("desc").getAsString()
                : "evolution.my_addon." + routeId + ".node." + id + ".desc";
        Item icon = Items.BARRIER;
        if (o.has("icon")) {
            Identifier iconId = Identifier.tryParse(o.get("icon").getAsString());
            if (iconId != null && Registries.ITEM.containsId(iconId)) {
                icon = Registries.ITEM.get(iconId);
            }
        }
        int cost = o.has("cost") ? o.get("cost").getAsInt() : 1;
        int col = o.has("col") ? o.get("col").getAsInt() : 0;
        int row = o.has("row") ? o.get("row").getAsInt() : 0;
        boolean autoUnlock = o.has("auto_unlock") && o.get("auto_unlock").getAsBoolean();
        String branch = o.has("branch") ? o.get("branch").getAsString() : "";
        List<String> prereqs = parseStringList(o, "prereqs");
        List<String> grantsPowers = parseStringList(o, "grants_powers");
        return new EvolutionNode(id, nameKey, descKey, icon, cost, prereqs, col, row, autoUnlock, branch, grantsPowers);
    }

    private static List<String> parseStringList(JsonObject o, String key) {
        List<String> list = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray arr = o.getAsJsonArray(key);
            for (JsonElement e : arr) {
                list.add(e.getAsString());
            }
        }
        return list;
    }
}
