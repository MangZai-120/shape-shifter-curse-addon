package net.jackcooper.shapeShifterCurseAddon.network.visual;

import java.util.random.RandomGenerator;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals.Kind;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals.View;
import net.jackcooper.shapeShifterCurseAddon.network.visual.recipes.MistMeteorDomainRecipes;
import net.jackcooper.shapeShifterCurseAddon.network.visual.recipes.MistFormRecipes;

/** Shared sampling and endpoints; viewers never consume the recipe RNG for local jitter or culling. */
public final class SceneRecipes {
    private SceneRecipes() {}
    public static boolean active(View v, int age) {
        if (age < 0) return false;
        return switch (v.key().kind()) {
            case CORRUPT_MIST -> age >= 1 && age < v.parameter(1);
            case METEOR_WARNING -> age >= 1 && age <= v.duration();
            case DOMAIN_EDGE -> age < v.duration();
            case DOMAIN_AMBIENT -> age < v.duration() && v.parameter(1) + age * v.parameter(2) <= v.parameter(3);
            case MIST_FORM_CHARGE -> age >= v.parameter(3) && age < v.duration();
            default -> v.duration() <= 0 || age < v.duration();
        };
    }
    /** Latest single due sample only. No burst catch-up after lag, late join or a world-clock correction. */
    public static int latestSampleAge(View v, int age) {
        if (!active(v, age)) return -1;
        int period = 1, offset = 0, first = 0;
        switch (v.key().kind()) {
            case CORRUPT_MIST -> { period = 5; offset = Math.floorMod((int)v.parameter(1), 5); first = offset == 0 ? 5 : offset; }
            case BLUE_FIRE_RING -> period = 4;
            case MIST_FORM_CHARGE -> { period = 2; first = (int)v.parameter(3); }
            case DOMAIN_AMBIENT -> {
                period = 5;
                int counter = (int)v.parameter(1), step = (int)v.parameter(2);
                offset = -1;
                for (int i = 0; i < 5; i++) if (Math.floorMod(counter + i * step,5) == 0) { offset = i; break; }
                if (offset < 0) return -1;
                first = offset;
            }
            case METEOR_WARNING -> first = 1;
            default -> { }
        }
        if (age < first) return -1;
        int sample = age - Math.floorMod(age - offset, period);
        return sample >= first && active(v, sample) ? sample : -1;
    }
    public static void emit(View v, int age, long tick, RandomGenerator random, SceneEmitter sink) {
        if (!active(v, age)) return;
        switch (v.key().kind()) {
            case CORRUPT_MIST -> MistMeteorDomainRecipes.mist((long)v.parameter(1)-age,v.parameter(0),random,sink);
            case METEOR_WARNING -> MistMeteorDomainRecipes.meteorWarning(age+(long)v.parameter(1),v.parameter(0),sink);
            case DOMAIN_EDGE -> MistMeteorDomainRecipes.domainEdge(Math.max(0,Math.min(v.parameter(2),v.parameter(0)+age*v.parameter(1))),sink);
            case DOMAIN_AMBIENT -> MistMeteorDomainRecipes.domainAmbient((long)v.parameter(1)+age*(long)v.parameter(2),v.parameter(0),random,sink);
            case MIST_FORM_AMBIENT -> MistFormRecipes.ambient(v.parameter(0),(int)v.parameter(1),sink);
            case MIST_FORM_CHARGE -> MistFormRecipes.charge(age,(int)v.parameter(0),v.parameter(1),(int)v.parameter(2),random::nextDouble,sink);
            default -> BlueTrailRecipes.emit(v.key().kind(),age,tick,v.width(),v.eyeHeight(),v.parameters(),random,sink);
        }
    }
}
