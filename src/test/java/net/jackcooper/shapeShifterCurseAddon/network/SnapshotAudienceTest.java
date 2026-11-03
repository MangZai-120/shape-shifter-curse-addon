package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.UUID;

/** Observer histories include replacement connections and late disconnect callbacks. */
public final class SnapshotAudienceTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var audience = new SnapshotAudience();
        UUID[] players = new UUID[20]; Object[] connections = new Object[20];
        int sent = 0;
        for (int i = 0; i < 20; i++) { players[i] = new UUID(0,i+1); connections[i] = new Object(); }
        for (int tick = 0; tick < 60; tick++) for (int i = 0; i < 20; i++) {
            boolean visible = i < 3;
            boolean send = audience.shouldSend(players[i], connections[i], visible);
            check(send == visible, "Never-visible observers do not receive repeated empty snapshots");
            if (send) { audience.sent(players[i], connections[i], visible); sent++; }
        }
        check(sent == 180 && audience.size() == 3, "Visible observers retain every original calibration");
        check(audience.shouldSend(players[0], connections[0], false), "Leaving range sends an immediate clear");
        audience.sent(players[0], connections[0], false);
        check(!audience.shouldSend(players[0], connections[0], false), "A cleared observer receives no further empty tables");
        check(audience.shouldSend(players[0], connections[0], true), "Entering range or a new dimension receives the current state");
        audience.sent(players[0], connections[0], true);
        Object replacement = new Object();
        check(!audience.shouldSend(players[0], replacement, false), "A replacement connection has no inherited client mirror");
        check(audience.shouldSend(players[0], replacement, true), "A replacement connection receives visible state");
        audience.sent(players[0], replacement, true);
        audience.disconnect(players[0], connections[0]);
        check(audience.shouldSend(players[0], replacement, false), "Delayed old disconnect cannot erase the new connection history");
        audience.sent(players[0], replacement, false);
        check(!audience.shouldSend(players[0], replacement, false), "New connection clears exactly once");
        audience.disconnect(players[1], connections[1]);
        check(audience.size() == 1, "Disconnected recipients release their history");
        audience.clear();
        check(audience.size() == 0 && !audience.shouldSend(players[2], connections[2], false), "Stopping a server clears all histories");
        System.out.println("Snapshot audience lifecycle PASS: " + checks + " checks");
    }
}
