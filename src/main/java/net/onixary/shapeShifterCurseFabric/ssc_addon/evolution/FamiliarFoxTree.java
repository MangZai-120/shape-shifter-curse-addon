package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import java.util.List;

/**
 * 使魔（familiar_fox）进化路线的便捷访问器 + 节点 id 常量表。
 *
 * <p><b>【数据驱动重构】</b>节点树本体已迁移到 datapack JSON
 * {@code data/my_addon/ssca_evolution/routes/familiar_fox.json}，由 {@link EvolutionRegistry} 加载。
 * 本类不再硬编码节点，仅承担两件事：</p>
 * <ol>
 *   <li>保留各节点 id 常量（供少数按「具体节点」做特殊渲染 / 业务的地方引用，
 *       如 cd 条门控、mana 条门控、Codex 叙述）；</li>
 *   <li>转发到 {@link EvolutionRegistry} 提供 {@link #nodes()} / {@link #get(String)}。</li>
 * </ol>
 *
 * <p>加 / 改 familiar_fox 的进化树只需改上述 JSON，无需改 Java。</p>
 */
public final class FamiliarFoxTree {
    public static final String ROUTE_ID = "familiar_fox";

    public static final String NODE_BASE = "familiar_fox_base";
    public static final String NODE_MANA = "mana_system";
    public static final String NODE_SPARK = "spark";
    public static final String NODE_ROCKET = "rocket";
    public static final String NODE_BUFF_IMMUNITY = "buff_immunity";
    public static final String NODE_ALCHEMY = "alchemy";
    public static final String NODE_SPIRIT_VISION = "spirit_vision";
    public static final String NODE_FIRE_RING = "fire_ring";
    public static final String NODE_BRANCH_SPIRIT_LORD = "branch_spirit_lord";
    public static final String NODE_BRANCH_MANCIANIMA = "branch_mancianima";

    private FamiliarFoxTree() {
    }

    /** 当前 familiar_fox 路线（数据驱动；未加载 / 未同步时为 null）。 */
    public static EvolutionRoute route() {
        return EvolutionRegistry.INSTANCE.getRoute(ROUTE_ID);
    }

    /** 全部节点（数据来自 JSON；未加载时返回空列表）。 */
    public static List<EvolutionNode> nodes() {
        EvolutionRoute r = route();
        return r == null ? List.of() : r.nodes;
    }

    public static EvolutionNode get(String id) {
        EvolutionRoute r = route();
        return r == null ? null : r.getNode(id);
    }

    public static boolean isValidNode(String id) {
        return get(id) != null;
    }
}
