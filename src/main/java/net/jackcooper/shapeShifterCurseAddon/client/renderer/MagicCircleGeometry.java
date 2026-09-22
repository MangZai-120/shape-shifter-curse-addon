package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Cached normalized line geometry, shared by the world renderer and the offline preview.
 * A ground design plus seven distinct aerial designs, ordered from lowest to highest.
 * Uses the original circle's normalized 0.020-0.035 line widths, without glyphs or markers.
 * No font, texture, random layout or per-frame tessellation is needed. */
public final class MagicCircleGeometry {
	/** Endpoint normals share exact radial edges on circles, avoiding cracks in wide rings. */
	public record Stroke(double x1, double z1, double x2, double z2, double width, int color, float alpha,
	                     double nx1, double nz1, double nx2, double nz2) {}
	public static final int GLOW_COLOR = 0xFF233F;
	public static final double GLOW_WIDTH = 3.4;
	public static final float GLOW_ALPHA = 0.12f;
	public static final int RED_PRIMARY = 0xFF5555;
	public static final int PURPLE_SECONDARY = 0xC6ABF5;
	private static final int LIGHT = 0xFFE3DB;
	private static final int RED = 0xFF6978;
	private static final int VIOLET = 0xC6ABF5;
	private static final double TAU = Math.PI * 2;
	private static final List<Stroke> STROKES = create();
	private static final List<List<Stroke>> LAYERS = List.of(
			triangles(), squares(), STROKES, hexagon(), octagon(), petals(), radialRings());
	private static final List<Stroke> GROUND = createGround();
	private static final Map<Integer, List<Stroke>> GROUND_BY_COLOR = new HashMap<>();

	private MagicCircleGeometry() {}
	public static List<Stroke> strokes() { return STROKES; }
	public static int layerCount() { return LAYERS.size(); }
	public static List<Stroke> layerStrokes(int layer) { return LAYERS.get(layer); }

	/** One ground drawing for all spells: only the primary color varies; purple remains fixed. */
	public static List<Stroke> groundStrokes(int primaryColor) {
		return GROUND_BY_COLOR.computeIfAbsent(primaryColor & 0xFFFFFF, color -> GROUND.stream()
				.map(s -> new Stroke(s.x1(), s.z1(), s.x2(), s.z2(), s.width(),
						s.color() == 0xFFFFFF ? color : s.color(), s.alpha(), s.nx1(), s.nz1(), s.nx2(), s.nz2()))
				.toList());
	}

	private static List<Stroke> createGround() {
		Builder b = new Builder();
		int primary = 0xFFFFFF, purple = PURPLE_SECONDARY;
		b.circle(0, 0, 0.975, 192, 0.035, primary, 1);
		b.circle(0, 0, 0.913, 192, 0.020, purple, 1);
		b.circle(0, 0, 0.851, 160, 0.030, primary, 1);
		// Four circular nodes and an interlaced octagon surround an open central diamond.
		for (int i = 0; i < 8; i++) {
			double a = -Math.PI / 2 + TAU * i / 8, c = a + TAU * 3 / 8;
			b.lattice(Math.cos(a) * 0.805, Math.sin(a) * 0.805,
					Math.cos(c) * 0.805, Math.sin(c) * 0.805, 0.025, purple, 1,
					4, 0.650, 0.150 + 0.024 / 2 + 0.025 / 2 + 0.006);
		}
		b.circle(0, 0, 0.445, 128, 0.028, primary, 1);
		b.circle(0, 0, 0.382, 128, 0.020, purple, 1);
		b.polygon(0.310, 4, 1, 0, 0.025, primary);
		b.polygon(0.210, 4, 1, Math.PI / 4, 0.020, purple);
		for (int i = 0; i < 4; i++) {
			double a = -Math.PI / 2 + TAU * i / 4;
			double x = Math.cos(a) * 0.650, z = Math.sin(a) * 0.650;
			b.circle(x, z, 0.150, 64, 0.024, primary, 1);
			b.circle(x, z, 0.100, 64, 0.020, purple, 1);
		}
		return List.copyOf(b.lines);
	}

	private static Builder border() {
		Builder b = new Builder();
		// Original line weights; wider spacing keeps the bold rings distinct.
		b.circle(0, 0, 0.975, 192, 0.035, LIGHT, 1);
		b.circle(0, 0, 0.913, 192, 0.020, RED, 0.9f);
		b.circle(0, 0, 0.851, 160, 0.030, VIOLET, 0.95f);
		return b;
	}

