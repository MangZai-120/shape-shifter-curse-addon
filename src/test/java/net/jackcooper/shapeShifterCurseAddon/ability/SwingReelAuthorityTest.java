package net.jackcooper.shapeShifterCurseAddon.ability;

import java.util.Random;

public final class SwingReelAuthorityTest {
    private static int checks;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private static void near(double a,double b,String message){check(Math.abs(a-b)<1e-8,message+": "+a+" / "+b);}
    private static SwingReelAuthority.Result apply(double length,double request,int intent,int elapsed,double mana,boolean tether){
        return SwingReelAuthority.apply(length,request,intent,elapsed,1.5,tether?16:32,tether,mana,2,Double.NaN);
    }
    public static void main(String[] args){
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            var r=apply(5,value,-1,100,100,false);near(r.length(),5,"Nonfinite input rejected");near(r.cost(),0,"No debit for rejected input");
        }
        near(apply(5,31,0,100,0,false).length(),5,"Zero intent cannot grow rope");
        near(apply(5,31,-1,100,0,false).length(),5,"Zero mana cannot extend");
        near(apply(5,31,1,100,100,false).length(),5,"Climb cannot extend");
        near(apply(5,1.5,-1,100,100,false).length(),5,"Extend cannot contract");
        near(apply(5,31,-1,0,100,false).length(),5,"Duplicate packets get zero time budget");
        near(apply(5,31,-1,100,100,false).length(),5.6,"Long silence has bounded budget");
        near(apply(5,31,-1,2,.04,false).length(),5.08,"Insufficient mana allows only paid distance");
        near(apply(5,31,-1,2,.04,false).cost(),.04,"Partial extension pays exactly once");
        near(apply(5,16,-1,4,100,true).length(),5,"Tether never extends");
        near(apply(5,1.5,1,2,0,true).length(),4.52,"Tether contract speed preserves approaching factor");
        near(SwingReelAuthority.apply(5,31,0,2,1.5,32,false,0,2,7).length(),7,"Ground slack follows actual server distance");
        var zero=SwingReelAuthority.apply(5,31,-1,2,1.5,32,false,100,0,Double.NaN);
        near(zero.length(),5,"Zero blocks-per-mana cannot create infinite extension");near(zero.cost(),0,"Zero config never makes NaN cost");
        Random random=new Random(126127);
        double length=8,mana=100,totalPaid=0,totalExtended=0;
        for(int tick=0;tick<1000;tick++)for(int flood=0;flood<12;flood++) {
            int intent=random.nextInt(5)-2;
            var r=apply(length,random.nextDouble()*100-25,intent,flood==0?1:0,mana,false);
            check(Double.isFinite(r.length())&&Double.isFinite(r.cost()),"Finite server state under packet fuzz");
            check(r.length()>=1.5&&r.length()<=32&&r.cost()>=0&&r.cost()<=mana+1e-9,"Bounds and resources");
            if(flood>0)near(r.length(),length,"Flood cannot add reel time");
            totalExtended+=Math.max(0,r.length()-length);totalPaid+=r.cost();mana-=r.cost();length=r.length();
        }
        near(totalExtended/2,totalPaid,"Every accepted growth is paid");
        for(int delay:new int[]{0,1,2,4,10,40})for(int intent:new int[]{-1,0,1}) {
            double authoritative=10,predicted=10-intent*delay*.24;
            double reconciled=SwingReelAuthority.reconcile(predicted,authoritative,intent,true,true);
            check(Math.abs(reconciled-authoritative)<=.96+1e-9,"Bounded prediction after delayed correction");
            near(SwingReelAuthority.reconcile(reconciled,authoritative,0,true,true),authoritative,"Release converges at all tested delays");
        }
        System.out.println("Swing reel authority PASS: "+checks+" checks; packet flood, resource and prediction boundaries");
    }
}
