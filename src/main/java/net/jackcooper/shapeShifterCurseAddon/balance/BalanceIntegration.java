package net.jackcooper.shapeShifterCurseAddon.balance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.ability.BatDesmodusBloodThirst;
import net.jackcooper.shapeShifterCurseAddon.compat.EasyAuthBalanceCompatibility;
import net.jackcooper.shapeShifterCurseAddon.power.BloodThirstResourcePower;
import net.jackcooper.shapeShifterCurseAddon.network.JsonSnapshotSync;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry;
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

    public static final Identifier PACKET_BALANCE_S2C = new Identifier("ssc_addon", "balance_sync");
    public static final Identifier PACKET_BALANCE_ACK = new Identifier("ssc_addon", "balance_ack");
    public static final Identifier PACKET_BALANCE_RESYNC = new Identifier("ssc_addon", "balance_resync");

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
    private static volatile Thread clientThread;
    private static java.util.Map<String, Object> pendingTree;
    private static BalanceSchema activeSchema;
    private static final java.util.Map<java.util.UUID, Long> lastResync = new java.util.HashMap<>();
    private static final long RETRY_INTERVAL_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    private static final java.util.Map<java.util.UUID, Long> lastSyncAttempt = new java.util.HashMap<>();
    /** 服务端最近一次提交的传输树 JSON（重载后为在线玩家重发）。 */
    private static String serverTreeJson;

    private BalanceIntegration() {}

    // ==== 服务端 ====

    public static synchronized void initServer(BalanceSchema schema) {
        activeSchema = schema;
        server = new ServerBalanceState(schema);
        server.commitDefaults();
        publishedServer = server.current();
        serverTreeJson = treeJsonOfDefaults();
        lastSyncAttempt.clear();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(BalanceIntegration::retryUnreadyPlayers);
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
                lastResync.clear();
                lastSyncAttempt.clear();
                net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry.INSTANCE.clearServer();
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
            int previousBloodMax = BatDesmodusBloodThirst.maxBlood();
            server.commitTree(pendingTree, "datapack-balance");
            serverTreeJson = GSON.toJson(pendingTree);
            publishedServer = server.current();
            pendingTree = null;
            if (BatDesmodusBloodThirst.maxBlood() < previousBloodMax) {
                BloodThirstResourcePower.clampOnlinePlayers(srv);
            }
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
        lastResync.remove(player.getUuid());
        lastSyncAttempt.remove(player.getUuid());
    }

    private static void broadcastToOnline(net.minecraft.server.MinecraftServer srv) {
        if (server == null || serverTreeJson == null) return;
        for (ServerPlayerEntity p : List.copyOf(PlayerLookup.all(srv))) {
            sendSequenceTo(p);
        }
    }

    private static synchronized void retryUnreadyPlayers(net.minecraft.server.MinecraftServer srv) {
        if (server == null || srv.getTicks() % 20 != 0) return;
        long now = System.nanoTime();
        // A snapshot also tolerates connection callbacks changing the live player list.
        for (ServerPlayerEntity player : List.copyOf(PlayerLookup.all(srv))) {
            if (player.isDisconnected() || !EasyAuthBalanceCompatibility.canSynchronize(player)) continue;
            if (server.isPlayerReady(player.getUuid())) continue;
            Long previous = lastSyncAttempt.get(player.getUuid());
            if (previous == null || now - previous >= RETRY_INTERVAL_NANOS) sendSequenceTo(player, true);
        }
    }

    private static void sendSequenceTo(ServerPlayerEntity player) {
        sendSequenceTo(player, false);
    }

    private static void sendSequenceTo(ServerPlayerEntity player, boolean retry) {
        // A reload can change only spells/routes, without changing the balance revision.
        // Revoke the previous ACK even when a required channel is temporarily unavailable.
        if (!retry) server.playerJoined(player.getUuid());
        if (player.isDisconnected() || !EasyAuthBalanceCompatibility.canSynchronize(player)) return;
        lastSyncAttempt.put(player.getUuid(), System.nanoTime());
        var spells = SpellRegistry.INSTANCE.getRawJson();
        var routes = EvolutionRegistry.INSTANCE.getRawJson();
        if (!ServerPlayNetworking.canSend(player, PACKET_BALANCE_S2C)
                || !JsonSnapshotSync.canSend(player, SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC, spells)
                || !JsonSnapshotSync.canSend(player, SscAddonNetworking.PACKET_EVO_ROUTES_SYNC, routes)) {
            // No partial transfer may unlock gameplay. Late channels are retried without kicking.
            return;
        }
        List<BalanceWire.Message> seq = retry
                ? server.buildRetrySequence(player.getUuid(), serverTreeJson)
                : server.buildSendSequence(player.getUuid(), serverTreeJson);
        var header = ((BalanceWire.Hello) seq.get(0)).header();
        boolean scoped = JsonSnapshotSync.supportsScoped(player);
        var beginChannel = scoped ? JsonSnapshotSync.BEGIN_SCOPED : JsonSnapshotSync.BEGIN;
        if (ServerPlayNetworking.canSend(player, beginChannel)) {
            var begin = PacketByteBufs.create();
            begin.writeLong(header.sessionId()); begin.writeLong(header.revision());
            ServerPlayNetworking.send(player, beginChannel, begin);
        }
        // Force both registries into this transfer, even if a previous connection or attempt received them.
        // Client application is queued before balance COMMIT, which confirms the complete configuration.
        if (scoped) {
            JsonSnapshotSync.sendScoped(player, SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC, spells, header.sessionId(), header.revision());
            JsonSnapshotSync.sendScoped(player, SscAddonNetworking.PACKET_EVO_ROUTES_SYNC, routes, header.sessionId(), header.revision());
        } else {
            JsonSnapshotSync.send(player, SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC, spells, true);
            JsonSnapshotSync.send(player, SscAddonNetworking.PACKET_EVO_ROUTES_SYNC, routes, true);
        }
        for (BalanceWire.Message m : seq) {
            PacketByteBuf buf = PacketByteBufs.create();
            byte[] encoded = BalanceWire.encode(m);
            buf.writeBytes(encoded);
            ServerPlayNetworking.send(player, PACKET_BALANCE_S2C, buf);
        }
    }

    public static synchronized void registerServerReceivers() {
        net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents.REGISTER.register((handler, sender, srv, channels) -> {
            if (!channels.contains(PACKET_BALANCE_S2C) && !channels.contains(JsonSnapshotSync.CHUNKS)
                    && !channels.contains(JsonSnapshotSync.BEGIN_SCOPED) && !channels.contains(JsonSnapshotSync.SCOPED_CHUNKS)
                    && !channels.contains(SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC)
                    && !channels.contains(SscAddonNetworking.PACKET_EVO_ROUTES_SYNC)) return;
            srv.execute(() -> {
                var player = handler.player;
                if (server != null && !player.isDisconnected() && srv.getPlayerManager().getPlayer(player.getUuid()) == player
                        && !server.isPlayerReady(player.getUuid())) sendSequenceTo(player, true);
            });
        });
        // ACK：客户端确认后标记就绪（未 ACK 前服务端可拒绝依赖新配置的施法请求，阶段 4 接）
        ServerPlayNetworking.registerGlobalReceiver(PACKET_BALANCE_ACK, (srv, player, handler, buf, rs) -> {
            if (buf.readableBytes() != 16) return;
            long session = buf.readLong();
            long revision = buf.readLong();
            net.jackcooper.shapeShifterCurseAddon.network.ServerPacketExecutor.execute(srv, player, handler, () -> {
                if (player.networkHandler == handler && !player.isRemoved() && server != null
                        && EasyAuthBalanceCompatibility.canSynchronize(player)) {
                    server.playerAcked(player.getUuid(), session, revision);
                    if (server.isPlayerReady(player.getUuid())) lastSyncAttempt.remove(player.getUuid());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(PACKET_BALANCE_RESYNC, (srv, player, handler, buf, rs) -> {
            net.jackcooper.shapeShifterCurseAddon.network.ServerPacketExecutor.execute(srv, player, handler, () -> {
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
        net.jackcooper.shapeShifterCurseAddon.client.ClientConfigurationSync.register(schema);
    }

    @Environment(EnvType.CLIENT)
    public static BalanceSnapshot clientSnapshot() {
        return net.jackcooper.shapeShifterCurseAddon.client.ClientConfigurationSync.snapshot();
    }

    @Environment(EnvType.CLIENT)
    public static synchronized boolean clientReady() {
        return net.jackcooper.shapeShifterCurseAddon.client.ClientConfigurationSync.isReady();
    }

    /** Current authoritative server snapshot. */
    public static BalanceSnapshot serverSnapshot() {
        return publishedServer;
    }

    /** Logical side, including an integrated server in a client process. No per-read locks. */
    public static boolean isClientThread() { return Thread.currentThread() == clientThread; }
    public static BalanceSnapshot currentSnapshot() { return isClientThread() ? clientSnapshot() : publishedServer; }
    public static boolean isPlayerReady(ServerPlayerEntity player) {
        if (!EasyAuthBalanceCompatibility.canSynchronize(player)) return false;
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
