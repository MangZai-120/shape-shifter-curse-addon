package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食梦魔「恐惧」客户端状态 —— 仅恐惧目标本人的客户端持有。
 *
 * <ul>
 *   <li>粉雾（16 格视野限制）：{@code FearFogMixin} 每帧查询 {@link #getFogStrength()}，
 *       以「剩余时长」驱动渐进淡入（约 2 秒从 0 → 1），恐惧结束淡出；</li>
 *   <li>心跳声：服务端按最近食梦魔的距离计算音量，使用原版声音包定向发送给恐惧目标；
 *       客户端不另播一份心跳，避免重复声音和旁人听到目标的心跳。</li>
 *   <li>「1 秒看不见梦魔」：{@code FearHideRendererMixin} 查询 {@link #isHidden(UUID)}，
 *       窗口内对该梦魔的实体渲染整体跳过（第一/第三人称均不可见，仅本地）。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class NightmareFearClient {

	/** 粉雾完全淡入耗时（tick，约 2 秒「慢慢出现」）。 */
	private static final int FADE_IN_TICKS = 40;

	/** 恐惧到期客户端时间（-1 = 未恐惧）。 */
	private static long endWorldTime = -1L;
	/** 恐惧开始客户端时间。 */
	private static long startWorldTime = -1L;
	/** 隐匿窗口：梦魔 UUID -> 隐匿到期客户端时间。 */
	private static final Map<UUID, Long> HIDDEN = new ConcurrentHashMap<>();
	/** 显形窗口：梦魔 UUID -> 显形到期客户端时间（梦魔攻击恐惧目标时显形 1s，期间不隐匿）。 */
	private static final Map<UUID, Long> REVEALED = new ConcurrentHashMap<>();
	/** Only the exact local carrier inserted by fear may be removed when fear ends. */
	private static Map<StatusEffect, StatusEffectInstance> blindnessEffects;
	private static StatusEffectInstance fearBlindness;

	private NightmareFearClient() {
	}

	public static void register() {
		// 恐惧状态
		ClientPlayNetworking.registerGlobalReceiver(SscAddonNetworking.PACKET_FEAR_STATE,
				(client, handler, buf, responseSender) -> {
					int duration = buf.readVarInt();
					client.execute(() -> {
						if (client.world == null) return;
						long now = client.world.getTime();
						if (duration <= 0) {
							endWorldTime = -1L;
							startWorldTime = -1L;
							HIDDEN.clear();
							return;
						}
						startWorldTime = now;
						endWorldTime = now + duration;
					});
				});
		// 1 秒看不见窗口
		ClientPlayNetworking.registerGlobalReceiver(SscAddonNetworking.PACKET_FEAR_HIDE,
				(client, handler, buf, responseSender) -> {
					UUID nightmareUuid = buf.readUuid();
					int hideTicks = buf.readVarInt();
					client.execute(() -> {
						if (client.world == null) return;
						HIDDEN.put(nightmareUuid, client.world.getTime() + hideTicks);
					});
				});
		// 梦魇显形窗口（攻击恐惧目标 → 显形 1s：清隐匿 + 压制新隐匿）
		ClientPlayNetworking.registerGlobalReceiver(SscAddonNetworking.PACKET_FEAR_REVEAL,
				(client, handler, buf, responseSender) -> {
					UUID nightmareUuid = buf.readUuid();
					int revealTicks = buf.readVarInt();
					client.execute(() -> {
						if (client.world == null) return;
						HIDDEN.remove(nightmareUuid);
						REVEALED.put(nightmareUuid, client.world.getTime() + revealTicks);
					});
				});
		// 断线清理
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			clearFearBlindness();
			endWorldTime = -1L;
			startWorldTime = -1L;
			HIDDEN.clear();
			REVEALED.clear();
		});
	}

	/** 当前恐惧是否激活（由 fog mixin 每帧调用）。 */
	public static boolean isFeared() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) return false;
		if (endWorldTime < 0L) return false;
		return client.world.getTime() < endWorldTime;
	}

	/**
	 * 粉雾强度 0~1：激活后随经过时间渐进淡入（约 2s 到满），到期前 0.5s 平滑淡出。
	 * 未恐惧返回 0。
	 */
	public static float getFogStrength(float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || endWorldTime < 0L) return 0.0F;
		float now = client.world.getTime() + tickDelta;
		if (now >= endWorldTime) return 0.0F;
		float fadeIn = (now - startWorldTime) / FADE_IN_TICKS;
		float fadeOut = (endWorldTime - now) / 10.0F; // 结束前 0.5s 淡出
		float s = Math.min(fadeIn, 1.0F);
		s = Math.min(s, Math.max(0.0F, fadeOut));
		return Math.max(0.0F, Math.min(1.0F, s));
	}

	/** 该梦魔当前是否处于「1 秒看不见」窗口（由渲染 mixin 每帧调用）。显形窗口压制隐匿。 */
	public static boolean isHidden(UUID nightmareUuid) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) return false;
		long now = client.world.getTime();
		Long revealed = REVEALED.get(nightmareUuid);
		if (revealed != null && revealed > now) return false; // 显形期内不隐匿
		Long until = HIDDEN.get(nightmareUuid);
		return until != null && until > now;
	}

	/** 每客户端 tick 推进（CLIENT_TICK 事件里调）：过期隐匿/显形清理 + 兜底心跳。
	 * 视野限制视觉由 FearSphereWallRenderer（粉球壳）与 FearFogMixin（无光影雾）承担。 */
	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			clearFearBlindness();
			return;
		}
		long now = client.world.getTime();
		if (!HIDDEN.isEmpty()) {
			HIDDEN.values().removeIf(until -> until <= now);
		}
		if (!REVEALED.isEmpty()) {
			REVEALED.values().removeIf(until -> until <= now);
		}
		boolean feared = isFeared();
		// 视野限制（用户定稿）：失明作为雾载体（Map 直写绕过形态效果免疫）。
		// 光影下失明雾的黑色问题由 IrisShaderPackMixin 解决（内存替换光影失明雾为粉色 12→16 格版）；
		// 无光影时由 FearFogMixin 覆盖雾距/雾色。粉球壳 FearSphereWallRenderer 增强界限感。
		if (client.player != null) {
			Map<StatusEffect, StatusEffectInstance> effects =
					((net.jackcooper.shapeShifterCurseAddon.mixin.client.LivingEntityStatusEffectsAccessor) client.player)
							.sscAddon$getActiveStatusEffects();
			updateBlindness(effects, feared);
		} else {
			clearFearBlindness();
		}
		// Heartbeat timing and distance volume arrive through the server's ordinary targeted sound packets.
	}

	private static void updateBlindness(Map<StatusEffect, StatusEffectInstance> effects, boolean feared) {
		if (effects != blindnessEffects) {
			clearFearBlindness();
			blindnessEffects = effects;
		}
		if (!feared) {
			clearFearBlindness();
			return;
		}
		StatusEffectInstance current = effects.get(StatusEffects.BLINDNESS);
		if (current == null || (current == fearBlindness && current.getDuration() < 30)) {
			fearBlindness = new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, false);
			effects.put(StatusEffects.BLINDNESS, fearBlindness);
		} else if (current != fearBlindness) {
			// Play Dead, potions and server updates own their duration. Never replace them,
			// even when they have less than 30 ticks remaining.
			fearBlindness = null;
		}
	}

	private static void clearFearBlindness() {
		if (blindnessEffects != null && fearBlindness != null
				&& blindnessEffects.get(StatusEffects.BLINDNESS) == fearBlindness) {
			blindnessEffects.remove(StatusEffects.BLINDNESS);
		}
		fearBlindness = null;
		blindnessEffects = null;
	}
}
