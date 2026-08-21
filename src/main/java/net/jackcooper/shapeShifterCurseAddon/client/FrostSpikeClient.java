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
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.SscAddonKeybindings;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity;

/**
 * 寒棘狐「冰刺」客户端按键检测器：单主技能键（sp_primary）区分长按 / 点按。
 * <ul>
 *   <li><b>长按（超过 {@value #HOLD_MS} ms）</b>：发 CHARGE_START 开始蓄力，松开发 CHARGE_RELEASE 停止。</li>
 *   <li><b>点按（{@value #HOLD_MS} ms 内松开）</b>：发 FIRE 发射一根冰锥。</li>
 * </ul>
 * 仅寒棘狐形态生效；所有判定与结算在服务端。
 */
@Environment(EnvType.CLIENT)
public final class FrostSpikeClient {

	/** 长按判定阈值：按住超过此毫秒数 → 确认为长按蓄力，否则视为点按发射。 */
	private static final long HOLD_MS = 250L;

	private static boolean wasPressed = false;
	private static boolean startSent = false; // 本次按住是否已发 CHARGE_START
	private static long pressDownMs = 0L;
	// 凝棘（次技能）
	private static boolean secWasPressed = false;
	private static boolean secondaryCharging = false; // 供 ViewRateLimitMixin 判定视角限速

	private FrostSpikeClient() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FrostSpikeClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		KeyBinding key = SscAddonKeybindings.getPrimaryKey();
		KeyBinding secKey = SscAddonKeybindings.getSecondaryKey();
		if (player == null || client.world == null || key == null
				|| !FormUtils.isForm(player, FormIdentifiers.SNOW_FOX_FROSTSPINE)) {
			// 形态切走 / 失焦时若正在蓄力，补发一次 RELEASE 收尾，避免服务端残留蓄力态
			if (wasPressed && startSent) send(SscAddonNetworking.PACKET_FROST_SPIKE_CHARGE_RELEASE);
			if (secondaryCharging) { send(SscAddonNetworking.PACKET_FROST_SPIKE_SECONDARY_RELEASE); secondaryCharging = false; }
			wasPressed = false;
			startSent = false;
			secWasPressed = false;
			return;
		}
		boolean pressed = key.isPressed();
		long now = System.currentTimeMillis();
		if (pressed && !wasPressed) {
			// 按下沿
			pressDownMs = now;
			startSent = false;
		} else if (pressed) {
			// 持续按住超过阈值 → 确认长按蓄力
			if (!startSent && (now - pressDownMs) >= HOLD_MS) {
				send(SscAddonNetworking.PACKET_FROST_SPIKE_CHARGE_START);
				startSent = true;
			}
		} else if (wasPressed) {
			// 松开沿：长按→停止蓄力；点按→发射
			if (startSent) {
				send(SscAddonNetworking.PACKET_FROST_SPIKE_CHARGE_RELEASE);
			} else {
				send(SscAddonNetworking.PACKET_FROST_SPIKE_FIRE);
			}
		}
		wasPressed = pressed;

		// ===== 凝棘（次技能）：按住蓄力（本地有环绕冰锥才进入），松开发射 =====
		boolean secPressed = secKey != null && secKey.isPressed();
		if (secPressed && !secWasPressed) {
			// 按下沿：本地有环绕冰锥才进入蓄力（与服务端判定同源，避免无冰锥误限视角/误发包）
			if (hasHoverThorn(player)) {
				secondaryCharging = true;
				send(SscAddonNetworking.PACKET_FROST_SPIKE_SECONDARY_START);
			}
		} else if (!secPressed && secWasPressed && secondaryCharging) {
			// 松开沿：发射强化冰锥
			send(SscAddonNetworking.PACKET_FROST_SPIKE_SECONDARY_RELEASE);
			secondaryCharging = false;
		}
		secWasPressed = secPressed;
	}

	/** 供 ViewRateLimitMixin 判定：本地玩家是否正在凝棘蓄力（蓄力期视角平滑限速）。 */
	public static boolean isSecondaryCharging() {
		return secondaryCharging;
	}

	/** 本地是否有环绕冰锥（HOVER 态且主人为本地玩家）——决定按次键能否进入蓄力。 */
	private static boolean hasHoverThorn(ClientPlayerEntity player) {
		return !player.getWorld().getEntitiesByClass(FrostThornEntity.class,
				player.getBoundingBox().expand(3.0),
				t -> t.isHover() && player.getUuid().equals(t.getOwnerUuid().orElse(null))).isEmpty();
	}

	private static void send(Identifier packet) {
		ClientPlayNetworking.send(packet, new PacketByteBuf(Unpooled.buffer()));
	}
}
