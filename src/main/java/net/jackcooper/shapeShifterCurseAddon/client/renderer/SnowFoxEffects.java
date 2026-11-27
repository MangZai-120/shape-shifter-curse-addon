package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.minecraft.block.Blocks;
import net.jackcooper.shapeShifterCurseAddon.client.sound.SnowFoxGatherSound;
import java.util.Collections;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxMotion;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxState;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SnowFoxEffects {
    public record Tip(Vec3d feet, float angle) {}
    private record Spike(Tip tip, long birth, float width, float height, float lean, float riseOffset) {}
    private static final List<Spike> SPIKES=new ArrayList<>();
    private static final WeakHashMap<PlayerEntity,Rotation> ROTATIONS=new WeakHashMap<>();
    private static final Identifier SNOW=new Identifier("minecraft","textures/block/snow.png");
    private static final Identifier ICE=new Identifier("my_addon","textures/effect/snow_fox/ice_spikes.png");
    private static final DustParticleEffect FROST = new DustParticleEffect(new Vector3f(.41f,.87f,.93f), .65f);
    private static final BlockStateParticleEffect SNOW_GRAINS = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.getDefaultState());
    private static final BlockStateParticleEffect ICE_CHIPS = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState());
    private static final Set<PlayerEntity> CHARGING = Collections.newSetFromMap(new WeakHashMap<>());
    private static ClientWorld scene;
    private static final SnowFoxEffectModel SPIKE_MODEL = new SnowFoxEffectModel("ice_spikes", true);
    private static final SnowFoxEffectModel SHELL_MODEL = new SnowFoxEffectModel("snow_shell", false);

    private static final class Rotation {
        Vec3d position;
        Quaternionf old=new Quaternionf(), now=new Quaternionf();
        Rotation(Vec3d position) { this.position=position; }
        void update(Vec3d next,float radius) {
            Vec3d diff=next.subtract(position);
            position=next;
            old.set(now);
            double length=Math.hypot(diff.x,diff.z);
            if (length>.0001 && length<2) now.premul(new Quaternionf().rotationAxis(
                    (float)(length/radius),(float)(diff.z/length),0,(float)(-diff.x/length))).normalize();
        }
    }

    public static void register() {
        ClientTickEvents.END_WORLD_TICK.register(world -> {
            setWorld(world);
            SPIKES.removeIf(s -> world.getTime()-s.birth>=13);
            for (var p:world.getPlayers()) {
                var state=SnowFoxMotion.STATE.get(p);
                if (state.phase() == SnowFoxState.Phase.CHARGE) {
                    if (CHARGING.add(p)) MinecraftClient.getInstance().getSoundManager().play(new SnowFoxGatherSound(p, state.age(0)));
                } else CHARGING.remove(p);
                if (state.phase()==SnowFoxState.Phase.SHELL) {
                    var rotation = ROTATIONS.computeIfAbsent(p, k -> new Rotation(k.getPos()));
                    Vec3d moved = p.getPos().subtract(rotation.position);
                    float r = radius(state.mass());
                    rotation.update(p.getPos(), r);
                    if (p.squaredDistanceTo(MinecraftClient.getInstance().gameRenderer.getCamera().getPos()) < 64 * 64) {
                        if (state.age(0) < 8) swirl(world, p.getPos().add(0, r, 0), state.age(0), 8, r + .15);
                        else if (moved.horizontalLengthSquared() > .0025 && moved.horizontalLengthSquared() < 4
                                && !world.getBlockState(p.getBlockPos().down()).isAir()) spray(world, p.getPos(), moved, r);
                    }
                } else {
                    if (state.phase()!=SnowFoxState.Phase.RECOVERY) ROTATIONS.remove(p);
                    if (state.phase()==SnowFoxState.Phase.CHARGE) swirl(world, p.getPos().add(0,.45,0), state.age(0), 10, .95);
                }
            }
        });
    }

    private static void swirl(ClientWorld world, Vec3d center, float age, int duration, double radius) {
        double reach = radius * (1 - .76 * MathHelper.clamp(age / duration, 0, 1));
        for (int i = 0; i < 3; i++) {
            double angle = age * .67 + i * Math.PI * 2 / 3;
            double dx = reach * Math.cos(angle), dz = reach * Math.sin(angle);
            world.addParticle(i == 0 ? FROST : ParticleTypes.SNOWFLAKE,
                    center.x + dx, center.y + .25*Math.sin(angle + age*.23), center.z + dz, -dx*.16, .018, -dz*.16);
        }
    }

    private static void spray(ClientWorld world, Vec3d feet, Vec3d motion, float radius) {
        double speed = motion.horizontalLength();
        double forwardX = motion.x / speed, forwardZ = motion.z / speed;
        for (int side : new int[]{-1, 1}) {
            double sideOffset = side * radius * (.21 + world.random.nextDouble()*.18);
            double x = feet.x - forwardX*radius*.4 - forwardZ*sideOffset;
            double z = feet.z - forwardZ*radius*.4 + forwardX*sideOffset;
            double vx = -motion.x*.26 - forwardZ*side*.055, vz = -motion.z*.26 + forwardX*side*.055;
            world.addParticle(SNOW_GRAINS,x,feet.y+.09,z,vx,speed*.28+.025,vz);
            if (world.random.nextBoolean()) world.addParticle(ParticleTypes.SNOWFLAKE,x,feet.y+.16,z,vx,.04,vz);
        }
    }

    public static void setWorld(ClientWorld world) {
        if (world!=scene) { scene=world; SPIKES.clear(); ROTATIONS.clear(); CHARGING.clear(); }
    }
    public static void spawn(ClientWorld world,List<Tip> tips) {
        setWorld(world);
        int index = 0;
        for (var tip:tips) {
            // Derive proportions once from the shared placement so observers see the same tooth.
            long seed = Double.doubleToLongBits(tip.feet().x) ^ Long.rotateLeft(Double.doubleToLongBits(tip.feet().z), 23)
                    ^ Float.floatToIntBits(tip.angle());
            var variation = net.minecraft.util.math.random.Random.create(seed);
            SPIKES.add(new Spike(tip, world.getTime(), .78f + .46f * variation.nextFloat(),
                    .68f + .66f * variation.nextFloat(), -8f + 19f * variation.nextFloat(), .8f * variation.nextFloat()));
            if (index++ % 3 != 0) continue;
            Vec3d at = tip.feet();
            double vx = Math.cos(tip.angle())*.065, vz = Math.sin(tip.angle())*.065;
            world.addParticle(ICE_CHIPS,at.x,at.y+.13,at.z,vx,.09+world.random.nextDouble()*.055,vz);
            world.addParticle(FROST,at.x,at.y+.17,at.z,vx*.6,.045,vz*.6);
        }
    }

    private static float radius(float mass) { return .75f+1.75f*MathHelper.clamp((mass-10)/40,0,1); }
    private static float smooth(float t) { t=MathHelper.clamp(t,0,1); return t*t*(3-2*t); }

    public static void render(WorldRenderContext ctx) {
        if (ctx.consumers()==null) return;
        setWorld(ctx.world());
        var cam=ctx.camera().getPos();
        var ms=ctx.matrixStack();
        float delta=ctx.tickDelta();
        if (!SPIKES.isEmpty()) {
            var ice=ctx.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(ICE));
            var sections = SPIKE_MODEL.sections();
            for (var s:SPIKES) {
                Vec3d pos=s.tip.feet();
                if (cam.squaredDistanceTo(pos)>4096) continue;
                float age=scene.getTime()-s.birth+delta+s.riseOffset;
                float exposure = smooth(age / 3.5f) * (1 - smooth((age - 6) / 5));
                if (exposure < .001f) continue;
                ms.push(); ms.translate(pos.x-cam.x,pos.y-cam.y,pos.z-cam.z);
                ms.multiply(RotationAxis.POSITIVE_Y.rotation(-s.tip.angle()));
                ms.scale(s.width, s.height, s.width);
                for (var shard : sections) {
                    ms.push();
                    ms.translate(0, shard.height() * (exposure - 1), 0);
                    ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s.lean));
                    shard.draw(ms,ice,0x00e000e0);
                    ms.pop();
                }
                ms.pop();
            }
        }
        for (var p:scene.getPlayers()) {
            var state=SnowFoxMotion.STATE.get(p);
            if (state.phase()==SnowFoxState.Phase.CHARGE || state.phase()==SnowFoxState.Phase.DIVE) {
                var pos=p.getLerpedPos(delta).subtract(cam);
                ms.push();
                ms.translate(pos.x,pos.y+.28,pos.z);
                var layer=ctx.consumers().getBuffer(RenderLayer.getDebugQuads());
                if (state.phase()==SnowFoxState.Phase.CHARGE) {
                    float progress=MathHelper.clamp(state.age(delta)/10,0,1);
                    ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(68));
                    drawFrostArc(ms,layer,.75f-.32f*progress,progress*6.5f,progress);
                } else {
                    // Two narrow crossed ribbons keep the vertical streak readable from either side.
                    for (int i=0;i<2;i++) {
                        var matrix=ms.peek().getPositionMatrix();
                        layer.vertex(matrix,-.08f,0,0).color(167,235,255,215).next();
                        layer.vertex(matrix,.08f,0,0).color(239,251,255,240).next();
                        layer.vertex(matrix,.012f,1.8f,0).color(180,220,255,15).next();
                        layer.vertex(matrix,-.012f,1.8f,0).color(177,221,255,15).next();
                        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
                    }
                }
                ms.pop();
                continue;
            }
            if (state.phase()!=SnowFoxState.Phase.SHELL && state.phase()!=SnowFoxState.Phase.RECOVERY) continue;
            float age=state.age(delta);
            float r=radius(state.mass());
            float size=state.phase()==SnowFoxState.Phase.SHELL ? .32f+.68f*smooth(age/5) : 1-smooth(age/6);
            if (size<.005f) continue;
            Vec3d pos=p.getLerpedPos(delta);
            ms.push(); ms.translate(pos.x-cam.x,pos.y+r-cam.y,pos.z-cam.z);
            ms.scale(r*size,r*size,r*size);
            var rot=ROTATIONS.get(p);
            if (rot!=null) ms.multiply(new Quaternionf(rot.old).slerp(rot.now,delta));
            var buf=ctx.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(SNOW));
            int light=WorldRenderer.getLightmapCoordinates(scene,BlockPos.ofFloored(pos.add(0,r,0)));
            for (var section : SHELL_MODEL.sections()) section.draw(ms, buf, light);
            ms.pop();
        }
    }

    private static void drawFrostArc(MatrixStack stack,VertexConsumer buffer,float radius,float start,float progress) {
        var matrix=stack.peek().getPositionMatrix();
        for (int i=0;i<24;i++) {
            float a=start+i*.19f,b=a+.19f;
            float width=.012f+.06f*i/24;
            for (int corner=0;corner<4;corner++) {
                float angle=corner<2?a:b;
                float r=corner==0||corner==3?radius:radius-width;
                buffer.vertex(matrix,MathHelper.cos(angle)*r,0,MathHelper.sin(angle)*r)
                        .color(181,234,255,(int)((45+7*i)*(.4f+.6f*progress))).next();
            }
        }
    }

    private SnowFoxEffects() {}
}
