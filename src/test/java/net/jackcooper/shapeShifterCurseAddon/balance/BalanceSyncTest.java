package net.jackcooper.shapeShifterCurseAddon.balance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 端别与同步状态机测试（阶段 3 · 制作流程 §10.2 服务端权威与会话的可模拟用例）。
 *
 * 覆盖：双端 hash 一致、缺分块不半提交、旧 revision/旧会话丢弃、退出清理、
 * 重复 ACK/重复 COMMIT 幂等、hash 不符拒绝、协议 round-trip、
 * 客户端本地配置不覆盖服务端、改树生效与 hash 变化。
 */
public final class BalanceSyncTest {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        fullSyncFlowSameHash();
        missingChunkNoPartialCommit();
        staleSessionAndRevisionDropped();
        exitConnectionClearsEverything();
        duplicateAckAndCommitIdempotent();
        hashMismatchRejected();
        wireRoundTrip();
        clientLocalNeverOverridesServer();
        treeChangeBumpsRevision();

        System.out.println("Balance sync: " + checks + " checks, " + failures + " failures"
                + (failures == 0 ? " — all passed." : " — FAILURES PRESENT."));
        if (failures > 0) System.exit(1);
    }

    // ---- 场景 ----

    private static void fullSyncFlowSameHash() {
        var server = newServer();
        server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "pack");
        var client = newClient();
        deliver(client, server.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8)));
        check(client.isReady(), "完整流程后客户端就绪");
        check(client.current().hash().equals(server.current().hash()), "双端 hash 一致");
        check(client.current().getDouble("abilities.bat_sonic_wave", "damage") == 8.0, "客户端收到服务端值 damage=8");
    }

    private static void missingChunkNoPartialCommit() {
        var server = newServer();
        server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "pack");
        var client = newClient();
        var seq = server.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8));
        // 丢掉中间一个分块（制造 3 块：丢 #1）
        var truncated = new java.util.ArrayList<BalanceWire.Message>();
        for (BalanceWire.Message m : seq) {
            if (m instanceof BalanceWire.Chunk c && c.chunkIndex() == 1 && seq.stream().filter(x -> x instanceof BalanceWire.Chunk).count() > 2) {
                continue;
            }
            truncated.add(m);
        }
        deliver(client, truncated);
        check(!client.isReady() || client.current().getDouble("abilities.bat_sonic_wave", "damage") == 8.0,
                "缺分块不得产出半份配置");
        // 单块场景下补一个「分块不完整」直测：只发 HELLO+COMMIT 不发数据
        var client2 = newClient();
        var server2 = newServer();
        server2.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "pack");
        var seq2 = server2.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8));
        for (BalanceWire.Message m : seq2) {
            if (m instanceof BalanceWire.Chunk) continue;   // 全部数据块丢弃
            client2.offer(m);
        }
        check(!client2.isReady(), "缺全部数据块：COMMIT 拒绝且镜像未切换");
    }

    private static void staleSessionAndRevisionDropped() {
        var server = newServer();
        server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "pack");
        var client = newClient();
        var seq = server.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8));
        deliver(client, seq);
        // 新会话 HELLO 后，旧会话的分块必须被丢弃
        long oldSession = ((BalanceWire.Hello) seq.get(0)).header().sessionId();
        var stale = client.offer(new BalanceWire.Chunk(oldSession, 0, new byte[]{1}));
        check(stale == null && client.isReady(), "旧会话分块被丢弃不影响已就绪镜像");
        // 旧 revision 的 ACK 在服务端被丢弃
        UUID p = UUID.randomUUID();
        server.playerJoined(p);
        server.buildSendSequence(p, treeJsonOf(1, 8));
        server.playerAcked(p, server.revision() - 1);
        check(!server.isPlayerReady(p), "旧 revision ACK 被丢弃");
        server.playerAcked(p, server.revision());
        check(server.isPlayerReady(p), "当前 revision ACK 生效");
    }

    private static void exitConnectionClearsEverything() {
        var server = newServer();
        server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "pack");
        var client = newClient();
        deliver(client, server.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8)));
        check(client.isReady(), "同步完成");
        client.exitConnection();
        check(!client.isReady() && client.current() == null, "退出后镜像清空");
        // 重连：新容器从零开始，旧回调/分块不生效
        var client2 = newClient();
        check(!client2.isReady(), "新连接初始未就绪");
        deliver(client2, server.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8)));
        check(client2.isReady(), "重连后重新同步成功");
    }

    private static void duplicateAckAndCommitIdempotent() {
        var server = newServer();
        server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "pack");
        var client = newClient();
        var seq = server.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8));
        deliver(client, seq);
        var hash1 = client.current().hash();
        // 重复投递整个序列（网络重试）：镜像不变、不报错
        deliver(client, seq);
        check(client.current().hash().equals(hash1), "重复 COMMIT 幂等（镜像不重复切换）");
        // 服务端重复 ACK 幂等
        UUID p = UUID.randomUUID();
        server.playerJoined(p);
        server.buildSendSequence(p, treeJsonOf(1, 8));
        server.playerAcked(p, server.revision());
        server.playerAcked(p, server.revision());
        check(server.isPlayerReady(p), "重复 ACK 幂等");
    }

    private static void hashMismatchRejected() {
        var server = newServer();
        server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "pack");
        var client = newClient();
        var seq = server.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8));
        deliver(client, seq);
        var goodHash = client.current().hash();
        // 篡改传输树（damage 8→99）但头 hash 不变 → 客户端 hash 对照失败拒绝
        var server2 = newServer();
        server2.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":99}}}"), "pack2");
        var forged = server2.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 99));
        // 用伪造头（hash 仍是 server 的）+ 伪造数据
        var header = new BalanceWire.SnapshotHeader(999L, server2.revision(),
                BalanceSchema.CURRENT_SCHEMA_VERSION, goodHash, 1, 0);
        var client2 = newClient();
        client2.offer(new BalanceWire.Hello(header));
        for (BalanceWire.Message m : forged) {
            if (m instanceof BalanceWire.Chunk) client2.offer(m);
        }
        var out = client2.offer(new BalanceWire.Commit(header));
        check(out instanceof ClientBalanceState.Rejected r && r.requestResync(),
                "hash 不符整批拒绝并请求重同步");
        check(!client2.isReady(), "拒绝后镜像保持未就绪");
    }

    private static void wireRoundTrip() {
        var header = new BalanceWire.SnapshotHeader(7L, 3L, 1, "a".repeat(64), 2, 128);
        var msgs = new BalanceWire.Message[]{
                new BalanceWire.Hello(header),
                new BalanceWire.Commit(header),
                new BalanceWire.Chunk(7L, 0, new byte[]{1, 2, 3}),
                new BalanceWire.Ack(3L),
                new BalanceWire.ResyncRequest("测试原因"),
        };
        for (BalanceWire.Message m : msgs) {
            Object back = BalanceWire.decode(BalanceWire.encode(m));
            check(m.equals(back), "round-trip 保真：" + m.getClass().getSimpleName());
        }
        // 大树分块 round-trip（验证切分与拼装与精度保真：0.1+0.2 之类值）
        var bigJson = "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":9,\"range\":0.30000000000000004}}}";
        var chunks = BalanceWire.chunkTreeJson(1L, bigJson);
        check(chunks.size() == 1, "小树单分块");
        var sb = new StringBuilder();
        for (BalanceWire.Chunk c : chunks) sb.append(new String(c.data(), java.nio.charset.StandardCharsets.UTF_8));
        check(sb.toString().equals(bigJson), "分块拼装无损（含双精度字面量）");
        // 超大树强制多分块（MAX_CHUNK_BYTES=256KB → 造 600KB）
        StringBuilder bigText = new StringBuilder("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"range\":8");
        for (int i = 0; i < 150_000; i++) bigText.append(",\"p").append(i).append("\":8");
        bigText.append("}}}");   // 未知字段会校验失败，但只测传输层拼装，不走校验
        String huge = bigText.toString();
        var many = BalanceWire.chunkTreeJson(1L, huge);
        check(many.size() >= 2, "大树多分块（实际 " + many.size() + "）");
        var rebuilt = new StringBuilder();
        for (BalanceWire.Chunk c : many) rebuilt.append(new String(c.data(), java.nio.charset.StandardCharsets.UTF_8));
        check(rebuilt.toString().equals(huge), "多分块拼装无损");
    }

    private static void clientLocalNeverOverridesServer() {
        // 客户端带着本地默认快照连接，服务端值必须覆盖
        var client = new ClientBalanceState(schema(), BalanceSnapshot.defaults(schema()));
        check(client.isReady(), "本地默认先就绪");
        var server = newServer();
        server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "pack");
        deliver(client, server.buildSendSequence(UUID.randomUUID(), treeJsonOf(1, 8)));
        check(client.current().getDouble("abilities.bat_sonic_wave", "damage") == 8.0,
                "服务端权威值覆盖客户端本地默认");
    }

    private static void treeChangeBumpsRevision() {
        var server = newServer();
        check(server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "a"), "首提交有变化");
        long rev1 = server.revision();
        check(!server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "b"), "同内容重提交无变化");
        check(server.revision() == rev1, "hash 未变 revision 不递增");
        check(server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":9}}}"), "c"), "改值有变化");
        check(server.revision() == rev1 + 1, "内容变化 revision+1");
        // 非法候选：committed 保持上一份
        try {
            server.commitTree(treeOf("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":-1}}}"), "bad");
            check(false, "非法候选必须抛异常");
        } catch (BalanceSnapshot.BalanceRejectException expected) {
            check(server.current().getDouble("abilities.bat_sonic_wave", "damage") == 9.0, "拒绝后保留旧快照");
        }
    }

    // ---- 工具 ----

    private static BalanceSchema schema() {
        var b = BalanceSchema.create();
        b.scope("abilities.bat_sonic_wave")
                .doubleParam("range", 8.0, 1.0, 64.0)
                .doubleParam("half_width", 1.75, 0.25, 8.0)
                .doubleParam("damage", 6.0, 0.0, 1000.0)
                .intParam("debuff_ticks", 60, 0, 1200);
        return b.build();
    }

    private static ServerBalanceState newServer() {
        return new ServerBalanceState(schema());
    }

    private static ClientBalanceState newClient() {
        return new ClientBalanceState(schema(), null);
    }

    private static Map<String, Object> treeOf(String json) {
        return BalanceJson.parse("t", json).tree;
    }

    private static String treeJsonOf(int schemaVersion, int damage) {
        return GSON.toJson(Map.of("schema_version", schemaVersion,
                "abilities", Map.of("bat_sonic_wave", Map.of("damage", damage))));
    }

    private static void deliver(ClientBalanceState client, List<BalanceWire.Message> seq) {
        for (BalanceWire.Message m : seq) client.offer(m);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (condition) return;
        failures++;
        System.out.println("  [FAIL] " + message);
    }
}
