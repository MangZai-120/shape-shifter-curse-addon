package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity.Motion;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.cache.object.GeoBone;

/** Blends the complete displayed pose, including the aim bones and swimming turn layer. */
final class OrcafisWaterPoseBlend {
    private static final int TICKS = 8, CHANNELS = 9;
    private final WeakHashMap<OrcafisEntity, History> histories = new WeakHashMap<>();
    private final List<GeoBone> bones = new ArrayList<>();
    private float[] raw;
    private byte[] changed;

    private static final class History {
        Motion motion;
        double lastFrame;
        float lastMotionTick, elapsed = TICKS;
        float[] displayed, source;

        History(int size) {
            displayed = new float[size];
            source = new float[size];
        }
    }

    private void collect(GeoBone bone) {
        bones.add(bone);
        for (GeoBone child : bone.getChildBones()) collect(child);
    }

    private static boolean waterSkill(Motion motion) {
        return motion == Motion.WATER_JET || motion == Motion.UNDERTOW;
    }

    boolean apply(OrcafisEntity entity, GeoBone root, float partial) {
        if (bones.isEmpty() || bones.get(0) != root) {
            bones.clear();
            histories.clear();
            collect(root);
            raw = new float[bones.size() * CHANNELS];
            changed = new byte[bones.size()];
        }
        History h = histories.computeIfAbsent(entity, ignored -> new History(raw.length));
        Motion motion = entity.motion();
        double now = entity.age + (double)partial;
        float step = (float)(now - h.lastFrame);
        float tick = entity.motionTick(partial);
        boolean seek = tick - h.lastMotionTick > step + 2 || tick < h.lastMotionTick - 1;
        if (h.motion == null || step < 0 || step > TICKS) {
            h.elapsed = TICKS;
        } else if ((motion != h.motion || seek) && (waterSkill(motion) || waterSkill(h.motion) || h.elapsed < TICKS)) {
            // A one-tick swim between attacks must hand off the pose just drawn.
            float[] swap = h.source;
            h.source = h.displayed;
            h.displayed = swap;
            h.elapsed = 0;
        } else if (!entity.isMotionPaused()) {
            h.elapsed = Math.min(TICKS, h.elapsed + step);
        }
        h.motion = motion;
        h.lastFrame = now;
        h.lastMotionTick = tick;
        boolean blending = h.elapsed < TICKS;
        float t = h.elapsed / TICKS;
        float weight = t*t*(3 - 2*t);
        for (int i = 0; i < bones.size(); i++) {
            GeoBone bone = bones.get(i);
            int j = i * CHANNELS;
            if (!blending) {
                read(bone, h.displayed, j);
                continue;
            }
            read(bone, raw, j);
            changed[i] = (byte)((bone.hasRotationChanged() ? 1 : 0) | (bone.hasPositionChanged() ? 2 : 0)
                    | (bone.hasScaleChanged() ? 4 : 0));
            for (int axis = 0; axis < CHANNELS; axis++) {
                int k = j + axis;
                float delta = raw[k] - h.source[k];
                if (axis < 3) delta = MathHelper.wrapDegrees(delta * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE;
                h.displayed[k] = h.source[k] + weight * delta;
            }
            write(bone, h.displayed, j);
        }
        return blending;
    }

    void restore() {
        for (int i = 0; i < bones.size(); i++) {
            GeoBone bone = bones.get(i);
            write(bone, raw, i * CHANNELS);
            bone.resetStateChanges();
            if ((changed[i] & 1) != 0) bone.markRotationAsChanged();
            if ((changed[i] & 2) != 0) bone.markPositionAsChanged();
            if ((changed[i] & 4) != 0) bone.markScaleAsChanged();
        }
    }

    private static void read(GeoBone b, float[] p, int i) {
        p[i] = b.getRotX(); p[i+1] = b.getRotY(); p[i+2] = b.getRotZ();
        p[i+3] = b.getPosX(); p[i+4] = b.getPosY(); p[i+5] = b.getPosZ();
        p[i+6] = b.getScaleX(); p[i+7] = b.getScaleY(); p[i+8] = b.getScaleZ();
    }

    private static void write(GeoBone b, float[] p, int i) {
        b.updateRotation(p[i], p[i+1], p[i+2]);
        b.updatePosition(p[i+3], p[i+4], p[i+5]);
        b.updateScale(p[i+6], p[i+7], p[i+8]);
    }
}
