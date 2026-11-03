package net.jackcooper.shapeShifterCurseAddon.client;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.jackcooper.shapeShifterCurseAddon.mixin.client.FrozenNativeStateAccessor;
import net.minecraft.client.render.entity.animation.Animation;
import net.minecraft.entity.AnimationState;
import net.minecraft.entity.LivingEntity;

/** Client visual clocks only. Never changes physics, AI timers or synchronized entity state. */
public final class FrozenVanillaAnimations {
    private static final Map<AnimationState, Playback> CLIPS = new WeakHashMap<>();
    private static final Map<LivingEntity, Map<String, Countdown>> TIMERS = new WeakHashMap<>();
    private FrozenVanillaAnimations() {}
    public static void clear() { CLIPS.clear(); TIMERS.clear(); }
    public static void sample(LivingEntity entity,float time) {
        if (!FrozenAnimationDrivers.paused(entity)) return;
        CLIPS.forEach((state,clip) -> { if (clip.owner.get()==entity) clip.freeze(state,time); });
        var timers=TIMERS.get(entity);
        if (timers!=null) timers.values().forEach(timer -> timer.freeze());
    }
    public record Sample(AnimationState state,float time) {}
    public static Sample select(AnimationState state,Animation animation,float time) {
        if (!(FrozenAnimationState.renderingEntity() instanceof LivingEntity entity)) return new Sample(state,time);
        if (!entity.isAlive() || entity.deathTime>0) { CLIPS.remove(state); return new Sample(state,entity.age); }
        Playback clip=CLIPS.computeIfAbsent(state,ignored -> new Playback(entity,animation));
        if (FrozenAnimationDrivers.paused(entity)) clip.freeze(state,time);
        if (clip.resume!=null) {
            if (FrozenAnimationDrivers.paused(entity) || clip.resume.getTimeRunning()<clip.end)
                return new Sample(clip.resume,time);
            clip.resume=null;
        }
        return new Sample(state,(float)FrozenAnimationDrivers.wallTime(time));
    }
    public static int countdown(LivingEntity entity,String key,int real) {
        if (!entity.getWorld().isClient || FrozenAnimationState.renderingEntity()!=entity || !entity.isAlive() || entity.deathTime>0) return real;
        Countdown timer=TIMERS.computeIfAbsent(entity,ignored -> new HashMap<>()).computeIfAbsent(key,ignored -> new Countdown());
        if (FrozenAnimationDrivers.paused(entity)) {
            if (!timer.frozen) timer.last=real;
            timer.freeze();return timer.last;
        }
        if (timer.frozen) { timer.frozen=false;timer.resumed=entity.age;timer.remaining=timer.last; }
        int remaining=Math.max(0,timer.remaining-(entity.age-timer.resumed));
        timer.last=remaining>0?remaining:real;
        return timer.last;
    }
    private static final class Playback {
        final WeakReference<LivingEntity> owner;final Animation animation;
        AnimationState resume;double end;
        Playback(LivingEntity entity,Animation animation) { owner=new WeakReference<>(entity);this.animation=animation; }
        void freeze(AnimationState source,float time) {
            if (resume!=null || !source.isRunning()) return;
            resume=new AnimationState();
            var data=(FrozenNativeStateAccessor)(Object)resume;
            data.ssca$updatedAt((long)Math.floor(time*50));data.ssca$timeRunning(source.getTimeRunning());
            double length=animation.lengthInSeconds()*1000d;
            end=animation.looping() && length>0 ? (Math.floor(source.getTimeRunning()/length)+1)*length : length;
        }
    }
    private static final class Countdown {
        int last,remaining,resumed;boolean frozen;
        void freeze(){frozen=true;}
    }
}
