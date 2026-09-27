package net.jackcooper.shapeShifterCurseAddon.balance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * balance 同步层与 Fabric/MC 的接线（制作流程 §6.2 落地，阶段 3）。
 *
 * 服务端：资源重载准备候选树，END_DATA_PACK_RELOAD 成功后提交 → 在线玩家全量广播；
 * 玩家登录（PLAY_READY）发送；断线清状态。客户端：主线程消费消息、ACK 上行。
 * 单人集成服务端：物理两端同进程，但客户端持独立 ClientBalanceState 实例
 * （文档 §6.1 禁止靠共享 static 凑同步——本类的 SERVER/CLIENT 容器严格按端别初始化）。
 */
public final class BalanceIntegration {

    public static final Identifier PACKET_BALANCE_S2C = new Identifier("my_addon", "balance_sync");
    public static final Identifier PACKET_BALANCE_ACK = new Identifier("my_addon", "balance_ack");
    public static final Identifier PACKET_BALANCE_RESYNC = new Identifier("my_addon", "balance_resync");

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * 是否物理客户端环境（供双端读取分支用）。
     * 测试环境（纯 JavaExec）无 Fabric loader → getInstance() 为 null，返回 false 走服务端/默认分支。
     * 统一入口：各消费类勿各自调 FabricLoader（null 守卫只有这一份）。
     */
    public static boolean isPhysicalClient() {
        try {
            var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            return loader != null && loader.getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT;
        } catch (Throwable t) {
            // 测试环境（纯 JavaExec）loader 处于半初始化状态：按非客户端处理
            return false;
        }
    }

    /** 服务端权威容器（仅物理服务端/集成服务端初始化； DedicatedServer 与单人 host 同一实例语义）。 */
    private static volatile ServerBalanceState server;
    private static volatile BalanceSnapshot publishedServer;
    private static volatile BalanceSnapshot publishedClient;
    private static volatile Thread clientThread;
    private static java.util.Map<String, Object> pendingTree;
    private static BalanceSchema activeSchema;
    private static final java.util.Map<java.util.UUID, Long> syncDeadlines = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long> lastResync = new java.util.HashMap<>();
    /** 服务端最近一次提交的传输树 JSON（重载后为在线玩家重发）。 */
    private static String serverTreeJson;

    /** 客户端会话镜像容器（仅物理客户端初始化；单人物理端同进程也独立实例）。 */
    private static ClientBalanceState client;

    private BalanceIntegration() {}

    // ==== 服务端 ====

