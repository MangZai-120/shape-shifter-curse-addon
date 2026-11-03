package net.ssca.neomagic;

import net.minecraft.test.TestContext;

/** Runs the wire suite against the actual server HUD builder, catching schema drift. */
public final class NeoHudNetworkWorldProbe {
    private NeoHudNetworkWorldProbe() {}
    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            var player = test.createMockCreativeServerPlayerInWorld();
            int checks = NeoHudNetworkChecks.run(NeoNetwork.hudState(player));
            System.out.println("HUD real-world schema checks=" + checks);
            test.complete();
        });
    }
}
