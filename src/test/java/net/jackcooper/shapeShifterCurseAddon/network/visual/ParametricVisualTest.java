package net.jackcooper.shapeShifterCurseAddon.network.visual;

import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals.*;

/** Wire boundaries, authoritative identity and deterministic sampling rather than renderer mocks. */
public final class ParametricVisualTest {
    private static int checks;
    private static final UUID EVENT = new UUID(17,31), ANCHOR = new UUID(37,41), OWNER = new UUID(43,47);
    private static void check(boolean ok, String message) { checks++; if(!ok)throw new AssertionError(message); }
    private static View view(Kind kind) {
        double[] p = switch(kind) {
            case CORRUPT_MIST -> new double[]{4.5,220};
            case METEOR_WARNING -> new double[]{5.001,0};
            case DOMAIN_EDGE -> new double[]{.25,.25,24};
            case DOMAIN_AMBIENT -> new double[]{24,2,2,300};
            case BLUE_FIRE_RING -> new double[]{6,1};
            case SSC_WEB_TRAIL -> new double[]{3};
            case MIST_FORM_AMBIENT -> new double[]{.1,0};
            case MIST_FORM_CHARGE -> new double[]{20,.1,0,0};
            case FOX_EXPLOSION -> new double[]{6};
            case FOX_RING, SPORE_CLOUD -> new double[]{2};
            default -> new double[0];
        };
        int duration = switch(kind) {case FOX_EXPLOSION->1;case FOX_RING->7;case CORRUPT_MIST->220;case METEOR_WARNING->10;case DOMAIN_EDGE->96;case DOMAIN_AMBIENT->150;case MIST_FORM_CHARGE->20;default->0;};
        return new View(new Key(EVENT,kind),ANCHOR,301,OWNER,new Vec3d(1.25,64.5,-2.75),
                new Vec3d(.125,-.25,.5),1000,duration,true,.6f,1.6f,6,p);
    }
    private static void wire() {
        check(Kind.CORRUPT_MIST.ordinal()==0 && Kind.LUNAR_BOLT_TRAIL.ordinal()==7,
                "Existing 68 recipe ordinals remain stable");
        for(Kind kind:Kind.values())check(ParametricVisuals.supportsKind(kind,false)==(kind.ordinal()<8),
                "A peer without the mist visual capability never receives a newly appended kind: "+kind);
        for(Kind kind:Kind.values()) {
            check(ParametricVisuals.supportsKind(kind,true)==(kind.ordinal()<10),
                    "Old mist peers do not receive ability kinds: "+kind);
            check(ParametricVisuals.supportsKind(kind,true,true),"Full capability supports all kinds: "+kind);
        }
        for(Kind k:Kind.values()) {
            View a=view(k);var b=new PacketByteBuf(Unpooled.buffer());
            try {
                a.write(b);View v=View.read(b);
                check(!b.isReadable(),"Scene codec consumes exact frame: "+k);
                check(ParametricVisuals.sameState(List.of(a),List.of(v)),"Static metadata round trip: "+k);
                check(a.position().equals(v.position())&&a.velocity().equals(v.velocity()),"Position/velocity codec: "+k);
                check(a.key().equals(v.key())&&a.anchorUuid().equals(v.anchorUuid())&&a.decorationOwner().equals(v.decorationOwner()),"All identities retained: "+k);
                double[] copy=v.parameters();if(copy.length>0){double old=v.parameter(0);copy[0]=999;check(v.parameter(0)==old,"Recipe params are immutable: "+k);}
                View moved=new View(a.key(),ANCHOR,a.entityId(),OWNER,a.position().add(10,0,0),Vec3d.ZERO,a.epoch(),a.duration(),true,a.width(),a.eyeHeight(),a.extent(),a.parameters());
                check(ParametricVisuals.sameState(List.of(a),List.of(moved)),"Movement reuses tiny motion path, not full static snapshot: "+k);
                View newCast=new View(a.key(),ANCHOR,a.entityId(),OWNER,a.position(),a.velocity(),a.epoch()+1,a.duration(),a.follows(),a.width(),a.eyeHeight(),a.extent(),a.parameters());
                check(!ParametricVisuals.sameState(List.of(a),List.of(newCast)),"New cast epoch forces synchronization: "+k);
            } finally {b.release();}
        }
        var circle=new Circle(71,OWNER,ParticleTypes.FLAME,true,true,new Vec3d(4,5,6),2,.125,new Vec3d(.125,.25,.5),6,64);
        var b=new PacketByteBuf(Unpooled.buffer());
        try {circle.write(b);var c=Circle.read(b);check(!b.isReadable(),"Circle exact boundary");check(c.equals(circle),"Parameterized circle round trip including projectile scope");}
        finally{b.release();}
        View bad=new View(new Key(EVENT,Kind.CORRUPT_MIST),ANCHOR,1,null,Vec3d.ZERO,Vec3d.ZERO,0,1,true,1,1,1,new double[]{Double.NaN,220});
        b=new PacketByteBuf(Unpooled.buffer());
        try{bad.write(b);boolean rejected=false;try{View.read(b);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Reject non-finite recipe params");}
        finally{b.release();}
        for (Kind kind : Kind.values()) {
            View valid = view(kind);
            if (valid.parameters().length == 0) continue;
            bad = new View(valid.key(),ANCHOR,1,null,Vec3d.ZERO,Vec3d.ZERO,0,1,true,1,1,1,new double[0]);
            b = new PacketByteBuf(Unpooled.buffer());
            try {
                bad.write(b); boolean rejected = false;
                try { View.read(b); } catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected,"Incomplete recipe is rejected before it reaches the tick sampler: " + kind);
            } finally { b.release(); }
        }
        for (Kind kind : List.of(Kind.CORRUPT_MIST,Kind.METEOR_WARNING)) {
            View valid = view(kind); double[] parameters = valid.parameters(); parameters[0] = 1.0e100;
            bad = new View(valid.key(),ANCHOR,1,null,Vec3d.ZERO,Vec3d.ZERO,0,1,true,1,1,1,parameters);
            b = new PacketByteBuf(Unpooled.buffer());
            try {
                bad.write(b); boolean rejected = false;
                try { View.read(b); } catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected,"Finite oversized recipe cannot cause an unbounded sampling loop: " + kind);
            } finally { b.release(); }
        }
        Circle oversized = new Circle(1,null,ParticleTypes.FLAME,false,false,Vec3d.ZERO,1,0,Vec3d.ZERO,65537,64);
        b = new PacketByteBuf(Unpooled.buffer());
        try {
            oversized.write(b); boolean rejected = false;
            try { Circle.read(b); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected,"Unbounded circle radius rejected");
        } finally { b.release(); }
    }
    private static void endpoints() {
        View mist=view(Kind.CORRUPT_MIST);int waves=0;
        for(int i=0;i<=220;i++){int a=SceneRecipes.latestSampleAge(mist,i);if(a==i)waves++;}
        check(waves==43,"220 tick mist keeps exactly 43 original five-tick waves");
        check(SceneRecipes.latestSampleAge(mist,219)==215,"A late viewer gets the most recent mist wave");
        check(SceneRecipes.latestSampleAge(mist,0)<0,"No non-existent age-zero mist");
        View meteor=view(Kind.METEOR_WARNING);check(!SceneRecipes.active(meteor,0)&&SceneRecipes.active(meteor,10)&&!SceneRecipes.active(meteor,11),"Meteor warning retains exact end tick");
        View edge=view(Kind.DOMAIN_EDGE);check(SceneRecipes.active(edge,95)&&!SceneRecipes.active(edge,96),"Domain edge has no extra endpoint ring");
        View ambient=view(Kind.DOMAIN_AMBIENT);int n=0;for(int i=0;i<150;i++)if(SceneRecipes.latestSampleAge(ambient,i)==i)n++;
        check(n==30,"Existing double-increment domain phase retains 30 ambient waves");
        check(SceneRecipes.latestSampleAge(ambient,150)<0,"Expired ambient scenes cannot replay their last wave while a removal packet is in flight");
        View blue=view(Kind.BLUE_FIRE_RING);check(SceneRecipes.latestSampleAge(blue,1)==0&&SceneRecipes.latestSampleAge(blue,7)==4&&SceneRecipes.latestSampleAge(blue,10001)==10000,"Only latest blue wave is eligible after latency or time jumps");
        View charge=view(Kind.MIST_FORM_CHARGE);
        check(SceneRecipes.latestSampleAge(charge,1)==0 && SceneRecipes.latestSampleAge(charge,3)==2
                && SceneRecipes.latestSampleAge(charge,19)==18 && SceneRecipes.latestSampleAge(charge,20)<0,
                "Charge keeps even ticks and reserves its final frame for the original one-shot broadcast");
        View firstOdd=new View(charge.key(),ANCHOR,charge.entityId(),null,charge.position(),charge.velocity(),
                charge.epoch(),20,true,charge.width(),charge.eyeHeight(),3,new double[]{20,.1,0,2});
        check(SceneRecipes.latestSampleAge(firstOdd,1)<0 && SceneRecipes.latestSampleAge(firstOdd,2)==2,
                "A first observed odd charge tick cannot invent a previous age-zero wave");
    }
    private static long trace(View v,int age,long salt) {
        var random=new Random(ParametricVisuals.seed(v,v.epoch()+age));long[] h={0xcbf29ce484222325L};
        SceneRecipes.emit(v,age,v.epoch()+age,random,(particle,x,y,z,count,dx,dy,dz,speed,decoration,projectile)->{
            // Different observers consume unrelated local Gaussian work, never the recipe stream.
            new Random(salt).nextGaussian();
            h[0]=(h[0]^Double.doubleToLongBits(x))*0x100000001b3L;
            h[0]=(h[0]^Double.doubleToLongBits(y))*0x100000001b3L;
            h[0]=(h[0]^Double.doubleToLongBits(z))*0x100000001b3L;
            h[0]=(h[0]^count^particle.getType().hashCode())*0x100000001b3L;
        });return h[0];
    }
    private static void observers() {
        for(Kind k:Kind.values()) {
            View v=view(k);int end=v.duration()>0?v.duration():64;
            for(int age=0;age<=end;age++){
                long expected=trace(v,age,1);
                for(int viewer=2;viewer<=20;viewer++)check(trace(v,age,viewer)==expected,"Twenty observers use identical relative geometry: "+k+"/"+age);
            }
        }
    }
    private static View projectile(int identity) {
        return new View(new Key(new UUID(101,identity),Kind.FIRE_BOLT_TRAIL),new UUID(103,identity),
                identity,null,new Vec3d(identity,70,0),new Vec3d(.25,0,0),1000+identity,0,true,
                .3f,.15f,0,new double[0]);
    }
    private static void churn() {
        List<View> baseline = new ArrayList<>();
        for (int i=0;i<20;i++) baseline.add(projectile(i));
        List<Map<Key,View>> viewers = new ArrayList<>();
        for (int observer=0;observer<20;observer++) {
            Map<Key,View> mirror = new LinkedHashMap<>();
            for (View v : baseline) mirror.put(v.key(),v);
            viewers.add(mirror);
        }
        long fullBytes=0, deltaBytes=0;
        for (int round=0;round<100;round++) {
            List<View> next = new ArrayList<>(baseline); View removed=next.remove(0), added=projectile(20+round);
            next.add(added);
            Delta delta=ParametricVisuals.diff(baseline,next);
            check(delta.changed().size()==1 && delta.changed().get(0)==added
                    && delta.removed().equals(List.of(removed.key())),"Projectile churn sends only its birth/death: "+round);
            var full=new PacketByteBuf(Unpooled.buffer());var compact=new PacketByteBuf(Unpooled.buffer());
            try {
                full.writeVarInt(next.size());for(View v:next)v.write(full);
                compact.writeInt(round+1);compact.writeInt(round); // sequence/base extra cost
                compact.writeVarInt(delta.changed().size());for(View v:delta.changed())v.write(compact);
                compact.writeVarInt(delta.removed().size());for(Key k:delta.removed())k.write(compact);
                fullBytes+=full.readableBytes();deltaBytes+=compact.readableBytes();
            } finally {full.release();compact.release();}
            for (Map<Key,View> mirror:viewers) {
                for(Key key:delta.removed())mirror.remove(key);
                for(View v:delta.changed())mirror.put(v.key(),v);
                check(ParametricVisuals.sameState(new ArrayList<>(mirror.values()),next),
                        "Twenty observer baselines stay complete through projectile churn: "+round);
            }
            Delta lateJoin=ParametricVisuals.diff(List.of(),next);
            check(lateJoin.changed().size()==20 && lateJoin.removed().isEmpty(),
                    "A late viewer receives the complete current table rather than another viewer's delta: "+round);
            baseline=next;
        }
        check(deltaBytes*5<fullBytes,"Birth/death metadata including delta base costs less than one fifth of full tables");
        List<View> reordered=new ArrayList<>(baseline);Collections.reverse(reordered);
        check(ParametricVisuals.diff(baseline,reordered).isEmpty(),"Reordering alone does not retransmit unchanged metadata");
        View original=baseline.get(0);
        View moved=new View(original.key(),original.anchorUuid(),original.entityId(),null,original.position().add(2,0,0),
                Vec3d.ZERO,original.epoch(),0,true,original.width(),original.eyeHeight(),0,new double[0]);
        check(ParametricVisuals.diff(List.of(original),List.of(moved)).isEmpty(),"Follow movement remains on MOTION rather than DELTA");
        View recast=new View(original.key(),original.anchorUuid(),original.entityId(),null,original.position(),
                original.velocity(),original.epoch()+1,0,true,original.width(),original.eyeHeight(),0,new double[0]);
        check(ParametricVisuals.diff(List.of(original),List.of(recast)).changed().equals(List.of(recast)),
                "A new cast with the same event identity updates the authoritative epoch");
    }
    public static void main(String[] args) {
        SharedConstants.createGameVersion();Bootstrap.initialize();wire();endpoints();observers();churn();
        abilityRecipes();
        checks+=MistFormRecipeTest.run();
        System.out.println("Parametric visual codec/timeline/20-observer sampling PASS: "+checks+" checks");
    }

