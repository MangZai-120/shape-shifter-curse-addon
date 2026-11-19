package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisLandAttacks;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisTidePulse;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisSplashWave;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/** Traveling crests are anchored to the impact or the animal's actual path. */
final class OrcafisAttackWaves {
    static void render(OrcafisEntity boss, MatrixStack m, VertexConsumerProvider buffers,
                       float tick, float partial, float frame, int light) {
        switch (boss.motion()) {
            case TIDE -> {
                if (!OrcafisTidePulse.active(tick)) return;
                Vec3d center = new Vec3d(boss.getX(), boss.tideWaterline(), boss.getZ());
                float phase = (tick - OrcafisTidePulse.START) % OrcafisTidePulse.PERIOD;
                OrcafisRenderer.waveRing(boss,m,buffers,center,partial,phase,20,24,1,frame,light);
            }
            case POUNCE -> {
                OrcafisRenderer.waveRing(boss,m,buffers,boss.splashPoint(false),partial,tick-24,22,6,.68f,frame,light);
                for (var wave : OrcafisSplashWave.values())
                    OrcafisRenderer.waveRing(boss,m,buffers,boss.splashPoint(true),partial,
                            tick-OrcafisSplashWave.IMPACT-wave.delay,wave.duration,wave.range,wave.height,frame,light);
            }
            case WAVE -> OrcafisRenderer.waveRing(boss,m,buffers,boss.getPos(),partial,tick-40,20,7,.50f,frame,light);
            case BREACH -> OrcafisRenderer.waveRing(boss,m,buffers,boss.getPos(),partial,
                    tick-OrcafisLandAttacks.IMPACT,31,OrcafisLandAttacks.SHOCK_RANGE,.88f,frame,light,true);
            case DASH, RETURN -> dashWake(boss,m,buffers,tick,partial,frame,light);
            case WATER_LUNGE -> dashWake(boss,m,buffers,tick+5,partial,frame,light);
            case SWEEP -> tailWake(boss,m,buffers,tick,partial,frame,light);
            default -> { }
        }
    }

    private static void dashWake(OrcafisEntity boss, MatrixStack m, VertexConsumerProvider buffers,
                                 float tick, float partial, float frame, int light) {
        float age = tick + (boss.motion() == OrcafisEntity.Motion.RETURN ? OrcafisEntity.Motion.DASH.ticks : 0);
        if (age < 13 || age >= 130) return;
        Vec3d current = boss.getLerpedPos(partial);
        VertexConsumer buf = buffers.getBuffer(RenderLayer.getEntityTranslucent(OrcafisRenderer.WALL));
        Vec3d previous = null, oldTangent = null;
        for (int i=0; i<=14 && i<=age-13; i++) {
            Vec3d point = boss.wakePoint(i, partial), before = boss.wakePoint(i+1, partial);
            if (point == null || before == null) break;
            Vec3d delta = point.subtract(before).multiply(1,0,1);
            if (delta.lengthSquared() < .0025) break;
            Vec3d tangent = delta.normalize();
            point = point.subtract(current).add(0,2.25,0);
            if (previous != null) {
                for (int side=-1; side<=1; side+=2) {
                    for (int band=0; band<2; band++) {
                        wakeVertex(buf,m,previous,oldTangent,i-1,side,band,frame,light);
                        wakeVertex(buf,m,point,tangent,i,side,band,frame,light);
                        wakeVertex(buf,m,point,tangent,i,side,band+1,frame,light);
                        wakeVertex(buf,m,previous,oldTangent,i-1,side,band+1,frame,light);
                    }
                }
            }
            previous=point;
            oldTangent=tangent;
        }
    }

    private static void wakeVertex(VertexConsumer buf, MatrixStack m, Vec3d p, Vec3d tangent, int age,
                                  int side, int band, float frame, int light) {
        float u=age/14f, strength=1-u;
        float span=.36f+3.25f*u + (band-1)*(.10f+.58f*u);
        double x=p.x+tangent.x*2.2 + tangent.z*side*span;
        double z=p.z+tangent.z*2.2 - tangent.x*side*span;
        double y=p.y + (band==1 ? .43 : -.05)*strength + .13*Math.sin(9*u);
        OrcafisRenderer.vertex(buf,m,(float)x,(float)y,(float)z,u*1.8f,frame+(band==1?.07f:.87f)/12,
                light,Math.round((band==1?225:135)*strength));
    }

    private static void tailWake(OrcafisEntity boss, MatrixStack m, VertexConsumerProvider buffers,
                                 float tick, float partial, float frame, int light) {
        if (tick <= 18 || tick >= 42) return;
        float yaw=MathHelper.lerpAngleDegrees(partial,boss.prevBodyYaw,boss.bodyYaw)*MathHelper.RADIANS_PER_DEGREE;
        float start=Math.max(18,tick-8), stop=Math.min(tick,34);
        VertexConsumer buf=buffers.getBuffer(RenderLayer.getEntityTranslucent(OrcafisRenderer.WALL));
        for (float t=start;t<stop;t+=.5f) {
            float t2=Math.min(stop,t+.5f);
            for (int band=0;band<2;band++) {
                tailVertex(boss,buf,m,yaw,t,tick,band,frame,light);
                tailVertex(boss,buf,m,yaw,t2,tick,band,frame,light);
                tailVertex(boss,buf,m,yaw,t2,tick,band+1,frame,light);
                tailVertex(boss,buf,m,yaw,t,tick,band+1,frame,light);
            }
        }
    }

    private static void tailVertex(OrcafisEntity boss, VertexConsumer buf, MatrixStack m, float yaw,
                                   float t, float tick, int band, float frame, int light) {
        float key=(t-18)/2, radius=.67f+.18f*band;
        float lx=OrcafisLandAttacks.tailX(t)*radius;
        float lz=OrcafisLandAttacks.tailZ(t)*radius;
        float x=lx*MathHelper.cos(yaw)-lz*MathHelper.sin(yaw), z=lx*MathHelper.sin(yaw)+lz*MathHelper.cos(yaw);
        double y=boss.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING,MathHelper.floor(boss.getX()+x),
                MathHelper.floor(boss.getZ()+z))-boss.getY();
        float fade=MathHelper.clamp(1-(tick-t)/8,0,1);
        OrcafisRenderer.vertex(buf,m,x,(float)y+.06f+(band==1?.52f*fade:0),z,key*.22f,
                frame+(band==1?.05f:.87f)/12,light,Math.round(218*fade));
    }
}
