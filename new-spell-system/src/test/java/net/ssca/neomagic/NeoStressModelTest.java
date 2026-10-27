package net.ssca.neomagic;

import java.nio.file.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Seeded adversarial sessions: external relief, threshold crossings, all timings and valid compositions. */
public final class NeoStressModelTest {
    private static long checks,ticks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        final int cases=200000;long start=System.nanoTime();Random random=new Random(20261027L);
        int failed=0,finished=0,reliefs=0;var forms=List.of(new Profile(80,1.15,.85,1,1,false),new Profile(100,1,1,1,1,false),new Profile(200,.8,.9,.8,1.1,false));
        var blueprints=new ArrayList<Blueprint>();
        for(int i=0;i<10000;i++) {
            Core core=Core.values()[random.nextInt(7)];Carrier carrier=Carrier.values()[random.nextInt(5)];
            var controls=new ArrayList<Control>();for(int j=0,n=random.nextInt(7);j<n;j++)controls.add(Control.values()[random.nextInt(6)]);
            var b=new Blueprint("",core,carrier,controls,core==Core.FROST?Core.THERMAL:core==Core.LUNAR?Core.VOID:Core.FROST,.5+random.nextInt(8)*.5,random.nextInt(81),1+random.nextInt(32));
            if(errors(b).isEmpty())blueprints.add(b);
        }
        check(blueprints.size()>100,"Sufficient diverse valid structures");
        for(int i=0;i<cases;i++) {
            Blueprint b=blueprints.get(random.nextInt(blueprints.size()));Profile f=forms.get(i%forms.size());
            Quote q=quote(b,random.nextDouble(),coefficients(b.core()));Session s=new Session(b,q);
            double c=switch(i%5){case 0->0;case 1->f.safety();case 2->f.safety()+.00001;case 3->f.safety()+20;default->f.safety()+60;};
            for(int t=0;t<=b.prep()&&!s.failed&&!s.complete;t++) {
                if(t>0&&random.nextInt(12)==0){c=Math.max(0,c-15);reliefs++;}
                double before=s.paid,delta=s.advance(t,c,f,random);c+=delta;ticks++;
                check(Double.isFinite(c+s.paid+s.yaw+s.pitch+s.survival)&&c>=0,"Finite nonnegative state");
                check(delta>=0&&s.paid>=before&&s.paid<=q.actual()+1e-8,"Partial payment bounded by quote");
                check(s.survival>=0&&s.survival<=1,"Cumulative conditional risk bounded");
            }
            if(s.failed)failed++;else{finished++;check(Math.abs(s.paid-q.actual())<1e-7,"Successful exact settlement");}
            check(s.failed||s.complete,"Every session terminates");
            int oldChecks=s.checkpoints.size();double oldPaid=s.paid;
            s.advance(b.prep()+1,c,f,random);check(oldChecks==s.checkpoints.size()&&oldPaid==s.paid,"No post-terminal charges or draws");
            for(Checkpoint cp:s.checkpoints) {
                check(cp.tick()%10==0,"Fixed 10-tick detection schedule");
                if(cp.burden()<=f.safety())check(cp.risk().failure()==0&&cp.risk().offset()==0&&!cp.failed()&&!cp.shifted(),"Safe region is deterministic");
            }
        }
        double seconds=(System.nanoTime()-start)/1e9;
        String json=String.format(Locale.ROOT,"{\"seed\":20261027,\"sessions\":%d,\"valid_blueprints\":%d,\"simulated_ticks\":%d,\"relief_events\":%d,\"completed\":%d,\"failed\":%d,\"invariant_checks\":%d,\"seconds\":%.3f}%n",cases,blueprints.size(),ticks,reliefs,finished,failed,checks,seconds);
        Files.writeString(Path.of("model-stress.json"),json);System.out.print("Neomagic stress model passed: "+json);
    }
}
