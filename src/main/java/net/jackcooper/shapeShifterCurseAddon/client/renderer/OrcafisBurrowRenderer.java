package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.util.ArrayList;
import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisBurrowProfile;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** Lifts a grid of terrain-textured quads and lays the directional wake over the same surface. */
final class OrcafisBurrowRenderer {
    private record Point(float x, float z, float rise, float u, float v) {}
    private record Ripple(float base, Point a, Point b, Point c, Point d) {}

    static void render(OrcafisEntity boss, MatrixStack matrices, VertexConsumerProvider buffers,
                       Identifier wake, float age, float partial, float frame, int light) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d origin = boss.getLerpedPos(partial);
        Vec3d forward = Vec3d.fromPolar(0,boss.burrowYaw(partial));
        VertexConsumer terrain = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        List<Ripple> ripples = new ArrayList<>();
        float strength = boss.moundStrength(age);
        int xStart = MathHelper.floor(origin.x - OrcafisBurrowProfile.HALF_LENGTH);
        int zStart = MathHelper.floor(origin.z - OrcafisBurrowProfile.HALF_LENGTH);
        for (int z = zStart; z <= MathHelper.floor(origin.z + OrcafisBurrowProfile.HALF_LENGTH); z++) {
            for (int x = xStart; x <= MathHelper.floor(origin.x + OrcafisBurrowProfile.HALF_LENGTH); x++) {
                var surface = OrcafisBurrowProfile.surface(boss.getWorld(),x+.5,z+.5,origin.y);
                if (surface == null) continue;
                var model = client.getBlockRenderManager().getModel(surface.state());
                List<BakedQuad> top = model.getQuads(surface.state(),Direction.UP,Random.create(17));
                BakedQuad cap = top.isEmpty() ? null : top.get(0);
                Sprite sprite = cap == null ? model.getParticleSprite() : cap.getSprite();
                int color = cap != null && cap.hasColor() ? client.getBlockColors()
                        .getColor(surface.state(),boss.getWorld(),surface.block(),cap.getColorIndex()) : 0xffffff;
                float y = surface.y() - (float)origin.y;
                for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++) {
                    float u = column*.5f, v = row*.5f;
                    Point a = point(x+u,z+v,origin,forward,age,strength);
                    Point b = point(x+u,z+v+.5,origin,forward,age,strength);
                    Point c = point(x+u+.5,z+v+.5,origin,forward,age,strength);
                    Point d = point(x+u+.5,z+v,origin,forward,age,strength);
                    float rise = Math.max(Math.max(a.rise,b.rise),Math.max(c.rise,d.rise));
                    if (rise < .035f) continue;
                    float shade = MathHelper.clamp(.83f + (a.rise+d.rise-b.rise-c.rise)*.44f, .60f, 1f);
                    tile(terrain,matrices,a,y,sprite,u,v,color,shade,light);
                    tile(terrain,matrices,b,y,sprite,u,v+.5f,color,shade,light);
                    tile(terrain,matrices,c,y,sprite,u+.5f,v+.5f,color,shade,light);
                    tile(terrain,matrices,d,y,sprite,u+.5f,v,color,shade,light);
                    ripples.add(new Ripple(y,a,b,c,d));
                }
            }
        }
        // Flush the opaque geometry as one layer, then lay the ripple over the same mesh.
        VertexConsumer ripple = buffers.getBuffer(RenderLayer.getEntityTranslucent(wake));
        for (Ripple tile : ripples) {
            ripple(ripple,matrices,tile.a,tile.base,frame,light);
            ripple(ripple,matrices,tile.b,tile.base,frame,light);
            ripple(ripple,matrices,tile.c,tile.base,frame,light);
            ripple(ripple,matrices,tile.d,tile.base,frame,light);
        }
    }

    private static Point point(double x, double z, Vec3d origin, Vec3d forward, float age, float strength) {
        float dx = (float)(x-origin.x), dz = (float)(z-origin.z);
        float right = (float)(dx*forward.z-dz*forward.x), along = (float)(dx*forward.x+dz*forward.z);
        return new Point(dx,dz,OrcafisBurrowProfile.lift(right,along,age,strength),
                .5f+right/(2*OrcafisBurrowProfile.HALF_WIDTH),.5f-along/(2*OrcafisBurrowProfile.HALF_LENGTH));
    }

    private static void tile(VertexConsumer buf, MatrixStack matrices, Point p, float y, Sprite sprite,
                             float u, float v, int color, float shade, int light) {
        var entry = matrices.peek();
        buf.vertex(entry.getPositionMatrix(),p.x,y+p.rise+.012f,p.z)
                .color(Math.round((color>>16&255)*shade),Math.round((color>>8&255)*shade),Math.round((color&255)*shade),255)
                .texture(MathHelper.lerp(u,sprite.getMinU(),sprite.getMaxU()),MathHelper.lerp(v,sprite.getMinV(),sprite.getMaxV()))
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(),0,1,0).next();
    }

    private static void ripple(VertexConsumer buf, MatrixStack matrices, Point p, float y, float frame, int light) {
        OrcafisRenderer.vertex(buf,matrices,p.x,y+p.rise+.022f,p.z,MathHelper.clamp(p.u,0,1),
                frame+MathHelper.clamp(p.v,0,1)/12,light,MathHelper.clamp((int)(p.rise*148),0,115));
    }
}
