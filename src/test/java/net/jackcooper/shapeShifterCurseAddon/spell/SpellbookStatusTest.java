package net.jackcooper.shapeShifterCurseAddon.spell;

import io.netty.buffer.Unpooled;
import net.jackcooper.shapeShifterCurseAddon.network.SpellbookStatusSync;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

/** Deadline boundaries and real wire round-trips, without starting a game. */
public final class SpellbookStatusTest {
    public static void main(String[] args) {
        SpellbookStatus waiting = new SpellbookStatus(1140, 1060, 1016, false, 5);
        for (long now = 1000; now <= 1180; now++) {
            boolean serverAllows = SpellCastingRules.naturalRegenAllowed(false, now - 1000, 140);
            check(waiting.naturalRegenAllowed(now) == serverAllows, "HUD and server recovery gate at " + now);
        }
        check(waiting.naturalRegenWait(1139) == 1 && waiting.naturalRegenWait(1140) == 0, "exact recovery boundary");
        check(waiting.swapWait(1059) == 1 && waiting.swapWait(1060) == 0, "exact swap boundary");
        check(waiting.castWait(1015) == 1 && waiting.castWait(1016) == 0, "exact global interval boundary");
        SpellbookStatus casting = new SpellbookStatus(1140, 0, 0, true, 5);
        check(!casting.naturalRegenAllowed(2000), "long cast still pauses recovery after the delay expires");
        SpellbookStatus completed = new SpellbookStatus(2140, 0, 2016, false, 5);
        check(!completed.naturalRegenAllowed(2139) && completed.naturalRegenAllowed(2140), "completion resets the wait");
        SpellbookStatus interrupted = new SpellbookStatus(1140, 0, 0, false, 5);
        check(interrupted.naturalRegenAllowed(2000), "interruption preserves the original spend deadline");
        check(new SpellbookStatus(0, 0, 0, false, 3).naturalRegenAllowed(0), "new player has no artificial delay");

        for (var status : new SpellbookStatus[]{waiting, casting, completed, interrupted, null}) {
            var snapshot = new SpellbookStatusSync.Snapshot(new Identifier("minecraft", "overworld"), 42, 1000, status);
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            try {
                snapshot.write(buf);
                check(snapshot.equals(SpellbookStatusSync.Snapshot.read(buf)), "snapshot wire round-trip including removal");
                check(!buf.isReadable(), "snapshot consumes exactly its own payload");
            } finally { buf.release(); }
        }
        check("0.1".equals(SpellCastFeedback.seconds(1)), "a pending one-tick gate must not display zero");
        check("7.0".equals(SpellCastFeedback.seconds(140)), "default recovery duration");
        check("message.ssc_addon.spellbook.capacity_insufficient".equals(key(120, 300, 450)), "capacity cannot be fixed by waiting");
        check("message.ssc_addon.spellbook.no_mana_details".equals(key(120, 900, 450)), "current mana shortage has a distinct explanation");
        check("message.ssc_addon.spellbook.cost_unavailable".equals(key(120, 900, -1)), "missing configuration is not zero cost or low mana");
        System.out.println("Spellbook status passed: recovery/casting/swap/GCD boundaries, wire snapshots and precise shortage reasons.");
    }

    private static String key(int mana, int capacity, int cost) {
        return ((TranslatableTextContent) SpellCastFeedback.manaFailure(mana, capacity, cost).getContent()).getKey();
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
