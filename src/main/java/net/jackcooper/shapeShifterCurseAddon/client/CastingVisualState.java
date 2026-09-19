package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 施法视觉状态客户端镜像（jackcooper）：接收服务端 {@code spell_cast_visual} S2C 包，
 * 维护「谁在施法」的客户端表，供渲染 mixin 查询：
 * <ul>
 * <li>人形态施法举手动画（{@code SpellCastPoseMixin}，铁魔法式双手前举，FERAL 兽形不举手）；</li>
 * <li>第一人称施法抬手（{@code SpellCastFirstPersonMixin}）。</li>
 * </ul>
 * <p>转身/视角不限制：特殊档禁的只是主动走动与跳跃，转身用原版 MC 机制（头转过 50° 身体自然跟上）。</p>
 * <p>服务端活动期每 20t 重播；客户端记录接收时刻，超过 45t 未刷新视为过期自动清除
 * （兜底：stop 包丢失 / 掉包 / 单机 integrated 关闭瞬间不残留举手）。</p>
 */
@Environment(EnvType.CLIENT)
public final class CastingVisualState {
	/** 过期阈值（tick）：服务端 20t 重播间隔 + 网络余量。 */
	private static final int EXPIRE_TICKS = 45;
	/** 抬手过渡时长（tick）：0→1 渐进，避免生硬跳变。 */
	public static final float RAISE_TICKS = 5.0F;

	/** 施法视觉条目：receivedAt 用客户端世界时间（连续不回退，dimension 无关）。 */
	private static final Map<UUID, Entry> ACTIVE = new HashMap<>();
	private static ClientWorld visualWorld;

	private CastingVisualState() {}

	private static final class Entry {
		final long receivedAt;
		/** 过期时刻：服务端每 20t 重播只刷新此值，不重置 receivedAt（否则举手进度被重播重置，手臂来回摆）。 */
		long expiresAt;
		boolean active = true;
		boolean armPose;
		int schoolColor;
		int rarityColor;

		Entry(long receivedAt, long expiresAt) {
			this.receivedAt = receivedAt;
			this.expiresAt = expiresAt;
		}
	}

	public static void register() {
		net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(
				net.jackcooper.shapeShifterCurseAddon.client.renderer.CastingCircleRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(CastingVisualState::tick);
		ClientPlayNetworking.registerGlobalReceiver(SpellChannelManager.CAST_VISUAL, (client, handler, buf, sender) -> {
			UUID uuid = buf.readUuid();
			boolean active = buf.readBoolean();
			boolean armPose = active && buf.readBoolean();
			int schoolColor = active ? buf.readInt() : 0;
			int rarityColor = active ? buf.readInt() : 0;
			client.execute(() -> {
				checkWorld(client);
				if (client.world == null) return;
				long now = client.world.getTime();
				if (active) {
					Entry old = ACTIVE.get(uuid);
					if (old != null && old.active && now <= old.expiresAt) {
						old.expiresAt = now + EXPIRE_TICKS; // 重播：只续命不重置抬手进度
					} else {
						old = new Entry(now, now + EXPIRE_TICKS);
						ACTIVE.put(uuid, old);
					}
					old.armPose = armPose;
					old.schoolColor = schoolColor;
					old.rarityColor = rarityColor;
				} else {
					Entry old = ACTIVE.get(uuid);
					if (old != null && old.active) {
						old.active = false;
						old.expiresAt = now + 6;
					}
				}
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ACTIVE.clear();
			visualWorld = null;
		});
	}

	private static void checkWorld(MinecraftClient client) {
		if (visualWorld != client.world) {
			ACTIVE.clear();
			visualWorld = client.world;
		}
	}

	private static void tick(MinecraftClient client) {
		checkWorld(client);
		if (client.world == null) return;
		long now = client.world.getTime();
		ACTIVE.values().removeIf(entry -> now > entry.expiresAt);
		if (now % 2 != 0) return;
		for (var player : client.world.getPlayers()) {
			Entry entry = ACTIVE.get(player.getUuid());
			if (entry == null || !entry.active || !entry.armPose || !player.isAlive()
					|| player.isSpectator() || player.isInvisible()) continue;
			if (client.getCameraEntity() == null || player.squaredDistanceTo(client.getCameraEntity()) > 32 * 32) continue;
			var random = client.world.random;
			for (int count = 0; count < 2; count++) {
				var particle = client.particleManager.addParticle(ParticleTypes.END_ROD,
						player.getX() + (random.nextDouble() - 0.5) * 0.65,
						player.getY() + player.getHeight() + 0.12 + random.nextDouble() * 0.12,
						player.getZ() + (random.nextDouble() - 0.5) * 0.65, 0, 0.04, 0);
				if (particle == null) continue;
				particle.setColor(((entry.schoolColor >> 16) & 255) / 255F,
						((entry.schoolColor >> 8) & 255) / 255F, (entry.schoolColor & 255) / 255F);
				if (particle instanceof net.minecraft.client.particle.EndRodParticle endRod) {
					endRod.setTargetColor(entry.schoolColor);
				}
				particle.setVelocity(0, 0.035 + random.nextDouble() * 0.02, 0);
				particle.setMaxAge(20);
				particle.scale(1.2F);
			}
		}
	}

	/** 惰性过期清理后的有效条目（null = 未在施法）。 */
	private static Entry entryOf(UUID uuid) {
		Entry entry = ACTIVE.get(uuid);
		if (entry == null) return null;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != visualWorld) return null;
		long now = client.world == null ? 0 : client.world.getTime();
		if (now > entry.expiresAt) {
			ACTIVE.remove(uuid); // 过期兜底：漏收 off 包 / 掉包时自动消隐
			return null;
		}
		return entry;
	}

	/** 该玩家是否正在施法（视觉态，客户端镜像）。 */
	public static boolean isCasting(UUID uuid) {
		Entry entry = entryOf(uuid);
		return entry != null && entry.active && entry.armPose;
	}

	public static boolean hasCircle(UUID uuid) {
		return entryOf(uuid) != null;
	}

	public static int circleColor(UUID uuid) {
		Entry entry = entryOf(uuid);
		return entry == null ? 0xFFFFFF : entry.rarityColor;
	}

	public static float circleAlpha(UUID uuid) {
		Entry entry = entryOf(uuid);
		if (entry == null) return 0;
		if (entry.active) return 1;
		long now = MinecraftClient.getInstance().world.getTime();
		return MathHelper.clamp((entry.expiresAt - now) / 6F, 0, 1);
	}

	/** 抬手过渡进度 0→1（RAISE_TICKS 内线性，之后恒 1）。 */
	public static float raiseProgress(UUID uuid) {
		Entry entry = entryOf(uuid);
		if (entry == null) return 0.0F;
		MinecraftClient client = MinecraftClient.getInstance();
		long now = client.world == null ? entry.receivedAt : client.world.getTime();
		return MathHelper.clamp((now - entry.receivedAt) / RAISE_TICKS, 0.0F, 1.0F);
	}

	public static float armSway(float age, boolean right) {
		return MathHelper.sin(age * 0.18F) * (right ? 1.0F : -1.0F);
	}

	/** 本地玩家快捷判定（第一人称手持渲染用）。 */
	public static boolean localPlayerCasting() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		return player != null && isCasting(player.getUuid());
	}
}
