package net.onixary.shapeShifterCurseFabric.ssc_addon.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaTeleport;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPrimary;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
//import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.Ability_AllayHeal;


public class SscAddonNetworking {
	public static final Identifier PACKET_KEY_PRESS = new Identifier("my_addon", "key_press");
	/** 契灵 - 次要技能：瞬移。payload: byte mode (0=RAYCAST, 1=PLATFORM) */
	public static final Identifier PACKET_MANCIANIMA_TELEPORT = new Identifier("my_addon", "mancianima_teleport");
	/** 契灵 - 主要技能：三段标记。无 payload，服务端根据当前状态分支。 */
	public static final Identifier PACKET_MANCIANIMA_PRIMARY = new Identifier("my_addon", "mancianima_primary");

	// ===== 白名单 GUI 网络包 =====
	/** S2C：服务端把调用者当前白名单 UUID 集合推给客户端，用于打开/刷新 GUI。payload: int n + n*UUID */
	public static final Identifier PACKET_WHITELIST_GUI_SYNC = new Identifier("my_addon", "whitelist_gui_sync");
	/** C2S：玩家在 GUI 中请求把某 UUID 加入自己的白名单。payload: UUID */
	public static final Identifier PACKET_WHITELIST_GUI_ADD = new Identifier("my_addon", "whitelist_gui_add");
	/** C2S：玩家在 GUI 中请求把某 UUID 从自己的白名单移除。payload: UUID */
	public static final Identifier PACKET_WHITELIST_GUI_REMOVE = new Identifier("my_addon", "whitelist_gui_remove");
	/** C2S：玩家切换模式。payload: byte (0=默认, 1=自定义) */
	public static final Identifier PACKET_WHITELIST_GUI_MODE = new Identifier("my_addon", "whitelist_gui_mode");
	/** C2S：玩家从生物白名单中移除一个 UUID。payload: UUID */
	public static final Identifier PACKET_WHITELIST_GUI_MOB_REMOVE = new Identifier("my_addon", "whitelist_gui_mob_remove");

	/** C2S：美西螈装死期间按技能键请求提前结束装死。无 payload。 */
	public static final Identifier PACKET_PLAY_DEAD_END = new Identifier("my_addon", "play_dead_end");

	/** C2S：美西螈漩涡开始蓄力。无 payload。 */
	public static final Identifier PACKET_VORTEX_START = new Identifier("my_addon", "vortex_start");
	/** C2S：美西螈漩涡释放（提前释放）。无 payload。 */
	public static final Identifier PACKET_VORTEX_RELEASE = new Identifier("my_addon", "vortex_release");

	// ===== SSCA 进化加点系统网络包（框架） =====
	/** C2S：玩家选择进化路线。payload: String routeId */
	public static final Identifier PACKET_EVO_SELECT_ROUTE = new Identifier("my_addon", "evo_select_route");
	/** C2S：玩家选择 SP 分支。payload: String branchId */
	public static final Identifier PACKET_EVO_SELECT_BRANCH = new Identifier("my_addon", "evo_select_branch");
	/** C2S：玩家请求解锁一个天赋节点。payload: String nodeId */
	public static final Identifier PACKET_EVO_UNLOCK = new Identifier("my_addon", "evo_unlock");
	/** C2S：一次性提交多个待确认节点（按点击顺序），服务端限频一次后顺序逐个解锁。payload: int count + count*String */
	public static final Identifier PACKET_EVO_UNLOCK_BATCH = new Identifier("my_addon", "evo_unlock_batch");
	/** C2S：开局选形态界面选定一个 SSCA 进化形态、直接走 SSCA 路线进化。payload: String formId */
	public static final Identifier PACKET_SSCA_START_ROUTE = new Identifier("my_addon", "ssca_start_route");

	/** C2S 限频：每玩家每个事件类型记录上一次服务端接收时间，防外挂客户端 spam。 */
	private static final Map<UUID, Long> LAST_WHITELIST_PACKET_TICK = new ConcurrentHashMap<>();
	/** 同一玩家两次白名单操作的最小间隔，单位：millis。 */
	private static final long WHITELIST_PACKET_MIN_INTERVAL_MS = 100L;

