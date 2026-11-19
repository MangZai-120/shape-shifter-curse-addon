package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/** The two crests launched at the locked landing point of a water pounce. */
public enum OrcafisSplashWave {
    MAIN(0,29,10,1.2f), FOLLOW(6,22,6,.46f);

    public static final int IMPACT = 66;
    public final int delay,duration;
    public final float range,height;
    OrcafisSplashWave(int delay,int duration,float range,float height) {
        this.delay=delay; this.duration=duration; this.range=range; this.height=height;
    }

    public static float radius(float phase,float range) {
        return OrcafisTidePulse.radius(phase)*range/OrcafisTidePulse.RANGE;
    }
    public static float envelope(float phase) {
        return Math.min(1,.35f+phase*.32f)*Math.min(1,(20-phase)*.6f);
    }
    public static float ripple(float angle,float phase) {
        return .12f*MathHelper.sin(9*angle+phase*.24f)+.06f*MathHelper.sin(17*angle-phase*.32f);
    }

    boolean touches(int sinceLanding,Box box) {
        int age=sinceLanding-delay;
        if (age<0 || age>=duration) return false;
        float phase=age*(float)OrcafisTidePulse.PERIOD/duration;
        float prior=Math.max(0,age-1)*(float)OrcafisTidePulse.PERIOD/duration;
        float angle=(float)Math.atan2(box.minZ+box.maxZ,box.minX+box.maxX);
        float r=radius(phase,range), noise=ripple(angle,phase);
        float crest=1.08f*height*envelope(phase)*(1+noise)*(1-.22f*r/range);
        if (box.maxY < -.58f*height || box.minY > crest) return false;
        double inner=MathHelper.clamp(radius(prior,range)+(-.4f+ripple(angle,prior))*height,0,range);
        double outer=MathHelper.clamp(r+(.50f+noise)*height,0,range);
        double x=MathHelper.clamp(0d,box.minX,box.maxX), z=MathHelper.clamp(0d,box.minZ,box.maxZ);
        double farX=Math.max(Math.abs(box.minX),Math.abs(box.maxX)), farZ=Math.max(Math.abs(box.minZ),Math.abs(box.maxZ));
        return x*x+z*z <= outer*outer && farX*farX+farZ*farZ >= inner*inner;
    }
}
