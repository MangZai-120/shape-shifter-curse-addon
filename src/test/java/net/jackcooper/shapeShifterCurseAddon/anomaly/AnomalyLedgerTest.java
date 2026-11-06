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
        fullDisplay();
        System.out.println("Anomaly ledger: " + checks + " behavioral checks passed.");
    }

    private static void fullDisplay() {
        var ledger = new AnomalyLedger();
        ledger.hit("fire", 500, 0);
        ledger.holdMaximum("fire", 0);
        check(ledger.value("fire", 0) == 500 && ledger.fullRemainingTicks(0) == 100, "A triggered meter stays full for five seconds");
        check(ledger.remainingTicks(0) == 0, "Full display does not receive the twenty-second accumulation timer");
        ledger.hit("curse", 125, 10);
        check(ledger.hit("fire", 9000, 50).after() == 500 && !ledger.hit("fire", 500, 50).reachedMaximum(),
                "Locked full meters reject repeat triggers and overflow");
        check(ledger.fullRemainingTicks(50) == 50, "Repeated hits do not refresh the full display");
        ledger.hit("ice", 499, 60);
        check(ledger.fullRemainingTicks(60) == 40, "Partial buildup in another element does not extend the full group");
        ledger.hit("ice", 1, 75);
        ledger.holdMaximum("ice", 75);
        var display = ledger.snapshot(75);
        check(display.fullRemainingTicks() == 100 && display.fullElements().equals(java.util.Set.of("fire", "ice")),
                "A new trigger refreshes all visible full meters together");
        check(ledger.value("fire", 174) == 500 && ledger.value("ice", 174) == 500 && ledger.fullRemainingTicks(174) == 1,
                "Both full bars survive to the exact last shared tick");
        check(ledger.value("fire", 175) == 0 && ledger.value("ice", 175) == 0 && ledger.fullRemainingTicks(175) == 0,
                "Both full bars disappear together at the five-second boundary");
        check(ledger.value("curse", 175) == 125, "Full expiry preserves unrelated partial buildup");
        check(ledger.hit("fire", 250, 175).after() == 250, "Buildup resumes immediately after full expiry");
        check(display.values().get("fire") == 500 && display.fullElements().contains("fire"), "Earlier full snapshots remain immutable");
        check(ledger.hit("fire", 250, 176).reachedMaximum(), "A released element can trigger normally again");
        ledger.holdMaximum("fire", 176);
        ledger.clearElement("fire");
        check(ledger.fullRemainingTicks(176) == 0 && ledger.value("curse", 176) == 125, "Explicit element reset also releases its full lock");
        ledger.clear();
        check(ledger.values(176).isEmpty() && ledger.fullRemainingTicks(176) == 0, "Target cleanup releases all full display state");

        var state = new AnomalyTargetState(null, null);
        check(state.activate("fire", 0).isEmpty(), "The first trigger opens an independent link window");
        check(state.activate("ice", 50).equals("fire"), "The first two elements still produce their normal reaction");
        check(state.linkRemaining(50) == 0, "Consumed reaction partners expose no second link opportunity");
        check(state.activate("space", 60).isEmpty(), "A third element cannot reuse either locked reaction partner");
        check(state.activate("curse", 70).equals("space"), "Two fresh elements can still react while an older pair is locked");
        check(state.activate("fire", 200).isEmpty(), "An expired pair cannot leak into the next buildup cycle");
    }
}
