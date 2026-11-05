package net.jackcooper.shapeShifterCurseAddon.ability;

import java.util.Random;
import net.minecraft.util.math.Vec3d;

/** Movement scenarios, using the production step; no game or network is replaced by this test. */
public final class SwingMotionTest {
    private static int checks;
    public static void main(String[] args) {
        climbThenHold();
        descendThenHold();
        minimumRopeMovement();
        anglesAndMana();
        randomizedBounds();
        maximumRopeWithMomentum();
        groundReeling();
        System.out.println("SwingMotionTest passed: " + checks + " checks (motion math; live collision checks separate)");
    }
    private static void climbThenHold() {
        double distance = 16, length = 16;
        Vec3d velocity = Vec3d.ZERO;
        for (int tick = 0; tick < 400; tick++) {
            var s = SwingMotion.step(velocity.multiply(.99).add(0, -.075, 0), new Vec3d(0,1,0),
                    distance, length, true, false, true, 0, 0);
            double previous = distance;
            distance -= s.velocity().y;
            length = s.ropeLength(); velocity = s.velocity();
            check(distance >= 1.5 - 1e-9, "Climbing cannot pass the anchor minimum");
            check(distance <= previous + 1e-9, "Holding climb never reverses direction");
            check(s.velocity().y <= .169 + 1e-9, "No second spring acceleration during climb");
            if (previous > 1.669) check(Math.abs(s.velocity().y - .169) < 1e-9, "Steady climb speed");
        }
        check(Math.abs(distance - 1.5) < 1e-9, "Climb reaches and stays at minimum");
        for (int tick = 0; tick < 100; tick++) {
            var s = SwingMotion.step(velocity.multiply(.99).add(0,-.075,0),new Vec3d(0,1,0),distance,length,false,false,true,0,0);
            distance -= s.velocity().y; velocity=s.velocity();length=s.ropeLength();
            check(Math.abs(distance-1.5)<1e-9,"Releasing space at minimum does not bob");
        }
    }
    private static void descendThenHold() {
        double distance=4, length=4; Vec3d velocity=Vec3d.ZERO;
        for(int tick=0;tick<300;tick++) {
            var s=SwingMotion.step(velocity.multiply(.99).add(0,-.075,0),new Vec3d(0,1,0),distance,length,false,true,true,0,0);
            double previous=distance;distance-=s.velocity().y;velocity=s.velocity();length=s.ropeLength();
            check(distance>=previous-1e-9,"Descending never climbs");
            check(distance<=32+1e-9,"Descending stops at maximum rope");
            if(previous<31.85)check(Math.abs(s.velocity().y+.15)<1e-9,"Unobstructed steady descent");
        }
        check(Math.abs(distance-32)<1e-9,"Maximum rope reached");
        var denied=SwingMotion.step(new Vec3d(0,-.3,0),new Vec3d(0,1,0),8,8,false,true,false,0,0);
        check(denied.reelIntent()==0&&Math.abs(denied.velocity().y)<1e-9,"No extension when mana is denied");
    }
    private static void minimumRopeMovement() {
        var s=SwingMotion.step(new Vec3d(.72,0,0),new Vec3d(0,1,0),1.5,1.5,false,false,true,.004,0);
        check(s.velocity().horizontalLength()<=.12+1e-9,"Short rope limits movement around anchor");
        s=SwingMotion.step(new Vec3d(.72,0,0),new Vec3d(0,1,0),1.6,1.6,true,false,true,.004,0);
        check(Math.abs(s.velocity().y-.1)<1e-9,"Final climb step uses only remaining distance");
        check(SwingMotion.tangentialLimit(12)==.72,"Long-rope speed retained");
        s=SwingMotion.step(new Vec3d(-.72,0,0),new Vec3d(1,0,0),1.5,1.5,false,false,true,-.004,0);
        check(s.velocity().horizontalLength()<=.12+1e-9,"Side-mounted minimum rope also limits radial movement");
    }
    private static void anglesAndMana() {
        Vec3d side=new Vec3d(1,0,0);
        var s=SwingMotion.step(Vec3d.ZERO,side,8,8,true,false,true,0,0);
        check(s.velocity().lengthSquared()==0&&s.ropeLength()==8,"No climbing toward a sideways anchor");
        s=SwingMotion.step(Vec3d.ZERO,new Vec3d(0,1,0),8,8,true,true,true,0,0);
        check(Math.abs(s.velocity().y-.169)<1e-9,"Space retains priority over shift");
        s=SwingMotion.step(new Vec3d(.1,-.075,0),new Vec3d(0,1,0),4,8,false,false,true,0,0);
        check(s.velocity().y<0,"Slack rope allows normal falling");
    }
    private static void randomizedBounds() {
        Random random=new Random(71);
        for(int i=0;i<10000;i++) {
            Vec3d dir=new Vec3d(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).normalize();
            var s=SwingMotion.step(new Vec3d(random.nextGaussian(),random.nextGaussian(),random.nextGaussian()),dir,
                    random.nextDouble()*36,1.5+random.nextDouble()*30.5,random.nextBoolean(),random.nextBoolean(),random.nextBoolean(),.004,0);
            check(s.ropeLength()>=1.5&&s.ropeLength()<=32,"Rope bounds");
            check(Double.isFinite(s.velocity().lengthSquared())&&s.velocity().length()<=.720000001,"Finite bounded velocity");
            double radial=s.velocity().dotProduct(dir);
            double tangent=s.velocity().subtract(dir.multiply(radial)).length();
            check(tangent<=SwingMotion.tangentialLimit(s.ropeLength())+1e-9,"Angular movement bounded at every rope length");
        }
    }
    private static void maximumRopeWithMomentum() {
        Vec3d position=new Vec3d(0,-32,0),velocity=new Vec3d(.2,0,0);
        for(int tick=0;tick<1200;tick++) {
            Vec3d dir=position.multiply(-1).normalize();
            var s=SwingMotion.step(velocity.multiply(.99).add(0,-.075,0),dir,position.length(),32,false,true,true,0,0);
            velocity=s.velocity();position=position.add(velocity);
            check(position.length()<32.05,"Holding descend at maximum cannot accumulate outward drift");
        }
    }
    private static void groundReeling() {
        Vec3d up = new Vec3d(0, 1, 0);
        var lifted = SwingMotion.groundStep(new Vec3d(0,-.0784,0),up,8,8,true);
        check(lifted.velocity().y>0,"Space starts upward motion while grounded");
        check(lifted.ropeLength()<8,"Ground reeling actually shortens an initially relaxed rope");
        var idle=SwingMotion.groundStep(new Vec3d(.1,-.0784,0),up,8,4,false);
        check(idle.ropeLength()==8&&idle.velocity().y<0,"No space leaves grounded rope relaxed");
        var shallow=SwingMotion.groundStep(Vec3d.ZERO,new Vec3d(10,1,0).normalize(),Math.sqrt(101),Math.sqrt(101),true);
        check(shallow.velocity().x>0&&shallow.velocity().y==0,"Shallow ground anchor starts horizontal reeling");
        check(shallow.ropeLength()<Math.sqrt(101),"Shallow ground rope shrinks before any prior tension exists");
        var minimum=SwingMotion.groundStep(Vec3d.ZERO,up,1.5,1.5,true);
        check(minimum.velocity().lengthSquared()==0&&minimum.reelIntent()==0,"Ground minimum cannot accelerate through anchor");
        Vec3d v=Vec3d.ZERO;
        for(int tick=0;tick<400;tick++) {
            var s=SwingMotion.groundStep(v,new Vec3d(1,0,0),1.6,1.6,true);v=s.velocity();
            check(v.horizontalLength()<=.128000001,"Blocked ground reeling cannot accumulate speed");
        }
        for(double anchorHeight:new double[]{1,3,8}) {
            Vec3d anchor=new Vec3d(10,anchorHeight,0),position=Vec3d.ZERO;v=Vec3d.ZERO;
            double ropeLength=anchor.length();boolean grounded=true;double highest=0;
            for(int tick=0;tick<220;tick++) {
                Vec3d delta=anchor.subtract(position);double dist=delta.length();
                var s=grounded?SwingMotion.groundStep(v,delta.normalize(),dist,ropeLength,true)
                        :SwingMotion.step(v.multiply(.99).add(0,-.075,0),delta.normalize(),dist,ropeLength,true,false,true,0,0);
                v=s.velocity();ropeLength=s.ropeLength();position=position.add(v);
                if(position.y<=0){position=new Vec3d(position.x,0,position.z);v=new Vec3d(v.x,0,v.z);grounded=true;}
                else grounded=false;
                highest=Math.max(highest,position.y);
                check(v.length()<=.72000001,"Ground-to-air transition remains bounded");
                // Floor collisions may move the body inside a slack rope; the minimum bounds rope length.
                check(ropeLength>=1.5,"Ground-to-air transition preserves minimum rope length");
            }
            check(position.x>5,"Space alone approaches a shallow anchor from ground");
            if(anchorHeight>=3)check(highest>1,"Space alone eventually lifts player without a normal jump");
        }
    }
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
}