	private static List<Stroke> triangles() {
		Builder b = border();
		b.polygon(0.795, 3, 1, -Math.PI / 2, 0.028, LIGHT);
		b.polygon(0.610, 3, 1, -Math.PI / 2, 0.025, VIOLET);
		b.polygon(0.425, 3, 1, -Math.PI / 2, 0.025, LIGHT);
		for (int i = 0; i < 3; i++) {
			double a = -Math.PI / 2 + TAU * i / 3;
			b.line(Math.cos(a) * 0.425, Math.sin(a) * 0.425,
					Math.cos(a) * 0.795, Math.sin(a) * 0.795, 0.025, RED, 1);
		}
		b.circle(0, 0, 0.160, 96, 0.024, RED, 1);
		return List.copyOf(b.lines);
	}

	private static List<Stroke> squares() {
		Builder b = border();
		b.polygon(0.795, 4, 1, 0, 0.028, LIGHT);
		b.polygon(0.795, 4, 1, Math.PI / 4, 0.028, VIOLET);
		b.circle(0, 0, 0.510, 128, 0.025, RED, 1);
		b.polygon(0.435, 4, 1, 0, 0.025, LIGHT);
		b.polygon(0.435, 4, 1, Math.PI / 4, 0.025, LIGHT);
		b.circle(0, 0, 0.215, 96, 0.020, VIOLET, 1);
		return List.copyOf(b.lines);
	}

	private static List<Stroke> hexagon() {
		Builder b = border();
		b.polygon(0.805, 6, 1, -Math.PI / 2, 0.025, VIOLET);
		b.polygon(0.745, 6, 2, -Math.PI / 2, 0.028, LIGHT);
		b.circle(0, 0, 0.635, 128, 0.020, RED, 1);
		b.circle(0, 0, 0.345, 96, 0.028, LIGHT, 1);
		b.polygon(0.265, 6, 1, -Math.PI / 2, 0.025, VIOLET);
		b.circle(0, 0, 0.155, 64, 0.020, RED, 1);
		return List.copyOf(b.lines);
	}

	private static List<Stroke> octagon() {
		Builder b = border();
		b.polygon(0.805, 8, 1, -Math.PI / 2, 0.025, VIOLET);
		b.polygon(0.765, 8, 3, -Math.PI / 2, 0.028, LIGHT);
		b.circle(0, 0, 0.490, 128, 0.024, RED, 1);
		b.polygon(0.265, 8, 1, Math.PI / 8, 0.025, LIGHT);
		b.circle(0, 0, 0.175, 64, 0.020, VIOLET, 1);
		return List.copyOf(b.lines);
	}

	private static List<Stroke> petals() {
		Builder b = border();
		b.polygon(0.800, 6, 1, 0, 0.025, VIOLET);
		b.circle(0, 0, 0.735, 144, 0.025, RED, 1);
		for (int i = 0; i < 6; i++) {
			double a = TAU * i / 6;
			b.circle(Math.cos(a) * 0.330, Math.sin(a) * 0.330, 0.310, 96, 0.025, LIGHT, 1);
		}
		b.circle(0, 0, 0.130, 64, 0.020, VIOLET, 1);
		return List.copyOf(b.lines);
	}

	private static List<Stroke> radialRings() {
		Builder b = border();
		// Spokes connect the hub to the rim; there are no isolated ticks or glyphs.
		for (int i = 0; i < 12; i++) {
			double a = TAU * i / 12;
			b.line(Math.cos(a) * 0.355, Math.sin(a) * 0.355,
					Math.cos(a) * 0.795, Math.sin(a) * 0.795, 0.025, LIGHT, 1);
		}
		b.circle(0, 0, 0.795, 160, 0.025, LIGHT, 1);
		b.circle(0, 0, 0.585, 128, 0.024, RED, 1);
		b.circle(0, 0, 0.355, 96, 0.028, LIGHT, 1);
		b.circle(0, 0, 0.265, 96, 0.020, VIOLET, 1);
		b.polygon(0.190, 12, 1, 0, 0.020, RED);
		return List.copyOf(b.lines);
	}

