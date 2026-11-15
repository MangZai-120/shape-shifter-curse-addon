package net.jackcooper.shapeShifterCurseAddon.network;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceSchema;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceSnapshot;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceWire;
import net.jackcooper.shapeShifterCurseAddon.balance.ClientBalanceState;

/** One connection's configuration transfer, from wire bytes through registry application and ACK eligibility. */
public final class ClientConfigurationSession {
    public enum Part { BEGIN, BEGIN_SCOPED, SPELLS, EVOLUTION, CHUNKS, CHUNKS_SCOPED, BALANCE }
    public record Completion(long session, long revision, boolean refreshAppearance) {}
    private final ClientBalanceState balance;
    private final JsonSnapshotWire.Receiver chunks = new JsonSnapshotWire.Receiver();
    private final EnumSet<JsonSnapshotWire.Kind> applied = EnumSet.noneOf(JsonSnapshotWire.Kind.class);
    private final BiConsumer<JsonSnapshotWire.Kind, Map<String, String>> applyRegistry;
    private final Consumer<Completion> completed;
    private long session = -1, revision = -1, appearanceSession = -1;
    private boolean ready, failed, scoped, receiving;
    private BalanceSnapshot published;

    public ClientConfigurationSession(BalanceSchema schema,
            BiConsumer<JsonSnapshotWire.Kind, Map<String, String>> applyRegistry, Consumer<Completion> completed) {
        balance = new ClientBalanceState(schema, null);
        this.applyRegistry = applyRegistry;
        this.completed = completed;
    }

    public void accept(Part part, byte[] bytes) {
        if (failed) throw new IllegalStateException("Configuration connection has failed");
        try {
            switch (part) {
                case BEGIN, BEGIN_SCOPED -> {
                    if (scoped && part == Part.BEGIN) return; // Never downgrade an established v2 connection.
                    if (bytes.length != 16) throw new IllegalArgumentException("Invalid configuration announcement");
                    var input = ByteBuffer.wrap(bytes);
                    long nextSession = input.getLong(), nextRevision = input.getLong();
                    if (nextSession < 0 || nextRevision < 0) throw new IllegalArgumentException("Invalid configuration session");
                    if (nextSession < session) return;
                    if (nextSession == session && nextRevision != revision)
                        throw new IllegalArgumentException("Conflicting configuration revision");
                    if (nextRevision < revision) throw new IllegalArgumentException("Configuration revision regressed");
                    scoped = part == Part.BEGIN_SCOPED;
                    session = nextSession; revision = nextRevision;
                    chunks.clear(); applied.clear(); balance.suspendTransfer(); ready = false; receiving = true;
                }
                case SPELLS, EVOLUTION -> {
                    if (scoped || session >= 0 && !receiving) return;
                    apply(part == Part.SPELLS ? JsonSnapshotWire.Kind.SPELLS : JsonSnapshotWire.Kind.EVOLUTION,
                            JsonSnapshotWire.decode(bytes));
                }
                case CHUNKS -> {
                    if (scoped || session >= 0 && !receiving) return;
                    chunk(bytes);
                }
                case CHUNKS_SCOPED -> {
                    if (!scoped || !receiving) return;
                    var frame = JsonSnapshotWire.unScope(bytes);
                    if (frame.session() != session || frame.revision() != revision) return;
                    chunk(frame.frame());
                }
                case BALANCE -> balance(BalanceWire.decode(bytes));
            }
        } catch (RuntimeException invalid) {
            failed = true; ready = false; published = null;
            balance.exitConnection(); chunks.clear(); applied.clear();
            throw invalid;
        }
    }

    private void apply(JsonSnapshotWire.Kind kind, Map<String, String> content) {
        if (session >= 0 && applied.contains(kind)) return;
        applyRegistry.accept(kind, content);
        applied.add(kind); // A throwing registry adapter must never earn an ACK.
    }

    private void chunk(byte[] bytes) {
        var received = chunks.offer(bytes);
        if (received != null) apply(received.kind(), received.content());
    }

    private void balance(BalanceWire.Message message) {
        if (session >= 0 && !receiving) return;
        BalanceWire.SnapshotHeader header = message instanceof BalanceWire.Hello hello ? hello.header()
                : message instanceof BalanceWire.Commit commit ? commit.header() : null;
        if (header != null && session >= 0) {
            if (header.sessionId() < session) return;
            if (header.sessionId() != session || header.revision() != revision)
                throw new IllegalArgumentException("Configuration/balance session mismatch");
            if (message instanceof BalanceWire.Commit && applied.size() != JsonSnapshotWire.Kind.values().length)
                throw new IllegalArgumentException("Required spell or evolution snapshot was not applied");
        }
        var outcome = balance.offer(message);
        if (message instanceof BalanceWire.Hello) ready = balance.isReady();
        if (outcome instanceof ClientBalanceState.Rejected rejected)
            throw new IllegalArgumentException(rejected.reason());
        if (outcome instanceof ClientBalanceState.Committed committed) {
            published = committed.committed(); ready = true;
            receiving = false; chunks.clear();
            boolean refresh = appearanceSession != committed.sessionId();
            appearanceSession = committed.sessionId();
            completed.accept(new Completion(committed.sessionId(), committed.revision(), refresh));
        }
    }

    public BalanceSnapshot snapshot() { return published; }
    public boolean isReady() { return ready; }
    public void clear() {
        balance.exitConnection(); chunks.clear(); applied.clear();
        session = revision = appearanceSession = -1;
        published = null; ready = failed = scoped = receiving = false;
    }
}
