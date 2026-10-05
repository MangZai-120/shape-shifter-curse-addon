package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.*;

/** Every natural reference is a neutral foundation, regardless of loot seed or spell level. */
public final class RandomRuneFormationChecks {
    private static int checks;
    private RandomRuneFormationChecks() {}
    private static void check(boolean condition,String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void run() {
        for (int worldSeed = 0; worldSeed < 8; worldSeed++) {
            RuneLanguage language = RuneLanguage.generate(worldSeed);
            int[] originalMapping = language.meanings(), originalRules = language.rules();
            for (var recipe : SlottedSpellRecipes.all()) for (int level = 1; level <= 5; level++) {
                int[] base = RuneLayout.foundation(recipe,level,language);
                Set<String> variations = new HashSet<>();
                for (int seed = 0; seed < 8; seed++) {
                    int[] slots = RandomRuneFormation.generate(recipe,level,language,seed);
                    var evaluation = RuneBuildEvaluator.evaluate(level,slots,language,SlottedSpellRecipes.all());
                    check(evaluation.valid() && evaluation.recipe().equals(recipe), "random result constructs the intended spell");
                    check(evaluation.stability() >= RuneBuildEvaluator.MIN_STABILITY, "random stability respects trial gate");
                    check(Arrays.equals(Arrays.copyOf(slots,RuneLayout.baseSize(level)),
                            Arrays.copyOf(base,RuneLayout.baseSize(level))), "all foundation slots preserved");
                    check(Arrays.equals(slots,RandomRuneFormation.generate(recipe,level,language,seed)), "same seed reproducible");
                    check(Arrays.equals(slots,base)&&Arrays.stream(Arrays.copyOfRange(slots,RuneLayout.baseSize(level),slots.length)).allMatch(v->v==-1),
                            "all natural references have an entirely empty enhancement ring");
                    check(evaluation.stability()==100&&Arrays.stream(evaluation.modifiers().values()).allMatch(v->v==0),
                            "natural formations have no effect, mana, charge-time or cooldown changes");
                    variations.add(Arrays.toString(slots));
                }
                check(variations.size()==1, "loot seeds never introduce enhancement variations");
            }
            check(Arrays.equals(originalMapping,language.meanings())&&Arrays.equals(originalRules,language.rules()),
                    "loot generation never rerandomizes world definitions");
        }
        System.out.println("Random rune formation checks passed: "+checks);
    }
}
