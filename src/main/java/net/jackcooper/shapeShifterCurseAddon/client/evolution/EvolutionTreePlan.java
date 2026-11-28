package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import java.util.*;

/** Pure tree presentation rules; purchases and point awards remain server-owned. */
public final class EvolutionTreePlan {
    public record Node(String id, int depth, int lane, int cost, boolean automatic, List<String> prerequisites) {
        public Node { prerequisites = List.copyOf(prerequisites); }
    }
    public record Boundary(List<String> nodes, long cumulativeCost) { }
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<String>> choiceRows = new HashMap<>();

    public EvolutionTreePlan(List<Node> source) {
        this(source, List.of());
    }

    public EvolutionTreePlan(List<Node> source, Collection<String> finalChoices) {
        for (Node node : source) nodes.put(node.id(), node);
        record ChoiceRow(int depth, int cost, Set<String> prerequisites) { }
        Map<ChoiceRow, List<String>> rows = new LinkedHashMap<>();
        for (String id : finalChoices) {
            Node node = nodes.get(id);
            var key = new ChoiceRow(node.depth(), node.cost(), Set.copyOf(node.prerequisites()));
            rows.computeIfAbsent(key, ignored -> new ArrayList<>()).add(id);
        }
        for (var row : rows.values()) {
            var ids = List.copyOf(row);
            for (String id : ids) choiceRows.put(id, ids);
        }
    }

    public boolean prerequisitesMet(Node node, Set<String> unlocked) {
        return unlocked.containsAll(node.prerequisites());
    }

    /** Each AND join shares a boundary. Independent paths keep independent totals. */
    public List<Boundary> boundaries(Set<String> unlocked) {
        List<Boundary> result = new ArrayList<>();
        Set<Set<String>> emitted = new HashSet<>();
        boolean formChosen = !Collections.disjoint(choiceRows.keySet(), unlocked);
        for (Node candidate : nodes.values()) {
            if (candidate.automatic() || unlocked.contains(candidate.id()) || !prerequisitesMet(candidate, unlocked)) continue;
            List<String> choices = choiceRows.get(candidate.id());
            if (choices != null) {
                if (!formChosen && candidate.id().equals(choices.get(0))) {
                    // Draw under the shared prerequisites. The displayed cost includes one final choice.
                    List<String> anchors = candidate.prerequisites().isEmpty() ? choices : candidate.prerequisites();
                    result.add(new Boundary(anchors, cumulativeCost(List.of(candidate.id()))));
                }
                continue;
            }
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
        long total = cumulativeCost(ids);
        if (total > 0) result.add(new Boundary(List.copyOf(ids), total));
    }

    private long cumulativeCost(List<String> ids) {
        Set<String> ancestors = new HashSet<>();
        for (String id : ids) collect(id, ancestors);
        return ancestors.stream().map(nodes::get).filter(Objects::nonNull)
                .filter(node -> !node.automatic()).mapToLong(Node::cost).sum();
    }

    private void collect(String id, Set<String> visited) {
        if (!visited.add(id)) return;
        Node node = nodes.get(id);
        if (node != null) for (String parent : node.prerequisites()) collect(parent, visited);
    }
}
