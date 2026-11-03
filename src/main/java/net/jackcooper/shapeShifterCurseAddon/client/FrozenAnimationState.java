package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.Map;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.anomaly.FrozenControl;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import software.bernie.geckolib.cache.object.GeoBone;

/** Render-only pose snapshots. Entity ticks, effect expiry and death continue normally. */
public final class FrozenAnimationState {
    private static final Map<LivingEntity, Frame> FRAMES = new WeakHashMap<>();
    private static Frame current;
    private static Entity rendering;
    private FrozenAnimationState() {}
    public static void init() {
        FrozenAnimationDrivers.init();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FRAMES.entrySet().removeIf(entry -> {
                var entity=entry.getKey();var frame=entry.getValue();
                if (entity.getWorld()!=client.world || entity.isRemoved() || !entity.isAlive() || entity.deathTime>0) return true;
                if (!frozen(entity) && frame.resumeAt<0) frame.resumeAt=entity.age;
                return !frozen(entity) && entity.age-frame.resumeAt>=4;
            });
        });
    }
    public static void clear() { FRAMES.clear(); current = null; rendering = null; FrozenAnimationDrivers.clear(); }
    public static Entity renderingEntity() { return rendering; }
    private static boolean frozen(LivingEntity entity) {
        return entity.deathTime == 0 && FrozenControl.blocked(entity);
    }
    public static void render(Entity entity, float delta, Consumer<Float> draw) {
        Frame previous = current;
        Entity previousEntity = rendering;
        rendering = entity;
        FrozenAnimationDrivers.sample(entity, delta);
        current=null;
        boolean paused=entity instanceof LivingEntity living && frozen(living);
        if (entity instanceof LivingEntity living && living.isAlive() && living.deathTime==0) {
            Frame frame=FRAMES.get(living);
            if (paused) {
                if (frame==null || frame.resumeAt>=0) { frame=new Frame(delta);FRAMES.put(living,frame); }
                frame.blend=0;current=frame;
            } else if (frame!=null) {
                double now=entity.age+(double)delta;
                if (frame.resumeAt<0) frame.resumeAt=now;
                frame.blend=(float)Math.max(0,Math.min(1,(now-frame.resumeAt)/4));
                if (frame.blend<1) current=frame;else FRAMES.remove(living);
            }
        } else if (entity instanceof LivingEntity living) FRAMES.remove(living);
        try {
            if (current != null) current.calls.clear();
            draw.accept(paused && current!=null ? current.delta : delta);
        } finally { current = previous; rendering = previousEntity; }
    }
    /** Anchor frozen local transforms to today's entity/camera matrix, never to screen coordinates. */
    public static void root(MatrixStack matrices, Runnable draw) {
        Frame frame = current;
        if (frame == null) { draw.run(); return; }
        MatrixPose previous = frame.root;
        MatrixPose root = frame.lease();
        try {
            root.position.set(matrices.peek().getPositionMatrix());
            root.normal.set(matrices.peek().getNormalMatrix());
            frame.root = root;
            draw.run();
        } finally { frame.root = previous; frame.release(); }
    }
    public static void matrix(Object key, MatrixStack matrices, Runnable draw) {
        matrix(key, matrices, draw, true);
    }
    /** ModelPart.rotate must leave its output matrix in place for cuboids/held items to consume. */
    public static void transform(Object key, MatrixStack matrices, Runnable draw) {
        matrix(key, matrices, draw, false);
    }
    private static void matrix(Object key, MatrixStack matrices, Runnable draw, boolean restore) {
        Frame frame = current;
        if (frame == null || frame.root == null) { draw.run(); return; }
        var entry = matrices.peek();
        var live = frame.lease();
        boolean captured = false;
        boolean completed = false;
        try {
            live.position.set(entry.getPositionMatrix());
            live.normal.set(entry.getNormalMatrix());
            captured = true;
            int index = frame.calls.getOrDefault(key, 0);
            frame.calls.put(key, index + 1);
            var poses = frame.transforms.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (index >= poses.size()) poses.add(new MatrixPose(
                    new Matrix4f(frame.root.position).invert().mul(live.position),
                    new Matrix3f(frame.root.normal).invert().mul(live.normal)));
            var pose = poses.get(index);
            entry.getPositionMatrix().set(frame.root.position).mul(pose.position).lerp(live.position,frame.blend);
            entry.getNormalMatrix().set(frame.root.normal).mul(pose.normal).lerp(live.normal,frame.blend);
            draw.run();
            completed = true;
        } finally {
            try {
                if (captured && (restore || !completed)) {
                    entry.getPositionMatrix().set(live.position);
                    entry.getNormalMatrix().set(live.normal);
                }
            } finally { frame.release(); }
        }
    }
    public static void part(ModelPart part, Runnable draw) {
        if (current == null) { draw.run(); return; }
        PartPose live = new PartPose(part);
        current.parts.computeIfAbsent(part, ignored -> live).blend(live,current.blend).apply(part);
        try { draw.run(); } finally { live.apply(part); }
    }
    public static void bone(GeoBone bone, Runnable draw) {
        if (current == null) { draw.run(); return; }
        BonePose live = new BonePose(bone);
        boolean moved = bone.hasPositionChanged(), rotated = bone.hasRotationChanged(), scaled = bone.hasScaleChanged();
        current.bones.computeIfAbsent(bone, ignored -> live).blend(live,current.blend).apply(bone);
        try { draw.run(); } finally {
            live.apply(bone);
            bone.resetStateChanges();
            if (moved) bone.markPositionAsChanged();
            if (rotated) bone.markRotationAsChanged();
            if (scaled) bone.markScaleAsChanged();
        }
    }
    private static final class Frame {
        final float delta;
        double resumeAt=-1;float blend;
        final Map<ModelPart, PartPose> parts = new IdentityHashMap<>();
        final Map<GeoBone, BonePose> bones = new IdentityHashMap<>();
        final Map<Object, List<MatrixPose>> transforms = new IdentityHashMap<>();
        final Map<Object, Integer> calls = new IdentityHashMap<>();
        // Active nested scopes own distinct slots; captured frozen transforms never enter this pool.
        final List<MatrixPose> scratch = new ArrayList<>();
        int scratchDepth;
        MatrixPose root;
        Frame(float delta) { this.delta = delta; }
        MatrixPose lease() {
            int depth = scratchDepth;
            MatrixPose pose;
            if (depth >= 64) pose = new MatrixPose(new Matrix4f(), new Matrix3f());
            else {
                if (depth >= scratch.size()) scratch.add(new MatrixPose(new Matrix4f(), new Matrix3f()));
                pose = scratch.get(depth);
            }
            scratchDepth = depth + 1;
            return pose;
        }
        void release() { scratchDepth--; }
    }
    private record MatrixPose(Matrix4f position, Matrix3f normal) {}
    private record PartPose(float x,float y,float z,float pitch,float yaw,float roll,
                            float sx,float sy,float sz,boolean visible,boolean hidden) {
        PartPose(ModelPart p) { this(p.pivotX,p.pivotY,p.pivotZ,p.pitch,p.yaw,p.roll,p.xScale,p.yScale,p.zScale,p.visible,p.hidden); }
        PartPose blend(PartPose p,float a) { if(a==0)return this; return new PartPose(lerp(x,p.x,a),lerp(y,p.y,a),lerp(z,p.z,a),angle(pitch,p.pitch,a),angle(yaw,p.yaw,a),angle(roll,p.roll,a),lerp(sx,p.sx,a),lerp(sy,p.sy,a),lerp(sz,p.sz,a),a<0.5?visible:p.visible,a<0.5?hidden:p.hidden); }
        void apply(ModelPart p) {
            p.pivotX=x;p.pivotY=y;p.pivotZ=z;p.pitch=pitch;p.yaw=yaw;p.roll=roll;
            p.xScale=sx;p.yScale=sy;p.zScale=sz;p.visible=visible;p.hidden=hidden;
        }
    }
    private record BonePose(float x,float y,float z,float rx,float ry,float rz,float sx,float sy,float sz,
                            float px,float py,float pz,boolean hidden,boolean children) {
        BonePose(GeoBone b) { this(b.getPosX(),b.getPosY(),b.getPosZ(),b.getRotX(),b.getRotY(),b.getRotZ(),
                b.getScaleX(),b.getScaleY(),b.getScaleZ(),b.getPivotX(),b.getPivotY(),b.getPivotZ(),b.isHidden(),b.isHidingChildren()); }
        BonePose blend(BonePose p,float a) { if(a==0)return this; return new BonePose(lerp(x,p.x,a),lerp(y,p.y,a),lerp(z,p.z,a),angle(rx,p.rx,a),angle(ry,p.ry,a),angle(rz,p.rz,a),lerp(sx,p.sx,a),lerp(sy,p.sy,a),lerp(sz,p.sz,a),lerp(px,p.px,a),lerp(py,p.py,a),lerp(pz,p.pz,a),a<0.5?hidden:p.hidden,a<0.5?children:p.children); }
        void apply(GeoBone b) {
            b.setPosX(x);b.setPosY(y);b.setPosZ(z);b.setRotX(rx);b.setRotY(ry);b.setRotZ(rz);
            b.setScaleX(sx);b.setScaleY(sy);b.setScaleZ(sz);b.setPivotX(px);b.setPivotY(py);b.setPivotZ(pz);
            b.setHidden(hidden);b.setChildrenHidden(children);
        }
    }
    private static float lerp(float from,float to,float a) { return from+(to-from)*a; }
    private static float angle(float from,float to,float a) { return from+(float)Math.atan2(Math.sin(to-from),Math.cos(to-from))*a; }
}
