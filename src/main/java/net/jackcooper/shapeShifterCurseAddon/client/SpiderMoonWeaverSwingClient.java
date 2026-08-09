package net.jackcooper.shapeShifterCurseAddon.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.SscAddonKeybindings;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 月织蛛「蛛丝荡漾」次技能 - 客户端输入采集 + 状态镜像。
 *
 * <p><b>职责</b>：
 * <ol>
 *   <li>检测次键（sp_secondary）按下边沿 → 发 {@code SWING_PRESS}（发射 / 断丝切换）。</li>
 *   <li>本地玩家摆荡中每 tick 上报当前绳长 + 收放意图 → 发 {@code SWING_SYNC}（服务端权威扣 mana）。</li>
 *   <li>维护所有摆荡玩家的状态镜像（销点 / 绳长 / 状态 / canExtend），供 {@code SwingPhysicsMixin}
 *       本地物理 与 {@code SpiderMoonWeaverSwingRenderer} 渲染绳索使用。</li>
 * </ol>
 *
 * <p>本地玩家的 {@code ropeLen} 由物理 mixin 本地权威维护（收放绳即时响应）；服务端广播的 ropeLen
 * 仅在「刚进入摆荡」时用于初始化，之后本地忽略。其他玩家的 ropeLen 全用服务端广播值渲染。
 */
@Environment(EnvType.CLIENT)
public final class SpiderMoonWeaverSwingClient {

	public static final int STATE_IDLE = 0;
	public static final int STATE_FIRING = 1;
	public static final int STATE_SWINGING = 2;
	public static final int STATE_TETHER = 3;

	/** 单个玩家的摆荡数据（本地玩家的即为物理 mixin 读写的权威源）。 */
	public static final class LocalSwing {
		public boolean active;
		public int state;
		public double anchorX, anchorY, anchorZ;
		public int tetherEntityId = -1; // TETHER 目标实体 id
		public double ropeLen;
		public boolean canExtend = true;
		public int reelIntent; // 本 tick 收放意图：+1 收 / -1 放 / 0 无
		public Vec3d swingVel = Vec3d.ZERO; // 自维护的摆锤速度（绕开原版空气阻力，惯性守恒）
		public boolean physicsStarted; // 是否已用玩家初速初始化摆锤速度
	}

	private static final Map<UUID, LocalSwing> DATA = new ConcurrentHashMap<>();
	private static boolean wasSecondaryPressed = false;

	private SpiderMoonWeaverSwingClient() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpiderMoonWeaverSwingClient::onClientTick);
	}

	/** 本地玩家摆荡数据（供物理 mixin 读写，可能 null）。 */
	public static LocalSwing getLocalSwing() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return null;
		return DATA.get(mc.player.getUuid());
	}

	/** 本地玩家是否正在摆荡（SWINGING 阶段，供物理 mixin 输入拦截判断）。 */
	public static boolean isLocalSwinging() {
		LocalSwing s = getLocalSwing();
		return s != null && s.active && s.state == STATE_SWINGING;
	}

	/** 本地玩家蛛丝是否挂着（FIRING 或 SWINGING，用于全程屏蔽空格跳跃）。 */
	public static boolean isLocalActive() {
		LocalSwing s = getLocalSwing();
		return s != null && s.active;
	}

	/** 遍历所有摆荡玩家（供渲染器逐帧画绳索）。 */
	public static Iterator<Map.Entry<UUID, LocalSwing>> iterator() {
		return DATA.entrySet().iterator();
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) return;
		KeyBinding secondaryKey = SscAddonKeybindings.getSecondaryKey();
		if (secondaryKey == null) return;

		if (!FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER)) {
			wasSecondaryPressed = false;
			return;
		}

		boolean pressed = secondaryKey.isPressed();
		if (pressed && !wasSecondaryPressed) {
			sendPress();
		}
		wasSecondaryPressed = pressed;

		// 摆荡中每 tick 上报绳长 + 收放意图（服务端权威扣 mana）
		LocalSwing s = DATA.get(player.getUuid());
		if (s != null && s.active && s.state == STATE_SWINGING) {
			sendSync(s.ropeLen, s.reelIntent);
		}
	}

	private static void sendPress() {
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPIDER_MOON_WEAVER_SWING_PRESS,
				new PacketByteBuf(Unpooled.buffer()));
	}

	private static void sendSync(double ropeLen, int reel) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeDouble(ropeLen);
		buf.writeVarInt(reel);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPIDER_MOON_WEAVER_SWING_SYNC, buf);
	}

	/** 收到服务端 S2C 状态同步：更新镜像。 */
	public static void onStateSync(UUID uuid, boolean active, double ax, double ay, double az,
	                               double ropeLen, int state, boolean canExtend, int tetherEntityId) {
		if (!active) {
			DATA.remove(uuid);
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean isLocal = mc.player != null && mc.player.getUuid().equals(uuid);
		LocalSwing s = DATA.computeIfAbsent(uuid, k -> new LocalSwing());
		boolean wasActive = s.active && (s.state == STATE_SWINGING || s.state == STATE_TETHER);
		s.active = true;
		s.anchorX = ax;
		s.anchorY = ay;
		s.anchorZ = az;
		s.state = state;
		s.canExtend = canExtend;
		s.tetherEntityId = tetherEntityId;
		if (isLocal) {
			// 本地玩家 ropeLen 本地权威：仅在刚进入摆荡/tether 时用广播值初始化
			if (!wasActive && (state == STATE_SWINGING || state == STATE_TETHER)) {
				s.ropeLen = ropeLen;
				s.physicsStarted = false;
			}
		} else {
			s.ropeLen = ropeLen;
		}
	}

	public static void onDisconnect(UUID uuid) {
		DATA.remove(uuid);
	}
}
