package net.jackcooper.shapeShifterCurseAddon.client;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 月织蛛动画调试 - 日志记录器。
 *
 * <p>由指令 {@code /ssc_addon debug anim} 开关（客户端指令切换）。
 * 开启期间每 tick 检测动画切换并写入游戏日志（搜 SSCA_AnimDebug），包含当前动画名、播放进度(tick/总长)、
 * velY、fallDistance、onGround、airTicks、sprint、二段跳额度——便于事后分析二段跳 jump→fall 衔接时序。
 *
 * <p>另提供 {@link #logSnapshotOnce} 供指令输出单条快照，以及 {@link #findActivePlayerFromStack}
 * 供二段跳动画修复逻辑读取当前播放动画。
 *
 * <p>纯客户端、仅调试用；不影响任何游戏逻辑。
 */
@Environment(EnvType.CLIENT)
public final class SpiderMoonWeaverAnimDebugHud {

	private static final Logger LOGGER = LoggerFactory.getLogger("SSCA_AnimDebug");
	/** 日志记录是否已开启（由 /ssc_addon debug anim 指令切换）。 */
	private static boolean recording = false;
	/** 上一 tick 的动画名 + tick，用于检测动画切换并写日志。 */
	private static String lastAnimName = null;
	private static int lastAnimTick = -1;

	private SpiderMoonWeaverAnimDebugHud() {}

	/** 注册客户端 tick 监听（检测动画切换写日志）。 */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpiderMoonWeaverAnimDebugHud::onClientTick);
	}

	/** 指令调用：切换日志记录开关，返回切换后的状态（true=已开启）。 */
	public static boolean toggleRecording() {
		recording = !recording;
		if (!recording) {
			// 关闭时重置状态，下次开启从干净状态开始
			lastAnimName = null;
			lastAnimTick = -1;
		}
		return recording;
	}

	/** 指令调用：无论是否开启记录，都向日志输出一条完整快照。 */
	public static void logSnapshotOnce(String action) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return;
		logSnapshot(player, action);
	}

	private static void onClientTick(MinecraftClient client) {
		if (!recording || client.player == null) return;
		// 检测动画切换并写日志（便于分析二段跳衔接时序）
		String cur = readCurrentAnimation(client.player);
		String shortName = cur.contains("tick=") ? cur.substring(0, cur.indexOf("  tick=")) : cur;
		int curTick = extractTick(cur);
		if (!shortName.equals(lastAnimName)) {
			// 动画切换：记录前后对比 + 物理状态
			logSnapshot(client.player, "动画切换: [" + lastAnimName + "@tick" + lastAnimTick
					+ "] -> [" + shortName + "@tick" + curTick + "]");
			lastAnimName = shortName;
			lastAnimTick = curTick;
		} else if (curTick != lastAnimTick) {
			lastAnimTick = curTick;
		}
	}

	/** 从 readCurrentAnimation 的输出里提取 tick=后的整数。 */
	private static int extractTick(String animInfo) {
		try {
			int i = animInfo.indexOf("tick=");
			int j = animInfo.indexOf('/', i + 5);
			return Integer.parseInt(animInfo.substring(i + 5, j));
		} catch (Exception e) {
			return -1;
		}
	}

	/** 向游戏日志写一条完整快照：动作描述 + 当前动画 + 物理状态。 */
	private static void logSnapshot(ClientPlayerEntity player, String action) {
		String anim = readCurrentAnimation(player);
		Vec3d v = player.getVelocity();
		LOGGER.info("[{}] anim=[{}] velY={} fallDist={} onGround={} airTicks={} sprint={} djAvail={}",
				action, anim, String.format("%.3f", v.getY()),
				String.format("%.3f", player.fallDistance), player.isOnGround(),
				SpiderMoonWeaverDoubleJumpClient.getAirTicks(), player.isSprinting(),
				SpiderMoonWeaverDoubleJumpClient.isJumpAvailable());
	}

	/** 通过 KosmX API 读玩家当前播放的动画名 + 进度。 */
	public static String readCurrentAnimation(ClientPlayerEntity player) {
		try {
			AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
			// AnimationStack.layers 是私有字段，用反射取出所有动画层，遍历找当前 active 的 KeyframeAnimationPlayer
			KeyframeAnimationPlayer kp = findActivePlayerFromStack(stack);
			if (kp == null) return "无动画 / T-pose";
			KeyframeAnimation data = kp.getData();
			String name = "未知动画";
			if (data != null && data.extraData != null) {
				// 动画名通常存在 extraData 的 "name" key（Emotecraft/KosmX 约定）
				Object n = data.extraData.get("name");
				if (n == null) n = data.extraData.get("animation_name");
				if (n instanceof String s && !s.isEmpty()) name = s;
				else name = "uuid=" + data.getUuid();
			}
			int tick = kp.getCurrentTick();
			int total = (data != null) ? data.getLength() : -1;
			int end = (data != null) ? data.endTick : -1;
			int stop = (data != null) ? data.stopTick : -1;
			boolean infinite = (data != null) && data.isInfinite;
			// stopTick>endTick 表示循环动画（到达 endTick 后回到 stopTick 循环）
			boolean loop = (data != null) && data.stopTick > data.endTick;
			return String.format("%s  tick=%d/%d (end=%d stop=%d inf=%b loop=%b)",
					name, tick, total, end, stop, infinite, loop);
		} catch (Throwable t) {
			return "读取失败: " + t.getClass().getSimpleName() + ": " + t.getMessage();
		}
	}

	/** 用反射从 AnimationStack.layers 私有字段取出所有动画层，遍历找当前激活的 KeyframeAnimationPlayer。 */
	public static KeyframeAnimationPlayer findActivePlayerFromStack(AnimationStack stack) {
		if (stack == null) return null;
		try {
			java.lang.reflect.Field f = AnimationStack.class.getDeclaredField("layers");
			f.setAccessible(true);
			Object layers = f.get(stack);
			if (layers instanceof List<?> list) {
				for (Object entry : list) {
					if (entry == null) continue;
					// 层是 Pair<Integer, IAnimation>，KosmX Pair 字段名可能不是 second；遍历所有字段取 IAnimation
					for (java.lang.reflect.Field df : entry.getClass().getDeclaredFields()) {
						df.setAccessible(true);
						Object anim = df.get(entry);
						KeyframeAnimationPlayer kp = findActivePlayer(anim);
						if (kp != null) return kp;
					}
				}
			}
		} catch (Throwable ignored) { }
		return null;
	}

	/** 在单个动画对象中递归找当前激活的 KeyframeAnimationPlayer（穿透 ModifierLayer 修饰链）。 */
	private static KeyframeAnimationPlayer findActivePlayer(Object anim) {
		if (anim == null) return null;
		if (anim instanceof KeyframeAnimationPlayer kfp) return kfp.isActive() ? kfp : null;
		// ModifierLayer.getAnimation() 返回当前播放的基础动画
		if (anim instanceof ModifierLayer<?> ml) {
			try {
				IAnimation base = ml.getAnimation();
				return findActivePlayer(base);
			} catch (Throwable ignored) { return null; }
		}
		return null;
	}
}
