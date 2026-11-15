package net.jackcooper.shapeShifterCurseAddon.network;

import java.nio.ByteBuffer;
import java.util.*;
import net.jackcooper.shapeShifterCurseAddon.balance.*;
import static net.jackcooper.shapeShifterCurseAddon.network.ClientConfigurationSession.Part.*;

/** Drives the complete transfer interface used by the Fabric configuration receivers. */
public final class ClientConfigurationSessionTest {
    private static int checks;
    public static int run() {
        var schema = BalanceSchema.create().build();
        var server = new ServerBalanceState(schema); server.commitDefaults();
        UUID player = UUID.randomUUID(); server.playerJoined(player);
        String json = "{\"schema_version\":1}";
        var sequence = server.buildSendSequence(player, json);
        var header = ((BalanceWire.Hello) sequence.get(0)).header();
        var applied = new EnumMap<JsonSnapshotWire.Kind, Map<String, String>>(JsonSnapshotWire.Kind.class);
        var completions = new ArrayList<ClientConfigurationSession.Completion>();
        var client = new ClientConfigurationSession(schema, applied::put, completions::add);
        begin(client, header);
        check(!client.isReady() && client.snapshot() == null, "BEGIN cannot publish or mark ready");
        registries(client);
        sequence.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(client.isReady() && client.snapshot().hash().equals(server.current().hash()), "Full configuration publishes exact balance");
        check(applied.size() == 2 && completions.size() == 1, "Both real registry adapters run before completion");
        check(completions.get(0).refreshAppearance(), "First completed session refreshes appearance");
        client.accept(BALANCE, BalanceWire.encode(sequence.get(sequence.size() - 1)));
        check(completions.size() == 1, "Duplicate COMMIT is idempotent");
        begin(client, header); registries(client);
        sequence.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(completions.size() == 2 && !completions.get(1).refreshAppearance(), "Retry ACKs again without duplicate appearance upload");
        server.playerAcked(player, completions.get(1).session(), completions.get(1).revision());
        check(server.isPlayerReady(player), "Completion identity satisfies the server ACK gate");

        server.playerJoined(player);
        var reload = server.buildSendSequence(player, json);
        var newer = ((BalanceWire.Hello) reload.get(0)).header();
        begin(client, newer);
        check(!client.isReady(), "Spell-only reload revokes readiness at BEGIN before balance HELLO");
        var spells = Map.of("updated", "{\"test\":2}");
        for (var frame : JsonSnapshotWire.frames(JsonSnapshotWire.Kind.SPELLS, 10, JsonSnapshotWire.encode(spells))) client.accept(CHUNKS, frame);
        for (var frame : JsonSnapshotWire.frames(JsonSnapshotWire.Kind.EVOLUTION, 11, JsonSnapshotWire.encode(Map.of()))) client.accept(CHUNKS, frame);
        reload.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(client.isReady() && applied.get(JsonSnapshotWire.Kind.SPELLS).equals(spells), "Chunked registries share the complete confirmation flow");
        check(completions.get(2).refreshAppearance(), "New configuration session refreshes appearance");
        begin(client, header);
        sequence.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(client.isReady() && completions.size() == 3, "Stale BEGIN and balance cannot replace confirmed session");

        client.clear();
        check(!client.isReady() && client.snapshot() == null, "Reconnect discards every transfer and published balance");
        sequence.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(client.isReady() && completions.get(3).refreshAppearance(), "Legacy server without BEGIN remains compatible after reconnect");

        for (var missing : List.of(SPELLS, EVOLUTION)) {
            client.clear(); begin(client, header);
            client.accept(missing == SPELLS ? EVOLUTION : SPELLS, JsonSnapshotWire.encode(Map.of()));
            int before = completions.size();
            rejects(() -> sequence.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m))), "Missing " + missing);
            check(!client.isReady() && client.snapshot() == null && completions.size() == before, "Partial registries never publish or ACK");
        }
        var broken = new ClientConfigurationSession(schema, (kind, content) -> { throw new IllegalArgumentException("registry rejected"); }, completions::add);
        begin(broken, header);
        rejects(() -> broken.accept(SPELLS, JsonSnapshotWire.encode(Map.of())), "Registry adapter failure propagates");
        rejects(() -> broken.accept(BALANCE, BalanceWire.encode(sequence.get(0))), "Failed connection cannot resume without clear");
        check(!broken.isReady() && broken.snapshot() == null, "Registry failure invalidates complete session");
        client.clear(); begin(client, header); registries(client);
        client.accept(BALANCE, BalanceWire.encode(sequence.get(0)));
        rejects(() -> client.accept(BALANCE, BalanceWire.encode(sequence.get(sequence.size() - 1))), "Missing balance chunks");
        client.clear(); begin(client, header); registries(client);
        sequence.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(client.isReady(), "A new connection recovers cleanly after failed transfer");
        scopedSessions();
        System.out.println("Configuration session PASS checks=" + checks);
        return checks;
    }
    private static void scopedSessions() {
        var schema = BalanceSchema.create().build();
        var server = new ServerBalanceState(schema); server.commitDefaults();
        UUID player = UUID.randomUUID(); server.playerJoined(player);
        var old = server.buildSendSequence(player, "{\"schema_version\":1}");
        var first = ((BalanceWire.Hello) old.get(0)).header();
        server.playerJoined(player);
        var next = server.buildSendSequence(player, "{\"schema_version\":1}");
        var second = ((BalanceWire.Hello) next.get(0)).header();
        var applied = new EnumMap<JsonSnapshotWire.Kind, Map<String, String>>(JsonSnapshotWire.Kind.class);
        var done = new ArrayList<ClientConfigurationSession.Completion>();
        var client = new ClientConfigurationSession(schema, applied::put, done::add);
        var fresh = Map.of("spell", "new");
        var stale = Map.of("spell", "old");
        scoped(client, first, JsonSnapshotWire.Kind.SPELLS, 1, stale);
        check(applied.isEmpty(), "Unannounced scoped data cannot apply");
        beginScoped(client, first);
        var incomplete = JsonSnapshotWire.frames(JsonSnapshotWire.Kind.SPELLS, 500,
                JsonSnapshotWire.encode(Map.of("spell", "x".repeat(70000))));
        client.accept(CHUNKS_SCOPED, JsonSnapshotWire.scope(first.sessionId(), first.revision(), incomplete.get(0)));
        client.accept(BALANCE, BalanceWire.encode(old.get(0)));
        beginScoped(client, second);
        check(!client.isReady() && applied.isEmpty(), "New BEGIN discards incomplete work and revokes readiness");
        for (int i = 1; i < incomplete.size(); i++)
            client.accept(CHUNKS_SCOPED, JsonSnapshotWire.scope(first.sessionId(), first.revision(), incomplete.get(i)));
        scoped(client, first, JsonSnapshotWire.Kind.SPELLS, 999, stale);
        var frame = JsonSnapshotWire.frames(JsonSnapshotWire.Kind.SPELLS, 1000, JsonSnapshotWire.encode(stale)).get(0);
        client.accept(CHUNKS_SCOPED, JsonSnapshotWire.scope(second.sessionId(), second.revision() + 1, frame));
        client.accept(CHUNKS_SCOPED, JsonSnapshotWire.scope(second.sessionId() + 1, second.revision(), frame));
        registries(client);
        client.accept(CHUNKS, frame);
        begin(client, second); // A v1 announcement must not downgrade the connection.
        registries(client);
        old.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(applied.isEmpty() && !client.isReady() && done.isEmpty(), "Stale, future, wrong revision and unscoped frames cannot populate v2");
        scoped(client, second, JsonSnapshotWire.Kind.SPELLS, 1, fresh);
        scoped(client, second, JsonSnapshotWire.Kind.SPELLS, 2, stale);
        check(applied.get(JsonSnapshotWire.Kind.SPELLS).equals(fresh), "Only one snapshot per registry applies in a transaction");
        scoped(client, second, JsonSnapshotWire.Kind.EVOLUTION, 1, Map.of());
        next.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(client.isReady() && done.size() == 1 && applied.size() == 2, "Scoped configuration commits despite unrelated stale traffic");
        scoped(client, second, JsonSnapshotWire.Kind.SPELLS, 3, stale);
        registries(client); client.accept(CHUNKS, frame);
        next.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(client.isReady() && done.size() == 1 && applied.get(JsonSnapshotWire.Kind.SPELLS).equals(fresh),
                "Committed window ignores registry overwrites and balance replay without a new BEGIN");
        beginScoped(client, second);
        check(!client.isReady(), "Same-session retry explicitly reopens the receive window");
        scoped(client, second, JsonSnapshotWire.Kind.SPELLS, 1, fresh);
        scoped(client, second, JsonSnapshotWire.Kind.EVOLUTION, 1, Map.of());
        next.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(done.size() == 2 && !done.get(1).refreshAppearance(), "Retry reuses transfer IDs and ACKs without duplicate appearance refresh");

        client.clear(); applied.clear(); begin(client, first); registries(client);
        old.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        var legacyValue = applied.get(JsonSnapshotWire.Kind.SPELLS);
        client.accept(SPELLS, JsonSnapshotWire.encode(stale)); client.accept(CHUNKS, frame);
        check(client.isReady() && applied.get(JsonSnapshotWire.Kind.SPELLS).equals(legacyValue),
                "Announced v1 compatibility also seals the committed window (independent D2 regression)");
        client.clear(); applied.clear();
        client.accept(SPELLS, JsonSnapshotWire.encode(stale));
        old.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        client.accept(SPELLS, JsonSnapshotWire.encode(fresh));
        check(client.isReady() && applied.get(JsonSnapshotWire.Kind.SPELLS).equals(fresh), "Explicit no-BEGIN legacy mode preserves historical registry updates");

        client.clear(); beginScoped(client, first);
        rejects(() -> client.accept(CHUNKS_SCOPED, new byte[16]), "Truncated scoped envelope");
        check(!client.isReady() && client.snapshot() == null, "Malformed active transaction cannot remain ready");
        client.clear(); beginScoped(client, first);
        rejects(() -> client.accept(BEGIN_SCOPED, ByteBuffer.allocate(16).putLong(first.sessionId()).putLong(first.revision() + 1).array()),
                "One session cannot change revision");

        // Even a same-session retry cannot reuse an incomplete balance transfer from before BEGIN.
        client.clear(); beginScoped(client, first);
        for (int i = 0; i < old.size() - 1; i++) client.accept(BALANCE, BalanceWire.encode(old.get(i)));
        beginScoped(client, first);
        scoped(client, first, JsonSnapshotWire.Kind.SPELLS, 1, fresh);
        scoped(client, first, JsonSnapshotWire.Kind.EVOLUTION, 1, Map.of());
        int before = done.size();
        client.accept(BALANCE, BalanceWire.encode(old.get(old.size() - 1)));
        check(!client.isReady() && done.size() == before, "BEGIN discards incomplete balance bytes as well as registry bytes");
        old.forEach(m -> client.accept(BALANCE, BalanceWire.encode(m)));
        check(client.isReady(), "A complete retry recovers after a premature COMMIT");
    }
    private static void beginScoped(ClientConfigurationSession client, BalanceWire.SnapshotHeader header) {
        client.accept(BEGIN_SCOPED, ByteBuffer.allocate(16).putLong(header.sessionId()).putLong(header.revision()).array());
    }
    private static void scoped(ClientConfigurationSession client, BalanceWire.SnapshotHeader header,
                               JsonSnapshotWire.Kind kind, long transfer, Map<String, String> content) {
        for (var frame : JsonSnapshotWire.frames(kind, transfer, JsonSnapshotWire.encode(content)))
            client.accept(CHUNKS_SCOPED, JsonSnapshotWire.scope(header.sessionId(), header.revision(), frame));
    }
    private static void begin(ClientConfigurationSession client, BalanceWire.SnapshotHeader header) {
        client.accept(BEGIN, ByteBuffer.allocate(16).putLong(header.sessionId()).putLong(header.revision()).array());
    }
    private static void registries(ClientConfigurationSession client) {
        client.accept(SPELLS, JsonSnapshotWire.encode(Map.of("spell", "{}")));
        client.accept(EVOLUTION, JsonSnapshotWire.encode(Map.of("route", "{}")));
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { check(true, message); return; }
        throw new AssertionError("Expected rejection: " + message);
    }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
