package net.jackcooper.shapeShifterCurseAddon.network.visual;

import java.util.random.RandomGenerator;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

/** Original fox explosion/ring, seed trail and infection cloud particle parameters. */
public final class AbilityParticleRecipes {
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(.85f,.1f,.05f),1.3f);
    private static final DustParticleEffect SEED = new DustParticleEffect(new Vector3f(.35f,.95f,.30f),1f);
    private static final DustParticleEffect POISON = new DustParticleEffect(new Vector3f(.30f,.50f,.10f),1.2f);
    private static final DustColorTransitionParticleEffect ARCANE = new DustColorTransitionParticleEffect(
            new Vector3f(.322f,0,.149f),new Vector3f(.149f,.012f,.039f),1.5f);
    private AbilityParticleRecipes() {}

    public static void emit(ParametricVisuals.View v, int age, long tick, RandomGenerator random, SceneEmitter sink) {
        switch (v.key().kind()) {
            case FOX_EXPLOSION -> explosion(v.parameter(0),random,sink);
            case FOX_RING -> ring((age+1)/(double)Math.max(1,v.duration()),v.parameter(0),sink);
            case PARASITIC_SEED_TRAIL -> sink.emit(SEED,0,.1,0,2,.05,.05,.05,0,false,false);
            case SPORE_CLOUD -> {
                if (Math.floorMod(tick,2)!=0) return;
                double radius=v.parameter(0);
                sink.emit(POISON,0,.5,0,8,radius*.6,.45,radius*.6,0,false,false);
                sink.emit(ParticleTypes.SPORE_BLOSSOM_AIR,0,.3,0,2,radius*.5,.3,radius*.5,0,false,false);
            }
            default -> throw new IllegalArgumentException("Not an ability particle recipe");
        }
    }

    private static void explosion(double radius, RandomGenerator rnd, SceneEmitter sink) {
        for(int i=0;i<130;i++) {
            double[] p=sphere(radius,rnd);
            boolean red=rnd.nextDouble()<.8;
            sink.emit(red?RED:ParticleTypes.SOUL_FIRE_FLAME,p[0],p[1],p[2],1,0,0,0,red?0:.01,true,true);
        }
        for(int i=0;i<16;i++) {
            double[] p=sphere(radius*.6,rnd);
            sink.emit(ParticleTypes.LAVA,p[0],p[1]+1,p[2],1,0,0,0,0,true,true);
        }
        sink.emit(ParticleTypes.EXPLOSION,0,.3,0,3,.3,.3,.3,1,true,true);
        sink.emit(ParticleTypes.LAVA,0,.8,0,20,.45,.45,.45,.2,true,true);
        sink.emit(ARCANE,0,.3,0,300,1.05,1.8,1.05,.01,true,true);
        sink.emit(ParticleTypes.FLAME,0,.3,0,80,.75,1.2,.75,.1,true,true);
        sink.emit(ParticleTypes.SQUID_INK,0,.3,0,5,.45,.45,.45,.1,true,true);
        sink.emit(ParticleTypes.FALLING_LAVA,0,.1,0,125,1.5,.75,1.5,.2,true,true);
    }
    private static double[] sphere(double radius,RandomGenerator rnd) {
        double r=radius*Math.cbrt(rnd.nextDouble()),theta=rnd.nextDouble()*2*Math.PI,phi=Math.acos(2*rnd.nextDouble()-1);
        return new double[]{r*Math.sin(phi)*Math.cos(theta),r*Math.cos(phi),r*Math.sin(phi)*Math.sin(theta)};
    }
    private static void ring(double progress,double chainRadius,SceneEmitter sink) {
        // Preserve the original float progress arithmetic before widening to doubles.
        float p=(float)progress;
        double radius=Math.max(.1,chainRadius*p),rot=p*.6,upward=.02+p*.04;
        int points=Math.max(2,(int)((12+26*p)*.2));
        for(int i=0;i<points;i++) {
            double a=2*Math.PI*i/points+rot,x=radius*Math.cos(a),z=radius*Math.sin(a);
            sink.emit(ParticleTypes.FLAME,x,0,z,1,0,upward,0,0,true,true);
            if(i%2==0)sink.emit(ParticleTypes.SOUL_FIRE_FLAME,x,.05,z,1,0,upward,0,.01,true,true);
        }
    }
}
