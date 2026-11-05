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
        combinations();
        orientations();
        healthScaling();
        System.out.println("Anomaly badges: atlas, element combinations, camera geometry and HP scaling passed (" + checks + " checks).");
    }

    private static void atlas() throws Exception {
        BufferedImage image;
        try (var stream = AnomalyBadgeRendererTest.class.getResourceAsStream(
                "/assets/ssc_addon/textures/gui/anomaly/badges.png")) {
            if (stream == null) throw new AssertionError("Actual production atlas missing");
            image = ImageIO.read(stream);
        }
        check(image.getWidth() == 1024 && image.getHeight() == 512, "Atlas holds exact 32 by 32 tiles");
        check(pixel(image, 501, 16, 16) == 0xFF3E3E3E, "Centre keeps the reference charcoal grey");
        check(pixel(image, 501, 16, 2) == 0xFF282828, "Unfilled ring keeps the reference darker grey");
        check(pixel(image, 501, 16, 1) == 0xFF090909, "Black outer outline inside the transparent one-pixel margin");
        check(pixel(image, 501, 0, 0) == 0, "Transparent square corners");
        for (int tile : new int[]{500, 501}) {
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int color = pixel(image, tile, x, y);
                check(color == pixel(image, tile, 31-x, 31-y), "Outer frame and full ring share the exact pixel centre");
                check(color == pixel(image, tile, 31-x, y) && color == pixel(image, tile, x, 31-y),
                        "Circular frame is mirrored horizontally and vertically without skew");
            }
        }
        int total = coverage(image, 500);
        check(total == 200 && coverage(image, 0) == 0, "Empty and full rings cover only the annulus");
        int previous = 0;
        for (int step = 0; step <= 500; step++) {
            int filled = coverage(image, step);
            check(filled >= previous, "Increasing accumulation never erases a ring pixel");
            check(Math.abs(filled / (double)total - step / 500.0) <= .015,
                    "Ring area follows the 500 maximum within its pixel resolution");
            previous = filled;
        }
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            if (pixel(image, 125, x, y) != 0) check(x >= 16 && y < 16, "Quarter ring starts at top and runs clockwise");
            if (pixel(image, 250, x, y) != 0) check(x >= 16, "Half ring occupies the right half");
            if (pixel(image, 500, x, y) != 0) {
                double radius = Math.hypot(x + .5 - 16, y + .5 - 16);
                check(radius > 12 && radius <= 14.5, "Progress cannot recolour the grey centre or black outlines");
            }
        }
        var silhouettes = new HashSet<List<Integer>>();
        for (var element : AnomalyBadgeLayout.ELEMENTS) {
            var pixels = new ArrayList<Integer>();
            int visible = 0;
            int minX = 32, minY = 32, maxX = -1, maxY = -1;
            boolean mirrorHorizontal = !element.id().equals("lunar");
            boolean mirrorVertical = List.of("ice", "void", "space").contains(element.id());
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int value = pixel(image, element.glyphTile(), x, y);
                if (value != 0) {
                    check(value == 0xFFFFFFFF, "Glyph can be tinted without altering the background");
                    check(Math.hypot(x+.5-16, y+.5-16) <= 11, "Glyph never touches the ring or crosses its inner border");
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    visible++;
                }
                if (mirrorHorizontal) check(value == pixel(image, element.glyphTile(), 31-x, y),
                        "Symmetric glyph has identical left/right pixels: " + element.id());
                if (mirrorVertical) check(value == pixel(image, element.glyphTile(), x, 31-y),
                        "Symmetric glyph has identical top/bottom pixels: " + element.id());
                pixels.add(value);
            }
            check(visible >= 35, "Small glyph retains a visible silhouette");
            if (element.id().equals("lunar"))
                check(minX == 8 && maxX == 22 && minY == 7 && maxY == 24,
                        "Original tilted crescent retains its authored position");
            else check(minX + maxX == 31 && minY + maxY == 31, "Glyph bounds have equal margins to the centred frame");
            if (element.id().equals("ice") || element.id().equals("void") || element.id().equals("space"))
                check(maxX - minX == maxY - minY, "Radial glyph keeps a square aspect ratio");
            check(silhouettes.add(pixels), "Every element has a different silhouette");
        }
        check(connectedParts(image, 504) == 1, "Lunar glyph is one crescent without a detached plus sign");
        var lunarHash = java.security.MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
            lunarHash.update((byte)(pixel(image, 504, x, y) >>> 24));
        check(java.util.HexFormat.of().formatHex(lunarHash.digest())
                        .equals("60b15d40d8a1a16760df0720d6f7c4755b318fdb18912d3d9b34f382809e75ac"),
                "Approved original crescent is pixel-identical after removing only the nine plus-sign pixels");
        check(pixel(image, 505, 11, 14) == 0 && pixel(image, 505, 20, 14) == 0
                        && pixel(image, 505, 15, 10) == 0xFFFFFFFF,
                "Curse skull has two centred eye sockets and an intact cranium");
        check(pixel(image, 505, 15, 17) == 0 && pixel(image, 505, 16, 17) == 0,
                "Skull nose is centred between the eye sockets");
        check(pixel(image, 505, 12, 21) == 0xFFFFFFFF && pixel(image, 505, 15, 21) == 0xFFFFFFFF
                        && pixel(image, 505, 18, 21) == 0xFFFFFFFF
                        && pixel(image, 505, 14, 21) == 0 && pixel(image, 505, 17, 21) == 0,
                "Skull teeth and gaps have matching spacing");
        check(AnomalyBadgeLayout.ELEMENTS.get(0).color() == 0xBD1641, "Fire uses the reference red");
        check(AnomalyBadgeLayout.ELEMENTS.get(1).color() == 0x55DAEA, "Ice uses cyan blue");
        for (int amount = 0; amount <= 500; amount++)
            check(AnomalyBadgeLayout.ringTile(amount, 500) == amount, "Every integer accumulation selects its actual atlas frame");
        check(AnomalyBadgeLayout.ringTile(900, 500) == 500, "Overfull input cannot run into glyph tiles");
        check(AnomalyBadgeLayout.ringTile(-1, 500) == 0 && AnomalyBadgeLayout.ringTile(Double.NaN, 500) == 0,
                "Invalid display values remain within the ring atlas");
        try (var stream = AnomalyBadgeRendererTest.class.getResourceAsStream(
                "/assets/ssc_addon/textures/gui/anomaly/badges.png.mcmeta")) {
            check(stream != null && new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .contains("\"blur\":false"), "Pixel art disables filtered enlargement");
        }
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
            check(consumer.vertices.size() == count * 12, "Exactly three quads per active badge");
            int slot = 0;
            for (int i = 0; i < 7; i++) {
                if ((mask & (1 << i)) == 0) continue;
                var style = AnomalyBadgeLayout.ELEMENTS.get(i);
                Vertex base = consumer.vertices.get(slot * 12);
                Vertex ring = consumer.vertices.get(slot * 12 + 4);
                Vertex glyph = consumer.vertices.get(slot * 12 + 8);
                check(close(base.x, .6f + slot / 4 * .23f - .1f)
                                && close(base.y, -(slot % 4) * .23f + .1f),
                        "Fill down four rows, then start the column to the right, without empty gaps");
                check(base.rgb == 0xFFFFFF && glyph.rgb == style.color() && ring.rgb == style.color(),
                        "Only glyph and ring receive the elemental colour");
                check(base.z < ring.z && ring.z < glyph.z, "Layer depth prevents the background covering coloured details");
                check(close(glyph.u, AnomalyBadgeLayout.u(style.glyphTile()))
                                && close(glyph.v, AnomalyBadgeLayout.v(style.glyphTile())),
                        "Element order and atlas glyph agree");
                check(close(ring.u, AnomalyBadgeLayout.u(125 + i * 25))
                                && close(ring.v, AnomalyBadgeLayout.v(125 + i * 25)),
                        "Actual emitted UVs select that element's own accumulation");
                slot++;
            }
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
            AnomalyBadgeRenderer.tile(consumer, matrix, 125, .6f, -.23f, 0x55DAEA);
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
                        var a = consumer.vertices.get(slot*12);
                        var b = consumer.vertices.get(slot*12+1);
                        var d = consumer.vertices.get(slot*12+3);
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
    private static int pixel(BufferedImage image, int tile, int x, int y) { return image.getRGB(tile % 32 * 32 + x, tile / 32 * 32 + y); }
    private static int coverage(BufferedImage image, int tile) {
        int count = 0;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) if (pixel(image, tile, x, y) != 0) count++;
        return count;
    }

    private static int connectedParts(BufferedImage image, int tile) {
        boolean[] visited = new boolean[1024];
        int[] pending = new int[1024];
        int parts = 0;
        for (int start = 0; start < 1024; start++) {
            if (visited[start] || pixel(image, tile, start%32, start/32) == 0) continue;
            parts++;
            int head = 0, tail = 0;
            pending[tail++] = start; visited[start] = true;
            while (head < tail) {
                int point = pending[head++], x = point%32, y = point/32;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x+dx, ny = y+dy;
                    if (nx < 0 || nx >= 32 || ny < 0 || ny >= 32) continue;
                    int next = ny*32+nx;
                    if (!visited[next] && pixel(image, tile, nx, ny) != 0) {
                        visited[next] = true; pending[tail++] = next;
                    }
                }
            }
        }
        return parts;
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
