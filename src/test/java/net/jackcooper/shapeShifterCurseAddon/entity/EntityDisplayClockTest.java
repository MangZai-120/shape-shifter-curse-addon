package net.jackcooper.shapeShifterCurseAddon.entity;

import java.nio.file.Files;
import java.nio.file.Path;
import net.jackcooper.shapeShifterCurseAddon.util.EntityDisplayClock;

/** Counter/clock equivalence at boundaries; actual tracker delivery is verified by the native suite. */
public final class EntityDisplayClockTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        boundaries();
        laserPhases();
        tetherCountdown();
        frostCycles();
        restoredAndPaused();
        Path report=Path.of("build/reports/network-sync/entity-display-clocks.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report,"Entity display-clock arithmetic checks: "+checks+"\n"
                +"Laser uses authoritative phase counters; the display marker changes at phase boundaries.\n"
                +"Tidal remaining countdown is equivalent for 170/204 tick durations and clamps at zero.\n"
                +"Frost matches the old sawtooth with/without thorns, interval changes, and levels 0 through 5.\n"
                +"The Frost fixture advances world time after its START_WORLD_TICK manager update.\n"
                +"Pure counter/clock checks only; native tracker, join, dimension, and performance checks are separate.\n");
        System.out.println("EntityDisplayClockTest: "+checks+" checks");
    }
    private static void boundaries() {
        check(EntityDisplayClock.elapsed(100,EntityDisplayClock.UNSET)==0,"Unreceived elapsed marker is idle");
        check(EntityDisplayClock.remaining(100,EntityDisplayClock.UNSET)==0,"Unreceived end marker is idle");
        check(EntityDisplayClock.elapsed(100,110)==0,"Future phase starts cannot create a negative display time");
        check(EntityDisplayClock.remaining(111,110)==0,"Expired countdown cannot become negative");
        check(EntityDisplayClock.elapsed(1,-9)==10,"An epoch before world zero is valid after a restored phase");
        check(EntityDisplayClock.elapsed(Long.MAX_VALUE,0)==Integer.MAX_VALUE,"Elapsed display saturates at the API int limit");
        check(EntityDisplayClock.remaining(-1,Long.MAX_VALUE)==Integer.MAX_VALUE,"Remaining display saturates on subtraction overflow");
        check(EntityDisplayClock.cyclicProgress(40,EntityDisplayClock.UNSET,20,100)==40,"Missing cycle marker keeps its base");
        check(EntityDisplayClock.cyclicProgress(40,80,0,100)==40,"A fixed explicit progress has no invented slope");
        check(EntityDisplayClock.cyclicProgress(40,110,20,100)==40,"A future cycle does not wrap backwards");
        check(EntityDisplayClock.cyclicProgress(20,-5,20,-4)==21,"Cycles accept negative restored epochs");
        check(EntityDisplayClock.cyclicProgress(20,-5,20,15)==20,"Cycles return exactly to base at their boundary");
    }
    private static void laserPhases() {
        for(int charge:new int[]{1,70,100,140}) {
            int[] durations={charge,60,30};
            int phase=0,counter=0,markerWrites=1;
            long now=500,marker=now;
            for(int tick=1;tick<charge+60+30;tick++) {
                now++; counter++;
                if(counter>=durations[phase]) {phase++;counter=0;}
                long next=now-counter;
                if(next!=marker) {marker=next;markerWrites++;}
                check(EntityDisplayClock.elapsed(now,marker)==counter,"Laser display equals the original current-phase counter");
                for(int observer=0;observer<20;observer++) {
                    // A late observer receives the current marker, rather than starting its own charge at zero.
                    check(EntityDisplayClock.elapsed(now,marker)==counter,"Twenty observers share the current phase time");
                }
            }
            check(markerWrites==3,"A normal laser only changes its initial/RELEASE/FADE markers");
        }
        long enhancedStart=1000;
        for(int tick=0;tick<=400;tick++)
            check(EntityDisplayClock.elapsed(enhancedStart+tick,enhancedStart)==tick,"Enhanced standby rotation advances without repeated timer packets");
    }
    private static void tetherCountdown() {
        for(int duration:new int[]{1,170,204}) {
            long now=1000,end=EntityDisplayClock.UNSET;
            int changes=0;
            check(EntityDisplayClock.remaining(now,end)==0,"Original pre-attraction countdown is hidden");
            for(int phaseTick=1;phaseTick<=duration;phaseTick++) {
                now++;
                int original=Math.max(0,duration-phaseTick);
                long next=now+original;
                if(next!=end) {end=next;changes++;}
                check(EntityDisplayClock.remaining(now,end)==original,"Tidal clock matches original remaining ticks");
                for(int viewer=0;viewer<20;viewer++)
                    check(EntityDisplayClock.remaining(now,end)==original,"Late and existing Tidal observers share the same deadline");
            }
            check(changes==1,"A stable Tidal countdown sends one deadline, not a new integer each tick");
            for(int delay=1;delay<=7;delay++)
                check(EntityDisplayClock.remaining(now+delay,end)==0,"Pop delay cannot restart a completed countdown");
        }
    }
    private static void frostCycles() {
        for(int period:new int[]{1,7,20,37}) for(int startingThorns:new int[]{0,1,3,5}) {
            int level=0,cycle=0,available=startingThorns,markerChanges=0;
            long world=1000,marker=EntityDisplayClock.UNSET;
            int publishedBase=0,publishedPeriod=0;
            for(int tick=1;tick<=period*8;tick++) {
                // Existing manager order: START_WORLD_TICK increments/restarts the partial counter.
                cycle++;
                if(cycle>=period) {
                    cycle=0;
                    if(available>0) {available--;level=Math.min(5,level+1);}
                }
                int original=level*period+cycle;
                // Vanilla advances time after the callback. The entity publishes its display sample afterwards.
                world++;
                long next=world-cycle;
                if(next!=marker || publishedBase!=level*period || publishedPeriod!=period) {
                    marker=next;publishedBase=level*period;publishedPeriod=period;markerChanges++;
                }
                check(EntityDisplayClock.cyclicProgress(publishedBase,marker,publishedPeriod,world)==original,
                        "Frost matches actual partial-cycle resets, including periods without a thorn");
                for(int observer=0;observer<20;observer++)
                    check(EntityDisplayClock.cyclicProgress(publishedBase,marker,publishedPeriod,world)==original,
                            "A late Frost observer uses the authoritative level and partial cycle");
            }
            if(period==20) check(markerChanges<period*8/4,"Frost marker traffic remains event-sized rather than every tick");
        }
        check(EntityDisplayClock.cyclicProgress(20,1000,20,1019)==39,"No-thorn cycle reaches its old final partial tick");
        check(EntityDisplayClock.cyclicProgress(20,1000,20,1020)==20,"No-thorn cycle resets instead of inventing another level");
        check(EntityDisplayClock.cyclicProgress(40,1020,20,1020)==40,"A consumed thorn increases only the server-published base");
        check(EntityDisplayClock.cyclicProgress(74,1100,37,1106)==80,"A balance interval change uses its newly published cycle size");
    }
    private static void restoredAndPaused() {
        int actualPhase=53;
        long world=5000,epoch=world-actualPhase;
        for(int frame=0;frame<200;frame++) check(EntityDisplayClock.elapsed(world,epoch)==actualPhase,"Pausing world ticks also pauses display advancement");
        long reloadedWorld=9000;
        long restoredEpoch=reloadedWorld-actualPhase;
        check(EntityDisplayClock.elapsed(reloadedWorld,restoredEpoch)==actualPhase,"NBT restore reanchors the real counter, excluding unloaded wall time");
        check(EntityDisplayClock.elapsed(reloadedWorld+1,restoredEpoch)==actualPhase+1,"Restored phase resumes one tick at a time");
        long anotherDimension=20;
        long dimensionEpoch=anotherDimension-actualPhase;
        check(EntityDisplayClock.elapsed(anotherDimension,dimensionEpoch)==actualPhase,"A new world's clock is reanchored from the unchanged counter");
        check(EntityDisplayClock.remaining(anotherDimension,anotherDimension+117)==117,"A restored tether preserves its remaining server duration");
    }
    private static void check(boolean passed,String reason) {checks++;if(!passed)throw new AssertionError(reason);}
}
