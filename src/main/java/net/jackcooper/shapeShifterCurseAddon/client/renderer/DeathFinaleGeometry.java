package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.util.ArrayList;
import java.util.List;

/** A new thirteen-mark dial and hourglass, sharing the existing cached stroke renderer. */
public final class DeathFinaleGeometry {
    public static final int OCHRE = 0xB99749;
    public static final int LIGHT = 0xE5CB86;
    public static final List<MagicCircleGeometry.Stroke> STROKES = create();
    private DeathFinaleGeometry() {}

    private static List<MagicCircleGeometry.Stroke> create() {
        var out = new ArrayList<MagicCircleGeometry.Stroke>();
        ring(out, 0.975, 0.024, OCHRE);
        ring(out, 0.91, 0.012, LIGHT);
        ring(out, 0.73, 0.020, OCHRE);
        ring(out, 0.43, 0.018, LIGHT);
        for (int i = 0; i < 13; i++) {
            double a = -Math.PI / 2 + i * Math.PI * 2 / 13;
            radial(out, a, 0.79, 0.875, 0.022, LIGHT);
            double b = a + Math.PI * 2 / 13;
            line(out, Math.cos(a) * .67, Math.sin(a) * .67,
                    Math.cos(b) * .67, Math.sin(b) * .67, .015, OCHRE);
            double m = a + Math.PI / 13;
            line(out, Math.cos(a) * .67, Math.sin(a) * .67,
                    Math.cos(m) * .50, Math.sin(m) * .50, .015, OCHRE);
            line(out, Math.cos(m) * .50, Math.sin(m) * .50,
                    Math.cos(b) * .67, Math.sin(b) * .67, .015, OCHRE);
        }
        // Crossed glass walls with separated upper/lower frames and a falling sand diamond.
        line(out, -.23, -.31, .23, -.31, .028, LIGHT);
        line(out, -.23, .31, .23, .31, .028, LIGHT);
        line(out, -.20, -.25, .20, .25, .021, OCHRE);
        line(out, .20, -.25, -.20, .25, .021, OCHRE);
        line(out, -.12, .22, .12, .22, .018, LIGHT);
        line(out, -.12, .22, 0, .10, .018, LIGHT);
        line(out, 0, .10, .12, .22, .018, LIGHT);
        return List.copyOf(out);
    }

    private static void radial(List<MagicCircleGeometry.Stroke> out, double a, double r1, double r2, double w, int color) {
        line(out, Math.cos(a) * r1, Math.sin(a) * r1, Math.cos(a) * r2, Math.sin(a) * r2, w, color);
    }
    private static void ring(List<MagicCircleGeometry.Stroke> out, double r, double w, int color) {
        for (int i = 0; i < 160; i++) {
            double a = i * Math.PI * 2 / 160, b = (i + 1) * Math.PI * 2 / 160;
            out.add(new MagicCircleGeometry.Stroke(Math.cos(a) * r, Math.sin(a) * r,
                    Math.cos(b) * r, Math.sin(b) * r, w, color, 1,
                    -Math.cos(a), -Math.sin(a), -Math.cos(b), -Math.sin(b)));
        }
    }
    private static void line(List<MagicCircleGeometry.Stroke> out, double x1, double z1, double x2, double z2, double w, int color) {
        double length = Math.hypot(x2 - x1, z2 - z1);
        double nx = -(z2 - z1) / length, nz = (x2 - x1) / length;
        out.add(new MagicCircleGeometry.Stroke(x1, z1, x2, z2, w, color, 1, nx, nz, nx, nz));
    }
}
