package net.jackcooper.shapeShifterCurseAddon.tutorial;

import java.util.UUID;
import net.minecraft.nbt.NbtIo;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

/** Uses the generated GameTest save; never changes a user's world. */
public final class FormTutorialServerProbe {
    public static void start(TestContext context) {
        context.waitAndRun(3, () -> {
            try {
                FormTutorialStateTest.main(new String[0]);
                var player = context.createMockCreativeServerPlayerInWorld();
                var server = context.getWorld().getServer();
                Identifier form = new Identifier("my_addon", "ocelot_wind_spirit");
                FormTutorialState state = FormTutorialState.get(server);
                check(!FormTutorialManager.offer(player, form, false), "A mock/stale session cannot request an offer");
                check(!FormTutorialManager.acknowledge(player, UUID.randomUUID()), "Unsolicited ACK is rejected");
                check(!state.hasSeen(player.getUuid(), form), "Rejected offers and ACKs leave the tutorial available");
                UUID first = UUID.randomUUID(), second = UUID.randomUUID();
                state.markSeen(first, new Identifier("my_addon", "familiar_fox_sp"));
                server.getOverworld().getPersistentStateManager().save();
                var path = server.getSavePath(WorldSavePath.ROOT).resolve("data/ssca_form_tutorials.dat");
                FormTutorialState disk = FormTutorialState.read(NbtIo.readCompressed(path.toFile()).getCompound("data"));
                check(disk.hasSeen(first, new Identifier("my_addon", "familiar_fox_spirit_realm_lord")),
                        "The actual compressed world save stores canonical viewed forms");
                check(!disk.hasSeen(second, new Identifier("my_addon", "familiar_fox_spirit_realm_lord")),
                        "The world save retains independent player records");
                System.out.println("FORM_TUTORIAL_SERVER_PASS: 5 checks; live-session rejection and compressed world-save roundtrip");
                context.complete();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
}
