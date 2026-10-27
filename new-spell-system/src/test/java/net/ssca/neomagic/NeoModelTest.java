package net.ssca.neomagic;

import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Behavioral tests of the very same model used by the server, including randomized payment boundaries. */
public final class NeoModelTest {
    private static int checks;
    private static void require(boolean test,String message){checks++;if(!test)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){require(Math.abs(actual-expected)<1e-8,message+": "+actual+" != "+expected);}
    private static Blueprint b(List<Control> controls,int ticks){return new Blueprint("",Core.THERMAL,Carrier.PROJECTILE,controls,Core.FROST,1,ticks,32);}
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
        require(s.checkpoints.get(2).risk().failure()!=s.checkpoints.get(3).risk().failure(),"Continuous risk inside same descriptive zone");
        Risk low=risk(b,90,25,neutral), high=risk(b,90,45,neutral);
        require(high.failure()>low.failure()&&high.offset()>low.offset()&&high.angle()>low.angle(),"Actual spell cost affects all risks");
        require(risk(b,90,25,new Profile(80,.8,.9,1,1,false)).failure()!=low.failure(),"Same excess different form roll modifier");
        require(risk(b,10000,25,neutral).failure()==1,"Unbounded burden can reach 100% failure");
        Session failure=new Session(b,q);double paid=0;
        for(int tick=0;tick<=10;tick++)paid+=failure.advance(tick,10000+paid,neutral,new Random(1));
        require(failure.failed&&!failure.complete,"Failure interrupts");near(failure.paid,0,"0-tick non-instant failure does not precharge");
        Session partial=new Session(b,q);double c=70;
        for(int tick=0;tick<=20;tick++)c+=partial.advance(tick,c,tick<20?safe:new Profile(1,100,1,1,1,false),new Random(1));
        require(partial.failed,"Mid-cast failure");near(partial.paid,12.5,"Retains partial payment only");
        Session instant=new Session(b(List.of(),0),q);near(instant.advance(0,0,safe,new Random(1)),25,"Instant prepays before checking");
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
