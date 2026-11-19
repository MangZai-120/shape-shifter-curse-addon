package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.util.math.MathHelper;

/** Each downstroke launches one traveling crest across the surface layer. */
public final class OrcafisTidePulse {
    public static final int START = 24, PERIOD = 20, END = START + 100;
    public static final float RANGE = 24, HALF_WIDTH = 1.15f;
    public static final double BODY_DEPTH = 3;

    public static boolean active(float tick) {
        return tick >= START && tick < END;
    }

    public static float radius(float phase) {
        return Math.min(RANGE, phase * RANGE / (PERIOD - 1));
    }

    public static double bob(int tick) {
        if (!active(tick)) return 0;
        int phase = (tick - START) % PERIOD;
        if (phase < 3) return -.42 * MathHelper.sin(phase * MathHelper.HALF_PI / 3);
        if (phase < 15) return MathHelper.lerp(.5 - .5 * Math.cos(Math.PI*(phase-3)/12), -.42, 1.3);
        return 1.3 * (.5 + .5 * Math.cos(Math.PI*(phase-15)/5));
    }
}
