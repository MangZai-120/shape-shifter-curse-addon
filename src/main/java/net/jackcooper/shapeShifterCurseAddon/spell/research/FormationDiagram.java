package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.ArrayList;
import java.util.List;

/** Coordinates are in a 512 by 512 logical canvas. Wire routes stay within their radial region. */
public final class FormationDiagram {
    public record Point(double x, double y) {}
    public record Ring(double x, double y, double radius, boolean clockwise) {}
    public record Node(int glyph, double x, double y) {}
    public record Edge(int from, int to) {}
    public final List<Ring> rings = new ArrayList<>();
    public final List<Node> nodes = new ArrayList<>();
    public final List<Edge> edges = new ArrayList<>();
    public FormationDiagram copy() {
        FormationDiagram d = new FormationDiagram(); d.rings.addAll(rings); d.nodes.addAll(nodes); d.edges.addAll(edges); return d;
    }
    public List<Ring> sortedRings() { return rings.stream().sorted(java.util.Comparator.comparingDouble(Ring::radius).reversed()).toList(); }
    public void removeNode(int index) {
        nodes.remove(index);
        List<Edge> updated = new ArrayList<>();
        for (Edge e : edges) if (e.from != index && e.to != index) updated.add(new Edge(e.from > index ? e.from - 1 : e.from, e.to > index ? e.to - 1 : e.to));
        edges.clear(); edges.addAll(updated);
    }
    public static List<Point> route(Node a, Node b, Ring outer) {
        if (outer == null) return List.of(new Point(a.x, a.y), new Point(b.x, b.y));
        double ax = a.x - outer.x, ay = a.y - outer.y, bx = b.x - outer.x, by = b.y - outer.y;
        double ra = Math.hypot(ax, ay), rb = Math.hypot(bx, by);
        double aa = Math.atan2(ay, ax), ab = Math.atan2(by, bx), delta = ab - aa;
        while (delta > Math.PI) delta -= Math.PI * 2;
        while (delta < -Math.PI) delta += Math.PI * 2;
        List<Point> points = new ArrayList<>();
        for (int i = 0; i <= 24; i++) { double t = i / 24.0, r = ra + (rb - ra) * t, angle = aa + delta * t; points.add(new Point(outer.x + Math.cos(angle) * r, outer.y + Math.sin(angle) * r)); }
        return points;
    }
}
