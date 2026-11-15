package net.jackcooper.shapeShifterCurseAddon.network;

import java.nio.ByteBuffer;
import java.util.*;

public final class JsonSnapshotWireTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    private static void rejects(Runnable action, String reason) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        var small = Map.of("a", "中");
        check(Arrays.equals(JsonSnapshotWire.encode(small), new byte[]{0,0,0,1,1,97,3,(byte)0xe4,(byte)0xb8,(byte)0xad}),
                "Small snapshots retain the legacy PacketByteBuf encoding");
        check(JsonSnapshotWire.decode(JsonSnapshotWire.encode(Map.of())).isEmpty(), "Empty registries round trip");
        var content = Map.of("large", "{\"text\":\"" + "中🙂".repeat(180000) + "\"}", "second", "{}");
        byte[] encoded = JsonSnapshotWire.encode(content);
        check(encoded.length > JsonSnapshotWire.LEGACY_LIMIT, "Exercise a real multi-byte snapshot above 1 MiB");
        var frames = JsonSnapshotWire.frames(JsonSnapshotWire.Kind.SPELLS, 10, encoded);
        var receiver = new JsonSnapshotWire.Receiver();
        for (int i = frames.size() - 1; i > 0; i--) {
            check(receiver.offer(frames.get(i)) == null, "No publication before every chunk arrives");
            check(receiver.offer(frames.get(i)) == null, "An identical duplicate does not complete the transfer early");
        }
        var result = receiver.offer(frames.get(0));
        check(result.kind() == JsonSnapshotWire.Kind.SPELLS && result.content().equals(content), "Reassembly preserves split UTF-8 sequences");
        check(receiver.offer(frames.get(0)) == null, "Completed transfer replay cannot overwrite a snapshot");
        check(receiver.offer(JsonSnapshotWire.frames(JsonSnapshotWire.Kind.SPELLS, 9, encoded).get(0)) == null, "Older transfer is ignored");
        receiver.clear();
        check(receiver.offer(frames.get(0)) == null, "New connections can reuse transfer IDs");
        byte[] conflict = frames.get(0).clone(); conflict[conflict.length - 1] ^= 1;
        rejects(() -> receiver.offer(conflict), "Reject conflicting duplicate chunks");
        receiver.clear();
        var damaged = new ArrayList<>(frames);
        byte[] last = damaged.get(damaged.size() - 1).clone(); last[last.length - 1] ^= 1;
        damaged.set(damaged.size() - 1, last);
        rejects(() -> damaged.forEach(receiver::offer), "Whole-snapshot hash detects corrupted content");
        byte[] tooLarge = frames.get(0).clone(); ByteBuffer.wrap(tooLarge).putInt(10, Integer.MAX_VALUE);
        rejects(() -> new JsonSnapshotWire.Receiver().offer(tooLarge), "Oversized header is rejected before allocation");
        byte[] invalidIndex = frames.get(0).clone(); ByteBuffer.wrap(invalidIndex).putInt(14, -1);
        rejects(() -> new JsonSnapshotWire.Receiver().offer(invalidIndex), "Invalid chunk index is rejected");
        rejects(() -> JsonSnapshotWire.decode(new byte[]{0,0,0,1,1,97,1,(byte)0xff}), "Malformed UTF-8 is rejected");
        rejects(() -> JsonSnapshotWire.decode(new byte[]{0,0,0,0,1}), "Trailing bytes are rejected");
        rejects(() -> JsonSnapshotWire.encode(Map.of("x".repeat(257), "{}")), "Validate legacy key limit before publishing a registry");
        rejects(() -> JsonSnapshotWire.encode(Map.of("x", " ".repeat(2_000_001))), "Validate per-entry limit before publishing a registry");
        var oversized = new LinkedHashMap<String,String>();
        for (int i = 0; i < 9; i++) oversized.put("entry" + i, " ".repeat(1_900_000));
        rejects(() -> JsonSnapshotWire.encode(oversized), "Validate total limit before publishing a registry");
        var many = new LinkedHashMap<String,String>();
        for (int i = 0; i <= JsonSnapshotWire.MAX_ENTRIES; i++) many.put("entry" + i, "{}");
        rejects(() -> JsonSnapshotWire.encode(many), "Validate entry count before publishing a registry");

        var receipt = new ConfigurationReceipt();
        check(receipt.canCommit(1, 1), "Old servers retain their existing balance protocol");
        receipt.applied(JsonSnapshotWire.Kind.SPELLS); receipt.applied(JsonSnapshotWire.Kind.EVOLUTION);
        receipt.begin(2, 1);
        check(!receipt.canCommit(2, 1), "Earlier independent registry packets do not confirm a new transfer");
        receipt.applied(JsonSnapshotWire.Kind.SPELLS);
        check(!receipt.canCommit(2, 1), "Missing evolution data prevents ACK");
        receipt.applied(JsonSnapshotWire.Kind.EVOLUTION);
        check(receipt.canCommit(2, 1), "Both applied registries permit ACK");
        check(!receipt.canCommit(1, 1) && !receipt.canCommit(2, 2), "Session and revision must match");
        check(receipt.needsAppearanceRefresh(2) && !receipt.needsAppearanceRefresh(2), "Retries do not repeatedly upload appearance");
        receipt.begin(3, 1);
        check(!receipt.canCommit(3, 1) && receipt.needsAppearanceRefresh(3), "Reauthentication and spell-only reload require a fresh receipt");
        receipt.applied(JsonSnapshotWire.Kind.EVOLUTION);
        check(!receipt.canCommit(3, 1), "Missing spells prevent ACK");
        receipt.clear();
        check(receipt.canCommit(1, 1) && receipt.needsAppearanceRefresh(1), "Reconnect clears receipt and upload history");
        System.out.println("Configuration snapshot protocol PASS: " + checks + " checks");
    }
}
