package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisSounds;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisWallProfile;

import net.jackcooper.shapeShifterCurseAddon.client.model.OrcafisModel;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity.Motion;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisTidePulse;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisSplashWave;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisLandAttacks;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisRockEntity;
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
    private final OrcafisWaterPoseBlend waterPose = new OrcafisWaterPoseBlend();
    private final org.joml.Matrix4f renderInverse = new org.joml.Matrix4f();
    private Vec3d upperLip, lowerLip;
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
    protected void applyRotations(OrcafisEntity entity, MatrixStack matrices, float age, float bodyYaw,float partial) {
        // The dash clip already turns the model around the course. Use its networked course bearing as the base.
        switch (entity.motion()) {
            case DASH, RETURN, WATER_LUNGE -> bodyYaw = MathHelper.lerpAngleDegrees(partial,entity.prevYaw,entity.getYaw());
            default -> { }
        }
        super.applyRotations(entity,matrices,age,bodyYaw,partial);
    }

    @Override
    public void render(OrcafisEntity entity, float yaw, float partial, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        Motion motion = entity.motion();
        boolean waterAttack = motion == Motion.UNDERTOW || motion == Motion.WATER_JET;
        upperLip = lowerLip = null;
        boolean tossing = motion == Motion.ROCK_TOSS;
        if (waterAttack || tossing) renderInverse.set(matrices.peek().getPositionMatrix()).invert();
        if (motion != Motion.BURROW) super.render(entity, yaw, partial, matrices, buffers, light);
        float tick = entity.motionTick(partial);
        float v = ((int)(tick / 2) % 12) / 12f;
        if (tossing && tick >= OrcafisRockEntity.PICKUP && tick < OrcafisRockEntity.RELEASE && upperLip != null && lowerLip != null) {
            Vec3d center = OrcafisRockEntity.carriedCenter(upperLip.lerp(lowerLip,.5),
                    MathHelper.lerpAngleDegrees(partial,entity.prevBodyYaw,entity.bodyYaw));
            matrices.push();
            matrices.translate(center.x,center.y,center.z);
            OrcafisRockRenderer.draw(matrices,buffers,light,0);
            matrices.pop();
        }
        if (waterAttack && upperLip != null && lowerLip != null) {
            OrcafisWaterFlow.render(entity,upperLip.lerp(lowerLip,.5),tick,partial,matrices,buffers,light);
        }
        OrcafisAttackWaves.render(entity, matrices, buffers, tick, partial, v, light);
        if (entity.hasBurrowMound()) OrcafisBurrowRenderer.render(entity,matrices,buffers,WAKE,tick,partial,v,light);
        motion = entity.motion();
        if (motion != Motion.PLOW && motion != Motion.BREACH && motion != Motion.WAVE) return;
        matrices.push();
        float facing = -MathHelper.lerpAngleDegrees(partial, entity.prevBodyYaw, entity.bodyYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing));
        switch (motion) {
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
    public void renderRecursively(MatrixStack matrices, OrcafisEntity entity, GeoBone bone, RenderLayer layer,
                                  VertexConsumerProvider buffers, VertexConsumer buffer, boolean reRender, float partial,
                                  int light, int overlay, float red, float green, float blue, float alpha) {
        boolean blended = bone.getParent() == null && waterPose.apply(entity, bone, partial);
        try {
            super.renderRecursively(matrices, entity, bone, layer, buffers, buffer, reRender, partial,
                    light, overlay, red, green, blue, alpha);
        } finally {
            if (blended) waterPose.restore();
        }
    }

    @Override
    public void renderCubesOfBone(MatrixStack matrices, GeoBone bone, VertexConsumer buffer, int light,
                                  int overlay, float red, float green, float blue, float alpha) {
        OrcafisEntity entity = getAnimatable();
        Motion motion = entity == null ? null : entity.motion();
        if ((motion == Motion.UNDERTOW || motion == Motion.WATER_JET || motion == Motion.ROCK_TOSS)
                && (bone.getName().equals("Head") || bone.getName().equals("shore_jaw"))) {
            // GeckoLib has already applied this frame's jaw, neck and body transforms to the stack.
            var lip = new org.joml.Vector3f(0,100.79703f/16,3.28202f/16);
            matrices.peek().getPositionMatrix().transformPosition(lip);
            Vec3d offset = new Vec3d(renderInverse.transformPosition(lip));
            if (bone.getName().equals("Head")) upperLip = offset;
            else lowerLip = offset;
        }
        if (motion == Motion.PLOW && entity.motionTick(0) < 62
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
        float remaining = OrcafisWallProfile.remaining(age);
        if (remaining <= 0) return;
        float opacity = Math.min(1, remaining * 3);
        // Fading faces must not occlude the water surface when the world draws it after entities.
        VertexConsumer buf = provider.getBuffer(opacity < 1 ? RenderLayer.getEntityNoOutline(WALL) : RenderLayer.getEntityTranslucent(WALL));
        WaveSlice left = WaveSlice.at(0, age, opacity);
        for (int i = 1; i <= 20; i++) {
            WaveSlice right = WaveSlice.at(i / 20f, age, opacity);
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
        float radius = ground ? OrcafisLandAttacks.shockRadius(age) : OrcafisSplashWave.radius(phase,range);
        float envelope = OrcafisSplashWave.envelope(phase);
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
        float ripple = OrcafisSplashWave.ripple(angle,phase);
        float r = MathHelper.clamp(radius + WAVE_OFFSET[band] * height + ripple*height, 0, range);
        float h = WAVE_HEIGHT[band] * height * envelope * (1 + ripple) * (1 - .22f*radius/range);
        vertex(buf,m,r*MathHelper.cos(angle),h,r*MathHelper.sin(angle),angle*8/MathHelper.TAU,
                frame + WAVE_V[band]/12, light, Math.round(225*envelope));
    }

    private static final int[] SIDES = {-1, 1};
    private static final int[] CAPS = {0, OrcafisWallProfile.bands() - 1};

    private record WaveSlice(float u, float x, float base, float crest, float centerZ, float opacity) {
        static WaveSlice at(float u, float age, float opacity) {
            float lateral = (u-.5f)*12;
            return new WaveSlice(u,lateral,OrcafisWallProfile.bottom(age),OrcafisWallProfile.crest(lateral,age),
                    OrcafisWallProfile.front(lateral,age),opacity);
        }

        void emit(VertexConsumer buf, MatrixStack m, int band, int side, float frame, int light) {
            float level = OrcafisWallProfile.level(band);
            float y = MathHelper.lerp(level, base, crest);
            float z = centerZ + OrcafisWallProfile.edge(band, side);
            float texU = .07f + .86f * u + side * .014f;
            float texV = frame + (.985f - .972f * level) / 12;
            vertex(buf, m, x * OrcafisWallProfile.width(level) / 6, y, z, texU, texV, light,
                    Math.round(opacity * (side > 0 ? 248 : 232)));
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
