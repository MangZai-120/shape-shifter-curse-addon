package net.jackcooper.shapeShifterCurseAddon.anomaly;

/** Exact timeout, cross-element refresh, independent target and threshold boundaries. */
public final class AnomalyLedgerTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var target = new AnomalyLedger();
        target.hit("ice", 120, 0);
        check(target.value("ice", 399) == 120, "No gradual decay before twenty seconds");
        check(target.remainingTicks(399) == 1, "Last retained tick");
        check(target.value("ice", 400) == 0, "All accumulation clears at twenty seconds");

        target.hit("ice", 120, 1000);
        target.hit("fire", 80, 1399);
        check(target.remainingTicks(1399) == 400, "Another element restarts the full timer");
        check(target.value("ice", 1798) == 120 && target.value("fire", 1798) == 80,
                "Each element is independent under the shared refreshed timer");
        check(target.values(1799).isEmpty(), "Both elements expire together");

        target.hit("ice", 120, 2000);
        target.hit("space", 0, 2399);
        check(target.value("ice", 2798) == 120, "An unconfigured element still refreshes existing accumulation");
        check(!target.values(2798).containsKey("space"), "Zero contribution does not create a fake meter");
        check(target.values(2799).isEmpty(), "Zero-contribution refresh retains the same exact timeout");

        var before = target.hit("ice", 499, 3000);
        check(!before.reachedMaximum(), "Below the threshold cannot activate");
        var full = target.hit("ice", 1, 3001);
        check(full.reachedMaximum() && full.after() == 500, "Exactly five hundred reaches the threshold");
        var capped = target.hit("ice", 9000, 3002);
        check(capped.after() == 500 && !capped.reachedMaximum(), "A full meter is capped without duplicate transitions");
        target.hit("fire", 100, 3002);
        target.clearElement("ice");
        check(target.value("ice", 3002) == 0 && target.value("fire", 3002) == 100,
                "Explicit consumption preserves other elements");
        check(target.hit("ice", 500, 3003).reachedMaximum(), "A consumed meter can reach the threshold again");

        var otherTarget = new AnomalyLedger();
        otherTarget.hit("ice", 35, 3003);
        check(target.value("ice", 3003) == 500 && otherTarget.value("ice", 3003) == 35,
                "Targets never share accumulation");
        check(AnomalyLedger.inLinkWindow(100, 100), "Same-tick independent triggers are within five seconds");
        check(AnomalyLedger.inLinkWindow(100, 199), "Last tick of the link window");
        check(!AnomalyLedger.inLinkWindow(100, 200), "Five seconds ends the link window");
        check(!AnomalyLedger.inLinkWindow(100, 99), "Earlier events cannot link backwards in time");

        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            boolean rejected = false;
            try { otherTarget.hit("ice", invalid, 3004); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Invalid contribution cannot poison target state");
        }
        check(otherTarget.value("ice", 3004) == 35, "Invalid input leaves earlier accumulation intact");
        var display = otherTarget.snapshot(3004);
        check(display.maximum() == 500 && display.remainingTicks() == 399,
                "Display snapshot exposes the authoritative maximum and shared countdown");
        otherTarget.hit("ice", 100, 3005);
        check(display.values().get("ice") == 35, "Later hits cannot mutate an earlier display snapshot");
        boolean immutable = false;
        try { display.values().put("ice", 0.0); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable && otherTarget.value("ice", 3005) == 135, "Display code cannot mutate combat state");
        System.out.println("Anomaly ledger: " + checks + " behavioral checks passed.");
    }
}
