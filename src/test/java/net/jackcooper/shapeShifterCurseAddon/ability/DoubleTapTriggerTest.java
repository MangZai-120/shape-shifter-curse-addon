package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.util.DoubleTapTrigger;

public final class DoubleTapTriggerTest {
    private static int checks;
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        var tap = new DoubleTapTrigger(350);
        check(!tap.update(true, 0), "A single press does not dash");
        check(!tap.update(true, 60), "Holding does not count as a second press");
        check(!tap.update(false, 90), "Release does not dash");
        check(tap.update(true, 180), "Press-release-press triggers once");
        check(!tap.update(true, 220), "Holding after the gesture does not repeat");
        check(!tap.update(false, 230), "Release after a dash does not repeat");
        check(!tap.update(true, 240), "A third press starts a fresh pair");
        tap.update(false, 250);
        check(tap.update(true, 260), "A fourth press completes the new pair");

        var slow = new DoubleTapTrigger(350);
        slow.update(true, 0); slow.update(false, 30);
        check(!slow.update(true, 351), "Slow presses do not trigger");
        slow.update(false, 360);
        check(slow.update(true, 500), "The slow second press can start a new pair");

        var boundary = new DoubleTapTrigger(350);
        boundary.update(true, 10); boundary.update(false, 20);
        check(boundary.update(true, 360), "The 350ms boundary is inclusive");

        var batched = new DoubleTapTrigger(350);
        batched.update(true, 100); batched.update(false, 100);
        check(batched.update(true, 100), "Packets received in the same server tick retain both edges");

        var held = new DoubleTapTrigger(350);
        held.update(true, 0);
        check(!held.update(true, 1000), "A long hold cannot retrigger");
        held.update(false, 1010);
        check(!held.update(true, 1020), "Release after a long hold is a new first press");

        var anotherPlayer = new DoubleTapTrigger(350);
        check(!anotherPlayer.update(true, 260), "Another player's press cannot complete this player's pair");
        var backwards = new DoubleTapTrigger(350);
        backwards.update(true, 200); backwards.update(false, 210);
        check(!backwards.update(true, 100), "A reset clock cannot create a double tap");
        System.out.println("Double-tap input: " + checks + " checks passed.");
    }
}
