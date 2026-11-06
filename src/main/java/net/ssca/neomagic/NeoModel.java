package net.ssca.neomagic;

import java.util.*;
import java.util.random.RandomGenerator;

/** Deterministic construction/payment rules. No Minecraft or old-magic state. */
public final class NeoModel {
    public static final int VERSION = 2;
    public enum Core { THERMAL, FROST, LUNAR, CURSE, SUMMON, VOID, SPACE }
    public enum Carrier { SELF, CONTACT, PROJECTILE, AREA, LOCK }
    public enum Control { SPLIT, MERGE, STORE, CONSTRAIN, BRIDGE, CONVERT }
    public record Blueprint(String name, Core core, Carrier carrier, List<Control> controls,
                            Core conversion, double scale, int prep, double range) {
        public Blueprint {
            Objects.requireNonNull(name); Objects.requireNonNull(core); Objects.requireNonNull(carrier);
            controls = List.copyOf(controls); Objects.requireNonNull(conversion);
        }
        public static Blueprint starter() {
            return new Blueprint("", Core.THERMAL, Carrier.PROJECTILE, List.of(), Core.FROST, 1, 40, 32);
        }
        public String identity() {
            return core + ":" + carrier + ":" + controls + (controls.contains(Control.CONVERT) ? ":" + conversion : "");
        }
        public int band() { return scale <= 1 ? 0 : scale <= 2 ? 1 : scale <= 3 ? 2 : 3; }
        public int tier() { return 1 + controls.size() + band(); }
        public Core output() { return controls.contains(Control.CONVERT) ? conversion : core; }
        public int count(Control c) { return Collections.frequency(controls, c); }
    }
    public record Profile(double safety, double failure, double offset, double merge, double split, boolean reference) {
        public double failureFor(Blueprint b) {
            return failure * (b.controls.contains(Control.MERGE) ? merge : 1) * (b.controls.contains(Control.SPLIT) ? split : 1);
        }
    }
    public record Coefficients(double a, double b) {}
    public record Tuning(double cost, double floor, int cooldown, double mastery) {
        public static Tuning defaults() {return new Tuning(1,0,0,0);}
    }
    public record Plan(int branches, double retention, double load, boolean focused, boolean stored, int constraints) {}
    public record Quote(double normal, double actual, double floor, Plan plan) {}
    public record Risk(double excess, double failure, double offset, double angle) {}
    public record Checkpoint(int tick, double burden, double paid, Risk risk, boolean failed, boolean shifted,
                             double yaw, double pitch, double attenuation, boolean reversed, boolean wrongTarget) {}
    public static boolean supports(Core c, Carrier v) {
        return switch(c) {
            case THERMAL, FROST, VOID -> v == Carrier.CONTACT || v == Carrier.PROJECTILE || v == Carrier.AREA;
            case LUNAR -> true;
            case CURSE -> v == Carrier.CONTACT || v == Carrier.PROJECTILE || v == Carrier.LOCK;
            case SUMMON -> v == Carrier.AREA || v == Carrier.LOCK;
            case SPACE -> v == Carrier.SELF || v == Carrier.LOCK;
        };
    }
    public static List<String> errors(Blueprint b) {
        List<String> e = new ArrayList<>();
        if (b.name.length() > 32) e.add("name");
        if (!Double.isFinite(b.scale) || b.scale < .5 || b.scale > 4) e.add("scale");
        if (b.prep < 0 || b.prep > 80) e.add("prep");
        if (!Double.isFinite(b.range) || b.range < 1 || b.range > (b.controls.contains(Control.BRIDGE) ? 64 : 32)) e.add("range");
        if (!supports(b.core, b.carrier) || !supports(b.output(), b.carrier)) e.add("carrier");
        if (b.controls.size() > 6) e.add("capacity");
        for (Control c : List.of(Control.STORE, Control.CONVERT, Control.BRIDGE))
            if (b.count(c) > 1) e.add("repeat");
        if (b.count(Control.CONSTRAIN) > 2) e.add("constraints");
        if (b.count(Control.CONVERT) > 0 && !((b.core == Core.THERMAL && b.conversion == Core.FROST)
                || (b.core == Core.FROST && b.conversion == Core.THERMAL)
                || (b.core == Core.LUNAR && b.conversion == Core.VOID))) e.add("conversion");
        int branches = 1;
        for (Control c : b.controls) {
            if (c == Control.SPLIT) branches *= 3;
            if (c == Control.MERGE) branches = 1;
            if (branches > 6) e.add("branches");
        }
        if (b.carrier == Carrier.SELF && b.controls.contains(Control.SPLIT)) e.add("self_split");
        if (b.core == Core.SUMMON && b.controls.contains(Control.MERGE)) e.add("summon_merge");
        return List.copyOf(new LinkedHashSet<>(e));
    }
    public static Plan plan(Blueprint b) {
        int branches = 1; double retention = 1, load = 0; boolean focused = false;
        for (Control c : b.controls) switch(c) {
            case SPLIT -> { branches *= 3; load += 2; focused = false; }
            case MERGE -> { if (branches > 1) retention *= .9; branches = 1; load += 2.5; focused = true; }
            case STORE -> { retention *= .85; load += 3; }
            case CONSTRAIN -> load += 2;
            case BRIDGE -> load += 2;
            case CONVERT -> { retention *= .8; load += 4; }
        }
        return new Plan(branches, retention, load, focused, b.controls.contains(Control.STORE), b.count(Control.CONSTRAIN));
    }
    public static Coefficients coefficients(Core c) {
        return switch(c) {
            case THERMAL -> new Coefficients(6,18); case FROST -> new Coefficients(5,15);
            case LUNAR -> new Coefficients(8,20); case CURSE -> new Coefficients(4,12);
            case SUMMON -> new Coefficients(10,30); case VOID -> new Coefficients(12,36);
            case SPACE -> new Coefficients(14,42);
        };
    }
    public static Quote quote(Blueprint b, double mastery, Coefficients c) {
        return quote(b,mastery,c,Tuning.defaults());
    }
    public static Quote quote(Blueprint b, double mastery, Coefficients c,Tuning tuning) {
        Plan p = plan(b);
        double path = b.range / 16;
        if (b.core == Core.SPACE) path *= path;
        double normal = c.a * b.scale + c.b * b.scale * b.scale / (b.prep / 20.0 + .5) + 1.5 * path + 2 * p.load;
        double floor = b.controls.contains(Control.MERGE) ? .35 : b.controls.contains(Control.SPLIT) ? .4 : .5;
        if(tuning.floor>0)floor=tuning.floor;
        normal*=tuning.cost;
        return new Quote(normal, normal * (1 - (1-floor)*clamp(mastery,0,1)), floor, p);
    }
    public static Risk risk(Blueprint b, double current, double q, Profile f) {
        return riskWithFailure(b,current,q,f,failureChance(b,current,q,f));
    }
    /** Pure forecast; actual failure randomness is consumed only by Session.drawFailure. */
    public static double failureChance(Blueprint b, double current, double q, Profile f) {
        double excess = Math.max(0, current - f.safety);
        if (excess == 0) return 0;
        if (current > 2 * f.safety) return 1;
        double scaled=excess/Math.max(1,f.safety)*60;
        return clamp(.03*Math.pow(scaled/10,1.3)*Math.pow(q/20,.7)*f.failureFor(b),0,1);
    }
    /** Live deviation never recalculates or redraws the frozen whole-cast failure chance. */
    public static Risk offsetRisk(Blueprint b, double current, double q, Profile f) {
        return riskWithFailure(b,current,q,f,0);
    }
    private static Risk riskWithFailure(Blueprint b, double current, double q, Profile f, double failure) {
        double excess = Math.max(0, current - f.safety);
        if (excess == 0) return new Risk(0,0,0,0);
        // 危险范围为 N～2N：按当前有效 N 归一化，保留连续风险曲线与形态修正。
        double scaled=excess/Math.max(1,f.safety)*60;
        double constraint = Math.pow(.65, b.count(Control.CONSTRAIN));
        return new Risk(excess, failure,
                clamp(.08*(scaled/10)*Math.sqrt(q/20)*f.offset*constraint,0,1),
                Math.min(12,.18*Math.pow(scaled,.7)*Math.pow(q/20,.35))*constraint);
    }
    public static double clamp(double n, double low, double high) { return Math.max(low, Math.min(high,n)); }
    public static int cooldown(Blueprint b) { return 60 + 20*b.tier(); }
    public static double masteryNeeded(Blueprint b) { return 20.0*b.tier()*b.tier(); }
    public static final class Session {
        public final Blueprint blueprint;
        public final Quote quote;
        public final List<Checkpoint> checkpoints = new ArrayList<>();
        public double paid, yaw, pitch, attenuation = 1, survival = 1;
        public double failureProbability, failureBasis;
        public int failureDraws;
        /** Special lock is exclusive; ordinary completion checks its final payment before releasing. */
        public final int failureBoundary;
        public boolean failed, complete, reversed, wrongTarget, hasShifted;
        private int previous = -1;
        private int failureAt = -1;
        private final int duration;
        private final int latestFailureTick;
        public Session(Blueprint b, Quote q) { this(b,q,b.prep()); }
        /** Existing spells may have longer preparation than the research editor's 80 tick range. */
        public Session(Blueprint b, Quote q, int duration) { this(b,q,duration,duration); }
        public Session(Blueprint b, Quote q, int duration, int lockInTick) {
            blueprint=b;quote=q;this.duration=Math.max(0,duration);
            failureBoundary=lockInTick<0?this.duration:Math.min(this.duration,lockInTick);
            // Ordinary completion checks payment before releasing its effect. An earlier
            // special lock protects every tick at/after that lock, including held casts.
            latestFailureTick=this.duration==0?0:failureBoundary<this.duration?failureBoundary-1:this.duration;
        }
        /** Only risk events need the current form; ordinary payment ticks need no profile allocation. */
        public boolean needsRisk(int age) {
            return !failed&&!complete&&age>previous&&(failureDraws==0||age%10==0
                    ||failureAt>=0&&age>=failureAt&&age<=latestFailureTick);
        }
        private void drawFailure(double current, Profile form, RandomGenerator rng) {
            failureBasis=current+quote.actual;
            failureProbability=failureChance(blueprint,failureBasis,quote.actual,form);
            failureDraws=1;survival=1-failureProbability;
            if(rng.nextDouble()<failureProbability) {
                int earliest=firstUnsafeTick(current,form.safety);
                if(earliest<=latestFailureTick)
                    failureAt=earliest+rng.nextInt(latestFailureTick-earliest+1);
            }
        }
        private int firstUnsafeTick(double initial,double safety) {
            if(initial>safety||duration==0)return 0;
            if(quote.actual<=0||initial+quote.actual<=safety)return Integer.MAX_VALUE;
            double crossing=(safety-initial)*duration/quote.actual;
            int first=(int)Math.min(duration,(Math.floor(crossing/2)+1)*2);
            // Correct floating point equality at an exact two-tick payment boundary.
            while(first<duration&&initial+quote.actual*first/duration<=safety)first=Math.min(duration,first+2);
            return first;
        }
        /** Caller adds returned delta to its sole C before consuming this checkpoint. */
        public double advance(int age, double current, Profile form, RandomGenerator rng) {
            return advance(age,current,form,rng,true);
        }
        public double advance(int age, double current, Profile form, RandomGenerator rng, boolean finishAtDuration) {
            if (failed || complete || age <= previous) return 0;
            if (age != previous + 1) throw new IllegalArgumentException("Session must advance one tick at a time");
            previous = age;
            if(failureDraws==0) {
                // One whole-cast draw, using the initial C plus this cast's actual locked quote.
                // Keep the result and scheduled time on the server; later C changes never redraw it.
                drawFailure(current,form,rng);
            }
            int billed = age >= duration ? duration : age / 2 * 2;
            double due = duration == 0 ? quote.actual : quote.actual*billed/duration;
            double delta = Math.max(0,due-paid); paid += delta;
            // One frozen draw and one scheduled time. Recovery can defer the failure,
            // but it can never fire while the live body is within its safety threshold.
            failed=failureAt>=0&&age>=failureAt&&age<=latestFailureTick
                    &&form!=null&&current+delta>form.safety;
            if (age % 10 == 0 || failed) {
                Risk r=riskWithFailure(blueprint,current+delta,quote.actual,form,failureProbability);
                boolean shiftDraw=!failed&&age%10==0&&r.offset>0&&rng.nextDouble()<r.offset;
                if (!failed && shiftDraw) {
                    hasShifted=true;
                    yaw += (rng.nextDouble()*2-1)*r.angle;
                    pitch += (rng.nextDouble()*2-1)*r.angle;
                    switch(blueprint.output()) {
                        case LUNAR -> { attenuation *= .75; if (rng.nextDouble() < Math.min(.8,60*r.excess/Math.max(1,form.safety)/80)) reversed = true; }
                        case SUMMON, SPACE -> attenuation *= .7;
                        default -> { }
                    }
                    if (blueprint.carrier == Carrier.LOCK && rng.nextDouble() < .4) wrongTarget = true;
                }
                checkpoints.add(new Checkpoint(age,current+delta,paid,r,failed,!failed&&shiftDraw,yaw,pitch,attenuation,reversed,wrongTarget));
            }
            complete = !failed && finishAtDuration && age >= duration;
            return delta;
        }
    }
    private NeoModel() {}
}
