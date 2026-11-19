package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/** Cross sections of the travelling wave in its caster's local coordinates. */
public final class OrcafisWallProfile {
    private OrcafisWallProfile() {}
    public static final float BASE = -1.2f, OFFSET_Y = .055f;
    private static final float[] LEVEL = {0,.24f,.60f,.87f,1};
    private static final float[] DEPTH = {2.25f,1.90f,1.02f,.48f,.15f};
    private static final float[] CURL = {0,.06f,.27f,.68f,1.12f};
    public static int bands() { return LEVEL.length; }
    public static float level(int band) { return LEVEL[band]; }
    public static float edge(int band, int side) { return CURL[band] + side * DEPTH[band]; }
    public static float width(float level) { return 6 * (1 - .14f * level); }
    public static float crest(float lateral, float age) {
        return Math.min(3.4f,.45f + age*.53f) + .25f*MathHelper.sin(lateral*1.35f + age*.17f)
                + .13f*MathHelper.sin(lateral*2.2f - age*.12f);
    }
    public static float front(float lateral, float age) {
        return OrcafisSounds.wallDistance(age) - lateral*lateral*.024f;
    }

    static boolean touches(int age, double x, double z, double radius, double baseY, Box body) {
        if (Math.abs(x) > 6 + radius) return false;
        float lateral = MathHelper.clamp((float)x,-6,6);
        double bottom = baseY + OFFSET_Y + BASE;
        double height = crest(lateral,age) - BASE;
        double low = Math.max(0, (body.minY-bottom)/height);
        double high = Math.min(1, (body.maxY-bottom)/height);
        if (low > high) return false;
        float center = front(lateral,age);
        for (int i=0;i<LEVEL.length-1;i++) {
            double a = Math.max(low,LEVEL[i]), b = Math.min(high,LEVEL[i+1]);
            if (a>b || Math.abs(x)>width((float)a)+radius) continue;
            double blend = (a-LEVEL[i]) / (LEVEL[i+1]-LEVEL[i]);
            double left = MathHelper.lerp(blend,edge(i,-1),edge(i+1,-1));
            double right = MathHelper.lerp(blend,edge(i,1),edge(i+1,1));
            if (z+radius>=center+left && z-radius<=center+right) return true;
        }
        return false;
    }
}
