package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.render.tech.EntityOverlayRenderSystem;

/** SSC cocoon overlay pipeline with an addon-owned vanilla ice PNG and cube_all geometry. */
public final class FrozenShellRenderer extends EntityOverlayRenderSystem.OverlayData {
    // Vanilla ice already has 190/255 texture alpha; avoid washing its markings out twice.
    public static final float OPACITY = 0.80f;
    public static final Identifier ICE_TEXTURE = new Identifier("ssc_addon","textures/entity/frozen_ice.png");
    private static final Direction[] NORMALS = Direction.values();
    // Same six faces as vanilla cube_all, independent of block-model/resource-pack replacements.
    private static final float[][][] FACES = {
            {{0,0,1},{0,0,0},{1,0,0},{1,0,1}}, // down
            {{0,1,0},{0,1,1},{1,1,1},{1,1,0}}, // up
            {{1,1,0},{1,0,0},{0,0,0},{0,1,0}}, // north
            {{0,1,1},{0,0,1},{1,0,1},{1,1,1}}, // south
            {{0,1,0},{0,0,0},{0,0,1},{0,1,1}}, // west
            {{1,1,1},{1,0,1},{1,0,0},{1,1,0}}  // east
    };
    private static final float[][] UV = {{0,0},{0,1},{1,1},{1,0}};

    public static void init() {
        if (EntityOverlayRenderSystem.overlayDataList.stream().noneMatch(FrozenShellRenderer.class::isInstance))
            EntityOverlayRenderSystem.overlayDataList.add(new FrozenShellRenderer());
    }

    @Override public boolean canRender(Entity entity, float yaw, float tickDelta, MatrixStack matrices,
                                       VertexConsumerProvider consumers, int light) {
        return entity instanceof LivingEntity living && FrozenControl.blocked(living);
    }

    @Override public void render(Entity entity, float yaw, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.translate(0, -0.04, 0);
        matrices.scale(entity.getWidth() + 0.2f, entity.getHeight() + 0.14f, entity.getWidth() + 0.2f);
        // The vanilla block occupies [0,1]; center it on the target's feet.
        matrices.translate(-0.5, 0, -0.5);
        try {
            renderModel(matrices,consumers.getBuffer(renderLayer()),light);
        } finally { matrices.pop(); }
    }
    public static RenderLayer renderLayer() { return RenderLayer.getEntityTranslucent(ICE_TEXTURE); }
    public static void renderModel(MatrixStack matrices,VertexConsumer buffer,int light) {
        var alpha = new AlphaConsumer(buffer);
        var entry = matrices.peek();
        for (int face = 0; face < FACES.length; face++) {
            var normal = NORMALS[face];
            for (int corner = 0; corner < 4; corner++) {
                var position = FACES[face][corner];
                var uv = UV[corner];
                alpha.vertex(entry.getPositionMatrix(),position[0],position[1],position[2])
                        .color(255,255,255,255).texture(uv[0],uv[1])
                        .overlay(OverlayTexture.DEFAULT_UV).light(light)
                        .normal(entry.getNormalMatrix(),normal.getOffsetX(),normal.getOffsetY(),normal.getOffsetZ()).next();
            }
        }
    }

    /** Preserve the embedded PNG colors, applying uniform translucency to the shell. */
    public record AlphaConsumer(VertexConsumer delegate) implements VertexConsumer {
        public VertexConsumer vertex(double x,double y,double z){delegate.vertex(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){delegate.color(r,g,b,Math.round(a*OPACITY));return this;}
        public VertexConsumer texture(float u,float v){delegate.texture(u,v);return this;}
        public VertexConsumer overlay(int u,int v){delegate.overlay(u,v);return this;}
        public VertexConsumer light(int u,int v){delegate.light(u,v);return this;}
        public VertexConsumer normal(float x,float y,float z){delegate.normal(x,y,z);return this;}
        public void next(){delegate.next();}
        public void fixedColor(int r,int g,int b,int a){delegate.fixedColor(r,g,b,Math.round(a*OPACITY));}
        public void unfixColor(){delegate.unfixColor();}
    }
}
