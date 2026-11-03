package net.jackcooper.shapeShifterCurseAddon.network.visual.recipes;

import java.util.function.DoubleSupplier;
import net.jackcooper.shapeShifterCurseAddon.network.visual.SceneEmitter;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

/** SSCA's original mist-form batches, shared by scene clients and legacy observers. */
public final class MistFormRecipes {
    public static final DustParticleEffect BLOOD_DUST = new DustParticleEffect(new Vector3f(.78f,.06f,.06f),1.2f);
    private MistFormRecipes() {}

    /** Relative to the entity's feet; the current authoritative height includes Pehkui scaling. */
    public static void ambient(double height,int flags,SceneEmitter sink) {
        boolean decoration=(flags&1)!=0, projectile=(flags&2)!=0;
        double center=height*.5;
        sink.emit(ParticleTypes.CLOUD,0,center,0,6,.4,.6,.4,.01,decoration,projectile);
        sink.emit(ParticleTypes.CAMPFIRE_COSY_SMOKE,0,center-height*.05,0,3,.35,.5,.35,.01,decoration,projectile);
        sink.emit(BLOOD_DUST,0,center,0,2,.4,.6,.4,0,decoration,projectile);
    }

    /** Original even-tick ten-point convergence; count zero retains directed particle velocity. */
    public static void charge(long elapsed,int duration,double height,int flags,DoubleSupplier random,SceneEmitter sink) {
        if(elapsed<0 || elapsed%2!=0)return;
        boolean decoration=(flags&1)!=0, projectile=(flags&2)!=0;
        double progress=Math.min(1.0,elapsed/(double)duration),radius=2.6*(1-progress)+.4;
        for(int i=0;i<10;i++) {
            double angle=2*Math.PI*i/10+elapsed*.35;
            double x=Math.cos(angle)*radius,z=Math.sin(angle)*radius,y=(random.getAsDouble()-.5)*height;
            sink.emit(BLOOD_DUST,x,height*.5+y,z,0,-x,-y,-z,.3,decoration,projectile);
        }
    }
}
