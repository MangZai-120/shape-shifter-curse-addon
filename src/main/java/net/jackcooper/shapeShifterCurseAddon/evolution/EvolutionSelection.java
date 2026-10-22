package net.jackcooper.shapeShifterCurseAddon.evolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 客户端暂存与服务端批量解锁共用的依赖规则。 */
public final class EvolutionSelection {
    private EvolutionSelection() { }

    public static boolean prerequisitesMet(EvolutionComponent comp, EvolutionNode node, Set<String> pending) {
        for (String prerequisite : node.prereqs) {
            if (!comp.isUnlocked(prerequisite) && !pending.contains(prerequisite)) return false;
        }
        return true;
    }

    /** 按依赖排序，移除失去前置、重复、已解锁或超出点数预算的选择。 */
    public static List<String> resolve(EvolutionRoute route, EvolutionComponent comp, Collection<String> requested) {
        if (route == null || comp == null) return List.of();
        Set<String> remaining = new LinkedHashSet<>(requested);
        Set<String> accepted = new LinkedHashSet<>();
        int points = comp.getPoints();
        boolean changed;
        do {
            changed = false;
            for (var iterator = remaining.iterator(); iterator.hasNext();) {
                String id = iterator.next();
                EvolutionNode node = route.getNode(id);
                if (node == null || node.autoUnlock || comp.isUnlocked(id)) {
                    iterator.remove();
                } else if (node.cost >= 0 && node.cost <= points && prerequisitesMet(comp, node, accepted)) {
                    points -= node.cost;
                    accepted.add(id);
                    iterator.remove();
                    changed = true;
                }
            }
        } while (changed);
        return new ArrayList<>(accepted);
    }
}
