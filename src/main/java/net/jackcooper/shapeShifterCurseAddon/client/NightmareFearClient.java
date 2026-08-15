package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 食梦魔「恐惧」客户端状态 —— 仅恐惧目标本人的客户端持有。
 *
 * <ul>
 *   <li>粉雾（16 格视野限制）：{@code FearFogMixin} 每帧查询 {@link #getFogStrength()}，
 *       以「剩余时长」驱动渐进淡入（约 2 秒从 0 → 1），恐惧结束淡出；</li>
 *   <li>心跳声：入雾后按固定节拍本地播放 WARDEN_HEARTBEAT（与服务端心跳互为补充：
 *       服务端按世界时间播、本地按包内节拍播，二者节拍一致不会叠加爆音——本地仅在
 *       服务端包丢失时兜底，故音量调低）；</li>
 *   <li>「1 秒看不见梦魔」：{@code FearHideRendererMixin} 查询 {@link #isHidden(UUID)}，
 *       窗口内对该梦魔的实体渲染整体跳过（第一/第三人称均不可见，仅本地）。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class NightmareFearClient {

	/** 粉雾完全淡入耗时（tick，约 2 秒「慢慢出现」）。 */
	private static final int FADE_IN_TICKS = 40;
	/** 心跳节拍（tick，与服务端 HEARTBEAT_INTERVAL 一致）。 */
	private static final int HEARTBEAT_INTERVAL = 32;

	/** 恐惧到期客户端时间（-1 = 未恐惧）。 */
	private static long endWorldTime = -1L;
	/** 恐惧开始客户端时间。 */
	private static long startWorldTime = -1L;
	/** 隐匿窗口：梦魔 UUID -> 隐匿到期客户端时间。 */
	private static final Map<UUID, Long> HIDDEN = new ConcurrentHashMap<>();
	/** 显形窗口：梦魔 UUID -> 显形到期客户端时间（梦魔攻击恐惧目标时显形 1s，期间不隐匿）。 */
	private static final Map<UUID, Long> REVEALED = new ConcurrentHashMap<>();
	/** 本地心跳节拍计数（用客户端世界时间取模）。 */
	private static long lastHeartbeat = -1L;

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
		if (client.world == null) return;
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
			java.util.Map<net.minecraft.entity.effect.StatusEffect, net.minecraft.entity.effect.StatusEffectInstance> effects =
					((net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client.LivingEntityStatusEffectsAccessor) client.player)
							.sscAddon$getActiveStatusEffects();
			if (feared) {
				net.minecraft.entity.effect.StatusEffectInstance cur =
						effects.get(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
				if (cur == null || cur.getDuration() < 30) {
					effects.put(net.minecraft.entity.effect.StatusEffects.BLINDNESS,
							new net.minecraft.entity.effect.StatusEffectInstance(
									net.minecraft.entity.effect.StatusEffects.BLINDNESS, 40, 0,
									false, false, false));
				}
			} else {
				effects.remove(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
			}
		}
		// 兜底心跳：恐惧激活时按节拍本地低音量播（服务端为主；丢了也不至于无声）。
		// 音量按最近食梦魔距离动态：范围外极小固定音量，16 格内越近越响（与服务端公式一致）。
		if (feared && now % HEARTBEAT_INTERVAL == 0 && now != lastHeartbeat && client.player != null) {
			lastHeartbeat = now;
			double nearest = Double.MAX_VALUE;
			for (net.minecraft.entity.Entity e : client.world.getEntities()) {
				if (e == client.player || !(e instanceof net.minecraft.entity.player.PlayerEntity)) continue;
				if (!net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(
						(net.minecraft.entity.player.PlayerEntity) e,
						net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.WILD_CAT_NIGHTMARE)) continue;
				nearest = Math.min(nearest, e.squaredDistanceTo(client.player));
			}
			float vol = 0.06f;
			if (nearest != Double.MAX_VALUE && nearest <= 16.0 * 16.0) {
				double dist = Math.sqrt(nearest);
				vol = 0.06f + (float) ((1.0 - dist / 16.0) * 0.24f);
			}
			client.getSoundManager().play(PositionedSoundInstance.master(
					net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT, vol, 0.9f));
		}
	}
}
