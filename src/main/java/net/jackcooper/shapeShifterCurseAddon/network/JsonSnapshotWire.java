package net.jackcooper.shapeShifterCurseAddon.network;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded registry snapshots. The unframed encoding remains compatible with the legacy channels. */
public final class JsonSnapshotWire {
    public enum Kind { SPELLS, EVOLUTION }
    public static final int LEGACY_LIMIT = 1_048_576;
    public static final int MAX_TOTAL_BYTES = 16 * 1_048_576;
    public static final int CHUNK_BYTES = 32_768;
    public static final int FRAME_HEADER_BYTES = 58;
    public static final int SESSION_HEADER_BYTES = 16;
    public static final int MAX_ENTRIES = 1000;
    private static final int MAX_JSON_CHARS = 2_000_000;
    private JsonSnapshotWire() {}

    public static byte[] encode(Map<String, String> content) {
        if (content.size() > MAX_ENTRIES) throw invalid("Too many registry entries");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(content.size());
            for (var entry : new TreeMap<>(content).entrySet()) {
                writeString(out, bytes, entry.getKey(), 256);
                writeString(out, bytes, entry.getValue(), MAX_JSON_CHARS);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }

    public static Map<String, String> decode(byte[] bytes) {
        if (bytes.length < 4 || bytes.length > MAX_TOTAL_BYTES) throw invalid("Invalid registry size");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) throw invalid("Invalid registry count");
            var content = new LinkedHashMap<String, String>();
            for (int i = 0; i < count; i++) {
                String id = readString(in, 256), json = readString(in, MAX_JSON_CHARS);
                if (content.putIfAbsent(id, json) != null) throw invalid("Duplicate registry entry");
            }
            if (in.available() != 0) throw invalid("Trailing registry data");
            return Map.copyOf(content);
        } catch (IOException truncated) { throw invalid("Truncated registry data", truncated); }
    }

    private static void writeString(DataOutputStream out, ByteArrayOutputStream bytes, String value, int maxChars) throws IOException {
        if (value == null || value.length() > maxChars) throw invalid("Registry string too long");
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if ((long) bytes.size() + utf8.length + 5 > MAX_TOTAL_BYTES) throw invalid("Registry snapshot exceeds 16 MiB");
        int length = utf8.length;
        while ((length & ~127) != 0) { out.writeByte((length & 127) | 128); length >>>= 7; }
        out.writeByte(length);
        out.write(utf8);
    }

    private static String readString(DataInputStream in, int maxChars) throws IOException {
        long length = 0;
        int shift = 0, value;
        do {
            if (shift >= 35) throw invalid("Invalid registry string length");
            value = in.readUnsignedByte();
            length |= (long) (value & 127) << shift;
            shift += 7;
        } while ((value & 128) != 0);
        if (length > (long) maxChars * 3 || length > in.available()) throw invalid("Invalid registry string size");
        try {
            String result = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes((int) length))).toString();
            if (result.length() > maxChars) throw invalid("Registry string too long");
            return result;
        } catch (CharacterCodingException malformed) { throw invalid("Invalid registry UTF-8", malformed); }
    }

    public static List<byte[]> frames(Kind kind, long transfer, byte[] payload) {
        if (transfer < 0 || payload.length < 4 || payload.length > MAX_TOTAL_BYTES) throw invalid("Invalid registry transfer");
        int count = (payload.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        byte[] hash = digest(payload);
        var frames = new ArrayList<byte[]>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * CHUNK_BYTES, length = Math.min(CHUNK_BYTES, payload.length - offset);
            var frame = ByteBuffer.allocate(FRAME_HEADER_BYTES + length);
            frame.put((byte) 1).put((byte) kind.ordinal()).putLong(transfer).putInt(payload.length)
                    .putInt(index).putInt(count).putInt(length).put(hash).put(payload, offset, length);
            frames.add(frame.array());
        }
        return frames;
    }

    public record Received(Kind kind, Map<String, String> content) {}

    /** The v2 channel binds each bounded v1 frame to the complete configuration transaction. */
    public record ScopedFrame(long session, long revision, byte[] frame) {}

    public static byte[] scope(long session, long revision, byte[] frame) {
        if (session < 0 || revision < 0 || frame.length < FRAME_HEADER_BYTES
                || frame.length > FRAME_HEADER_BYTES + CHUNK_BYTES) throw invalid("Invalid scoped registry frame");
        return ByteBuffer.allocate(SESSION_HEADER_BYTES + frame.length)
                .putLong(session).putLong(revision).put(frame).array();
    }

    public static ScopedFrame unScope(byte[] packet) {
        if (packet.length < SESSION_HEADER_BYTES + FRAME_HEADER_BYTES
                || packet.length > SESSION_HEADER_BYTES + FRAME_HEADER_BYTES + CHUNK_BYTES)
            throw invalid("Invalid scoped registry frame size");
        var input = ByteBuffer.wrap(packet);
        long session = input.getLong(), revision = input.getLong();
        if (session < 0 || revision < 0) throw invalid("Invalid registry session");
        byte[] frame = new byte[input.remaining()]; input.get(frame);
        return new ScopedFrame(session, revision, frame);
    }

    /** One connection, at most one bounded incomplete transfer per registry. */
    public static final class Receiver {
        private final Map<Kind, Transfer> pending = new EnumMap<>(Kind.class);
        private final Map<Kind, Long> latest = new EnumMap<>(Kind.class);

        public Received offer(byte[] packet) {
            if (packet.length < FRAME_HEADER_BYTES || packet.length > FRAME_HEADER_BYTES + CHUNK_BYTES)
                throw invalid("Invalid registry frame size");
            var frame = ByteBuffer.wrap(packet);
            int version = Byte.toUnsignedInt(frame.get()), kindId = Byte.toUnsignedInt(frame.get());
            if (version != 1 || kindId >= Kind.values().length) throw invalid("Unsupported registry frame");
            Kind kind = Kind.values()[kindId];
            long id = frame.getLong();
            int total = frame.getInt(), index = frame.getInt(), count = frame.getInt(), length = frame.getInt();
            byte[] hash = new byte[32]; frame.get(hash);
            if (id < 0 || total < 4 || total > MAX_TOTAL_BYTES || count != (total + CHUNK_BYTES - 1) / CHUNK_BYTES
                    || index < 0 || index >= count || length != Math.min(CHUNK_BYTES, total - index * CHUNK_BYTES)
                    || frame.remaining() != length) throw invalid("Invalid registry frame header");
            long previous = latest.getOrDefault(kind, -1L);
            if (id < previous || id == previous && !pending.containsKey(kind)) return null;
            Transfer transfer = pending.get(kind);
            if (id > previous) {
                transfer = new Transfer(total, count, hash);
                pending.put(kind, transfer); latest.put(kind, id);
            }
            if (transfer == null || transfer.bytes.length != total || transfer.received.length != count
                    || !Arrays.equals(transfer.hash, hash)) throw invalid("Conflicting registry frame header");
            int offset = index * CHUNK_BYTES;
            if (transfer.received[index]) {
                for (int i = 0; i < length; i++) if (transfer.bytes[offset + i] != frame.get())
                    throw invalid("Conflicting duplicate registry chunk");
                return null;
            }
            frame.get(transfer.bytes, offset, length);
            transfer.received[index] = true;
            if (++transfer.count != count) return null;
            pending.remove(kind);
            if (!MessageDigest.isEqual(hash, digest(transfer.bytes))) throw invalid("Registry hash mismatch");
            return new Received(kind, decode(transfer.bytes));
        }

        public void clear() { pending.clear(); latest.clear(); }
    }

    private static final class Transfer {
        final byte[] bytes, hash;
        final boolean[] received;
        int count;
        Transfer(int size, int chunks, byte[] hash) {
            bytes = new byte[size]; received = new boolean[chunks]; this.hash = hash;
        }
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }
    private static IllegalArgumentException invalid(String reason, Throwable error) { return new IllegalArgumentException(reason, error); }
}
