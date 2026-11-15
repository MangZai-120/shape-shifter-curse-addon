package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

final class MultiplayerSyncWireChecks {
    private static int checks;
    static int run() {
        var key = new CountdownSync.Key(UUID.randomUUID(), new Identifier("my_addon", "test_clock"));
        var state = new CountdownSync.State(key, 17, new CountdownValue(40, 200, 0, true));
        var packet = PacketByteBufs.create();
        try {
            packet.writeVarInt(1); state.write(packet);
            var states = CountdownSync.readStates(packet, 200);
            check(states.size() == 1 && states.get(0).equals(state), "Countdown round trip is exact");
            check(states.get(0).clock().at(210) == 30, "Countdown still predicts at server time");
            check(!packet.isReadable(), "Snapshot consumed completely");
            packet.clear(); packet.writeVarInt(0);
            check(CountdownSync.readStates(packet, 200).isEmpty(), "Empty snapshot clears old predictions");
        } finally { packet.release(); }
        rejected(p -> p.writeVarInt(-1), "Negative count");
        rejected(p -> p.writeVarInt(Integer.MAX_VALUE), "Unbounded claimed count");
        rejected(p -> { p.writeVarInt(1); p.writeUuid(key.owner()); }, "Truncated state");
        rejected(p -> { p.writeVarInt(0); p.writeByte(1); }, "Trailing data");
        rejected(p -> { p.writeVarInt(2); state.write(p); state.write(p); }, "Duplicate resource key");
        rejected(p -> { p.writeVarInt(1); new CountdownSync.State(key, 17,
                new CountdownValue(-1, 200, 0, true)).write(p); }, "Value below resource minimum");
        // Multiple owners and powers share one observer snapshot without colliding.
        packet = PacketByteBufs.create();
        try {
            packet.writeVarInt(256);
            for (int i = 0; i < 256; i++) new CountdownSync.State(
                    new CountdownSync.Key(new UUID(0, i / 8), new Identifier("my_addon", "clock_" + i % 8)),
                    i / 8, new CountdownValue(i, 200, 0, i != 0)).write(packet);
            check(CountdownSync.readStates(packet, 200).size() == 256, "32 owners with 8 clocks retain every entry");
        } finally { packet.release(); }
        return checks;
    }
    private static void rejected(Consumer<PacketByteBuf> write, String name) {
        var packet = PacketByteBufs.create();
        try {
            write.accept(packet);
            try { CountdownSync.readStates(packet, 0); }
            catch (RuntimeException expected) { check(true, name); return; }
            throw new AssertionError("Accepted malformed countdown: " + name);
        } finally { packet.release(); }
    }
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
