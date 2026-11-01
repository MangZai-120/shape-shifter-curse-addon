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
		for (double radius : new double[]{0, 0.001, 0.72, 1, 2, 5.1, 20, 128.345}) {
			verifySphere(radius);
			verifyExplosionSphere(ExplosionRenderer.Geometry.SHOCKWAVE, 12, 24, radius);
			verifyExplosionSphere(ExplosionRenderer.Geometry.BALL, 8, 24, radius);
			verifyBeam(radius);
			for (double width : new double[]{0.009, 0.025, 0.055, 0.085}) {
				verifyRing(radius, width, 0.0);
				verifyRing(radius, width, -0.0);
			}
		}
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
	/** 对照原有极角公式；包括两极、经度接缝、逐条带顺序以及最后的半径乘法。 */
	private static void verifyExplosionSphere(ExplosionRenderer.Sphere sphere, int latitudes, int longitudes, double radius) {
		int vertex = 0;
		for (int lat = 0; lat < latitudes; lat++) {
			double phi0 = Math.PI * lat / latitudes, phi1 = Math.PI * (lat + 1) / latitudes;
			for (int lon = 0; lon < longitudes; lon++) {
				double theta0 = 2 * Math.PI * lon / longitudes, theta1 = 2 * Math.PI * (lon + 1) / longitudes;
				double s0 = Math.sin(phi0), c0 = Math.cos(phi0), s1 = Math.sin(phi1), c1 = Math.cos(phi1);
				verifyExplosionVertex(sphere, vertex++, radius, Math.cos(theta0) * s0, c0, Math.sin(theta0) * s0);
				verifyExplosionVertex(sphere, vertex++, radius, Math.cos(theta1) * s0, c0, Math.sin(theta1) * s0);
				verifyExplosionVertex(sphere, vertex++, radius, Math.cos(theta1) * s1, c1, Math.sin(theta1) * s1);
				verifyExplosionVertex(sphere, vertex++, radius, Math.cos(theta0) * s1, c1, Math.sin(theta0) * s1);
			}
		}
		check(vertex == sphere.vertexCount(), "Explosion sphere vertex count and ordering unchanged");
	}
	private static void verifyExplosionVertex(ExplosionRenderer.Sphere sphere, int vertex, double radius,
	                                          double x, double y, double z) {
		equal(sphere.x(vertex), x);
		equal(sphere.y(vertex), y);
		equal(sphere.z(vertex), z);
		equal((float) (sphere.x(vertex) * radius), (float) (x * radius));
		equal((float) (sphere.y(vertex) * radius), (float) (y * radius));
		equal((float) (sphere.z(vertex) * radius), (float) (z * radius));
	}
	private static void verifyBeam(double radius) {
		for (int point = 0; point <= 16; point++) {
			double angle = 2 * Math.PI * point / 16;
			equal(ExplosionRenderer.Geometry.BEAM_COS[point], Math.cos(angle));
			equal(ExplosionRenderer.Geometry.BEAM_SIN[point], Math.sin(angle));
			equal((float) (ExplosionRenderer.Geometry.BEAM_COS[point] * radius), (float) (Math.cos(angle) * radius));
			equal((float) (ExplosionRenderer.Geometry.BEAM_SIN[point] * radius), (float) (Math.sin(angle) * radius));
		}
	}
	/** 复算原始粗线展开，检查缓存没有改变正负零、接缝和最终四角的浮点舍入。 */
	private static void verifyRing(double radius, double width, double rotation) {
		for (int segment = 0; segment < 64; segment++) {
			double start = rotation + segment * Math.PI / 32;
			double end = rotation + (segment + 1) * Math.PI / 32;
			equal(CastingCircleRenderer.RingTrig.cos(segment), Math.cos(start));
			equal(CastingCircleRenderer.RingTrig.sin(segment), Math.sin(start));
			equal(CastingCircleRenderer.RingTrig.cos(segment + 1), Math.cos(end));
			equal(CastingCircleRenderer.RingTrig.sin(segment + 1), Math.sin(end));
			float[] original = lineCorners(Math.cos(start) * radius, Math.sin(start) * radius,
					Math.cos(end) * radius, Math.sin(end) * radius, width);
			float[] cached = lineCorners(CastingCircleRenderer.RingTrig.cos(segment) * radius,
					CastingCircleRenderer.RingTrig.sin(segment) * radius,
					CastingCircleRenderer.RingTrig.cos(segment + 1) * radius,
					CastingCircleRenderer.RingTrig.sin(segment + 1) * radius, width);
			for (int coordinate = 0; coordinate < original.length; coordinate++) equal(cached[coordinate], original[coordinate]);
		}
	}
	private static float[] lineCorners(double startX, double startZ, double endX, double endZ, double width) {
		double length = Math.hypot(endX - startX, endZ - startZ);
		double offsetX = -(endZ - startZ) / length * width * 0.5;
		double offsetZ = (endX - startX) / length * width * 0.5;
		return new float[]{(float) (startX + offsetX), (float) (startZ + offsetZ),
				(float) (endX + offsetX), (float) (endZ + offsetZ),
				(float) (endX - offsetX), (float) (endZ - offsetZ),
				(float) (startX - offsetX), (float) (startZ - offsetZ)};
	}
	private static void equal(double actual, double expected) {
		check(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected),
				"Cached direction bits differ: " + actual + " != " + expected);
	}
	private static void equal(float actual, float expected) {
		check(Float.floatToRawIntBits(actual) == Float.floatToRawIntBits(expected), "Vertex/opacity bits differ: " + actual + " != " + expected);
	}
	private static void check(boolean condition, String message) {
		checks++;
		if (!condition) throw new AssertionError(message);
	}
}
