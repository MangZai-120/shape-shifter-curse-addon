package net.jackcooper.shapeShifterCurseAddon.balance;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * balance 快照同步协议（制作流程 §6.2，阶段 3）。
 *
 * 消息：HELLO → SNAPSHOT_CHUNK* → COMMIT → (客户端校验提交) → ACK。
 * 传输内容 = 合并树 JSON（只含数据包覆盖字段）；客户端用同一 fromTree 校验路径重建快照，
 * hash 与 COMMIT 头对照 = 一致性证明；双端 schema 代码同版本由 SCHEMA_VERSION 门控。
 *
 * 编码为自描述二进制（类型字节 + payload），协议无关：测试直接 round-trip，
 * 接线阶段映射到 Fabric PacketByteBuf / 自定义 payload。
 * 纯逻辑类：不依赖 MC。
 */
public final class BalanceWire {

    /** 协议版本：不兼容变更时 +1，旧端立即明确报错。 */
    public static final int PROTOCOL_VERSION = 2;

    // 单包/整批上限（文档 §6.2「限制单包、整批、条目数」；恶意/损坏包直接拒绝）
    public static final int MAX_CHUNK_BYTES = 256 * 1024;
    public static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;
    public static final int MAX_CHUNKS = MAX_TOTAL_BYTES / MAX_CHUNK_BYTES + 1;

    public static final byte T_HELLO = 1;
    public static final byte T_CHUNK = 2;
    public static final byte T_COMMIT = 3;
    public static final byte T_ACK = 4;
    public static final byte T_RESYNC_REQUEST = 5;

    /** 快照头：HELLO 与 COMMIT 共带，客户端先行校验再决定是否收数据。 */
    public record SnapshotHeader(long sessionId, long revision, int schemaVersion, String hash,
                                 int totalChunks, int totalLength) {}

    public sealed interface Message permits Hello, Chunk, Commit, Ack, ResyncRequest {}

    /** 服务端 → 客户端：会话开始（此时客户端清空旧暂存）。 */
    public record Hello(SnapshotHeader header) implements Message {}
    /** 服务端 → 客户端：数据分块（按索引归位，重复幂等）。数组按内容比较（record 默认引用相等不适用）。 */
    public record Chunk(long sessionId, int chunkIndex, byte[] data) implements Message {
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Chunk c)) return false;
            return sessionId == c.sessionId && chunkIndex == c.chunkIndex && java.util.Arrays.equals(data, c.data);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(sessionId, chunkIndex, java.util.Arrays.hashCode(data));
        }
    }
    /** 服务端 → 客户端：整批结束，客户端校验完整性 + hash 后切换并回 ACK。 */
    public record Commit(SnapshotHeader header) implements Message {}
    /** 客户端 → 服务端：已切换到该 revision。 */
    public record Ack(long revision) implements Message {}
    /** 客户端 → 服务端：会话丢失/hash 不符/版本不符，请求重发。 */
    public record ResyncRequest(String reason) implements Message {}

    private BalanceWire() {}

    // ==== 编码 ====

    public static byte[] encode(Message m) {
        try {
            var buf = new ByteArrayOutputStream();
            var out = new DataOutputStream(buf);
            out.writeInt(PROTOCOL_VERSION);
            if (m instanceof Hello h) {
                out.writeByte(T_HELLO);
                writeHeader(out, h.header());
            } else if (m instanceof Commit c) {
                out.writeByte(T_COMMIT);
                writeHeader(out, c.header());
            } else if (m instanceof Chunk c) {
                if (c.data().length > MAX_CHUNK_BYTES) {
                    throw new IllegalArgumentException("分块超限：" + c.data().length);
                }
                out.writeByte(T_CHUNK);
                out.writeLong(c.sessionId());
                out.writeInt(c.chunkIndex());
                out.writeInt(c.data().length);
                out.write(c.data());
            } else if (m instanceof Ack a) {
                out.writeByte(T_ACK);
                out.writeLong(a.revision());
            } else if (m instanceof ResyncRequest r) {
                out.writeByte(T_RESYNC_REQUEST);
                writeString(out, r.reason());
            } else {
                throw new IllegalArgumentException("未知消息：" + m);
            }
            return buf.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("编码失败（内存流不应 IO 异常）", e);
        }
    }

    private static void writeHeader(DataOutputStream out, SnapshotHeader h) throws IOException {
        out.writeLong(h.sessionId());
        out.writeLong(h.revision());
        out.writeInt(h.schemaVersion());
        writeString(out, h.hash());
        out.writeInt(h.totalChunks());
        out.writeInt(h.totalLength());
    }

    /** 把合并树 JSON 文本按固定块切分（服务端发送侧）。 */
    public static List<Chunk> chunkTreeJson(long sessionId, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("快照超整批上限：" + bytes.length);
        }
        int chunkCount = (bytes.length + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
        if (chunkCount == 0) chunkCount = 1;   // 空树也发 1 块（空载荷）
        List<Chunk> out = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            int from = i * MAX_CHUNK_BYTES;
            int to = Math.min(bytes.length, from + MAX_CHUNK_BYTES);
            byte[] slice = new byte[Math.max(0, to - from)];
            System.arraycopy(bytes, from, slice, 0, slice.length);
            out.add(new Chunk(sessionId, i, slice));
        }
        return out;
    }

    // ==== 解码 ====

    public static Message decode(byte[] bytes) {
        try {
            var in = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
            if (bytes.length > MAX_CHUNK_BYTES + 1024 || in.readInt() != PROTOCOL_VERSION)
                throw new IllegalArgumentException("Incompatible balance protocol or oversized packet");
            byte type = in.readByte();
            if (type == T_HELLO) {
                return new Hello(readHeader(in));
            }
            if (type == T_COMMIT) {
                return new Commit(readHeader(in));
            }
            if (type == T_CHUNK) {
                long sessionId = in.readLong();
                int index = in.readInt();
                int len = in.readInt();
                if (len < 0 || len > MAX_CHUNK_BYTES) {
                    throw new IllegalArgumentException("分块长度非法：" + len);
                }
                byte[] data = new byte[len];
                in.readFully(data);
                return new Chunk(sessionId, index, data);
            }
            if (type == T_ACK) {
                return new Ack(in.readLong());
            }
            if (type == T_RESYNC_REQUEST) {
                return new ResyncRequest(readString(in));
            }
            throw new IllegalArgumentException("未知消息类型：" + type);
        } catch (IOException e) {
            throw new IllegalArgumentException("解码失败（损坏的包）", e);
        }
    }

    private static SnapshotHeader readHeader(DataInputStream in) throws IOException {
        long sessionId = in.readLong();
        long revision = in.readLong();
        int schemaVersion = in.readInt();
        String hash = readString(in);
        int totalChunks = in.readInt();
        int totalLength = in.readInt();
        if (totalChunks < 0 || totalChunks > MAX_CHUNKS || totalLength < 0 || totalLength > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("快照头规模非法：chunks=" + totalChunks + " length=" + totalLength);
        }
        if (hash == null || hash.length() != 64) {
            throw new IllegalArgumentException("hash 必须是 64 位十六进制");
        }
        return new SnapshotHeader(sessionId, revision, schemaVersion, hash, totalChunks, totalLength);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > MAX_CHUNK_BYTES) throw new IllegalArgumentException("字符串长度非法：" + len);
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