    public static synchronized void initServer(BalanceSchema schema) {
        activeSchema = schema;
        server = new ServerBalanceState(schema);
        server.commitDefaults();
        publishedServer = server.current();
        serverTreeJson = treeJsonOfDefaults();
        // Stage during apply; commit only at SERVER_STARTED or successful END_DATA_PACK_RELOAD.
        net.jackcooper.shapeShifterCurseAddon.balance.BalanceLoader.register(layers -> {
            onLayersLoaded(layers);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(BalanceIntegration::onServerReload);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((srv, manager) -> {
            synchronized (BalanceIntegration.class) { pendingTree = null; }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((srv, manager, success) -> {
            if (success) onServerReload(srv);
            else {
                synchronized (BalanceIntegration.class) { pendingTree = null; }
                net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry.INSTANCE.discardPending();
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(srv -> {
            synchronized (BalanceIntegration.class) {
                server = new ServerBalanceState(activeSchema);
                server.commitDefaults();
                publishedServer = server.current();
                serverTreeJson = treeJsonOfDefaults();
                pendingTree = null;
                syncDeadlines.clear();
                lastResync.clear();
                net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry.INSTANCE.clearServer();
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(srv -> {
            if (srv.getTicks() % 20 != 0) return;
            long now = System.nanoTime();
            for (var player : PlayerLookup.all(srv)) {
                Long deadline = syncDeadlines.get(player.getUuid());
                if (deadline == null) continue;
                if (isPlayerReady(player)) syncDeadlines.remove(player.getUuid());
                else if (now > deadline) {
                    syncDeadlines.remove(player.getUuid());
                    player.networkHandler.disconnect(net.minecraft.text.Text.literal("SSCA configuration sync timed out; check client/server mod versions."));
                }
            }
        });
    }

    /** Loader 回调：提交候选树（拒绝保留旧快照）；广播由 END_DATA_PACK_RELOAD 触发。 */
    private static synchronized void onLayersLoaded(List<BalanceMerge.Layer> layers) {
        if (server == null) return;
        var merged = BalanceMerge.merge(layers);
        BalanceSnapshot.fromTree(activeSchema, merged, "datapack-balance");
        // Validate transport size before committing anything.
        BalanceWire.chunkTreeJson(0, GSON.toJson(merged));
        pendingTree = merged;
    }

    /** END_DATA_PACK_RELOAD 成功回调（须在服务端线程）：广播在线玩家（提交已在 Loader 回调完成）。 */
    public static synchronized void onServerReload(net.minecraft.server.MinecraftServer srv) {
        if (server == null) return;
        if (pendingTree != null) {
            server.commitTree(pendingTree, "datapack-balance");
            serverTreeJson = GSON.toJson(pendingTree);
            publishedServer = server.current();
            pendingTree = null;
        }
        net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry.INSTANCE.commitPending();
        broadcastToOnline(srv);
    }

    public static synchronized void onPlayerReady(ServerPlayerEntity player) {
        if (server == null) return;
        server.playerJoined(player.getUuid());
        sendSequenceTo(player);
    }

    public static synchronized void onPlayerLeft(ServerPlayerEntity player) {
        if (server != null) server.playerLeft(player.getUuid());
        syncDeadlines.remove(player.getUuid());
        lastResync.remove(player.getUuid());
    }

    private static void broadcastToOnline(net.minecraft.server.MinecraftServer srv) {
        if (server == null || serverTreeJson == null) return;
        for (ServerPlayerEntity p : PlayerLookup.all(srv)) {
            sendSequenceTo(p);
        }
    }

    private static void sendSequenceTo(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, PACKET_BALANCE_S2C)) {
            player.networkHandler.disconnect(net.minecraft.text.Text.literal("This server requires a compatible SSCA balance sync client."));
            return;
        }
        syncDeadlines.putIfAbsent(player.getUuid(), System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30));
        // Queued before balance COMMIT, so its ACK also follows legacy spell application.
        net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry.INSTANCE.sendTo(player);
        List<BalanceWire.Message> seq = server.buildSendSequence(player.getUuid(), serverTreeJson);
        for (BalanceWire.Message m : seq) {
            PacketByteBuf buf = PacketByteBufs.create();
            byte[] encoded = BalanceWire.encode(m);
            buf.writeBytes(encoded);
            ServerPlayNetworking.send(player, PACKET_BALANCE_S2C, buf);
        }
    }

    public static synchronized void registerServerReceivers() {
        // ACK：客户端确认后标记就绪（未 ACK 前服务端可拒绝依赖新配置的施法请求，阶段 4 接）
        ServerPlayNetworking.registerGlobalReceiver(PACKET_BALANCE_ACK, (srv, player, handler, buf, rs) -> {
            long session = buf.readLong();
            long revision = buf.readLong();
            srv.execute(() -> {
                if (player.networkHandler == handler && !player.isRemoved() && server != null)
                    server.playerAcked(player.getUuid(), session, revision);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(PACKET_BALANCE_RESYNC, (srv, player, handler, buf, rs) -> {
            srv.execute(() -> {
                if (server == null || player.networkHandler != handler || player.isRemoved()) return;
                long now = System.nanoTime();
                Long previous = lastResync.get(player.getUuid());
                if (previous != null && now - previous < java.util.concurrent.TimeUnit.SECONDS.toNanos(2)) return;
                lastResync.put(player.getUuid(), now);
                sendSequenceTo(player);
            });
        });
    }

    // ==== 客户端 ====

    @Environment(EnvType.CLIENT)
    public static synchronized void initClient(BalanceSchema schema) {
        clientThread = Thread.currentThread();
        client = new ClientBalanceState(schema, null);
        ClientPlayNetworking.registerGlobalReceiver(PACKET_BALANCE_S2C, (c, handler, buf, rs) -> {
            if (buf.readableBytes() > BalanceWire.MAX_CHUNK_BYTES + 1024) {
                c.execute(() -> handler.getConnection().disconnect(net.minecraft.text.Text.literal("SSCA: balance packet too large")));
                return;
            }
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            c.execute(() -> {
                if (client == null || c.getNetworkHandler() != handler) return;
                try {
                BalanceWire.Message msg = BalanceWire.decode(bytes);
                ClientBalanceState.Outcome outcome = client.offer(msg);
                if (outcome instanceof ClientBalanceState.Committed committed) {
                    publishedClient = committed.committed();
                    sendAck(committed.sessionId(), committed.revision());
                    net.jackcooper.shapeShifterCurseAddon.util.ClientResourceCache.invalidate();
                } else if (outcome instanceof ClientBalanceState.Rejected rejected) {
                    handler.getConnection().disconnect(net.minecraft.text.Text.literal("SSCA balance: " + rejected.reason()));
                }
                } catch (RuntimeException invalid) {
                    handler.getConnection().disconnect(net.minecraft.text.Text.literal("SSCA balance sync failed: " + invalid.getMessage()));
                }
            });
        });
    }

    @Environment(EnvType.CLIENT)
    private static void sendAck(long session, long revision) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(session);
        buf.writeLong(revision);
        ClientPlayNetworking.send(PACKET_BALANCE_ACK, buf);
    }

    @Environment(EnvType.CLIENT)
    private static void sendResync(String reason) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(reason, 1024);
        ClientPlayNetworking.send(PACKET_BALANCE_RESYNC, buf);
    }

    /** 客户端当前镜像（未就绪 = null）。消费方（HUD/预检）阶段 5+ 读取。 */
    @Environment(EnvType.CLIENT)
    public static BalanceSnapshot clientSnapshot() {
        return publishedClient;
    }

    @Environment(EnvType.CLIENT)
    public static synchronized boolean clientReady() {
        return client != null && client.isReady();
    }

    /** 客户端断线清理（ClientPlayConnectionEvents.DISCONNECT 接线，阶段 4 补）。 */
    @Environment(EnvType.CLIENT)
    public static synchronized void onClientDisconnect() {
        if (client != null) client.exitConnection();
        publishedClient = null;
        net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry.INSTANCE.clearClient();
    }

    /** 服务端当前权威快照（未初始化 = null；消费端须自行回退）。 */
    public static BalanceSnapshot serverSnapshot() {
        return publishedServer;
    }

    /** Logical side, including an integrated server in a client process. No per-read locks. */
    public static boolean isClientThread() { return Thread.currentThread() == clientThread; }
    public static BalanceSnapshot currentSnapshot() { return isClientThread() ? publishedClient : publishedServer; }
    public static boolean isPlayerReady(ServerPlayerEntity player) {
        return server == null || server.isPlayerReady(player.getUuid());
    }

    /** 服务端当前配置代数（重载/提交会递增；消费端比对检测「配置变了需重算」）。 */
    public static synchronized long serverRevision() {
        return server == null ? 0 : server.revision();
    }

    /** 双端便捷读取：物理客户端读镜像、否则服务端快照，null 回退 def。 */
    public static double balanceDouble(String scope, String param, double def) {
        var s = currentSnapshot();
        return s == null ? def : s.getDouble(scope, param);
    }

    // ==== 工具 ====

    private static String treeJsonOfDefaults() {
        return GSON.toJson(java.util.Map.of("schema_version", BalanceSchema.CURRENT_SCHEMA_VERSION));
    }
}
