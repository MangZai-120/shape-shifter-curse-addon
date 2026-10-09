package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.util.ArrayList;
import java.util.List;

/** Compare cached coordinates with the original rendering formulas, including seams and glow edges. */
public final class RenderGeometryCacheTest {
	private static int checks;
	public static void main(String[] args) {
		for (int layer = 0; layer < MagicCircleGeometry.layerCount(); layer++) verifyPattern(MagicCircleGeometry.layerStrokes(layer));
		for (int color : new int[]{0, 0xFFFFFF, 0xFF5555, 0xC6ABF5, 0x31AF72, 0x01ABCDEF}) {
			var strokes = MagicCircleGeometry.groundStrokes(color);
			verifyPattern(strokes);
			check(MagicCircleGeometry.groundStrokes(color) == strokes, "Canonical ground list reused");
			check(MagicCircleGeometry.mesh(strokes) == MagicCircleGeometry.mesh(strokes), "Mesh reused");
		}
		check(MagicCircleGeometry.mesh(new ArrayList<>(MagicCircleGeometry.strokes())) == null,
				"Caller-owned mutable patterns keep the uncached renderer");
		for (double radius : new double[]{0, 0.001, 0.72, 1, 2, 5.1, 20, 128.345}) verifySphere(radius);
		System.out.println("Render geometry cache checks passed: " + checks);
	}
	private static void verifyPattern(List<MagicCircleGeometry.Stroke> strokes) {
		var mesh = MagicCircleGeometry.mesh(strokes);
		check(mesh != null && mesh.size() == strokes.size(), "All canonical strokes are present");
		for (int i = 0; i < strokes.size(); i++) {
			var stroke = strokes.get(i);
			for (boolean glow : new boolean[]{false, true}) {
				var quad = mesh.quad(i, glow);
				double half = (glow ? stroke.width() * MagicCircleGeometry.GLOW_WIDTH : stroke.width()) / 2;
				equal(quad.x1(), (float) (stroke.x1() + stroke.nx1() * half));
				equal(quad.z1(), (float) (stroke.z1() + stroke.nz1() * half));
				equal(quad.x2(), (float) (stroke.x2() + stroke.nx2() * half));
				equal(quad.z2(), (float) (stroke.z2() + stroke.nz2() * half));
				equal(quad.x3(), (float) (stroke.x2() - stroke.nx2() * half));
				equal(quad.z3(), (float) (stroke.z2() - stroke.nz2() * half));
				equal(quad.x4(), (float) (stroke.x1() - stroke.nx1() * half));
				equal(quad.z4(), (float) (stroke.z1() - stroke.nz1() * half));
				check(quad.color() == stroke.color(), "Stroke colors unchanged");
				equal(quad.alpha(), stroke.alpha());
				for (float alpha : new float[]{0, 0.001f, 0.12f, 0.67f, 1}) {
					equal(glow ? alpha * quad.alpha() * MagicCircleGeometry.GLOW_ALPHA : alpha * quad.alpha(),
							glow ? alpha * stroke.alpha() * MagicCircleGeometry.GLOW_ALPHA : alpha * stroke.alpha());
				}
			}
		}
	}
	private static void verifySphere(double radius) {
		int vertex = 0;
		for (int latitude = 0; latitude < 32; latitude++) {
			double lower = -Math.PI / 2 + latitude * Math.PI / 32, upper = lower + Math.PI / 32;
			for (int longitude = 0; longitude < 64; longitude++) {
				double start = longitude * Math.PI / 32, end = start + Math.PI / 32;
				verifySphereVertex(vertex++, radius, lower, start);
				verifySphereVertex(vertex++, radius, upper, start);
				verifySphereVertex(vertex++, radius, upper, end);
				verifySphereVertex(vertex++, radius, lower, end);
			}
		}
		check(vertex == DomainSphereGeometry.vertexCount(), "Sphere vertex count and ordering unchanged");
	}
	private static void verifySphereVertex(int vertex, double radius, double latitude, double longitude) {
		equal(DomainSphereGeometry.x(vertex, radius), (float) (Math.cos(latitude) * Math.cos(longitude) * radius));
		equal(DomainSphereGeometry.y(vertex, radius), (float) (Math.sin(latitude) * radius));
		equal(DomainSphereGeometry.z(vertex, radius), (float) (Math.cos(latitude) * Math.sin(longitude) * radius));
	}
	private static void equal(float actual, float expected) {
		check(Float.floatToIntBits(actual) == Float.floatToIntBits(expected), "Vertex/opacity bits differ: " + actual + " != " + expected);
	}
	private static void check(boolean condition, String message) {
		checks++;
		if (!condition) throw new AssertionError(message);
	}
}
