package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.*;
import static net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers.Stat.*;

/** Bounded, deterministic evaluator. Foundation recognition and modifier rules are separate. */
public final class RuneBuildEvaluator {
    public static final int RULE_VERSION = 4, BASE_STABILITY = 100, MIN_STABILITY = 20;
    public record Problem(String rule, int first, int second, int amount) {}
    public record Contribution(RuneModifiers modifiers, int stability, String inactiveReason) {}
    public record Resonance(RuneResonance rule, boolean active, int protection, int edges) {}
    public record Result(SlottedSpellRecipes.Recipe recipe, RuneModifiers modifiers, int stability,
                         List<Problem> problems, BitSet inactive, BitSet synergy, BitSet suppressed,
                         Map<Integer,Contribution> contributions, List<Problem> interactions,
                         RuneModifiers baseModifiers, RuneModifiers interactionModifiers, int interactionStability,
                         List<Resonance> resonances) {
        public boolean valid() { return recipe != null && problems.isEmpty() && stability >= MIN_STABILITY; }
    }
    private RuneBuildEvaluator() {}
    public static Result evaluate(int level, int[] slots, RuneLanguage language, List<SlottedSpellRecipes.Recipe> candidates) {
        List<Problem> problems = new ArrayList<>();
        BitSet inactive = new BitSet(), synergy = new BitSet(), suppressed = new BitSet();
        if (!RuneLayout.validDraft(level, slots))
            return new Result(null, RuneModifiers.NONE, 100, List.of(new Problem("input", -1, -1, 0)), inactive, synergy, suppressed,
                    Map.of(), List.of(), RuneModifiers.NONE, RuneModifiers.NONE, 0, List.of());
        SlottedSpellRecipes.Recipe found = null;
        for (var recipe : candidates) if (RuneLayout.matches(recipe, level, slots, language)) {
            if (found != null) throw new IllegalStateException("Ambiguous rune foundation");
            found = recipe;
        }
        if (found == null) problems.add(new Problem("foundation", -1, -1, 0));
        var capabilities = found == null ? EnumSet.noneOf(RuneModifiers.Stat.class) : RuneCapabilities.of(found.spell());
        RuneRole[] roles = new RuneRole[slots.length];
        for (int i = RuneLayout.baseSize(level); i < slots.length; i++)
            if (slots[i] >= 0) roles[i] = language.meaning(slots[i]);
        int missing = 0, loaded = 0;
        for (int i = RuneLayout.baseSize(level); i < slots.length; i++) {
            if (slots[i] < 0) missing++; else loaded++;
        }
        // Partial drafts remain editable; testing and completion require a whole enhancement ring.
        if (loaded > 0 && missing > 0)
            for (int i = RuneLayout.baseSize(level); i < slots.length; i++)
                if (slots[i] < 0) problems.add(new Problem("outer_incomplete", i, -1, missing));
        BitSet wideConflict = new BitSet(), disableConflict = new BitSet(), gainMergeSynergy = new BitSet();
        List<Problem> interactions = new ArrayList<>();
        List<int[]> edges = RuneLayout.edges(level);
        Map<RuneResonance,List<int[]>> resonanceEdges = new EnumMap<>(RuneResonance.class);
        Set<Problem> protectionHints = new LinkedHashSet<>();
        int[] interactionValues = new int[RuneModifiers.Stat.values().length];
        int extraLoad = 0, cd = 0;
        for (int[] edge : edges) {
            int a = edge[0], b = edge[1]; RuneRole x = roles[a], y = roles[b];
            if (x == null || y == null) continue;
            if (x == y && x != RuneRole.STABLE) problems.add(new Problem("repeat", a, b, 0));
            if (pair(x,y,RuneRole.GAIN,RuneRole.MERGE)) {
                gainMergeSynergy.set(a); gainMergeSynergy.set(b);
                synergy.set(a); synergy.set(b); extraLoad += 6; cd += 8;
                interactions.add(new Problem("gain_merge",a,b,0));
                if (protect(edge, roles, edges, protectionHints)) extraLoad -= 2;
            }
            for (var resonance : RuneResonance.values()) if (resonance.matches(x,y))
                resonanceEdges.computeIfAbsent(resonance, ignored -> new ArrayList<>()).add(edge);
            if (pair(x,y,RuneRole.BRIDGE,RuneRole.SPLIT)) {
                wideConflict.set(a); wideConflict.set(b); suppressed.set(a); suppressed.set(b);
                interactions.add(new Problem("bridge_split",a,b,0));
            }
            if (pair(x,y,RuneRole.DISABLE,RuneRole.GAIN)) {
                disableConflict.set(a); disableConflict.set(b); suppressed.set(a); suppressed.set(b);
                interactions.add(new Problem("disable_gain",a,b,0));
            }
            if (pair(x,y,RuneRole.SPLIT,RuneRole.MERGE)) problems.add(new Problem("split_merge",a,b,0));
            if (pair(x,y,RuneRole.STORE,RuneRole.STABLE)) problems.add(new Problem("store_stable",a,b,0));
            if (pair(x,y,RuneRole.DISABLE,RuneRole.STORE)) problems.add(new Problem("disable_store",a,b,0));
            if (pair(x,y,RuneRole.DISABLE,RuneRole.BRIDGE)) problems.add(new Problem("disable_bridge",a,b,0));
        }
        interactionValues[CD.ordinal()] = cd;
        List<Resonance> resonances = new ArrayList<>();
        for (var entry : resonanceEdges.entrySet()) {
            var rule = entry.getKey(); boolean active = rule.supports(capabilities);
            boolean protectedLoad = false;
            for (int[] edge : entry.getValue()) {
                interactions.add(new Problem(rule.id() + (active ? "" : "_inactive"), edge[0], edge[1], 0));
                if (active) {
                    synergy.set(edge[0]); synergy.set(edge[1]);
                    protectedLoad |= protect(edge, roles, edges, protectionHints);
                }
            }
            int protection = protectedLoad ? 2 : 0;
            resonances.add(new Resonance(rule, active, protection, entry.getValue().size()));
            if (!active) continue;
            extraLoad += rule.load() - protection;
            for (var stat : RuneModifiers.Stat.values()) interactionValues[stat.ordinal()] += rule.modifiers().get(stat);
        }
        interactions.addAll(protectionHints);
        int start = RuneLayout.baseSize(level), count = slots.length - start;
        BitSet tooLong = new BitSet();
        for (int i = start; i < slots.length; i++) if (roles[i] == RuneRole.STABLE) {
            int length = 0;
            while (length < count) {
                int j = i + length;
                if (j >= slots.length) {
                    if (!RuneLayout.closed(level)) break;
                    j = start + (j - start) % count;
                }
                if (roles[j] != RuneRole.STABLE) break;
                length++;
            }
            if (length > 3) for (int k = 0; k < length; k++) tooLong.set(start + (i - start + k) % count);
        }
        if (!tooLong.isEmpty()) {
            for (int i = tooLong.nextSetBit(0); i >= 0; i = tooLong.nextSetBit(i + 1))
                problems.add(new Problem("stable_run", i, -1, 4));
        }
        int[] sums = new int[RuneModifiers.Stat.values().length], baseValues = new int[sums.length];
        int stability = BASE_STABILITY - extraLoad;
        Map<Integer,Contribution> contributions = new HashMap<>();
        for (int i = start; i < slots.length; i++) {
            RuneRole role = roles[i]; if (role == null) continue;
            int beforeStability = stability;
            int[] contribution = new int[sums.length];
            int[] effect = new int[sums.length];
            int[] cost = costs(role); stability -= cost[0]; sums[MANA.ordinal()] += cost[1];
            sums[TIME.ordinal()] += cost[2]; sums[FLAT_MANA.ordinal()]++;
            contribution[MANA.ordinal()] = cost[1]; contribution[TIME.ordinal()] = cost[2];
            contribution[FLAT_MANA.ordinal()] = 1;
            baseValues[MANA.ordinal()] += cost[1]; baseValues[TIME.ordinal()] += cost[2]; baseValues[FLAT_MANA.ordinal()]++;
            switch (role) {
                case GAIN -> effect[POWER.ordinal()] = 10;
                case MERGE -> { effect[POWER.ordinal()] = 12; effect[AREA.ordinal()] = -8; }
                case SOURCE -> effect[SPEED.ordinal()] = 15;
                case SPLIT -> effect[AREA.ordinal()] = 20;
                case BRIDGE -> effect[DISTANCE.ordinal()] = 20;
                case STORE -> effect[DAMAGE.ordinal()] = 20;
                case CONVERT -> effect[NEGATIVE.ordinal()] = 15;
                case INSIGHT -> effect[MARK.ordinal()] = 20;
                case RECOVER -> effect[HEAL.ordinal()] = 12;
                case STABLE -> stability += 12;
                case DISABLE -> {
                    if (capabilities.contains(POWER) || capabilities.contains(AREA) || capabilities.contains(DISTANCE)) {
                        effect[POWER.ordinal()] = -12; effect[AREA.ordinal()] = -12;
                        effect[DISTANCE.ordinal()] = -12; effect[TIME.ordinal()] = -8;
                    }
                }
                default -> {
                    if (found != null && role.ordinal() == found.family()) switch (role) {
                        case FIRE -> effect[BURN.ordinal()] = 20;
                        case ICE -> effect[(capabilities.contains(SHIELD_DURATION) ? SHIELD_DURATION : NEGATIVE).ordinal()] = 15;
                        case LUNAR -> effect[(capabilities.contains(HEAL) ? HEAL : POWER).ordinal()] = 10;
                        case CURSE -> effect[NEGATIVE.ordinal()] = 15;
                        case SUMMON -> effect[(capabilities.contains(SUMMON_DURATION) ? SUMMON_DURATION : ALLY_DURATION).ordinal()] = 15;
                        case VOID -> effect[NEGATIVE.ordinal()] = 20;
                        case SPACE -> effect[DISTANCE.ordinal()] = 10;
                        default -> {}
                    }
                }
            }
            boolean useful = role == RuneRole.STABLE;
            for (var stat : RuneModifiers.Stat.values()) {
                int value = effect[stat.ordinal()];
                if (value == 0 || stat != TIME && !capabilities.contains(stat)) continue;
                baseValues[stat.ordinal()] += value;
                useful = true; long numerator = value, denominator = 1;
                if (gainMergeSynergy.get(i) && stat == POWER && value > 0) { numerator *= 5; denominator *= 4; }
                if (wideConflict.get(i) && value > 0) { numerator *= 3; denominator *= 4; }
                if (disableConflict.get(i)) { numerator *= 1; denominator *= 2; }
                int valueAfterInteraction = RuneModifiers.rounded(numerator, denominator);
                sums[stat.ordinal()] += valueAfterInteraction;
                contribution[stat.ordinal()] += valueAfterInteraction;
            }
            if (!useful) inactive.set(i);
            String reason = useful ? "" : found == null ? "foundation"
                    : role == RuneRole.DISABLE ? "disable"
                    : role.element() && role.ordinal() != found.family() ? "element" : "unsupported";
            contributions.put(i,new Contribution(new RuneModifiers(contribution),stability-beforeStability,reason));
        }
        for (var stat : RuneModifiers.Stat.values()) sums[stat.ordinal()] += interactionValues[stat.ordinal()];
        if (stability < MIN_STABILITY) problems.add(new Problem("stability", -1, -1, MIN_STABILITY - stability));
        return new Result(found, new RuneModifiers(sums), stability, List.copyOf(problems), inactive, synergy, suppressed,
                Map.copyOf(contributions),List.copyOf(interactions),new RuneModifiers(baseValues),
                new RuneModifiers(interactionValues),-extraLoad,List.copyOf(resonances));
    }
    /** One discount per charged edge/package; both directions and the closing seam use the same adjacency graph. */
    private static boolean protect(int[] charged, RuneRole[] roles, List<int[]> edges, Set<Problem> hints) {
        boolean protectedLoad = false;
        for (int endpoint : charged) {
            if (roles[endpoint] != RuneRole.GAIN && roles[endpoint] != RuneRole.MERGE) continue;
            for (int[] edge : edges) {
                int other = edge[0] == endpoint ? edge[1] : edge[1] == endpoint ? edge[0] : -1;
                if (other < 0 || roles[other] != RuneRole.STABLE) continue;
                hints.add(new Problem("stable_protection",other,endpoint,2)); protectedLoad = true;
            }
        }
        return protectedLoad;
    }
    private static boolean pair(RuneRole a,RuneRole b,RuneRole x,RuneRole y) { return a == x && b == y || a == y && b == x; }
    public static int[] costs(RuneRole role) {
        return switch (role) {
            case SOURCE -> new int[]{8,6,2}; case GAIN -> new int[]{12,8,2};
            case STABLE -> new int[]{2,2,1}; case SPLIT -> new int[]{14,10,4};
            case MERGE -> new int[]{14,10,3}; case BRIDGE -> new int[]{12,8,3};
            case CONVERT -> new int[]{10,8,3}; case STORE -> new int[]{18,12,8};
            case INSIGHT -> new int[]{8,6,2}; case RECOVER -> new int[]{10,8,3};
            case DISABLE -> new int[]{4,0,0}; case VOID -> new int[]{12,8,3};
            default -> new int[]{10,6,2};
        };
    }
}
