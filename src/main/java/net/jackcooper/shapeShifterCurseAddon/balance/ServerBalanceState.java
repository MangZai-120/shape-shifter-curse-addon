package net.jackcooper.shapeShifterCurseAddon.balance;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端权威配置状态（制作流程 §5 ServerBalanceState + §6.2，阶段 3）。
 *
 * 候选快照构建（加载期）→ 服务端线程安全边界提交（原子切换 + revision 递增）→
 * 为每个在线玩家生成发送序列（HELLO + 全部分块 + COMMIT）→ 等各玩家 ACK 后标记就绪。
 * hash 未变可免发完整数据（只发 HELLO+COMMIT 头确认）。
 * 纯逻辑类：不依赖 MC；接线层把 beginCommit 挂到 PostPowerReloadCallback 之后的
 * 安全边界（阶段 1 台账已核实该时机）。
 */
public final class ServerBalanceState {

    /** 就绪状态：依赖该配置的新施法是否放行。 */
    public enum PlayerSync { NOT_SENT, SENT, ACKED }

    private final BalanceSchema schema;
    private volatile BalanceSnapshot committed;                    // 当前权威快照（未加载 = null）
    private volatile long revision = 0;
    private final AtomicLong sessionSeq = new AtomicLong();
    private final Map<java.util.UUID, Long> sentSessions = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, PlayerSync> players = new ConcurrentHashMap<>();

    public ServerBalanceState(BalanceSchema schema) {
        this.schema = schema;
    }

    public BalanceSnapshot current() { return committed; }
    public long revision() { return revision; }

    /**
     * 安全边界提交：把已通过校验的候选树切成新权威快照（原子引用切换 + revision+1）。
     * 候选非法（校验失败）抛异常，committed 保持上一份有效快照（文档 §5 第 6 步）。
     *
     * @return true = 快照内容有变化（需广播）；false = hash 未变（只补发头确认即可）。
     */
    public boolean commitTree(Map<String, Object> mergedTree, String source) {
        BalanceSnapshot next = BalanceSnapshot.fromTree(schema, mergedTree, source);
        if (committed != null && committed.hash().equals(next.hash())) {
            // 内容未变：保留旧 revision 语义（在线玩家已 ACK 的继续有效），只确保全员状态正确
            return false;
        }
        committed = next;
        revision++;
        players.replaceAll((k, v) -> PlayerSync.SENT);   // 广播后待确认
        return true;
    }

    /** 无外部数据包启动：提交全默认快照。 */
    public void commitDefaults() {
        BalanceSnapshot next = BalanceSnapshot.defaults(schema);
        if (committed != null && committed.hash().equals(next.hash())) return;
        committed = next;
        revision++;
        players.replaceAll((k, v) -> PlayerSync.SENT);
    }

    public void playerJoined(java.util.UUID playerId) {
        sentSessions.remove(playerId);
        players.put(playerId, PlayerSync.NOT_SENT);
    }

    public void playerLeft(java.util.UUID playerId) {
        sentSessions.remove(playerId);
        players.remove(playerId);   // 退出清理：该连接的 ACK 状态一并移除
    }

    public void playerAcked(java.util.UUID playerId, long revision) {
        playerAcked(playerId, sentSessions.getOrDefault(playerId, -1L), revision);
    }

    public void playerAcked(java.util.UUID playerId, long session, long revision) {
        PlayerSync s = players.get(playerId);
        if (s == null || s == PlayerSync.NOT_SENT || session < 0 || !java.util.Objects.equals(sentSessions.get(playerId), session)) return;                       // 未知玩家/已退出：过期 ACK 丢弃
        if (revision != this.revision) return;       // 旧 revision ACK：丢弃（等待重同步）
        players.put(playerId, PlayerSync.ACKED);
    }

    public boolean isPlayerReady(java.util.UUID playerId) {
        return players.get(playerId) == PlayerSync.ACKED;
    }

    /**
     * 为一个玩家生成完整发送序列（登录或重载广播）。
     * 传输树 = 生成快照的合并树（含 schema_version）；树转 JSON 由接线层/测试用 Gson。
     */
    public List<BalanceWire.Message> buildSendSequence(java.util.UUID playerId, String treeJson) {
        if (committed == null) throw new IllegalStateException("尚未提交快照");
        long sessionId = sessionSeq.incrementAndGet();
        byte[] bytes = treeJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var header = new BalanceWire.SnapshotHeader(sessionId, revision,
                BalanceSchema.CURRENT_SCHEMA_VERSION, committed.hash(),
                0, bytes.length);
        // 分块数按实际切分修正
        var chunks = BalanceWire.chunkTreeJson(sessionId, treeJson);
        header = new BalanceWire.SnapshotHeader(sessionId, revision,
                BalanceSchema.CURRENT_SCHEMA_VERSION, committed.hash(),
                chunks.size(), bytes.length);
        sentSessions.put(playerId, sessionId);
        players.put(playerId, PlayerSync.SENT);
        List<BalanceWire.Message> out = new java.util.ArrayList<>(chunks.size() + 2);
        out.add(new BalanceWire.Hello(header));
        out.addAll(chunks);
        out.add(new BalanceWire.Commit(header));
        return out;
    }
}
