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
	/** 风灵「疾风连爪」：C2S 上报左键按住(boolean)；S2C 同步爪击阶段(int)+准星条进度(float)。 */
	public static final Identifier PACKET_CLAW_HOLD = new Identifier("my_addon", "claw_hold");
	public static final Identifier PACKET_CLAW_STATE = new Identifier("my_addon", "claw_state");
	/** 风灵副技能：C2S 按 sp_secondary 触发 +50% 增伤 buff。无 payload。 */
	public static final Identifier PACKET_CLAW_BUFF = new Identifier("my_addon", "claw_buff");
	/** 风灵「风之冲刺」：C2S 按主技能键（无 payload，服务端按阶段分支）；S2C 同步阶段(int)+targetY(double)。 */
	public static final Identifier PACKET_WIND_DASH = new Identifier("my_addon", "wind_dash");
	public static final Identifier PACKET_DASH_STATE = new Identifier("my_addon", "dash_state");

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
	/** C2S：月织蛛织网术 - 潜行双击主键切换 搭路/攻击 模式。无 payload。 */
	public static final Identifier PACKET_SPIDER_MOON_WEAVER_TOGGLE = new Identifier("my_addon", "spider_moon_weaver_toggle");
	/** C2S：月织蛛织网术 - 主键按下开始蓄力。无 payload。 */
	public static final Identifier PACKET_SPIDER_MOON_WEAVER_CHARGE_START = new Identifier("my_addon", "spider_moon_weaver_charge_start");
	/** C2S：月织蛛织网术 - 主键按下开始「平铺搭桥」蓄力（双击长按 / 潜行长按触发）。无 payload。 */
	public static final Identifier PACKET_SPIDER_MOON_WEAVER_CHARGE_START_FLAT = new Identifier("my_addon", "spider_moon_weaver_charge_start_flat");
	/** C2S：月织蛛织网术 - 主键松开释放。无 payload。 */
	public static final Identifier PACKET_SPIDER_MOON_WEAVER_CHARGE_RELEASE = new Identifier("my_addon", "spider_moon_weaver_charge_release");
	/** C2S：月织蛛二段跳 - 空中按跳跃键。无 payload。 */
	public static final Identifier PACKET_SPIDER_MOON_WEAVER_DOUBLE_JUMP = new Identifier("my_addon", "spider_moon_weaver_double_jump");
	/** C2S：月织蛛蛛丝荡漾 - 次键按下（发射/断丝切换）。无 payload。 */
	public static final Identifier PACKET_SPIDER_MOON_WEAVER_SWING_PRESS = new Identifier("my_addon", "spider_moon_weaver_swing_press");
	/** C2S：月织蛛蛛丝荡漾 - 摆荡中上报当前绳长 + 收放意图（服务端权威扣 mana）。payload: double ropeLen + varint reel(>0收/<0放/0无)。 */
	public static final Identifier PACKET_SPIDER_MOON_WEAVER_SWING_SYNC = new Identifier("my_addon", "spider_moon_weaver_swing_sync");
	/** S2C：月织蛛蛛丝荡漾 - 状态同步给附近玩家（销点/绳长/状态/canExtend）。payload: UUID + boolean active + 3×double anchor + double ropeLen + varint state + boolean canExtend。 */
	public static final Identifier PACKET_SPIDER_MOON_WEAVER_SWING_STATE = new Identifier("my_addon", "spider_moon_weaver_swing_state");

	/** C2S：进化美西蟠上报「真正疾跑键」按住状态（区分双击 W/游泳自动疾跑）。payload: boolean held。 */
	public static final Identifier PACKET_AXOLOTL_SPRINT_KEY = new Identifier("my_addon", "axolotl_sprint_key");

	/** S2C：踩网蓝色高亮——仅向施法者发送，令其客户端把受害者描蓝边。payload: varint entityId + varint duration。 */
	public static final Identifier PACKET_WEB_HIGHLIGHT = new Identifier("my_addon", "web_highlight");
	/** C2S：进化美西螈主技能「投掷水矛」按键。无 payload。 */
	public static final Identifier PACKET_UPGRADE_AXOLOTL_SPEAR = new Identifier("my_addon", "upgrade_axolotl_spear");
	/** C2S：进化美西螈次技能「涡流引导」按键。无 payload。 */
	public static final Identifier PACKET_UPGRADE_AXOLOTL_VORTEX_GUIDE = new Identifier("my_addon", "upgrade_axolotl_vortex_guide");

	/** S2C：动画调试记录开关切换（由 /ssc_addon debug anim 指令触发，服务端转发给执行者客户端）。无 payload。 */
	public static final Identifier PACKET_ANIM_DEBUG_TOGGLE = new Identifier("my_addon", "anim_debug_toggle");
	public static final Identifier PACKET_UPGRADE_AXOLOTL_VORTEX = new Identifier("my_addon", "upgrade_axolotl_vortex");
	/** S2C：进化美西螈「投掷水矛」蓄力期手持水矛渲染状态（对追踪者+自身广播）。payload: UUID + boolean charging */
	public static final Identifier PACKET_SPEAR_CHARGE_STATE = new Identifier("my_addon", "spear_charge_state");

	// ===== 荧光幼灵技能网络包 =====
	/** C2S：荧光幼灵主要技能（法阵激光）按键。无 payload。 */
	public static final Identifier PACKET_FLUO_LASER = new Identifier("my_addon", "fluo_laser_key");
	/** C2S：荧光幼灵次要技能（潮汐波动）按键。无 payload。 */
	public static final Identifier PACKET_FLUO_TIDAL = new Identifier("my_addon", "fluo_tidal_key");
	/** S2C：荧光幼灵「潮汐束缚」把被拴目标的 entityId 同步给客机，用于渲染守卫者激光。payload: varint orbId + varint count + count*varint entityId */
	public static final Identifier PACKET_TIDAL_TETHER = new Identifier("my_addon", "tidal_tether");

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
	/** C2S：客机加入后请求服务端把所有在场玩家的形态+皮肤同步过来（修复客机看其它玩家默认白模型）。无 payload */
	public static final Identifier PACKET_REQUEST_ALL_FORM_SYNC = new Identifier("my_addon", "request_all_form_sync");
	/** S2C：服务端广播所有在场玩家的形态 ID。payload: int count + count*(UUID + String formId) */
	public static final Identifier PACKET_BROADCAST_FORMS = new Identifier("my_addon", "broadcast_forms");
	/** S2C：把所有 SSCA 进化路线定义（JSON）同步给客户端，供进化树 UI 渲染。payload: int count + count*(routeId + rawJson) */
	public static final Identifier PACKET_EVO_ROUTES_SYNC = new Identifier("my_addon", "evo_routes_sync");
	/** S2C：灵能宝珠长按成功后，让客户端打开「转职选择形态」界面。无 payload。 */
	public static final Identifier PACKET_OPEN_JOB_CHANGE = new Identifier("my_addon", "open_job_change");
	/** C2S：玩家在转职界面选定目标进化形态并确认。payload: String formId */
	public static final Identifier PACKET_JOB_CHANGE_CONFIRM = new Identifier("my_addon", "job_change_confirm");
	/** C2S：玩家打开进化加点界面（触发旧存档迁移检测）。无 payload。 */
	public static final Identifier PACKET_EVO_OPEN = new Identifier("my_addon", "evo_open");

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

	/** 风灵「疾风连爪」：同步爪击阶段(phase)与准星条进度给客户端。 */
	public static void syncClawState(net.minecraft.server.network.ServerPlayerEntity player, int phase, float crosshairProgress) {
		net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeInt(phase);
		buf.writeFloat(crosshairProgress);
		ServerPlayNetworking.send(player, PACKET_CLAW_STATE, buf);
	}

	/** 风灵「风之冲刺」：同步阶段(phase)与目标悬浮 Y 给客户端（驱动落点预览）。 */
	public static void syncDashState(net.minecraft.server.network.ServerPlayerEntity player, int phase, double targetY) {
		net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeInt(phase);
		buf.writeDouble(targetY);
		ServerPlayNetworking.send(player, PACKET_DASH_STATE, buf);
	}

	/** 进化美西螈「投掷水矛」：向追踪该玩家的客户端 + 玩家自身广播蓄力手持水矛渲染状态。 */
	public static void syncSpearChargeState(net.minecraft.server.network.ServerPlayerEntity player, boolean charging) {
		net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeUuid(player.getUuid());
		buf.writeBoolean(charging);
		for (net.minecraft.server.network.ServerPlayerEntity viewer :
				net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, PACKET_SPEAR_CHARGE_STATE, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.copy(buf));
		}
		ServerPlayNetworking.send(player, PACKET_SPEAR_CHARGE_STATE, buf);
	}

	/** S2C：向施法者发「高亮」（默认蓝色），令其客户端把 entityId 对应实体描边 duration tick。 */
	public static void sendWebHighlight(ServerPlayerEntity owner, int entityId, int durationTicks) {
		sendWebHighlight(owner, entityId, durationTicks, 0x3AA0FF);
	}

	/** S2C：向施法者发「高亮」并指定描边颜色（RGB），仅施法者可见。 */
	public static void sendWebHighlight(ServerPlayerEntity owner, int entityId, int durationTicks, int color) {
		net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeVarInt(entityId);
		buf.writeVarInt(durationTicks);
		buf.writeInt(color);
		ServerPlayNetworking.send(owner, PACKET_WEB_HIGHLIGHT, buf);
	}

	/** S2C：月织蛛蛛丝荡漾 - 向追踪该玩家的客户端 + 玩家自身广播摆荡状态（销点/绳长/状态/canExtend）。 */
	public static void syncSwingState(ServerPlayerEntity player, boolean active,
	                                  double ax, double ay, double az, double ropeLen, int state, boolean canExtend, int tetherEntityId) {
		net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		buf.writeUuid(player.getUuid());
		buf.writeBoolean(active);
		buf.writeDouble(ax);
		buf.writeDouble(ay);
		buf.writeDouble(az);
		buf.writeDouble(ropeLen);
		buf.writeVarInt(state);
		buf.writeBoolean(canExtend);
		buf.writeInt(tetherEntityId);
		for (ServerPlayerEntity viewer :
				net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, PACKET_SPIDER_MOON_WEAVER_SWING_STATE,
					net.fabricmc.fabric.api.networking.v1.PacketByteBufs.copy(buf));
		}
		ServerPlayNetworking.send(player, PACKET_SPIDER_MOON_WEAVER_SWING_STATE, buf);
	}

	/** S2C：向执行者发「动画调试记录开关切换」，客户端收到后切换本地日志记录。 */
	public static void sendAnimDebugToggle(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, PACKET_ANIM_DEBUG_TOGGLE,
				net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
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

		// SSCA 月织蛛「织网术」- 切换模式 / 开始蓄力 / 释放
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SPIDER_MOON_WEAVER_TOGGLE, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverWebManager.toggleMode(player));
		});
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SPIDER_MOON_WEAVER_CHARGE_START, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverWebManager.start(player));
		});
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SPIDER_MOON_WEAVER_CHARGE_START_FLAT, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverWebManager.startFlat(player));
		});
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SPIDER_MOON_WEAVER_CHARGE_RELEASE, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverWebManager.release(player));
		});
		// SSCA 月织蛛二段跳 - 空中按跳跃键触发（跳跃由客户端原版 jump() 完成，此处仅广播音效粒子）
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SPIDER_MOON_WEAVER_DOUBLE_JUMP, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverDoubleJumpManager.onDoubleJump(player));
		});
		// SSCA 月织蛛蛛丝荡漾 - 次键按下（发射 / 断丝切换）
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SPIDER_MOON_WEAVER_SWING_PRESS, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.onSecondaryPress(player));
		});
		// SSCA 月织蛛蛛丝荡漾 - 摆荡中上报绳长 + 收放意图（服务端权威扣 mana）
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SPIDER_MOON_WEAVER_SWING_SYNC, (server, player, handler, buf, responseSender) -> {
			double ropeLen = buf.readDouble();
			int reel = buf.readVarInt();
			server.execute(() -> net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager.onReelSync(player, ropeLen, reel));
		});

		// SSCA 进化美西蟠水流冲刺 - 真正疾跑键按住状态上报
		ServerPlayNetworking.registerGlobalReceiver(PACKET_AXOLOTL_SPRINT_KEY, (server, player, handler, buf, responseSender) -> {
			boolean held = buf.readBoolean();
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AxolotlWaterSpurtHandler.setClientSprintHeld(player, held));
		});

		// SSCA 进化美西螈技能：主「投掷水矛」 / 次「涡流引导」
		ServerPlayNetworking.registerGlobalReceiver(PACKET_UPGRADE_AXOLOTL_SPEAR, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WaterSpearLeapManager.onKeyPress(player));
		});
		ServerPlayNetworking.registerGlobalReceiver(PACKET_UPGRADE_AXOLOTL_VORTEX, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexGuideManager.onKeyPress(player));
		});

		// 风灵「疾风连爪」：客户端上报左键按住状态
		ServerPlayNetworking.registerGlobalReceiver(PACKET_CLAW_HOLD, (server, player, handler, buf, responseSender) -> {
			boolean hold = buf.readBoolean();
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritClawManager.setHolding(player, hold));
		});

		// 风灵副技能：sp_secondary 触发 +50% 增伤 buff
		ServerPlayNetworking.registerGlobalReceiver(PACKET_CLAW_BUFF, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritClawManager.activateSecondaryBuff(player));
		});

		// 风灵「风之冲刺」：主技能键（服务端按当前阶段分支：起飞 / 悬浮中冲刺）
		ServerPlayNetworking.registerGlobalReceiver(PACKET_WIND_DASH, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindDashManager.onKeyPress(player));
		});

		// 荧光幼灵技能按键：主要（法阵激光）/ 次要（潮汐波动）
		ServerPlayNetworking.registerGlobalReceiver(PACKET_FLUO_LASER, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentLaserManager.onKeyPress(player));
		});
		ServerPlayNetworking.registerGlobalReceiver(PACKET_FLUO_TIDAL, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentTidalManager.onKeyPress(player));
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

		// 灵能宝珠转职：玩家在转职界面选定目标进化形态并确认
		ServerPlayNetworking.registerGlobalReceiver(PACKET_JOB_CHANGE_CONFIRM, (server, player, handler, buf, responseSender) -> {
			String formId = buf.readString(256);
			server.execute(() -> {
				if (isRateLimited(player)) return;
				EvolutionManager.startJobChange(player, formId);
			});
		});

		// 打开进化加点界面：检测旧存档并迁移到内置 exp 机制
		ServerPlayNetworking.registerGlobalReceiver(PACKET_EVO_OPEN, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> {
				if (isRateLimited(player)) return;
				EvolutionManager.checkAndMigrate(player);
			});
		});

		// 客机加入后请求：把所有在场玩家的形态 ID + 皮肤数据广播给「所有在线玩家」（含请求者与已在线客机），
		// 绕过 CCA 同步的不确定性，修复刚进游戏 / 新玩家加入时看其它玩家是默认白模型。
		// 形态模型由客机据 formId 重建 origin 决定，颜色据皮肤数据上色。
		ServerPlayNetworking.registerGlobalReceiver(PACKET_REQUEST_ALL_FORM_SYNC, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> {
				java.util.List<net.minecraft.server.network.ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
				for (net.minecraft.server.network.ServerPlayerEntity recipient : players) {
					net.minecraft.network.PacketByteBuf out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
					out.writeInt(players.size());
					for (net.minecraft.server.network.ServerPlayerEntity p : players) {
						out.writeUuid(p.getUuid());
						net.minecraft.util.Identifier fid =
								net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent.PLAYER_FORM.get(p).nowFormID;
						out.writeString(fid == null ? "" : fid.toString());
						// 皮肤数据：保留原皮 / 是否启用形态颜色 / 五种颜色(ABGR) / 灰度反转 / 随机音效
						net.onixary.shapeShifterCurseFabric.player_form.skin.PlayerSkinComponent skin =
								net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent.SKIN_SETTINGS.get(p);
						out.writeBoolean(skin.shouldKeepOriginalSkin());
						out.writeBoolean(skin.isEnableFormColor());
						net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.ColorSetting c = skin.getFormColor();
						out.writeInt(c.getPrimaryColor());
						out.writeInt(c.getAccentColor1());
						out.writeInt(c.getAccentColor2());
						out.writeInt(c.getEyeColorA());
						out.writeInt(c.getEyeColorB());
						out.writeBoolean(c.getPrimaryGreyReverse());
						out.writeBoolean(c.getAccent1GreyReverse());
						out.writeBoolean(c.getAccent2GreyReverse());
						out.writeBoolean(skin.isEnableFormRandomSound());
					}
					ServerPlayNetworking.send(recipient, PACKET_BROADCAST_FORMS, out);
				}
				// 同步 SSCA 进化路线定义给请求者（客户端进化树 UI 渲染需要，多人环境客户端无 datapack 数据）
				net.minecraft.network.PacketByteBuf routesOut = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
				java.util.Map<String, String> rawRoutes =
						net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRegistry.INSTANCE.getRawJson();
				routesOut.writeInt(rawRoutes.size());
				for (java.util.Map.Entry<String, String> e : rawRoutes.entrySet()) {
					routesOut.writeString(e.getKey(), 256);
					routesOut.writeString(e.getValue(), 2000000);
				}
				ServerPlayNetworking.send(player, PACKET_EVO_ROUTES_SYNC, routesOut);
			});
		});
	}

	/** 服务端：通知客户端打开灵能宝珠「转职选择形态」界面（无 payload）。 */
	public static void sendOpenJobChange(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, PACKET_OPEN_JOB_CHANGE, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
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
