package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import java.util.*;

/** User-defined cumulative thresholds, AND joins, and independent branches. */
public final class EvolutionTreePlanTest {
    private static int checks;
    private static EvolutionTreePlan.Node n(String id, int depth, int lane, int cost, String... pre) {
        return new EvolutionTreePlan.Node(id, depth, lane, cost, false, List.of(pre));
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void boundary(EvolutionTreePlan plan, Set<String> unlocked, Set<String> row, int total) {
        var lines = plan.boundaries(unlocked);
        check(lines.size() == 1, "one shared boundary: " + lines);
        check(new HashSet<>(lines.get(0).nodes()).equals(row), "correct frontier row: " + lines);
        check(lines.get(0).cumulativeCost() == total, "cumulative threshold: " + lines);
    }
    public static void main(String[] args) {
        var joined = new EvolutionTreePlan(List.of(n("root", 0, 1, 1), n("a", 1, 0, 1, "root"), n("b", 1, 2, 1, "root"),
                n("c", 2, 0, 1, "a", "b"), n("d", 2, 2, 1, "a", "b"), n("end", 3, 1, 1, "c", "d")));
        boundary(joined, Set.of(), Set.of("root"), 1);
        boundary(joined, Set.of("root"), Set.of("a", "b"), 3);
        boundary(joined, Set.of("root", "a"), Set.of("a", "b"), 3);
        boundary(joined, Set.of("root", "a", "b"), Set.of("c", "d"), 5);
        boundary(joined, Set.of("root", "a", "b", "c"), Set.of("c", "d"), 5);
        boundary(joined, Set.of("root", "a", "b", "c", "d"), Set.of("end"), 6);
        check(joined.boundaries(Set.of("root", "a", "b", "c", "d", "end")).isEmpty(), "completed tree has no boundary");
        var independent = new EvolutionTreePlan(List.of(n("root", 0, 1, 1), n("left", 1, 0, 2, "root"),
                n("right", 1, 2, 3, "root"), n("left2", 2, 0, 2, "left"), n("right2", 2, 2, 3, "right"),
                n("merge", 3, 1, 3, "left2", "right2")));
        var split = independent.boundaries(Set.of("root"));
        check(split.size() == 2, "independent branches have separate lines");
        check(split.stream().anyMatch(b -> b.nodes().equals(List.of("left")) && b.cumulativeCost() == 3), "left excludes optional right costs");
        check(split.stream().anyMatch(b -> b.nodes().equals(List.of("right")) && b.cumulativeCost() == 4), "right excludes optional left costs");
        var staggered = independent.boundaries(Set.of("root", "left"));
        check(staggered.stream().anyMatch(b -> b.nodes().equals(List.of("right")) && b.cumulativeCost() == 4), "right boundary stays in place");
        check(staggered.stream().anyMatch(b -> b.nodes().equals(List.of("left2")) && b.cumulativeCost() == 5), "left advances independently");
        boundary(independent, Set.of("root", "left", "right"), Set.of("left2", "right2"), 11);
        boundary(independent, Set.of("root", "left", "right", "left2", "right2"), Set.of("merge"), 14);
        var automatic = new EvolutionTreePlan(List.of(new EvolutionTreePlan.Node("base", 0, 1, 0, true, List.of()),
                n("paid", 1, 1, 2, "base"), new EvolutionTreePlan.Node("sp", 2, 1, 0, true, List.of("paid"))));
        boundary(automatic, Set.of("base"), Set.of("paid"), 2);
        check(automatic.boundaries(Set.of("base", "paid")).isEmpty(), "level-gated automatic forms do not create point purchases");
        System.out.println("EVOLUTION_TREE_PLAN_PASS checks=" + checks);
    }
}
