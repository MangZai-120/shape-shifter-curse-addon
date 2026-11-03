package net.jackcooper.shapeShifterCurseAddon.network.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jackcooper.shapeShifterCurseAddon.network.visual.recipes.MistFormRecipes;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

/** Cadence, endpoint fixtures and particle/velocity contracts for the original scaled mist form. */
public final class MistFormRecipeTest {
    private static int checks;
    private record Batch(String particle,double x,double y,double z,int count,double dx,double dy,double dz,
                         double speed,boolean decoration,boolean projectile) {}
    private static SceneEmitter collect(List<Batch> rows) {
        return(p,x,y,z,n,dx,dy,dz,s,d,j)->rows.add(new Batch(p.asString(),x,y,z,n,dx,dy,dz,s,d,j));
    }
    private static void check(boolean valid,String message){checks++;if(!valid)throw new AssertionError(message);}
    private static Batch batch(ParticleEffect p,double x,double y,double z,int n,double dx,double dy,double dz,double speed,int flags) {
        return new Batch(p.asString(),x,y,z,n,dx,dy,dz,speed,(flags&1)!=0,(flags&2)!=0);
    }
    private static List<Batch> originalAmbient(double height,int flags) {
        double center=height*.5;
        return List.of(batch(ParticleTypes.CLOUD,0,center,0,6,.4,.6,.4,.01,flags),
                batch(ParticleTypes.CAMPFIRE_COSY_SMOKE,0,center-height*.05,0,3,.35,.5,.35,.01,flags),
                batch(MistFormRecipes.BLOOD_DUST,0,center,0,2,.4,.6,.4,0,flags));
    }
    public static int run() {
        checks=0;
        for(double height:new double[]{0,.1,1.8,6})for(int flags:new int[]{0,1,3}) {
            List<Batch> rows=new ArrayList<>();MistFormRecipes.ambient(height,flags,collect(rows));
            check(rows.equals(originalAmbient(height,flags)),"Ambient particle types/counts/spread/vertical centers survive scaled bodies");
            check(rows.stream().mapToInt(Batch::count).sum()==11,"Every ambient tick still spawns eleven particles in three batches");
            for(int duration:new int[]{1,2,19,20,37}) {
                int total=0;
                for(int age=0;age<=duration;age++) {
                    long seed=0x31f201L+age;rows=new ArrayList<>();Random random=new Random(seed);
                    MistFormRecipes.charge(age,duration,height,flags,random::nextDouble,collect(rows));
                    check(rows.size()==(age%2==0?10:0),"Charge keeps ten points on even ticks and no points on odd ticks");
                    total+=rows.size();
                    for(Batch b:rows) {
                        check(b.count()==0 && b.speed()==.3,"Count zero still supplies a directed velocity, not a Gaussian cloud");
                        check(b.y()>=0 && b.y()<=height,"Vertical random sample stays inside the scaled body");
                        check(b.dx()==-b.x() && b.dz()==-b.z(),"Horizontal velocity points to the original body center");
                    }
                }
                check(total==(duration/2+1)*10,"Lease interior plus unchanged terminal broadcast has the original wave count");
            }
        }
        // Captured original first-point fixtures: start radius 3, midpoint radius 1.7,
        // terminal radius .4, with the existing .35-radian-per-tick rotation.
        int[] ages={0,10,20};double[][] firstPoints={{3,0},{-1.5919763683943538,-.5963314870723537},{.30156090173732187,.26279463948751565}};
        for(int i=0;i<ages.length;i++) {
            List<Batch> rows=new ArrayList<>();MistFormRecipes.charge(ages[i],20,1.8,0,()->.25,collect(rows));
            Batch first=rows.get(0);
            check(Math.abs(first.x()-firstPoints[i][0])<1e-12 && Math.abs(first.z()-firstPoints[i][1])<1e-12,
                    "Start/midpoint/final ring orientation and radius match the captured original fixtures");
            check(Math.abs(first.y()-.45)<1e-12 && Math.abs(first.dy()-.45)<1e-12,
                    "Vertical origin and inward velocity retain the body's original midpoint");
        }
        check(ParametricVisuals.exactRange(ParametricVisuals.Kind.MIST_FORM_AMBIENT)
                && ParametricVisuals.exactRange(ParametricVisuals.Kind.MIST_FORM_CHARGE),
                "Mist retains ParticleUtils exact spherical range instead of block-position distance");
        check(ParametricVisuals.Kind.MIST_FORM_AMBIENT.range==512 && ParametricVisuals.Kind.MIST_FORM_AMBIENT.force
                && ParametricVisuals.Kind.MIST_FORM_CHARGE.range==512 && ParametricVisuals.Kind.MIST_FORM_CHARGE.force,
                "Mist remains forced and visible at its original 512-block range");
        return checks;
    }
}
