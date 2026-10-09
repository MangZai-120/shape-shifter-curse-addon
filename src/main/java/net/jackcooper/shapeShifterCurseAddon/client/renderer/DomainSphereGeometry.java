package net.jackcooper.shapeShifterCurseAddon.client.renderer;

/** Fixed 32-by-64 quad sphere. Keeps the old vertex order and double arithmetic before float conversion. */
public final class DomainSphereGeometry {
	private static final int VERTEX_COUNT = 32 * 64 * 4;
	private static final double[] X = new double[VERTEX_COUNT];
	private static final double[] Y = new double[VERTEX_COUNT];
	private static final double[] Z = new double[VERTEX_COUNT];
	static {
		int vertex = 0;
		for (int latitude = 0; latitude < 32; latitude++) {
			double lower = -Math.PI / 2 + latitude * Math.PI / 32;
			double upper = lower + Math.PI / 32;
			for (int longitude = 0; longitude < 64; longitude++) {
				double start = longitude * Math.PI / 32, end = start + Math.PI / 32;
				put(vertex++, lower, start);
				put(vertex++, upper, start);
				put(vertex++, upper, end);
				put(vertex++, lower, end);
			}
		}
	}
	private DomainSphereGeometry() {}
	private static void put(int vertex, double latitude, double longitude) {
		X[vertex] = Math.cos(latitude) * Math.cos(longitude);
		Y[vertex] = Math.sin(latitude);
		Z[vertex] = Math.cos(latitude) * Math.sin(longitude);
	}
	public static int vertexCount() { return VERTEX_COUNT; }
	public static float x(int vertex, double radius) { return (float) (X[vertex] * radius); }
	public static float y(int vertex, double radius) { return (float) (Y[vertex] * radius); }
	public static float z(int vertex, double radius) { return (float) (Z[vertex] * radius); }
}
