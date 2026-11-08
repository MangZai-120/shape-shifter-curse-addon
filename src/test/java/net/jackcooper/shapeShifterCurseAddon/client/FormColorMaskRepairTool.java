package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Repairs packed RGBA channel data, without altering any source appearance texture. */
public final class FormColorMaskRepairTool {
    private static final int ACCENT2 = 0xff0000ff;
    private final Path root, resources, output;
    private final ZipFile ssc;
    private final Map<Path, byte[]> edits = new LinkedHashMap<>();
    private final List<Map<String, Object>> changes = new ArrayList<>();
    private final Map<String, Integer> expectedMeshPixels = new LinkedHashMap<>();

    private FormColorMaskRepairTool(Path root, Path jar) throws Exception {
        this.root = root; resources = root.resolve("src/main/resources");
        output = root.resolve("output/ssca-form-color-fix-2026-11-07");
        ssc = new ZipFile(jar.toFile());
        JsonObject audit = JsonParser.parseString(Files.readString(root.resolve(
                "output/ssca-form-color-coverage-2026-11-07/client/color-coverage/result.json"))).getAsJsonObject();
        for (JsonElement form : audit.getAsJsonArray("forms")) for (JsonElement layer : form.getAsJsonObject().getAsJsonArray("layers")) {
            JsonObject l = layer.getAsJsonObject();
            if (l.get("layer").getAsString().equals("texture")) expectedMeshPixels.put(
                    l.get("texture").getAsString(), l.get("mesh_used_visible_pixels").getAsInt());
        }
    }

    private byte[] resource(String id) throws Exception {
        String[] parts = id.split(":", 2);
        String path = "assets/" + parts[0] + "/" + parts[1];
        Path local = resources.resolve(path);
        if (Files.isRegularFile(local)) return Files.readAllBytes(local);
        var entry = ssc.getEntry(path);
        if (entry == null) throw new IllegalArgumentException("Missing resource " + id);
        try (var stream = ssc.getInputStream(entry)) { return stream.readAllBytes(); }
    }

