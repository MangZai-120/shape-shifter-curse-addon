package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import java.util.*;

/** Pure tree presentation rules; purchases and point awards remain server-owned. */
public final class EvolutionTreePlan {
    public record Node(String id, int depth, int lane, int cost, boolean automatic, List<String> prerequisites) {
        public Node { prerequisites = List.copyOf(prerequisites); }
    }
    public record Boundary(List<String> nodes, int cumulativeCost) { }
    private final Map<String, Node> nodes = new LinkedHashMap<>();

    public EvolutionTreePlan(List<Node> source) {
        for (Node node : source) nodes.put(node.id(), node);
    }

    public boolean prerequisitesMet(Node node, Set<String> unlocked) {
        return unlocked.containsAll(node.prerequisites());
    }

    /** Each AND join shares a boundary. Independent paths keep independent totals. */
    public List<Boundary> boundaries(Set<String> unlocked) {
        List<Boundary> result = new ArrayList<>();
        Set<Set<String>> emitted = new HashSet<>();
        for (Node candidate : nodes.values()) {
            if (candidate.automatic() || unlocked.contains(candidate.id()) || !prerequisitesMet(candidate, unlocked)) continue;
            boolean grouped = false;
            for (Node child : nodes.values()) {
                if (!child.prerequisites().contains(candidate.id())) continue;
                boolean readyRow = child.prerequisites().stream().allMatch(id -> {
                    Node parent = nodes.get(id);
                    return parent != null && (unlocked.contains(id) || prerequisitesMet(parent, unlocked));
                });
                if (!readyRow) continue;
                addBoundary(child.prerequisites(), result, emitted);
                grouped = true;
            }
            if (!grouped) addBoundary(List.of(candidate.id()), result, emitted);
        }
        return List.copyOf(result);
    }

    private void addBoundary(List<String> ids, List<Boundary> result, Set<Set<String>> emitted) {
        Set<String> key = new LinkedHashSet<>(ids);
        if (!emitted.add(key)) return;
        Set<String> ancestors = new HashSet<>();
        for (String id : ids) collect(id, ancestors);
        int total = ancestors.stream().map(nodes::get).filter(Objects::nonNull)
                .filter(node -> !node.automatic()).mapToInt(Node::cost).sum();
        if (total > 0) result.add(new Boundary(List.copyOf(ids), total));
    }

    private void collect(String id, Set<String> visited) {
        if (!visited.add(id)) return;
        Node node = nodes.get(id);
        if (node != null) for (String parent : node.prerequisites()) collect(parent, visited);
    }
}