	private static List<Stroke> create() {
		Builder b = border();
		// Interlaced decagonal chords, clipped around the five seals for legibility.
		for (int i = 0; i < 10; i++) {
			double a = -Math.PI / 2 + TAU * i / 10;
			double c = a + TAU * 3 / 10;
			b.lattice(Math.cos(a) * 0.814, Math.sin(a) * 0.814,
					Math.cos(c) * 0.814, Math.sin(c) * 0.814, 0.025, LIGHT, 0.9f);
		}
		b.circle(0, 0, 0.478, 128, 0.028, LIGHT, 0.95f);
		b.circle(0, 0, 0.417, 128, 0.020, RED, 0.9f);
		b.star(0, 0, 0.374, -Math.PI / 2, 0.025, LIGHT);
		for (int i = 0; i < 5; i++) {
			double angle = -Math.PI / 2 + TAU * i / 5;
			double x = Math.cos(angle) * 0.657, z = Math.sin(angle) * 0.657;
			b.circle(x, z, 0.148, 64, 0.024, LIGHT, 1);
			b.circle(x, z, 0.109, 56, 0.020, VIOLET, 0.95f);
			b.star(x, z, 0.083, angle, 0.020, LIGHT);
		}
		return List.copyOf(b.lines);
	}

	private static final class Builder {
		final List<Stroke> lines = new ArrayList<>();
		void line(double x1, double z1, double x2, double z2, double width, int color, float alpha) {
			double length = Math.hypot(x2 - x1, z2 - z1);
			if (length <= 1e-8) return;
			double nx = -(z2 - z1) / length, nz = (x2 - x1) / length;
			lines.add(new Stroke(x1, z1, x2, z2, width, color, alpha, nx, nz, nx, nz));
		}
		void circle(double x, double z, double r, int steps, double w, int color, float alpha) {
			for (int i = 0; i < steps; i++) {
				double a = TAU * i / steps, c = TAU * (i + 1) / steps;
				lines.add(new Stroke(x + Math.cos(a) * r, z + Math.sin(a) * r,
						x + Math.cos(c) * r, z + Math.sin(c) * r, w, color, alpha,
						-Math.cos(a), -Math.sin(a), -Math.cos(c), -Math.sin(c)));
			}
		}
		void star(double x, double z, double r, double rotation, double w, int color) {
			for (int i = 0; i < 5; i++) {
				double a = rotation + TAU * i / 5, c = a + TAU * 2 / 5;
				line(x + Math.cos(a) * r, z + Math.sin(a) * r, x + Math.cos(c) * r, z + Math.sin(c) * r, w, color, 1);
			}
		}
		void polygon(double r, int sides, int step, double rotation, double width, int color) {
			for (int i = 0; i < sides; i++) {
				double a = rotation + TAU * i / sides, c = rotation + TAU * ((i + step) % sides) / sides;
				line(Math.cos(a) * r, Math.sin(a) * r, Math.cos(c) * r, Math.sin(c) * r, width, color, 1);
			}
		}
		void lattice(double x1, double z1, double x2, double z2, double width, int color, float alpha) {
			// Include half the line widths and a small gap outside each satellite circle.
			double clearance = 0.148 + 0.024 / 2 + width / 2 + 0.006;
			lattice(x1, z1, x2, z2, width, color, alpha, 5, 0.657, clearance);
		}
		void lattice(double x1, double z1, double x2, double z2, double width, int color, float alpha,
		             int nodes, double orbit, double clearance) {
			List<Double> cuts = new ArrayList<>(List.of(0.0, 1.0));
			double dx = x2 - x1, dz = z2 - z1, length2 = dx * dx + dz * dz;
			for (int i = 0; i < nodes; i++) {
				double a = -Math.PI / 2 + TAU * i / nodes;
				double ux = x1 - Math.cos(a) * orbit, uz = z1 - Math.sin(a) * orbit;
				double dot = ux * dx + uz * dz, disc = dot * dot - length2 * (ux * ux + uz * uz - clearance * clearance);
				if (disc <= 0) continue;
				for (double t : new double[]{(-dot - Math.sqrt(disc)) / length2, (-dot + Math.sqrt(disc)) / length2})
					if (t > 0 && t < 1) cuts.add(t);
			}
			cuts.sort(Double::compare);
			for (int i = 0; i < cuts.size() - 1; i++) {
				double from = cuts.get(i), to = cuts.get(i + 1), mid = (from + to) / 2;
				boolean covered = false;
				for (int node = 0; node < nodes; node++) {
					double a = -Math.PI / 2 + TAU * node / nodes;
					covered |= Math.hypot(x1 + dx * mid - Math.cos(a) * orbit, z1 + dz * mid - Math.sin(a) * orbit) < clearance;
				}
				if (!covered) line(x1 + dx * from, z1 + dz * from, x1 + dx * to, z1 + dz * to, width, color, alpha);
			}
		}
	}
}
