package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Real atlas coverage, compact seven-element layout and emitted camera-facing vertices; no world. */
public final class AnomalyBadgeRendererTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        atlas();
        progressGeometry();
        combinations();
        orientations();
        healthScaling();
        System.out.println("Anomaly badges: atlas, element combinations, camera geometry and HP scaling passed (" + checks + " checks).");
    }

    private static void atlas() throws Exception {
        BufferedImage image;
        try (var stream = AnomalyBadgeRendererTest.class.getResourceAsStream(
                "/assets/ssc_addon/textures/gui/anomaly/badges64.png")) {
            if (stream == null) throw new AssertionError("Actual production atlas missing");
            image = ImageIO.read(stream);
        }
        check(AnomalyBadgeLayout.PIXELS == 64 && image.getWidth() == 1024 && image.getHeight() == 128,
                "The compact atlas stores native 64px layered art without hundreds of animation frames");
        var faces = new HashSet<List<Integer>>();
        int index = 0;
        for (var element : AnomalyBadgeLayout.ELEMENTS) {
            var pixels = new ArrayList<Integer>();
            var faceColours = new HashSet<Integer>();
            var ringColours = new HashSet<Integer>();
            check(element.baseTile() == index++ * 2 && element.progressTile() == element.baseTile() + 1,
                    "Every element addresses its own neighbouring face and progress layers");
            int ringPixels = 0;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                int face = pixel(image, element.baseTile(), x, y);
                int ring = pixel(image, element.progressTile(), x, y);
                check((face >>> 24) == 0 || (face >>> 24) == 255, "Face uses crisp transparent or opaque pixels");
                check((ring >>> 24) == 0 || (ring >>> 24) == 255, "Progress uses crisp transparent or opaque pixels");
                if (face != 0) faceColours.add(face);
                if (ring != 0) {
                    ringPixels++; ringColours.add(ring);
                    check(Math.hypot(x + .5 - 32, y + .5 - 32) > 20,
                            "Dynamic progress leaves the central element symbol untouched");
                }
                if (x == 0 || y == 0 || x == 63 || y == 63)
                    check((face >>> 24) == 0 && (ring >>> 24) == 0, "Transparent margin prevents neighbouring tile bleed");
                pixels.add(face);
            }
            check(faceColours.size() > 30 && ringColours.size() > 16,
                    "Authored metallic shading and element colour are stored in the texture, not a white tint mask");
            check(ringPixels > 150 && ringPixels < 1800, "The progress layer contains a narrow ring");
            check((pixel(image, element.baseTile(), 32, 32) >>> 24) == 255,
                    "The recessed face remains opaque behind hollow elemental symbols");
            check(faces.add(pixels), "All seven selected element faces remain distinct");
            symmetricFrameAndCleanInnerEdge(image, element);
            neutralTrackAndHighlights(image, element);
            uniformRingMaterial(image, element);
        }
        check(AnomalyBadgeLayout.ELEMENTS.get(0).color() == 0xBD1641, "Fire uses the reference red");
        check(AnomalyBadgeLayout.ELEMENTS.get(1).color() == 0x55DAEA, "Ice uses cyan blue");
        for (int amount = 0; amount <= 500; amount++)
            check(Math.abs(AnomalyBadgeLayout.fraction(amount, 500) - amount / 500.0) < 1e-12,
                    "Every accumulation retains its continuous progress fraction");
        check(AnomalyBadgeLayout.fraction(900, 500) == 1, "Overfull input clips to the complete ring");
        check(AnomalyBadgeLayout.fraction(-1, 500) == 0 && AnomalyBadgeLayout.fraction(Double.NaN, 500) == 0
                        && AnomalyBadgeLayout.fraction(Double.POSITIVE_INFINITY, 500) == 0
                        && AnomalyBadgeLayout.fraction(200, 0) == 0,
                "Invalid display values cannot create invalid geometry");
        try (var stream = AnomalyBadgeRendererTest.class.getResourceAsStream(
                "/assets/ssc_addon/textures/gui/anomaly/badges64.png.mcmeta")) {
            check(stream != null && new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .contains("\"blur\":false"), "Pixel art disables filtered enlargement");
        }
    }

    private static void symmetricFrameAndCleanInnerEdge(BufferedImage image, AnomalyBadgeLayout.Element element) {
        var reference = AnomalyBadgeLayout.ELEMENTS.get(0);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            double radius = Math.hypot(x - 31.5, y - 31.5);
            int face = pixel(image, element.baseTile(), x, y), alpha = face >>> 24;
            check(alpha == (radius <= 30 ? 255 : 0),
                    "Every face uses the same analytic 30px circular silhouette rather than inherited cutout fragments");
            check(alpha == (pixel(image, element.baseTile(), 63 - x, y) >>> 24)
                            && alpha == (pixel(image, element.baseTile(), x, 63 - y) >>> 24)
                            && alpha == (pixel(image, element.baseTile(), 63 - x, 63 - y) >>> 24),
                    "The complete circular frame is mirror-symmetric and centrally symmetric independently of glyph colour");
            check(alpha == (pixel(image, reference.baseTile(), x, y) >>> 24),
                    "All seven elements have a pixel-identical circular outline");
            if (radius >= 20 && radius < 22)
                check((face & 0xFFFFFF) == 0x141414,
                        "The inner-edge guard disc is clean neutral grey, including the old coloured residue at radius 21");
            if (radius >= 22 && radius < 23)
                check((face & 0xFFFFFF) == 0x080808,
                        "The inner bevel is rebuilt with a neutral black outline");
            if (radius > 29 && radius <= 30)
                check((face & 0xFFFFFF) == 0x090909,
                        "The outer black outline follows the same circular mask in every direction");
        }
    }

    private static void neutralTrackAndHighlights(BufferedImage image, AnomalyBadgeLayout.Element element) {
        int[][] origins = {{30, 4}, {48, 12}, {56, 30}, {48, 48}, {30, 56}, {12, 48}, {4, 30}, {12, 12}};
        double[][] shades = {{0, .58, .53, 0}, {.50, .68, .62, .45}, {.42, .57, .48, .36}, {0, .40, .30, 0}};
        var markers = new boolean[64 * 64];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            if (Math.hypot(x - 31.5, y - 31.5) < 20) continue;
            int face = pixel(image, element.baseTile(), x, y);
            if ((face >>> 24) == 0) continue;
            int r = face >> 16 & 255, g = face >> 8 & 255, b = face & 255;
            double shade = 0;
            for (int[] origin : origins) {
                int xx = x - origin[0], yy = y - origin[1];
                if (xx >= 0 && xx < 4 && yy >= 0 && yy < 4) shade = shades[yy][xx];
            }
            if (shade == 0) {
                check(r == g && g == b,
                        "The entire empty frame outside the glyph and marker masks is neutral, including the inner guard edge");
                continue;
            }
            int red = element.color() >> 16 & 255, green = element.color() >> 8 & 255, blue = element.color() & 255;
            check(r == (int) Math.floor(red * shade) && g == (int) Math.floor(green * shade)
                            && b == (int) Math.floor(blue * shade),
                    "Each marker is a darker shade of its own element rather than a shared cyan highlight");
            check(Math.max(r, Math.max(g, b)) <= Math.max(red, Math.max(green, blue)) * .68,
                    "Even the brightest marker pixel remains darker than the elemental accent");
            markers[y * 64 + x] = true;
            check((pixel(image, element.progressTile(), x, y) >>> 24) == 0,
                    "Dynamic elemental fill cannot cover the eight fixed dark elemental markers");
        }
        var visited = new boolean[markers.length];
        var angles = new ArrayList<Double>();
        double minimumRadius = Double.POSITIVE_INFINITY, maximumRadius = 0;
        var pending = new int[markers.length];
        for (int start = 0; start < markers.length; start++) {
            if (!markers[start] || visited[start]) continue;
            int head = 0, tail = 0, count = 0, sumX = 0, sumY = 0;
            pending[tail++] = start; visited[start] = true;
            while (head < tail) {
                int point = pending[head++], x = point % 64, y = point / 64;
                count++; sumX += x; sumY += y;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (xx < 0 || xx >= 64 || yy < 0 || yy >= 64) continue;
                    int next = yy * 64 + xx;
                    if (markers[next] && !visited[next]) { visited[next] = true; pending[tail++] = next; }
                }
            }
            check(count == 12, "Each elemental marker retains the complete twelve-pixel clipped-corner shape");
            double x = sumX / (double) count - 31.5, y = sumY / (double) count - 31.5;
            double radius = Math.hypot(x, y), angle = Math.atan2(x, -y);
            if (angle < 0) angle += Math.PI * 2;
            check(radius >= 24.5 && radius <= 28.5, "Every elemental marker lies inside the same narrow ring");
            minimumRadius = Math.min(minimumRadius, radius); maximumRadius = Math.max(maximumRadius, radius);
            angles.add(angle);
        }
        check(angles.size() == 8, "Each empty ring contains exactly eight separate dark elemental markers");
        check(maximumRadius - minimumRadius <= 1.5, "Elemental markers share one radius after pixel rounding");
        angles.sort(Double::compareTo);
        for (int i = 0; i < angles.size(); i++) {
            double next = i + 1 == angles.size() ? angles.get(0) + Math.PI * 2 : angles.get(i + 1);
            check(Math.abs(next - angles.get(i) - Math.PI / 4) <= .08,
                    "The eight elemental markers are evenly spaced at 45-degree intervals");
        }
    }

    private static void uniformRingMaterial(BufferedImage image, AnomalyBadgeLayout.Element element) {
        // Pixel-grid rotations and reflections compare the actual material, not only its average
        // brightness: a separately reconstructed final third cannot pass with a different palette.
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int ring = pixel(image, element.progressTile(), x, y);
            check(ring == pixel(image, element.progressTile(), 63 - x, y)
                            && ring == pixel(image, element.progressTile(), x, 63 - y)
                            && ring == pixel(image, element.progressTile(), y, x),
                    "Every octant uses the same approved ring material, including colour, shading and transparent marker gaps");
            double radius = Math.hypot(x - 31.5, y - 31.5);
            if (radius < 23 || radius > 29)
                check((ring >>> 24) == 0, "Dynamic fill stays strictly inside its symmetric 23-to-29px annulus");
        }
    }

    private static void progressGeometry() {
        int tile = AnomalyBadgeLayout.ELEMENTS.get(0).progressTile();
        for (int amount = 0; amount <= 500; amount++) {
            var consumer = new RecordingConsumer();
            AnomalyBadgeRenderer.progress(consumer, new Matrix4f(), tile, 0, 0, amount / 500.0);
            check(consumer.vertices.size() % 4 == 0 && consumer.vertices.size() <= 20,
                    "A partial circle needs at most five quads regardless of pixel resolution");
            check(amount > 0 || consumer.vertices.isEmpty(), "Zero accumulation has no coloured progress geometry");
            for (var vertex : consumer.vertices) {
                check(vertex.rgb == 0xFFFFFF && close(vertex.z, .001),
                        "Progress preserves texture colours and stays in front of the face");
                double u = (vertex.u - AnomalyBadgeLayout.u(tile)) * AnomalyBadgeLayout.ATLAS_COLUMNS;
                double v = (vertex.v - AnomalyBadgeLayout.v(tile)) * AnomalyBadgeLayout.ATLAS_ROWS;
                check(Math.abs(u - (vertex.x / .2 + .5)) < 1e-5
                                && Math.abs(v - (.5 - vertex.y / .2)) < 1e-5,
                        "Sector clipping keeps texels fixed instead of stretching the ring");
            }
            for (int offset = 0; offset < consumer.vertices.size(); offset += 4) {
                var a = consumer.vertices.get(offset); var b = consumer.vertices.get(offset + 1);
                var c = consumer.vertices.get(offset + 2);
                check(cross(a.x, a.y, b.x, b.y, c.x, c.y) > 0,
                        "Every visible sector triangle faces the camera");
            }
            for (int sample = 0; sample < 64; sample++) {
                double turn = (sample + .37) / 64.0, angle = turn * Math.PI * 2;
                double x = Math.sin(angle) * .08, y = Math.cos(angle) * .08;
                check(covered(consumer.vertices, x, y) == (turn < amount / 500.0),
                        "Rendered sectors fill clockwise from twelve o'clock for every value from 0 to 500");
            }
        }
        var full = new RecordingConsumer();
        AnomalyBadgeRenderer.progress(full, new Matrix4f(), tile, 0, 0, 1);
        check(full.vertices.size() == 4, "A full ring uses a single quad");
        var invalid = new RecordingConsumer();
        AnomalyBadgeRenderer.progress(invalid, new Matrix4f(), tile, 0, 0, Double.NaN);
        AnomalyBadgeRenderer.progress(invalid, new Matrix4f(), tile, 0, 0, -1);
        check(invalid.vertices.isEmpty(), "Invalid progress cannot leak malformed vertices");
    }

    private static boolean covered(List<Vertex> vertices, double x, double y) {
        for (int i = 0; i < vertices.size(); i += 4) {
            var a = vertices.get(i); var b = vertices.get(i + 1);
            var c = vertices.get(i + 2); var d = vertices.get(i + 3);
            if (inside(a, b, c, x, y) || inside(a, c, d, x, y)) return true;
        }
        return false;
    }

    private static boolean inside(Vertex a, Vertex b, Vertex c, double x, double y) {
        if (cross(a.x, a.y, b.x, b.y, c.x, c.y) <= 1e-12) return false;
        return cross(a.x, a.y, b.x, b.y, x, y) >= -1e-10
                && cross(b.x, b.y, c.x, c.y, x, y) >= -1e-10
                && cross(c.x, c.y, a.x, a.y, x, y) >= -1e-10;
    }

    private static double cross(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static void combinations() {
        for (float height : new float[]{.2f, .4f, .6f, 1.5f, 1.8f, 3f}) {
            for (int count = 1; count <= 7; count++) {
                float top = AnomalyBadgeLayout.top(height, count);
                float bottom = top - (Math.min(count, 4) - 1) * .23f - .1f;
                check(bottom >= .0399f, "Small forms and swimming cannot bury lower rows below the feet");
                if (height >= 1.5f) check(close(top, height * .8f), "Normal player badges stay beside the upper body");
            }
        }
        for (int mask = 0; mask < 128; mask++) {
            var amounts = new LinkedHashMap<String, Double>();
            // Deliberately reversed insertion order: network map order must not determine placement.
            for (int i = 6; i >= 0; i--) amounts.put(AnomalyBadgeLayout.ELEMENTS.get(i).id(),
                    (mask & (1 << i)) != 0 ? 125.0 + i * 25 : 0.0);
            amounts.put("unknown_future_type", 250.0);
            var consumer = new RecordingConsumer();
            int count = AnomalyBadgeRenderer.badges(consumer, new Matrix4f(), amounts, .6f);
            check(count == Integer.bitCount(mask), "Only populated supported elements occupy slots");
            check(consumer.vertices.size() <= count * 24, "Geometry stays bounded for every active-element combination");
            int slot = 0, offset = 0;
            for (int i = 0; i < 7; i++) {
                if ((mask & (1 << i)) == 0) continue;
                var style = AnomalyBadgeLayout.ELEMENTS.get(i);
                var single = new RecordingConsumer();
                AnomalyBadgeRenderer.progress(single, new Matrix4f(), style.progressTile(), 0, 0,
                        (125 + i * 25) / 500.0);
                Vertex base = consumer.vertices.get(offset);
                Vertex ring = consumer.vertices.get(offset + 4);
                check(close(base.x, .6f + slot / 4 * .23f - .1f)
                                && close(base.y, -(slot % 4) * .23f + .1f),
                        "Fill down four rows, then start the column to the right, without empty gaps");
                check(base.rgb == 0xFFFFFF && ring.rgb == 0xFFFFFF,
                        "The selected 64px art retains its original colours rather than multiplying by a flat tint");
                check(base.z < ring.z, "Layer depth prevents the face covering coloured progress");
                check(close(base.u, AnomalyBadgeLayout.u(style.baseTile()))
                                && close(base.v, AnomalyBadgeLayout.v(style.baseTile())),
                        "Stable element order selects its matching face tile");
                check(close(ring.u, AnomalyBadgeLayout.u(style.progressTile()) + .5f / AnomalyBadgeLayout.ATLAS_COLUMNS)
                                && close(ring.v, AnomalyBadgeLayout.v(style.progressTile()) + .5f / AnomalyBadgeLayout.ATLAS_ROWS),
                        "The progress sector samples its element's own coloured ring");
                offset += 4 + single.vertices.size();
                slot++;
            }
            check(offset == consumer.vertices.size(), "All emitted vertices belong to the expected compact element slots");
        }
    }

    private static void orientations() {
        for (int yaw = -180; yaw <= 180; yaw += 30) for (int pitch = -90; pitch <= 90; pitch += 30) {
            // Camera.setRotation uses rotationYXZ(-yaw, pitch, 0). WorldRenderer's actual
            // view rotates by pitch, then yaw+180; a quaternion-only test misses back-face culling.
            float y = (float)Math.toRadians(yaw), p = (float)Math.toRadians(pitch);
            var rotation = new Quaternionf().rotationYXZ(-y,p,0);
            var view = new Matrix4f().rotationX(p).rotateY(y+(float)Math.PI);
            var point = new Vector3f(0,0,4).rotate(rotation);
            var matrix = new Matrix4f(view).translate(point);
            AnomalyBadgeRenderer.faceCamera(matrix,rotation);
            var consumer = new RecordingConsumer();
            AnomalyBadgeRenderer.tile(consumer, matrix, 0, .6f, -.23f, 0);
            var a = consumer.vertices.get(0); var b = consumer.vertices.get(1);
            var c = consumer.vertices.get(2); var d = consumer.vertices.get(3);
            var right = difference(d, a).normalize();
            var up = difference(a, b).normalize();
            check(right.distance(new Vector3f(1, 0, 0)) < 1e-5,
                    "Badge right remains screen right from every camera yaw and pitch");
            check(up.distance(new Vector3f(0, 1, 0)) < 1e-5,
                    "Badge stays upright and camera-facing, including directly above/below");
            check(close(difference(d, a).length(), .2f) && close(difference(c, d).length(), .2f),
                    "World size stays small and independent of viewing angle");
            check(a.u == b.u && c.u == d.u && a.v == d.v && b.v == c.v && a.v < b.v,
                    "Texture cannot become upside-down or mirrored");
            var front=difference(b,a).cross(difference(c,a)).normalize();
            check(front.z > .99999f, "Real camera view sees the quad front, so normal back-face culling keeps it visible");
            check(a.x > 0 && d.x > a.x && a.z < -3.9,
                    "Badge is in front of the actual camera and on the target's screen-right");
        }
    }

    private static void healthScaling() {
        check(AnomalyBadgeLayout.bodyRadius(.6f,1.95f) >= .58f,
                "Villager shoulders are accounted for beyond its narrow collision box");
        check(close(AnomalyBadgeLayout.bodyRadius(.9f,.9f),.45f), "Short wide creatures retain their full collision width");
        for (float[] pair : new float[][]{{-10,.3f},{0,.3f},{1,.3f},{5,.3f},{10,.37777778f},
                {20,.53333333f},{27.5f,.65f},{50,1},{100,1},{Float.MAX_VALUE,1}}) {
            check(close(AnomalyBadgeLayout.healthScale(pair[0]), pair[1]), "Health uses the absolute 5/50 HP thresholds");
            check(close(AnomalyBadgeLayout.displayScale(true,pair[0]),1), "Player badges always retain normal size regardless of health");
            check(close(AnomalyBadgeLayout.displayScale(false,pair[0]),pair[1]), "NPC badges retain the health-based size rule");
        }
        float previous = .3f;
        for (float hp = 0; hp <= 60; hp += .25f) {
            float scale = AnomalyBadgeLayout.healthScale(hp);
            check(scale >= previous && scale >= .3f && scale <= 1, "Scaling is continuous, monotonic and clamped");
            previous = scale;
        }
        check(close(AnomalyBadgeLayout.healthScale(Float.NaN), 1), "Invalid external health cannot corrupt the render matrix");

        var amounts = new LinkedHashMap<String, Double>();
        for (var style : AnomalyBadgeLayout.ELEMENTS) amounts.put(style.id(), 250.0);
        for (float hp : new float[]{0,5,20,27.5f,50,120}) {
            float scale = AnomalyBadgeLayout.healthScale(hp);
            for (float height : new float[]{.2f,.6f,1.8f,4}) for (int count = 1; count <= 7; count++) {
                float bottom = AnomalyBadgeLayout.top(height,count,scale)
                        - ((Math.min(4,count)-1)*.23f+.1f)*scale;
                check(bottom >= .0399f, "Scaled low-health creature badges keep their lower row above the feet");
            }
            for (float radius : new float[]{.15f,1.5f}) {
                for (float yaw : new float[]{-120,0,120}) for (float pitch : new float[]{-80,0,80}) {
                    float y=(float)Math.toRadians(yaw),p=(float)Math.toRadians(pitch);
                    var rotation = new Quaternionf().rotationYXZ(-y,p,0);
                    var matrix = new Matrix4f().rotationX(p).rotateY(y+(float)Math.PI);
                    AnomalyBadgeRenderer.faceCamera(matrix,rotation);
                    AnomalyBadgeRenderer.scaleAndOffset(matrix,radius,scale);
                    var consumer = new RecordingConsumer();
                    AnomalyBadgeRenderer.badges(consumer,matrix,amounts,.1f);
                    var right = new Vector3f(1,0,0);
                    var up = new Vector3f(0,1,0);
                    for (int slot = 0; slot < 7; slot++) {
                        // At half progress the face and three sector quads occupy sixteen vertices.
                        var a = consumer.vertices.get(slot*16);
                        var b = consumer.vertices.get(slot*16+1);
                        var d = consumer.vertices.get(slot*16+3);
                        float screenX = new Vector3f((float)a.x,(float)a.y,(float)a.z).dot(right);
                        float screenY = new Vector3f((float)a.x,(float)a.y,(float)a.z).dot(up);
                        check(close(difference(d,a).length(),.2f*scale)
                                        && close(difference(a,b).length(),.2f*scale),
                                "Every health level scales both axes equally without stretching the icon");
                        check(close(screenX,radius+.12f+slot/4*.23f*scale) && screenX > radius,
                                "Creature body radius stays unscaled and badges stay outside its right side");
                        check(close(screenY,(.1f-slot%4*.23f)*scale), "Row spacing scales with the badge size");
                    }
                }
            }
        }
    }

    private static Vector3f difference(Vertex a, Vertex b) { return new Vector3f((float)(a.x-b.x), (float)(a.y-b.y), (float)(a.z-b.z)); }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-5; }
    private static int pixel(BufferedImage image, int tile, int x, int y) {
        return image.getRGB(tile % AnomalyBadgeLayout.ATLAS_COLUMNS * AnomalyBadgeLayout.PIXELS + x,
                tile / AnomalyBadgeLayout.ATLAS_COLUMNS * AnomalyBadgeLayout.PIXELS + y);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }

    private record Vertex(double x, double y, double z, float u, float v, int rgb) {}
    private static final class RecordingConsumer implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        double x, y, z; float u, v; int rgb;
        @Override public VertexConsumer vertex(double x, double y, double z) { this.x=x; this.y=y; this.z=z; return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { check(a == 255, "Opaque pixel palette"); rgb=r<<16|g<<8|b; return this; }
        @Override public VertexConsumer texture(float u, float v) { this.u=u; this.v=v; return this; }
        @Override public VertexConsumer overlay(int u, int v) { throw new AssertionError("Text layer has no overlay attribute"); }
        @Override public VertexConsumer light(int u, int v) { check(u == 240 && v == 240, "Full bright preserves the pixel palette"); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { throw new AssertionError("Unshaded text layer has no normal attribute"); }
        @Override public void next() { vertices.add(new Vertex(x,y,z,u,v,rgb)); }
        @Override public void fixedColor(int r, int g, int b, int a) { throw new AssertionError(); }
        @Override public void unfixColor() { throw new AssertionError(); }
    }
}
