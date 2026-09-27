package net.jackcooper.shapeShifterCurseAddon.balance;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** One connection's transactional mirror. Never publishes incomplete or older snapshots. */
public final class ClientBalanceState {
    public sealed interface Outcome permits Committed, Rejected {}
    public record Committed(BalanceSnapshot committed, BalanceSnapshot previous, long revision, long sessionId) implements Outcome {}
    public record Rejected(String reason, boolean requestResync) implements Outcome {}
    private final BalanceSchema schema;
    private BalanceSnapshot current;
    private boolean ready;
    private BalanceWire.SnapshotHeader pending;
    private final Map<Integer, byte[]> chunks = new HashMap<>();
    private long revision = Long.MIN_VALUE;
    private long latestSession = Long.MIN_VALUE;
    private int receivedBytes;

    public ClientBalanceState(BalanceSchema schema, BalanceSnapshot initial) {
        this.schema = schema;
        current = initial;
        ready = initial != null;
    }
    public BalanceSnapshot current() { return current; }
    public boolean isReady() { return ready; }

    public Outcome offer(BalanceWire.Message message) {
        if (message instanceof BalanceWire.Hello hello) {
            var h = hello.header();
            if (h.revision() < revision || h.sessionId() < latestSession) return null;
            if (h.schemaVersion() != BalanceSchema.CURRENT_SCHEMA_VERSION) return reject("Incompatible balance schema");
            if (h.totalChunks() < 1 || h.totalChunks() > BalanceWire.MAX_CHUNKS
                    || h.totalLength() < 0 || h.totalLength() > BalanceWire.MAX_TOTAL_BYTES
                    || h.hash() == null || !h.hash().matches("[0-9a-f]{64}")) return reject("Invalid balance header");
            if (current != null && h.revision() == revision && !current.hash().equals(h.hash()))
                return reject("Same revision with different content");
            latestSession = h.sessionId();
            clearPending();
            pending = h;
            ready = false;
        } else if (message instanceof BalanceWire.Chunk chunk) {
            if (pending == null || chunk.sessionId() != pending.sessionId()) return null;
            if (chunk.chunkIndex() < 0 || chunk.chunkIndex() >= pending.totalChunks()
                    || chunk.data().length > BalanceWire.MAX_CHUNK_BYTES) return reject("Invalid chunk");
            byte[] old = chunks.get(chunk.chunkIndex());
            if (old != null) {
                if (!java.util.Arrays.equals(old, chunk.data())) return reject("Conflicting duplicate chunk");
                return null;
            }
            if ((long) receivedBytes + chunk.data().length > pending.totalLength()) return reject("Oversized transfer");
            receivedBytes += chunk.data().length;
            chunks.put(chunk.chunkIndex(), chunk.data().clone());
        } else if (message instanceof BalanceWire.Commit commit) {
            var h = commit.header();
            if (pending == null || h.sessionId() != pending.sessionId()) return null;
            if (!pending.equals(h)) return reject("HELLO/COMMIT header mismatch");
            if (chunks.size() != h.totalChunks() || receivedBytes != h.totalLength()) return reject("Incomplete transfer");
            var bytes = new ByteArrayOutputStream(receivedBytes);
            for (int i = 0; i < h.totalChunks(); i++) {
                byte[] chunk = chunks.get(i);
                if (chunk == null) return reject("Missing chunk");
                bytes.writeBytes(chunk);
            }
            try {
                // Decode once, after byte concatenation: a UTF-8 character may cross a chunk boundary.
                var tree = BalanceJson.parse("server-snapshot", bytes.toString(StandardCharsets.UTF_8)).tree;
                var next = BalanceSnapshot.fromTree(schema, tree, "server-snapshot");
                if (!next.hash().equals(h.hash())) return reject("Balance hash mismatch; check mod versions");
                var previous = current;
                current = next;
                revision = h.revision();
                ready = true;
                clearPending();
                return new Committed(next, previous, revision, h.sessionId());
            } catch (RuntimeException invalid) {
                return reject("Invalid balance snapshot: " + invalid.getMessage());
            }
        }
        return null;
    }
    private Rejected reject(String reason) {
        ready = false;
        clearPending();
        return new Rejected(reason, true);
    }
    private void clearPending() { pending = null; chunks.clear(); receivedBytes = 0; }
    public void exitConnection() {
        clearPending(); current = null; ready = false;
        revision = Long.MIN_VALUE; latestSession = Long.MIN_VALUE;
    }
}
