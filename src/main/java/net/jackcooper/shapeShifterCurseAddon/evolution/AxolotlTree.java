package net.jackcooper.shapeShifterCurseAddon.evolution;

import java.util.List;

/**
 * 美西螈（axolotl）进化路线的便捷访问器 + 节点 id 常量表。
 *
 * <p>节点树本体在 datapack JSON {@code data/my_addon/ssca_evolution/routes/axolotl.json}，
 * 由 {@link EvolutionRegistry} 加载。本类仅保留节点 id 常量（供 Java 技能门控引用）
 * 与到 {@link EvolutionRegistry} 的转发。加 / 改进化树只需改上述 JSON。</p>
 */
public final class AxolotlTree {
    public static final String ROUTE_ID = "axolotl";

    public static final String NODE_BASE = "axolotl_base";
    public static final String NODE_AQUATIC_ADAPT = "aquatic_adapt";
    public static final String NODE_WATER_PROPEL = "water_propel";
    public static final String NODE_WATER_SPURT = "water_spurt";
    public static final String NODE_WATER_SPEAR = "water_spear";
    public static final String NODE_VORTEX_GUIDE = "vortex_guide";
    public static final String NODE_BECOME_WATER = "become_water";
    public static final String NODE_BRANCH_MOON_RING = "branch_moon_ring";
    public static final String NODE_BRANCH_STONE = "branch_stone";

    private AxolotlTree() {
    }

    /** 全部节点（数据来自 JSON；未加载时返回空列表）。 */
    public static List<EvolutionNode> nodes() {
        EvolutionRoute r = EvolutionRegistry.INSTANCE.getRoute(ROUTE_ID);
        return r == null ? List.of() : r.nodes;
    }
}
