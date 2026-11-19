package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisSounds;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisWallProfile;

import net.jackcooper.shapeShifterCurseAddon.client.model.OrcafisModel;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisTidePulse;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisLandAttacks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.Heightmap;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;

public final class OrcafisRenderer extends GeoEntityRenderer<OrcafisEntity> {
    private static final Identifier WAKE = texture("ground_wake");
    private static final Identifier RING = texture("impact_ring");
    static final Identifier WALL = texture("tidal_wall");

    private static Identifier texture(String file) {
        return new Identifier("my_addon", "textures/entity/orcafis/" + file + ".png");
    }

    public OrcafisRenderer(EntityRendererFactory.Context context) {
        super(context, new OrcafisModel());
        shadowRadius = 0;
    }

    @Override
    public void render(OrcafisEntity entity, float yaw, float partial, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        if (entity.motion() != OrcafisEntity.Motion.BURROW) super.render(entity, yaw, partial, matrices, buffers, light);
        float tick = entity.motionTick(partial);
        float v = ((int)(tick / 2) % 12) / 12f;
        matrices.push();
        OrcafisAttackWaves.render(entity, matrices, buffers, tick, partial, v, light);
        if (entity.hasBurrowMound()) OrcafisBurrowRenderer.render(entity,matrices,buffers,WAKE,tick,partial,v,light);
        float facing = -MathHelper.lerpAngleDegrees(partial, entity.prevBodyYaw, entity.bodyYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing));
        switch (entity.motion()) {
            case PLOW -> {
                if (tick < 34) ground(entity, matrices, buffers, WAKE, 0, 0, .78f,
                        1.4f, facing, v, light,
                        Math.min(255, (int)((60 - tick) * 24)));
            }
            case BREACH -> {
                if (tick < 24) ground(entity, matrices, buffers, WAKE, 0, 0, 1.7f, 2.9f, facing, v, light, 225);
            }
            case WAVE -> {
                matrices.translate(0, OrcafisWallProfile.OFFSET_Y, 0);
                flat(matrices, buffers, RING, 2f, 2.7f, v, light, 170);
                if (tick >= OrcafisSounds.WALL_RELEASE) wall(matrices, buffers, tick - OrcafisSounds.WALL_RELEASE, v, light);
            }
            default -> { }
        }
        matrices.pop();
    }

    @Override
    public void renderCubesOfBone(MatrixStack matrices, GeoBone bone, VertexConsumer buffer, int light,
                                  int overlay, float red, float green, float blue, float alpha) {
        OrcafisEntity entity = getAnimatable();
        if (entity != null && entity.motion() == OrcafisEntity.Motion.PLOW && entity.motionTick(0) < 62
                && !bone.getName().equals("dorsal_fin") && !bone.getName().equals("dorsal_fin2")) return;
        super.renderCubesOfBone(matrices, bone, buffer, light, overlay, red, green, blue, alpha);
    }

    private static void flat(MatrixStack m, VertexConsumerProvider provider, Identifier image,
                             float x, float z, float v, int light, int alpha) {
        VertexConsumer buf = provider.getBuffer(RenderLayer.getEntityTranslucent(image));
        vertex(buf,m,-x,0,z,0,v,light,alpha);
        vertex(buf,m,x,0,z,1,v,light,alpha);
        vertex(buf,m,x,0,-z,1,v+1f/12,light,alpha);
        vertex(buf,m,-x,0,-z,0,v+1f/12,light,alpha);
    }

    private static void ground(OrcafisEntity entity, MatrixStack m, VertexConsumerProvider provider,
                               Identifier texture, float cx, float cz, float rx, float rz,
                               float facing, float v, int light, int alpha) {
        VertexConsumer buf = provider.getBuffer(RenderLayer.getEntityTranslucent(texture));
        float rads = facing * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(rads), sin = MathHelper.sin(rads);
        int nx = MathHelper.ceil(rx * 2), nz = MathHelper.ceil(rz * 2);
        for (int j = 0; j < nz; j++) {
            float v0 = (float)j / nz, v1 = (j + 1f) / nz;
            float z0 = cz + rz * (1 - 2*v0), z1 = cz + rz * (1 - 2*v1);
            for (int i = 0; i < nx; i++) {
                float u0 = (float)i / nx, u1 = (i + 1f) / nx;
                float x0 = cx + rx*(2*u0-1), x1 = cx + rx*(2*u1-1);
                float x = (x0+x1)*.5f, z = (z0+z1)*.5f;
                int wx = MathHelper.floor(entity.getX()+cos*x+sin*z);
                int wz = MathHelper.floor(entity.getZ()-sin*x+cos*z);
                double offset = entity.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, wx, wz) - entity.getY();
                if (Math.abs(offset) > 3) continue;
                float y = (float)offset+.035f;
                vertex(buf,m,x0,y,z0,u0,v+v0/12,light,alpha);
                vertex(buf,m,x1,y,z0,u1,v+v0/12,light,alpha);
                vertex(buf,m,x1,y,z1,u1,v+v1/12,light,alpha);
                vertex(buf,m,x0,y,z1,u0,v+v1/12,light,alpha);
            }
        }
    }

    private static void wall(MatrixStack m, VertexConsumerProvider provider, float age, float v, int light) {
        VertexConsumer buf = provider.getBuffer(RenderLayer.getEntityTranslucent(WALL));
        WaveSlice left = WaveSlice.at(0, age);
        for (int i = 1; i <= 20; i++) {
            WaveSlice right = WaveSlice.at(i / 20f, age);
            for (int band = 0; band < OrcafisWallProfile.bands() - 1; band++) {
                for (int side : SIDES) {
                    left.emit(buf, m, band, side, v, light);
                    right.emit(buf, m, band, side, v, light);
                    right.emit(buf, m, band + 1, side, v, light);
                    left.emit(buf, m, band + 1, side, v, light);
                }
                if (i == 1) left.endCap(buf, m, band, v, light);
                if (i == 20) right.endCap(buf, m, band, v, light);
            }
            for (int band : CAPS) {
                left.emit(buf, m, band, -1, v, light);
                right.emit(buf, m, band, -1, v, light);
                right.emit(buf, m, band, 1, v, light);
                left.emit(buf, m, band, 1, v, light);
            }
            left = right;
        }
    }

    // A long low wake, a steep translucent face and a curled white crest.
    private static final float[] WAVE_OFFSET = {-2.4f, -1.5f, -.9f, -.4f, .12f, .50f, 1.12f};
    private static final float[] WAVE_HEIGHT = {-.38f, .03f, .18f, .68f, 1.08f, .62f, -.58f};
    private static final float[] WAVE_V = {.91f, .74f, .60f, .30f, .04f, .35f, .96f};

    static void waveRing(OrcafisEntity entity, MatrixStack m, VertexConsumerProvider buffers, Vec3d center,
                         float partial, float age, float duration, float range, float height, float frame, int light) {
        waveRing(entity,m,buffers,center,partial,age,duration,range,height,frame,light,false);
    }

    static void waveRing(OrcafisEntity entity, MatrixStack m, VertexConsumerProvider buffers, Vec3d center,
                         float partial, float age, float duration, float range, float height, float frame, int light, boolean ground) {
        if (center == null || age < 0 || age >= duration) return;
        float phase = age * OrcafisTidePulse.PERIOD / duration;
        float radius = ground ? OrcafisLandAttacks.shockRadius(age) : OrcafisTidePulse.radius(phase) * range / OrcafisTidePulse.RANGE;
        float envelope = Math.min(1, .35f + phase*.32f) * Math.min(1, (20 - phase)*.6f);
        Vec3d translation = center.subtract(entity.getLerpedPos(partial));
        m.push();
        m.translate(translation.x,translation.y,translation.z);
        int slices = Math.max(24, MathHelper.ceil(radius*4));
        VertexConsumer buf = buffers.getBuffer(RenderLayer.getEntityTranslucent(WALL));
        BlockPos.Mutable sample = new BlockPos.Mutable();
        for (int i = 0; i < slices; i++) {
            float a = i * MathHelper.TAU / slices, b = (i+1) * MathHelper.TAU / slices;
            float middle = (a+b)*.5f;
            sample.set(center.x+radius*MathHelper.cos(middle), center.y-.5, center.z+radius*MathHelper.sin(middle));
            double offset=0;
            if (ground) {
                offset=OrcafisLandAttacks.groundY(entity.getWorld(),sample.getX(),sample.getZ(),center.y)-center.y;
                if (Math.abs(offset)>3) continue;
                m.push(); m.translate(0,offset+.035,0);
            } else if (!entity.getWorld().getFluidState(sample).isIn(FluidTags.WATER)) continue;
            for (int j = 0; j < WAVE_OFFSET.length-1; j++) {
                waveVertex(buf,m,a,j,radius,phase,envelope,range,height,frame,light);
                waveVertex(buf,m,b,j,radius,phase,envelope,range,height,frame,light);
                waveVertex(buf,m,b,j+1,radius,phase,envelope,range,height,frame,light);
                waveVertex(buf,m,a,j+1,radius,phase,envelope,range,height,frame,light);
            }
            if (ground) m.pop();
        }
        m.pop();
    }

    private static void waveVertex(VertexConsumer buf, MatrixStack m, float angle, int band,
                                   float radius, float phase, float envelope, float range, float height, float frame, int light) {
        float ripple = .12f*MathHelper.sin(9*angle + phase*.24f) + .06f*MathHelper.sin(17*angle - phase*.32f);
        float r = MathHelper.clamp(radius + WAVE_OFFSET[band] * height + ripple*height, 0, range);
        float h = WAVE_HEIGHT[band] * height * envelope * (1 + ripple) * (1 - .22f*radius/range);
        vertex(buf,m,r*MathHelper.cos(angle),h,r*MathHelper.sin(angle),angle*8/MathHelper.TAU,
                frame + WAVE_V[band]/12, light, Math.round(225*envelope));
    }

    private static final int[] SIDES = {-1, 1};
    private static final int[] CAPS = {0, OrcafisWallProfile.bands() - 1};

    private record WaveSlice(float u, float x, float crest, float centerZ) {
        static WaveSlice at(float u, float age) {
            float lateral = (u-.5f)*12;
            return new WaveSlice(u,lateral,OrcafisWallProfile.crest(lateral,age),OrcafisWallProfile.front(lateral,age));
        }

        void emit(VertexConsumer buf, MatrixStack m, int band, int side, float frame, int light) {
            float level = OrcafisWallProfile.level(band);
            float y = MathHelper.lerp(level, OrcafisWallProfile.BASE, crest);
            float z = centerZ + OrcafisWallProfile.edge(band, side);
            float texU = .07f + .86f * u + side * .014f;
            float texV = frame + (.985f - .972f * level) / 12;
            vertex(buf, m, x * OrcafisWallProfile.width(level) / 6, y, z, texU, texV, light, side > 0 ? 248 : 232);
        }

        void endCap(VertexConsumer buf, MatrixStack m, int band, float frame, int light) {
            emit(buf, m, band, -1, frame, light);
            emit(buf, m, band, 1, frame, light);
            emit(buf, m, band + 1, 1, frame, light);
            emit(buf, m, band + 1, -1, frame, light);
        }
    }

    static void vertex(VertexConsumer buf, MatrixStack m, float x, float y, float z, float u, float v, int light, int alpha) {
        var entry=m.peek();
        buf.vertex(entry.getPositionMatrix(),x,y,z).color(255,255,255,alpha).texture(u,v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(),0,1,0).next();
    }
}