    private static void abilityRecipes() {
        int[] counts={0,0};
        SceneRecipes.emit(view(Kind.FOX_EXPLOSION),0,1000,new Random(127),
                (particle,x,y,z,count,dx,dy,dz,speed,decoration,projectile)->{
                    counts[0]++;counts[1]+=count;
                    check(decoration&&projectile,"Fox burst keeps projectile first-person scope");
                    if(counts[0]<=130)check(x*x+y*y+z*z<=36+1e-9,"Fox samples stay inside original six-block sphere");
                });
        check(counts[0]==152&&counts[1]==679,"Original fox explosion keeps 152 batches and 679 particles");
        for(int age=0;age<7;age++) {
            int current=age;double radius=Math.max(.1,2*((age+1)/7f));int[] ring={0};
            SceneRecipes.emit(view(Kind.FOX_RING),age,1000+age,new Random(127),
                    (particle,x,y,z,count,dx,dy,dz,speed,decoration,projectile)->{
                        ring[0]++;check(Math.abs(Math.hypot(x,z)-radius)<1e-8,"Original seven-frame ring radius "+current);
                        check(count==1&&decoration&&projectile,"Ring particle density and scope preserved");
                    });
            check(ring[0]>0,"Ring frame emitted");
        }
        check(!SceneRecipes.active(view(Kind.FOX_RING),7),"Ring ends after seven original frames");
        for(int tick=1000;tick<1010;tick++) {
            int[] cloud={0};
            SceneRecipes.emit(view(Kind.SPORE_CLOUD),tick-1000,tick,new Random(127),
                    (particle,x,y,z,count,dx,dy,dz,speed,decoration,projectile)->{
                        cloud[0]+=count;check(!decoration&&!projectile,"Cloud remains visible to its owner");
                        if(particle instanceof net.minecraft.particle.DustParticleEffect dust)
                            check(dust.getColor().equals(new org.joml.Vector3f(.30f,.50f,.10f))&&dust.getScale()==1.2f,"Original poison cloud color and size");
                    });
            check(cloud[0]==(tick%2==0?10:0),"Cloud preserves original world-clock cadence");
        }
    }
}