	/**
	 * 检查玩家是否在限频阈值内 spam。返回 true 表示该包应被丢弃。
	 * 使用 ConcurrentHashMap 保证多玩家环境下线程安全。
	 */
	private static boolean isRateLimited(ServerPlayerEntity player) {
		long now = System.currentTimeMillis();
		Long last = LAST_WHITELIST_PACKET_TICK.put(player.getUuid(), now);
		return last != null && (now - last) < WHITELIST_PACKET_MIN_INTERVAL_MS;
	}

	/** 玩家退服时调用：清理限频时间戳，防止僵尸 UUID 长期积累。 */
	public static void onPlayerDisconnect(UUID uuid) {
		LAST_WHITELIST_PACKET_TICK.remove(uuid);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(PACKET_KEY_PRESS, (server, player, handler, buf, responseSender) -> {
			int keyId = buf.readInt();
			server.execute(() -> handleKeyPress(player, keyId));
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_MANCIANIMA_TELEPORT, (server, player, handler, buf, responseSender) -> {
			byte mode = buf.readByte();
			server.execute(() -> MancianimaTeleport.execute(player, mode));
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_MANCIANIMA_PRIMARY, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> MancianimaPrimary.execute(player));
		});

		// 白名单 GUI - 添加
		ServerPlayNetworking.registerGlobalReceiver(PACKET_WHITELIST_GUI_ADD, (server, player, handler, buf, responseSender) -> {
			UUID target = buf.readUuid();
			server.execute(() -> {
				if (isRateLimited(player)) return; // 防 spam
				if (target.equals(player.getUuid())) return; // 不允许把自己加入自己的白名单
				// 限制单玩家白名单总容量，防恶意客户端纯增加坚持性 tag 撑爆服务端存储
				String tag = AllaySPGroupHeal.WHITELIST_TAG_PREFIX + target.toString();
				long existing = player.getCommandTags().stream()
					.filter(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX)).count();
				if (!player.getCommandTags().contains(tag) && existing >= 256L) return; // 每人最多 256 个
				player.getCommandTags().add(tag);
				sendWhitelistSync(player);
			});
		});

		// 白名单 GUI - 移除
		ServerPlayNetworking.registerGlobalReceiver(PACKET_WHITELIST_GUI_REMOVE, (server, player, handler, buf, responseSender) -> {
			UUID target = buf.readUuid();
			server.execute(() -> {
				if (isRateLimited(player)) return;
				String tag = AllaySPGroupHeal.WHITELIST_TAG_PREFIX + target.toString();
				player.getCommandTags().remove(tag);
				sendWhitelistSync(player);
			});
		});

		// 白名单 GUI - 切换默认/自定义模式
		ServerPlayNetworking.registerGlobalReceiver(PACKET_WHITELIST_GUI_MODE, (server, player, handler, buf, responseSender) -> {
			byte mode = buf.readByte();
			server.execute(() -> {
				if (isRateLimited(player)) return;
				WhitelistUtils.setCustomMode(player, mode == 1);
				sendWhitelistSync(player);
			});
		});

		// 白名单 GUI - 生物移除
		ServerPlayNetworking.registerGlobalReceiver(PACKET_WHITELIST_GUI_MOB_REMOVE, (server, player, handler, buf, responseSender) -> {
			UUID mobUuid = buf.readUuid();
			server.execute(() -> {
				if (isRateLimited(player)) return;
				WhitelistUtils.removeMobFromWhitelist(player, mobUuid);
				// 同时清掉友军标记双写时写入的 player 前缀 tag，避免残留导致 count 不一致
				AllaySPGroupHeal.removeFromWhitelistByUuid(player, mobUuid);
				sendWhitelistSync(player);
			});
		});

