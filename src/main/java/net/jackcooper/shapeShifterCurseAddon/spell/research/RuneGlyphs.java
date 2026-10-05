package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.*;

/** Single-pen vector glyphs; the brush can follow the visible stroke reference. */
public final class RuneGlyphs {
    private static final double[][] SHAPES = {
        {0,-1, 1,1, -1,1, 0,-1},
        {-1,-1, 1,-1, 1,1, -1,1, -1,-1},
        {0,-1, 1,-.25, .6,1, -.6,1, -1,-.25, 0,-1},
        {-1,-1, -.5,1, 0,-1, .5,1, 1,-1},
        {-1,-1, 1,-1, 1,1, -1,1, -1,0, .3,0, .3,.4},
        {-1,-1, 1,1, -1,1, 1,-1, -1,-1},
        {0,1, 0,0, -1,-1, 0,0, 1,-1},
        {0,1, 0,-1, 0,0, -1,0, 1,0},
        {-1,-1, 0,1, 1,-1},
        {-1,1, -1,-1, 0,0, 1,-1, 1,1},
        {-1,-1, 1,-1, -1,1, 1,1},
        {0,-1, 1,0, 0,1, -1,0, 0,-1, 0,1},
        {1,-1, -1,-1, -1,1, 1,1},
        {-1,-1, 1,-1, 1,1, -1,1, -1,-1, 1,1},
        {-1,-1, 0,-1, 0,0, 1,0, 1,1},
        {-1,-1, -1,1, 1,1, 1,0, 0,0},
        {1,-1, -1,-1, -1,0, .6,0, -1,0, -1,1, 1,1},
        {-1,-1, .6,-1, .6,0, -.6,0, -.6,1, 1,1}
    };
    private RuneGlyphs() {}
    public static List<FormationDiagram.Point> shape(int glyph) {
        if (glyph < 0 || glyph >= SHAPES.length) return List.of();
        double[] a = SHAPES[glyph]; List<FormationDiagram.Point> p = new ArrayList<>();
        for (int i = 0; i < a.length; i += 2) p.add(new FormationDiagram.Point(a[i], a[i + 1])); return p;
    }
    public record Match(int glyph, double error) {}
    public static Match recognize(List<FormationDiagram.Point> stroke) {
        if (stroke.size() < 4) return new Match(-1, 1);
        List<FormationDiagram.Point> sample = normalized(resample(stroke, 48));
        double best = Double.POSITIVE_INFINITY; int glyph = -1;
        for (int g = 0; g < 17; g++) {
            List<FormationDiagram.Point> template = normalized(resample(shape(g), 48));
            boolean closed = distance(shape(g).get(0), shape(g).get(shape(g).size() - 1)) < .01;
            for (int reverse = 0; reverse < 2; reverse++) for (int shift = 0; shift < (closed ? 48 : 1); shift += closed ? 3 : 1) {
                double c = 0, s = 0;
                for (int i = 0; i < 48; i++) {
                    var a = sample.get(i); var b = template.get(Math.floorMod((reverse == 0 ? i : 47 - i) + shift, 48));
                    c += a.x() * b.x() + a.y() * b.y(); s += a.y() * b.x() - a.x() * b.y();
                }
                double angle = Math.atan2(s, c), cos = Math.cos(angle), sin = Math.sin(angle), error = 0;
                for (int i = 0; i < 48; i++) {
                    var a = sample.get(i); var b = template.get(Math.floorMod((reverse == 0 ? i : 47 - i) + shift, 48));
                    error += Math.hypot(a.x() - b.x() * cos + b.y() * sin, a.y() - b.x() * sin - b.y() * cos);
                }
                error /= 48;
                if (error < best) { best = error; glyph = g; }
            }
        }
        return new Match(best < .35 ? glyph : -1, best);
    }
    private static List<FormationDiagram.Point> normalized(List<FormationDiagram.Point> p) {
        double x = 0, y = 0, scale = 0; for (var v : p) { x += v.x(); y += v.y(); } x /= p.size(); y /= p.size();
        for (var v : p) scale += (v.x() - x) * (v.x() - x) + (v.y() - y) * (v.y() - y); scale = Math.sqrt(scale / p.size());
        final double cx = x, cy = y, size = Math.max(.001, scale);
        return p.stream().map(v -> new FormationDiagram.Point((v.x() - cx) / size, (v.y() - cy) / size)).toList();
    }
    private static List<FormationDiagram.Point> resample(List<FormationDiagram.Point> p, int count) {
        double length = 0; for (int i = 1; i < p.size(); i++) length += distance(p.get(i - 1), p.get(i));
        List<FormationDiagram.Point> result = new ArrayList<>();
        int segment = 1; double walked = 0;
        for (int i = 0; i < count; i++) {
            double wanted = length * i / (count - 1.0);
            while (segment < p.size() - 1 && walked + distance(p.get(segment - 1), p.get(segment)) < wanted) { walked += distance(p.get(segment - 1), p.get(segment)); segment++; }
            var a = p.get(segment - 1); var b = p.get(segment); double t = Math.min(1, Math.max(0, (wanted - walked) / Math.max(.0001, distance(a, b))));
            result.add(new FormationDiagram.Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t));
        }
        return result;
    }
    public static double distance(FormationDiagram.Point a, FormationDiagram.Point b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }
}
