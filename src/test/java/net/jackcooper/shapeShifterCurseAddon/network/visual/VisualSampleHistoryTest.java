package net.jackcooper.shapeShifterCurseAddon.network.visual;

import java.util.UUID;

/** Regression for a real three-client dimension-load stall, plus identity and memory bounds. */
public final class VisualSampleHistoryTest {
    private static int checks;
    private static final UUID FIRST = new UUID(1, 2), SECOND = new UUID(3, 4);

    public static void main(String[] args) {
        stalledLease();
        sourceIdentity();
        capacityAndClear();
        System.out.println("VisualSampleHistoryTest: " + checks + " checks");
    }

    private static void stalledLease() {
        var history = new VisualSampleHistory<String>(20);
        for (int source = 0; source < 20; source++) history.retain("scene" + source, FIRST, source, 1000, 60);
        // Native failure: lease expires at local tick 61; server resumes at scene age 21.
        // A new full snapshot must restore the old watermark, rather than emit ages 20..60 twice.
        for (int source = 0; source < 20; source++) {
            int last = history.resume("scene" + source, FIRST, source, 1000);
            check(last == 60, "Same cast restores its sampled watermark");
            int emitted = 0;
            for (int serverAge = 21; serverAge <= 90; serverAge++) {
                if (serverAge <= last) continue;
                last = serverAge; emitted++;
            }
            check(emitted == 30, "Only new ages 61 through 90 emit after recovery");
        }
        check(history.size() == 0, "Recovered checkpoints are consumed");
        history.retain("scene", FIRST, 1, 1000, 60);
        history.retain("scene", FIRST, 1, 1000, 40);
        check(history.resume("scene", FIRST, 1, 1000) == 60, "A repeated expiry cannot move an exact cast watermark backwards");
    }

    private static void sourceIdentity() {
        var history = new VisualSampleHistory<String>(4);
        history.retain("scene", FIRST, 7, 1000, 60);
        check(history.resume("scene", FIRST, 7, 1001) == -1, "A new cast epoch starts normally");
        history.retain("scene", FIRST, 7, 1000, 60);
        check(history.resume("scene", SECOND, 7, 1000) == -1, "A reused entity ID cannot inherit a different entity's samples");
        history.retain("scene", FIRST, 7, 1000, 60);
        check(history.resume("scene", FIRST, 8, 1000) == -1, "A source replacement with another entity ID starts normally");
        check(history.resume("absent", FIRST, 7, 1000) == -1, "First-time or late observers begin from their current sample");
        history.retain("idle", FIRST, 7, 1000, -1);
        check(history.size() == 0, "Never sampled scenes need no checkpoint");
        history.retain("scene", FIRST, 7, 1000, 60);
        check(history.resume("other-kind", FIRST, 7, 1000) == -1, "Distinct visual keys do not share a watermark");
        history.clear();
    }

    private static void capacityAndClear() {
        var history = new VisualSampleHistory<Integer>(32);
        for (int i = 0; i < 10000; i++) {
            history.retain(i, FIRST, i, i, i);
            check(history.size() <= 32, "Expired casts have a hard memory bound");
        }
        check(history.resume(0, FIRST, 0, 0) == -1, "Oldest expired cast is evicted");
        check(history.resume(9999, FIRST, 9999, 9999) == 9999, "Newest expired cast remains resumable");
        history.retain(1, FIRST, 1, 1, 10);history.retain(2, FIRST, 2, 2, 20);
        history.clear();check(history.size() == 0, "Disconnect or dimension clear removes all checkpoints");
        check(history.resume(1, FIRST, 1, 1) == -1, "A fresh world does not inherit the old world's watermark");
    }

    private static void check(boolean value, String message) {checks++;if(!value)throw new AssertionError(message);}
}
