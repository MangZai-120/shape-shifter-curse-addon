package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

/**
 * 月织蛛「织网术」- 客户端按键检测器。
 * 仅月织蛛形态生效，单主键（sp_primary）多手势。所有按下都先延迟 HOLD_MS 判定短按/长按，
 * 以正确区分「单击长按」与「双击长按」（避免双击首击误触发蛛丝弹）：
 * <ul>
 *   <li><b>不潜行单击长按 → 松开</b>：发射蛛丝弹（WebBullet）。</li>
 *   <li><b>不潜行双击后按住（长按）→ 松开</b>：脚下平铺搭桥。</li>
 *   <li><b>潜行按住（长按）→ 松开</b>：脚下平铺搭桥。</li>
 *   <li><b>潜行双击</b>：切换 搭路 / 攻击 模式。</li>
 * </ul>
 * 短按（HOLD_MS 内松开）不发蓄力包，仅记为双击候选；长按（超 HOLD_MS 仍在按）按类型发
 * START / START_FLAT。所有判定与结算在服务端。
 */
@Environment(EnvType.CLIENT)
public final class SpiderMoonWeaverWebClient {

	/** 长按判定阈值：按住超过此毫秒数 → 确认为长按蓄力（否则为短按，进入双击判定）。 */
	private static final long HOLD_MS = 250L;
	/** 双击窗口：两次短按间隔在此毫秒数内 → 视为双击（切换模式 / 双击长按平铺）。 */
	private static final long DOUBLE_CLICK_MS = 300L;

	private static boolean wasPressed = false;
	private static boolean startSent = false;         // 本次按住是否已发 START/START_FLAT
	private static boolean flatCandidate = false;     // 本次按下是否为「平铺」候选（潜行 / 双击长按第二下）
	private static long pressDownMs = 0L;             // 本次按下时刻
	private static boolean pressWhileSneaking = false;// 本次按下时是否潜行
	private static long lastSneakClickMs = -1L;       // 上一次潜行短按时刻（潜行双击切换判定）
	private static long lastNormalClickMs = -1L;      // 上一次不潜行短按时刻（双击长按平铺判定）

	private SpiderMoonWeaverWebClient() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpiderMoonWeaverWebClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		KeyBinding key = SscAddonKeybindings.getPrimaryKey();
		if (player == null || client.world == null || key == null || !FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER)) {
			wasPressed = false;
			startSent = false;
			return;
		}
		boolean pressed = key.isPressed();
		long now = System.currentTimeMillis();
		if (pressed && !wasPressed) {
			// 按下沿：记录起手状态，并在此时判定是否平铺候选（窗口判定不受后续 HOLD_MS 影响）
			pressDownMs = now;
			pressWhileSneaking = player.isSneaking();
			startSent = false;
			if (pressWhileSneaking) {
				flatCandidate = true; // 潜行+主键 → 平铺
			} else if (lastNormalClickMs >= 0 && (now - lastNormalClickMs) <= DOUBLE_CLICK_MS) {
				flatCandidate = true; // 不潜行双击的第二下 → 平铺
				lastNormalClickMs = -1L; // 消费双击首击
			} else {
				flatCandidate = false; // 单击 → 蛛丝弹
			}
		} else if (pressed) {
			// 持续按住超过阈值 → 确认长按，按按下沿判定的类型发蓄力包
			if (!startSent && (now - pressDownMs) >= HOLD_MS) {
				if (flatCandidate) {
					SscAddonNetworking.sendEmpty(SscAddonNetworking.PACKET_SPIDER_MOON_WEAVER_CHARGE_START_FLAT); // 平铺
				} else {
					SscAddonNetworking.sendEmpty(SscAddonNetworking.PACKET_SPIDER_MOON_WEAVER_CHARGE_START);        // 蛛丝弹
				}
				startSent = true;
				lastSneakClickMs = -1L; // 转为长按，作废未决的双击切换首击
			}
		} else if (wasPressed) {
			// 松开沿
			if (startSent) {
				SscAddonNetworking.sendEmpty(SscAddonNetworking.PACKET_SPIDER_MOON_WEAVER_CHARGE_RELEASE);
			} else if (pressWhileSneaking) {
				// 潜行短按 → 双击切换模式判定
				if (lastSneakClickMs >= 0 && (now - lastSneakClickMs) <= DOUBLE_CLICK_MS) {
					SscAddonNetworking.sendEmpty(SscAddonNetworking.PACKET_SPIDER_MOON_WEAVER_TOGGLE);
					lastSneakClickMs = -1L;
				} else {
					lastSneakClickMs = now;
				}
			} else {
				// 不潜行短按 → 记为双击长按首击（等待第二下）
				lastNormalClickMs = now;
			}
		}
		wasPressed = pressed;
	}

}
