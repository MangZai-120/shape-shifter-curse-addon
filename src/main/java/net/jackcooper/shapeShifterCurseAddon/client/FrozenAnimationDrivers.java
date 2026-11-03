package net.jackcooper.shapeShifterCurseAddon.client;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import net.jackcooper.shapeShifterCurseAddon.mixin.client.FrozenAnimationStackAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.model.GeoModel;

/** Pauses playback state as well as the rendered pose. No entity age is rewritten. */
public final class FrozenAnimationDrivers {
    private static final Map<LivingEntity, Timeline> CLOCKS = new WeakHashMap<>();
    private static final Map<AnimationStack, StackOwner> STACKS = new WeakHashMap<>();
    private static final Map<AnimatableManager<?>, Double> PLAYHEADS = new WeakHashMap<>();
    private static final Map<AnimationController<?>, WeakReference<LivingEntity>> GEO_OWNERS = new WeakHashMap<>();
    private static final Map<AnimationController<?>, Boolean> GEO_RESUME = new WeakHashMap<>();
    private FrozenAnimationDrivers() {}
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Track freeze time even when a target is outside the camera and is never rendered.
            if (client.world != null) for (Entity entity : client.world.getEntities())
                if (entity instanceof LivingEntity living && (paused(living) || CLOCKS.containsKey(living)))
                    sample(living,0);
            CLOCKS.keySet().removeIf(entity -> entity.getWorld() != client.world || entity.isRemoved() || !entity.isAlive());
            STACKS.values().removeIf(owner -> {
                LivingEntity entity = owner.entity.get();
                return entity == null || entity.isRemoved() || entity.getWorld() != client.world;
            });
        });
    }
    public static void clear() { CLOCKS.clear(); STACKS.clear(); PLAYHEADS.clear(); GEO_OWNERS.clear(); GEO_RESUME.clear(); FrozenVanillaAnimations.clear(); }
    public static boolean paused(LivingEntity entity) {
        return entity != null && entity.deathTime == 0 && FrozenControl.blocked(entity);
    }
    public static void sample(Entity entity, float delta) {
        if (!(entity instanceof LivingEntity living)) return;
        if (!living.isAlive() || living.deathTime > 0) { CLOCKS.remove(living); return; }
        Timeline clock = CLOCKS.get(living);
        if (clock == null && paused(living)) { clock = new Timeline(); CLOCKS.put(living,clock); }
        if (clock != null) clock.sample(entity.age + (double)delta, paused(living));
        if (paused(living)) FrozenVanillaAnimations.sample(living,vanillaTime(living,entity.age+delta));
        if (paused(living)) GEO_OWNERS.forEach((controller,owner) -> {
            if (owner.get() == living && controller.getCurrentAnimation() != null && !controller.hasAnimationFinished())
                GEO_RESUME.put(controller,true);
        });
    }
    public static double animationTime(double raw) {
        Entity entity = FrozenAnimationState.renderingEntity();
        Timeline clock = entity instanceof LivingEntity living ? CLOCKS.get(living) : null;
        return clock == null ? raw : raw - clock.offset();
    }
    public static double wallTime(double animationTime) {
        Entity entity=FrozenAnimationState.renderingEntity();
        Timeline clock=entity instanceof LivingEntity living?CLOCKS.get(living):null;
        return clock==null?animationTime:animationTime+clock.offset();
    }
    public static double geoTime(AnimatableManager<?> manager,double raw) {
        double time = animationTime(raw);
        PLAYHEADS.put(manager,time);
        if (FrozenAnimationState.renderingEntity() instanceof LivingEntity living)
            for (var controller : manager.getAnimationControllers().values())
                GEO_OWNERS.put(controller,new WeakReference<>(living));
        return time;
    }
    /** Keep the old predicate selection until the resumed clip (or current loop cycle) completes. */
    public static boolean continueGeo(AnimationController<?> controller,double tick) {
        if (!GEO_RESUME.containsKey(controller)) return false;
        var owner = GEO_OWNERS.get(controller);
        LivingEntity entity = owner == null ? null : owner.get();
        var clip = controller.getCurrentAnimation();
        if (entity == null || !entity.isAlive() || entity.deathTime > 0 || clip == null || controller.hasAnimationFinished()
                || (clip.loopType() != Animation.LoopType.PLAY_ONCE && tick >= clip.animation().length())) {
            GEO_RESUME.remove(controller); return false;
        }
        return true;
    }
    /** SSC's own animation layer is priority 1; inspect its actual playhead, including speed modifiers. */
    public static KeyframeAnimationPlayer sscClip(LivingEntity entity) {
        if (!(entity instanceof IAnimatedPlayer player)) return null;
        var stack=player.getAnimationStack();
        if (stack==null) return null;
        for (var layer : ((FrozenAnimationStackAccessor)stack).ssca$layers()) {
            if (layer.getLeft() != 1) continue;
            var animation = layer.getRight();
            while (animation instanceof ModifierLayer<?> modifier) animation = modifier.getAnimation();
            if (animation instanceof KeyframeAnimationPlayer clip) return clip;
        }
        return null;
    }
    public static final class ResumedClip {
        private final KeyframeAnimationPlayer clip;
        private int lastTick;
        public ResumedClip(KeyframeAnimationPlayer clip) { this.clip = clip; lastTick = clip.getCurrentTick(); }
        public boolean unfinished() {
            int now = clip.getCurrentTick();
            boolean playing = clip.isActive() && now >= lastTick && now < (clip.isInfinite() ? clip.getData().endTick : clip.getStopTick());
            lastTick = now;
            return playing;
        }
    }
    public static float vanillaTime(LivingEntity entity,float raw) {
        Timeline clock = CLOCKS.get(entity);
        return clock == null ? raw : (float)(clock.paused ? clock.held : raw - clock.offset());
    }
    public static void bind(LivingEntity entity, AnimationStack stack) {
        if (stack != null) STACKS.computeIfAbsent(stack, ignored -> new StackOwner(entity));
    }
    public static boolean paused(AnimationStack stack) {
        StackOwner owner = STACKS.get(stack);
        return owner != null && paused(owner.entity.get());
    }
    public static float partial(AnimationStack stack,float delta) {
        StackOwner owner = STACKS.get(stack);
        if (owner == null) return delta;
        if (!paused(owner.entity.get())) owner.delta = delta;
        return owner.delta;
    }
    /** Restore this instance's last animated bones; other entities may share the same GeoModel. */
    public static <T extends GeoAnimatable> boolean holdGeo(GeoModel<T> model,T animatable,long id,AnimationState<T> state) {
        if (!(FrozenAnimationState.renderingEntity() instanceof LivingEntity living) || !paused(living)) return false;
        var manager = animatable.getAnimatableInstanceCache().getManagerForId(id);
        if (manager.isFirstTick()) return false; // Initialize an unseen model once, then pause its existing controller.
        for (var controller : manager.getAnimationControllers().values()) {
            GEO_OWNERS.put(controller,new WeakReference<>(living));
            if (controller.getCurrentAnimation() != null && !controller.hasAnimationFinished()) GEO_RESUME.put(controller,true);
        }
        var snapshots = manager.getBoneSnapshotCollection();
        for (var bone : model.getAnimationProcessor().getRegisteredBones()) {
            var pose = snapshots.get(bone.getName());
            if (pose == null) continue;
            bone.setRotX(pose.getRotX()); bone.setRotY(pose.getRotY()); bone.setRotZ(pose.getRotZ());
            bone.setPosX(pose.getOffsetX()); bone.setPosY(pose.getOffsetY()); bone.setPosZ(pose.getOffsetZ());
            bone.setScaleX(pose.getScaleX()); bone.setScaleY(pose.getScaleY()); bone.setScaleZ(pose.getScaleZ());
        }
        state.animationTick = PLAYHEADS.getOrDefault(manager,manager.getLastUpdateTime());
        model.setCustomAnimations(animatable,id,state);
        return true;
    }
    private static final class StackOwner {
        final WeakReference<LivingEntity> entity; float delta;
        StackOwner(LivingEntity entity) { this.entity = new WeakReference<>(entity); }
    }
    /** Holds the playhead and subtracts paused wall time on resume, including repeated freezes. */
    public static final class Timeline {
        private boolean paused;
        private double start, total, raw, held;
        public double sample(double now,boolean freeze) {
            raw = now;
            if (freeze && !paused) { paused = true; start = now; held = now - total; }
            else if (!freeze && paused) { total += now - start; paused = false; }
            return paused ? held : now - total;
        }
        public double offset() { return paused ? raw - held : total; }
    }
}