		// SSCA 美西螈装死 - 提前结束（装死期间按 sp_secondary）
		ServerPlayNetworking.registerGlobalReceiver(PACKET_PLAY_DEAD_END, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> {
				if (!player.hasStatusEffect(net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.PLAYING_DEAD)) return;
				player.removeStatusEffect(net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.PLAYING_DEAD);
				player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
				player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
				player.setPose(net.minecraft.entity.EntityPose.STANDING);
				// 提前结束：CD 从此刻起算 25 秒
				net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils.setResourceValueAndSync(player, net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.SP_SECONDARY_CD, 500);
			});
		});

		// SSCA 美西螈漩涡蓄力 - 开始 / 释放
		ServerPlayNetworking.registerGlobalReceiver(PACKET_VORTEX_START, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexChargeManager.start(player));
		});
		ServerPlayNetworking.registerGlobalReceiver(PACKET_VORTEX_RELEASE, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexChargeManager.release(player));
		});

		// ===== SSCA 进化加点系统 =====
		ServerPlayNetworking.registerGlobalReceiver(PACKET_EVO_SELECT_ROUTE, (server, player, handler, buf, responseSender) -> {
			String routeId = buf.readString(256);
			server.execute(() -> {
				if (isRateLimited(player)) return;
				EvolutionManager.selectRoute(player, routeId);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_EVO_SELECT_BRANCH, (server, player, handler, buf, responseSender) -> {
			String branchId = buf.readString(256);
			server.execute(() -> {
				if (isRateLimited(player)) return;
				EvolutionManager.selectBranch(player, branchId);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_EVO_UNLOCK, (server, player, handler, buf, responseSender) -> {
			String nodeId = buf.readString(256);
			server.execute(() -> {
				if (isRateLimited(player)) return;
				EvolutionManager.tryUnlock(player, nodeId);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(PACKET_EVO_UNLOCK_BATCH, (server, player, handler, buf, responseSender) -> {
			int count = Math.max(0, Math.min(64, buf.readInt()));
			java.util.List<String> ids = new java.util.ArrayList<>(count);
			for (int i = 0; i < count; i++) ids.add(buf.readString(256));
			server.execute(() -> {
				if (isRateLimited(player)) return;
				for (String id : ids) EvolutionManager.tryUnlock(player, id);
			});
		});

		// 开局选形态界面：直接走 SSCA 进化路线进入选定形态
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SSCA_START_ROUTE, (server, player, handler, buf, responseSender) -> {
			String formId = buf.readString(256);
			server.execute(() -> {
				if (isRateLimited(player)) return;
				EvolutionManager.startSscaRoute(player, formId);
			});
		});
	}

	/** 服务端：把指定玩家当前白名单推送到其客户端，用于打开/刷新白名单 GUI。 */
	public static void sendWhitelistSync(ServerPlayerEntity player) {
		List<UUID> uuids = AllaySPGroupHeal.getWhitelistUuids(player);
		List<UUID> mobs = WhitelistUtils.getWhitelistedMobUuids(player);
		// 去重：生物 UUID 同时存于玩家前缀时（双写机制产生），不要出现在玩家 tab
		java.util.Set<UUID> mobSet = new java.util.HashSet<>(mobs);
		List<UUID> filteredPlayers = new java.util.ArrayList<>();
		for (UUID u : uuids) if (!mobSet.contains(u)) filteredPlayers.add(u);
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeBoolean(WhitelistUtils.isCustomMode(player));
		buf.writeInt(filteredPlayers.size());
		for (UUID u : filteredPlayers) buf.writeUuid(u);
		// 生物列表：int n + n * (UUID + String typeId or "")
		buf.writeInt(mobs.size());
		for (UUID u : mobs) {
			buf.writeUuid(u);
			String typeId = WhitelistUtils.getMobTypeId(player, u);
			buf.writeString(typeId != null ? typeId : "");
		}
		ServerPlayNetworking.send(player, PACKET_WHITELIST_GUI_SYNC, buf);
	}

	private static void handleKeyPress(ServerPlayerEntity player, int keyId) {
		// Find current form
		IForm form = FormUtils.getCurrentForm(player);
		if (form == null) return;

		// formId 暂未使用（旧 Ability_AllayHeal 已废弃），保留 form 引用以便后续按形态分发
		// Allay Heal (using keyId 1 for now, mapped from client)
        /*if (keyId == 1 && (formId.getPath().equals("form_allay_sp") || formId.getPath().equals("allay_sp"))) {
             Ability_AllayHeal.onHold(player);
        }*/

		// Add other key handlers here if needed (e.g. Fox Fire)
	}
}