    private BufferedImage image(String id) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(resource(id)));
        if (image == null) throw new IllegalArgumentException("Invalid packed image " + id);
        return image;
    }

    private static boolean active(int pixel) {
        return (pixel & 0xffffff) != 0 || pixel >>> 24 == 1 || pixel >>> 24 == 2;
    }

    private static boolean eye(int pixel) { return pixel >>> 24 == 1 || pixel >>> 24 == 2; }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private record Mesh(boolean[] used, boolean[] eyes, boolean[] originalAuditFootprint) { }

    private Mesh mesh(String model, int width, int height) throws Exception {
        JsonObject geo = JsonParser.parseString(new String(resource(model), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geo.getAsJsonObject("description");
        double scaleX = width / description.get("texture_width").getAsDouble();
        double scaleY = height / description.get("texture_height").getAsDouble();
        boolean[] used = new boolean[width * height], eyes = new boolean[width * height], originalAuditFootprint = new boolean[width * height];
        for (JsonElement element : geo.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("cubes")) continue;
            boolean eyeBone = bone.get("name").getAsString().toLowerCase(java.util.Locale.ROOT).contains("eye");
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                JsonObject cube = c.getAsJsonObject();
                if (!cube.has("uv")) continue;
                List<double[]> faces = new ArrayList<>();
                if (cube.get("uv").isJsonArray()) {
                    JsonArray uv = cube.getAsJsonArray("uv"), size = cube.getAsJsonArray("size");
                    double u = uv.get(0).getAsDouble(), v = uv.get(1).getAsDouble();
                    // GeckoLib's box UV factory floors cube dimensions, including fractional decorative cubes.
                    double x = Math.floor(size.get(0).getAsDouble()), y = Math.floor(size.get(1).getAsDouble()), z = Math.floor(size.get(2).getAsDouble());
                    double gx = size.get(0).getAsDouble(), gy = size.get(1).getAsDouble(), gz = size.get(2).getAsDouble();
                    faces.add(new double[]{u + z, v, x, z, gx, gz});
                    faces.add(new double[]{u + z + x, v, x, z, gx, gz});
                    faces.add(new double[]{u, v + z, z, y, gz, gy});
                    faces.add(new double[]{u + z, v + z, x, y, gx, gy});
                    faces.add(new double[]{u + z + x, v + z, z, y, gz, gy});
                    faces.add(new double[]{u + z + x + z, v + z, x, y, gx, gy});
                } else {
                    for (var face : cube.getAsJsonObject("uv").entrySet()) {
                        JsonObject f = face.getValue().getAsJsonObject();
                        JsonArray uv = f.getAsJsonArray("uv"), size = f.getAsJsonArray("uv_size");
                        JsonArray cubeSize = cube.getAsJsonArray("size");
                        String direction = face.getKey();
                        double gx = cubeSize.get(direction.equals("east") || direction.equals("west") ? 2 : 0).getAsDouble();
                        double gy = cubeSize.get(direction.equals("up") || direction.equals("down") ? 2 : 1).getAsDouble();
                        faces.add(new double[]{uv.get(0).getAsDouble(), uv.get(1).getAsDouble(),
                                size.get(0).getAsDouble(), size.get(1).getAsDouble(), gx, gy});
                    }
                }
                for (double[] face : faces) {
                    double x0 = Math.min(face[0], face[0] + face[2]) * scaleX, x1 = Math.max(face[0], face[0] + face[2]) * scaleX;
                    double y0 = Math.min(face[1], face[1] + face[3]) * scaleY, y1 = Math.max(face[1], face[1] + face[3]) * scaleY;
                    if (x1 - x0 >= 0.001 && y1 - y0 >= 0.001) {
                        for (int py = Math.max(0, (int)Math.floor(y0 + 0.0001)); py < Math.min(height, (int)Math.ceil(y1 - 0.0001)); py++) {
                            for (int px = Math.max(0, (int)Math.floor(x0 + 0.0001)); px < Math.min(width, (int)Math.ceil(x1 - 0.0001)); px++) originalAuditFootprint[py * width + px] = true;
                        }
                    }
                    if (Math.abs(face[4] * face[5]) < 1.0e-12) continue;
                    boolean pointX = x1 - x0 < 0.001, pointY = y1 - y0 < 0.001;
                    int startX = (int)Math.floor(x0 + (pointX ? 0 : 0.0001)), startY = (int)Math.floor(y0 + (pointY ? 0 : 0.0001));
                    int endX = pointX ? startX + 1 : (int)Math.ceil(x1 - 0.0001), endY = pointY ? startY + 1 : (int)Math.ceil(y1 - 0.0001);
                    for (int py = startY; py < endY; py++) {
                        for (int px = startX; px < endX; px++) {
                            int index = Math.floorMod(py, height) * width + Math.floorMod(px, width);
                            used[index] = true;
                            if (eyeBone) eyes[index] = true;
                        }
                    }
                }
            }
        }
        return new Mesh(used, eyes, originalAuditFootprint);
    }

    private static int nearestEye(int index, int width, int[] mask, int[] reference) {
        int best = Integer.MAX_VALUE, result = 0;
        for (int i = 0; i < mask.length; i++) {
            int candidate = eye(mask[i]) ? mask[i] : reference != null && eye(reference[i]) ? reference[i] : 0;
            if (candidate == 0) continue;
            int dx = i % width - index % width, dy = i / width - index / width;
            int distance = dx * dx + dy * dy;
            if (distance < best) { best = distance; result = candidate & 0xff000000; }
        }
        return result;
    }

    private void writeMask(String id, int width, int height, int[] values) throws Exception {
        if (!id.startsWith("my_addon:") || !id.endsWith("_colormask.png")) throw new AssertionError("Only own packed masks may change: " + id);
        BufferedImage packed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        packed.setRGB(0, 0, width, height, values, 0, width);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(packed, "png", bytes)) throw new AssertionError("PNG encoder unavailable");
        if (!Arrays.equals(values, pixels(ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()))))) {
            throw new AssertionError("PNG round trip changed packed channel/eye alpha data: " + id);
        }
        edits.put(resources.resolve("assets/" + id.replace(':', '/')), bytes.toByteArray());
    }

    private static String setProperty(String text, String textureKey, String key, String value) {
        Pattern existing = Pattern.compile("(\\\"" + key + "\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");
        Matcher match = existing.matcher(text);
        if (match.find()) return match.replaceFirst(Matcher.quoteReplacement(match.group(1) + value + match.group(2)));
        Pattern anchor = Pattern.compile("(?m)^([ \\t]*)(\\\"" + textureKey + "\\\"\\s*:\\s*\\\"[^\\\"]*\\\")(,?)");
        match = anchor.matcher(text);
        if (!match.find()) throw new AssertionError("Missing JSON texture anchor " + textureKey);
        String replacement = match.group(1) + match.group(2) + ",\n" + match.group(1) + "\"" + key + "\": \"" + value + "\"" + match.group(3);
        return match.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private void repair(Path config) throws Exception {
        String originalConfig = Files.readString(config), updated = originalConfig;
        JsonObject json = JsonParser.parseString(originalConfig).getAsJsonObject();
        String name = config.getFileName().toString();
        if (name.contains("form_axolotl_aling") || name.contains("form_familiar_fox_red")) return;
        String model = json.get("model").getAsString();
        BufferedImage base = image(json.get("texture").getAsString());
        int width = base.getWidth(), height = base.getHeight();
        Mesh mesh = mesh(model, width, height);
        int meshVisible = 0;
        int[] appearance = pixels(base);
        for (int i = 0; i < appearance.length; i++) if (mesh.originalAuditFootprint[i] && appearance[i] >>> 24 != 0) meshVisible++;
        Integer expected = expectedMeshPixels.get(json.get("texture").getAsString());
        if (expected == null || expected != meshVisible) throw new AssertionError("UV decoder differs from real baked model: " + name + " " + meshVisible + " expected " + expected);
        int[] bodyReference = null;
        if (name.contains("form_spider_salticidae")) bodyReference = pixels(image(
                "my_addon:textures/form_spider_moon_weaver/form_spider_moon_weaver_colormask.png"));
        int[] completedBody = null;
        for (String layer : new String[]{"texture", "overlay", "fullbright_texture", "emissive_overlay"}) {
            if (!json.has(layer)) continue;
            String textureId = json.get(layer).getAsString(), maskKey = layer + "_mask";
            BufferedImage texture = image(textureId);
            if (texture.getWidth() != width || texture.getHeight() != height) throw new AssertionError("Layer size differs: " + textureId);
            int[] original = json.has(maskKey) ? pixels(image(json.get(maskKey).getAsString())) : new int[width * height];
            int[] result = original.clone(), colors = pixels(texture);
            boolean geo = layer.equals("texture") || layer.equals("fullbright_texture");
            int fills = 0, eyeFills = 0;
            for (int i = 0; i < colors.length; i++) {
                if (colors[i] >>> 24 == 0 || geo && !mesh.used[i] || active(original[i])) continue;
                int marker = 0;
                if (geo && mesh.eyes[i]) marker = nearestEye(i, width, original,
                        layer.equals("texture") ? bodyReference : completedBody);
                if (marker == 0 && !layer.equals("texture") && geo && completedBody != null) marker = completedBody[i];
                if (marker == 0 && layer.equals("texture") && bodyReference != null && active(bodyReference[i])) marker = bodyReference[i];
                if (!active(marker)) marker = ACCENT2;
                result[i] = marker;
                if (eye(marker)) eyeFills++;
                fills++;
            }
            if (layer.equals("texture")) completedBody = result;
            for (int i = 0; i < original.length; i++) {
                if (active(original[i]) && original[i] != result[i]) throw new AssertionError("An existing channel changed: " + textureId);
            }
            if (fills == 0) continue;
            String maskId;
            if (json.has(maskKey) && json.get(maskKey).getAsString().startsWith("my_addon:")) maskId = json.get(maskKey).getAsString();
            else {
                String localTexture = json.get("texture").getAsString();
                String dir = localTexture.substring(0, localTexture.lastIndexOf('/') + 1);
                String baseName = localTexture.substring(localTexture.lastIndexOf('/') + 1, localTexture.length() - 4);
                maskId = dir + baseName + (layer.equals("texture") ? "" : "_" + layer) + "_colormask.png";
            }
            writeMask(maskId, width, height, result);
            updated = setProperty(updated, layer, maskKey, maskId);
            changes.add(Map.of("form_model", name, "layer", layer, "mask", maskId, "filled_pixels", fills, "eye_pixels", eyeFills));
            Path legacy = resources.resolve("assets/orif-defaults/furs/" + name.replace("origins.origin.", ""));
            if (Files.isRegularFile(legacy)) {
                String old = edits.containsKey(legacy) ? new String(edits.get(legacy), StandardCharsets.UTF_8) : Files.readString(legacy);
                JsonObject oldJson = JsonParser.parseString(old).getAsJsonObject();
                if (oldJson.has(layer)) {
                    String text = setProperty(old, layer, maskKey, maskId);
                    if (!text.equals(old)) edits.put(legacy, text.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        if (!updated.equals(originalConfig)) {
            JsonParser.parseString(updated);
            edits.put(config, updated.getBytes(StandardCharsets.UTF_8));
        }
    }

    private Map<String, String> appearanceHashes() throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        try (var paths = Files.walk(resources.resolve("assets/my_addon/textures"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".png"))
                    .filter(p -> !p.toString().contains("colormask")).sorted().toList()) {
                hashes.put(root.relativize(path).toString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
            }
        }
        return hashes;
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path jar = Path.of(args[1]).toAbsolutePath().normalize();
        boolean apply = args.length > 2 && args[2].equals("--apply");
        FormColorMaskRepairTool tool = new FormColorMaskRepairTool(root, jar);
        try {
            var appearanceBefore = tool.appearanceHashes();
            try (var paths = Files.list(tool.resources.resolve("assets/my_addon/ssc_form_model"))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) tool.repair(path);
            }
            for (var edit : tool.edits.entrySet()) {
                if (!edit.getKey().normalize().startsWith(tool.resources)) throw new AssertionError("Edit escaped resources");
                Path preview = tool.output.resolve("generated").resolve(tool.resources.relativize(edit.getKey()));
                Files.createDirectories(preview.getParent()); Files.write(preview, edit.getValue());
            }
            if (apply) for (var edit : tool.edits.entrySet()) {
                Files.createDirectories(edit.getKey().getParent()); Files.write(edit.getKey(), edit.getValue());
            }
            if (!appearanceBefore.equals(tool.appearanceHashes())) throw new AssertionError("An appearance texture changed");
            var manifest = new LinkedHashMap<String, Object>();
            manifest.put("applied", apply); manifest.put("edits", tool.edits.keySet().stream().map(root::relativize).map(Path::toString).toList());
            manifest.put("changes", tool.changes); manifest.put("appearance_sha256", appearanceBefore);
            Files.writeString(tool.output.resolve(apply ? "applied.json" : "plan.json"), new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
            System.out.println("Mask repair " + (apply ? "APPLIED" : "DRY RUN") + ": " + tool.changes.size() + " layers; " + tool.edits.size() + " files");
        } finally { tool.ssc.close(); }
    }
}
