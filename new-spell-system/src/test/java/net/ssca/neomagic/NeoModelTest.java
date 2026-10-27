package net.ssca.neomagic;

import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Behavioral tests of the very same model used by the server, including randomized payment boundaries. */
public final class NeoModelTest {
    private static int checks;
    private static void require(boolean test,String message){checks++;if(!test)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){require(Math.abs(actual-expected)<1e-8,message+": "+actual+" != "+expected);}
    private static Blueprint b(List<Control> controls,int ticks){return new Blueprint("",Core.THERMAL,Carrier.PROJECTILE,controls,Core.FROST,1,ticks,32);}
    private static final class Draw extends Random {
        final double value;final int time;int draws,times;
        Draw(double value,int time){this.value=value;this.time=time;}
        @Override public double nextDouble(){draws++;return value;}
        @Override public int nextInt(int bound){times++;return Math.floorMod(time,bound);}
    }
    public static void main(String[] args) {
        var safe=new Profile(100000,1,1,1,1,false);var neutral=new Profile(80,1,1,1,1,false);
        for(int prep=0;prep<=80;prep++) {
            Blueprint b=b(List.of(),prep);Quote q=quote(b,0,coefficients(b.core()));Session s=new Session(b,q);double current=0;
            for(int tick=0;tick<=prep;tick++) {double previous=s.paid;current+=s.advance(tick,current,safe,new Random(42));require(s.paid>=previous,"Payment monotonic");}
            near(current,q.actual(),"Exact total including odd final tick");require(s.complete&&!s.failed,"Safe completion");
            require(s.checkpoints.size()==prep/10+1,"0 tick and exact ten-tick schedule");
            require(s.checkpoints.stream().allMatch(c->c.risk().failure()==0&&c.risk().offset()==0&&!c.shifted()),"Safe guarantees");
            near(s.advance(prep+1,current,safe,new Random(1)),0,"Completed sessions do not charge or roll again");
        }
        Blueprint b=b(List.of(),40);Quote q=new Quote(25,25,.5,plan(b));Session s=new Session(b,q);double current=70;
        for(int tick=0;tick<=40;tick++)current+=s.advance(tick,current,new Profile(80,.000001,1,1,1,false),new Random(7));
        double[] expected={70,76.25,82.5,88.75,95};
        for(int i=0;i<5;i++)near(s.checkpoints.get(i).burden(),expected[i],"Live burden at checkpoint");
        for(var point:s.checkpoints)near(point.risk().failure(),risk(b,95,25,new Profile(80,.000001,1,1,1,false)).failure(),"One locked probability uses initial C plus actual Q");
        Risk low=risk(b,90,25,neutral), high=risk(b,90,45,neutral);
        require(high.failure()>low.failure()&&high.offset()>low.offset()&&high.angle()>low.angle(),"Actual spell cost affects all risks");
        require(risk(b,90,25,new Profile(80,.8,.9,1,1,false)).failure()!=low.failure(),"Same excess different form roll modifier");
        require(risk(b,10000,25,neutral).failure()==1,"Unbounded burden can reach 100% failure");
        Session failure=new Session(b,q);double paid=0;
        for(int tick=0;tick<=10;tick++)paid+=failure.advance(tick,10000+paid,neutral,new Draw(.5,0));
        require(failure.failed&&!failure.complete,"Failure interrupts");near(failure.paid,0,"0-tick non-instant failure does not precharge");
        Session partial=new Session(b,q);double c=70;var draw=new Draw(.5,17);
        for(int tick=0;tick<=20;tick++)c+=partial.advance(tick,c,tick==0?new Profile(1,100,0,1,1,false):safe,draw);
        require(partial.failed,"Previously drawn failure fires even after safety improves");near(partial.paid,10,"Odd-tick failure retains only the last two-tick payment");
        require(partial.checkpoints.get(partial.checkpoints.size()-1).tick()==17,"Failure may fire off the ten-tick drift grid");
        require(draw.draws==1&&draw.times==1&&partial.failureDraws==1,"Failure and its time are sampled exactly once");
        Session passed=new Session(b,q);var passingDraw=new Draw(.5,0);double passingC=0;
        for(int tick=0;tick<=40;tick++)passingC+=passed.advance(tick,tick==0?0:10000,new Profile(tick==0?1000:1,100,0,1,1,false),passingDraw);
        require(passed.complete&&!passed.failed&&passingDraw.draws==1&&passingDraw.times==0,"Later C spikes do not reroll a successful initial draw");
        Session projected=new Session(b,q);var projectedDraw=new Draw(0,19);
        projected.advance(0,70,neutral,projectedDraw);require(projected.failureProbability>0&&!projected.failed,"Projected unsafe payment can schedule failure while current C is safe");
        Session locked=new Session(b,q,700,200);var finalWindow=new Draw(.5,199);double longC=10000;
        for(int tick=0;tick<=200;tick++)longC+=locked.advance(tick,longC,new Profile(1,100,0,1,1,false),finalWindow,false);
        require(locked.failed&&locked.checkpoints.get(locked.checkpoints.size()-1).tick()==199,"Failure fires strictly before the lock boundary");
        require(finalWindow.draws==1&&finalWindow.times==1,"A long preparation still has one failure draw");
        Session instant=new Session(b(List.of(),0),q);near(instant.advance(0,0,safe,new Random(1)),25,"Instant prepays before checking");
        var instantFail=new Session(b(List.of(),0),q);instantFail.advance(0,10000,neutral,new Draw(.5,19));
        require(instantFail.failed&&instantFail.paid==25&&instantFail.failureDraws==1,"Instant failure pays and fires at zero");
        var rng=new Random(20261027);int[] bins=new int[20];
        for(int run=0;run<4000;run++) {
            var attempt=new Session(b,q,700,200);double value=10000;
            for(int tick=0;tick<=700&&!attempt.failed;tick++)value+=attempt.advance(tick,value,new Profile(1,100,0,1,1,false),rng,false);
            require(attempt.failed,"Guaranteed failure terminates within preparation");
            int when=attempt.checkpoints.get(attempt.checkpoints.size()-1).tick();
            require(when>=0&&when<200&&attempt.failureDraws==1,"Sampled failures stay inside the pre-lock window");bins[when/10]++;
        }
        require(Arrays.stream(bins).allMatch(n->n>140&&n<260),"Failure times cover the entire window without ten-tick quantization");
        for(int duration:new int[]{20,80}) {
            int failures=0;var spell=b(List.of(),duration);var fixed=new Quote(20,20,.5,plan(spell));
            for(int run=0;run<10000;run++) {
                var attempt=new Session(spell,fixed);double value=90;
                for(int tick=0;tick<=duration&&!attempt.failed;tick++)value+=attempt.advance(tick,value,new Profile(100,10.0/3,0,1,1,false),rng);
                if(attempt.failed)failures++;
                require(attempt.failureDraws==1,"Failure draw count is independent of preparation length");
            }
            require(Math.abs(failures/10000.0-.1)<.015,"Whole-cast chance stays near 10 percent for both short and long preparation");
        }
        for(var controls:List.of(List.<Control>of(),List.of(Control.SPLIT),List.of(Control.MERGE))) {
            var spell=b(controls,40);Quote novice=quote(spell,0,coefficients(Core.THERMAL)),master=quote(spell,1,coefficients(Core.THERMAL));
            require(master.actual()/novice.actual()>=.35-1e-8&&master.actual()/novice.actual()<=.5+1e-8,"35–50 percent remaining cost");
            near(9*spell.scale()*master.plan().retention(),9,"Split/merge conserve action budget");
        }
        require(!errors(b(List.of(Control.SPLIT,Control.SPLIT),40)).isEmpty(),"Reject terminal overflow");
        require(!errors(b(List.of(Control.STORE,Control.STORE),40)).isEmpty(),"Reject duplicate store");
        require(errors(b(List.of(Control.SPLIT,Control.MERGE,Control.CONVERT),40)).isEmpty(),"Valid typed composed pipeline");
        require(plan(b(List.of(Control.SPLIT,Control.MERGE),40)).retention()<1,"Redundant split/merge incurs loss");
        require(quote(b(List.of(),80),0,coefficients(Core.THERMAL)).actual()<quote(b,0,coefficients(Core.THERMAL)).actual(),"Longer prep reduces rapid organization burden");
        require(risk(b(List.of(Control.CONSTRAIN),40),100,25,neutral).offset()<risk(b,100,25,neutral).offset(),"Constraint reduces deviation with explicit cost");
        Quote tuned=quote(b,1,coefficients(Core.THERMAL),new Tuning(1.5,.45,120,80));
        near(tuned.actual()/tuned.normal(),.45,"Independent per-spell adaptation floor");
        near(tuned.normal()/quote(b,0,coefficients(Core.THERMAL)).normal(),1.5,"Independent per-spell cost multiplier");
        for(Core core:Core.values())for(Carrier carrier:Carrier.values()) {
            var spell=new Blueprint("",core,carrier,List.of(),Core.FROST,1,40,16);
            require(errors(spell).isEmpty()==supports(core,carrier),"Carrier capability matrix");
        }
        System.out.println("Neomagic: "+checks+" behavioral checks passed.");
    }
}
