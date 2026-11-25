package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.List;

/** Bake the static cube transforms once. Each wave tip then animates its own copy of the ridge. */
final class SnowFoxEffectModel {
    private record Vertex(Vector3f pos, float u, float v, int red, int green) {}
    private record Face(Vector3f normal, Vertex[] vertices) {}
    record Section(List<Face> faces, float height) {
        void draw(MatrixStack matrices, VertexConsumer buffer, int light) {
            var entry = matrices.peek();
            for (Face face : faces) {
                var normal = face.normal;
                for (Vertex vertex : face.vertices) {
                    var pos = vertex.pos;
                    buffer.vertex(entry.getPositionMatrix(), pos.x, pos.y, pos.z)
                            .color(vertex.red, vertex.green, 255, 255).texture(vertex.u, vertex.v)
                            .overlay(OverlayTexture.DEFAULT_UV).light(light)
                            .normal(entry.getNormalMatrix(), normal.x, normal.y, normal.z).next();
                }
            }
        }
    }

    private final Identifier resource;
    private final boolean ice;
    private BakedGeoModel baked;
    private List<Section> sections = List.of();

    SnowFoxEffectModel(String name, boolean ice) {
        resource = new Identifier("my_addon", "geo/effect/snow_fox/" + name + ".geo.json");
        this.ice = ice;
    }

    List<Section> sections() {
        var current = GeckoLibCache.getBakedModels().get(resource);
        if (current != baked) {
            List<Section> updated = new ArrayList<>();
            if (current != null) {
                for (GeoBone root : current.topLevelBones()) {
                    List<Face> faces = new ArrayList<>();
                    float height = bakeBone(root, new MatrixStack(), faces);
                    updated.add(new Section(List.copyOf(faces), height));
                }
            }
            sections = List.copyOf(updated);
            baked = current;
        }
        return sections;
    }

    private float bakeBone(GeoBone bone, MatrixStack matrices, List<Face> faces) {
        matrices.push();
        RenderUtils.prepMatrixForBone(matrices, bone);
        float height = 0;
        for (var cube : bone.getCubes()) {
            matrices.push();
            RenderUtils.translateToPivotPoint(matrices, cube);
            RenderUtils.rotateMatrixAroundCube(matrices, cube);
            RenderUtils.translateAwayFromPivotPoint(matrices, cube);
            var entry = matrices.peek();
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                var vertices = new Vertex[quad.vertices().length];
                for (int i = 0; i < vertices.length; i++) {
                    var source = quad.vertices()[i];
                    var pos = entry.getPositionMatrix().transformPosition(new Vector3f(source.position()));
                    height = Math.max(height, pos.y);
                    float tint = Math.max(0, Math.min(1, pos.y));
                    vertices[i] = new Vertex(pos, source.texU(), source.texV(),
                            ice ? (int)(158 + 97*tint) : 225, ice ? (int)(209 + 46*tint) : 245);
                }
                faces.add(new Face(entry.getNormalMatrix().transform(new Vector3f(quad.normal())).normalize(), vertices));
            }
            matrices.pop();
        }
        for (var child : bone.getChildBones()) height = Math.max(height, bakeBone(child, matrices, faces));
        matrices.pop();
        return height;
    }
}
