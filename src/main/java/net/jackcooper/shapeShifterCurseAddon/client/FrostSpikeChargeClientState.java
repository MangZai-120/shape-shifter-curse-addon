package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寒棘狐主技能蓄力粒子客户端自算（零网络粒子包）。
 *
 * <p>服务端只在蓄力开始/结束各发一个事件级状态包（{@code syncFrostSpikeChargeState}），
 * 各客户端收到后本地复现原服务端的持续汇聚流：每 4t 在「下一个将生成的冰锥位」
 * （{@code firstEmptySlot → oldestSlot}，SLOT/hoverTicks 均有 DataTracker 同步）生成 18 颗向心冰晶。</p>
 *
 * <p>与原服务端逻辑严格同构：中心 = {@link FrostThornEntity#hoverTarget}(玩家, nextIdx)，
 * 几何 = 球面均匀 + 初速 1格/20t 向心（与服务端原 spawnInwardIceParticles 逐参数一致）。</p>
 */
@Environment(EnvType.CLIENT)
public final class FrostSpikeChargeClientState {

	/** 正在主技能蓄力的玩家（镜像服务端 charging 态）。 */
	private static final Map<UUID, Boolean> CHARGING = new ConcurrentHashMap<>();

	private FrostSpikeChargeClientState() {}

	public static void setCharging(UUID playerId, boolean charging) {
		if (charging) CHARGING.put(playerId, Boolean.TRUE);
		else CHARGING.remove(playerId);
	}

	public static void clearAll() {
		CHARGING.clear();
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FrostSpikeChargeClientState::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || CHARGING.isEmpty()) return;
		// 与服务端同步的节奏：每 4 tick 一波
		if (world.getTime() % 4 != 0) return;
		for (UUID id : CHARGING.keySet()) {
			PlayerEntity p = world.getPlayerByUuid(id);
			if (p == null) continue;
			if (!FormUtils.isForm(p, FormIdentifiers.SNOW_FOX_FROSTSPINE)) continue;
			// 环绕冰锥都在玩家 2 格内：以玩家为中心扫 4 格盒足够（不能全图扫，性能）
			net.minecraft.util.math.Box scan = p.getBoundingBox().expand(4.0);
			// 复算服务端同款选位：firstEmptySlot → oldestSlot（满 5 根时汇聚到将被替换的最旧锥位）
			int nextIdx = firstEmptySlot(world, scan, id);
			if (nextIdx < 0) nextIdx = oldestSlot(world, scan, id);
			if (nextIdx < 0) continue;
			net.minecraft.util.math.Vec3d center = FrostThornEntity.hoverTarget(p, nextIdx);
			// 球面均匀 + 初速 1格/20t 向心（与服务端原几何逐参数一致）
			for (int i = 0; i < 18; i++) {
				double u = world.random.nextDouble() * 2 - 1;
				double theta = world.random.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(1 - u * u);
				double dx = r * Math.cos(theta), dy = u, dz = r * Math.sin(theta);
				double speed = 1.0 / 20.0;
				world.addParticle(SscAddon.INWARD_ICE_PARTICLE,
						center.x + dx, center.y + dy, center.z + dz,
						-dx * speed, -dy * speed, -dz * speed);
			}
		}
	}

	/** 客户端版 firstEmptySlot：扫描玩家身边的环绕冰锥（SLOT DataTracker 同步），找最小空槽位。 */
	private static int firstEmptySlot(ClientWorld world, net.minecraft.util.math.Box scan, UUID ownerId) {
		boolean[] used = new boolean[5];
		for (FrostThornEntity t : world.getEntitiesByClass(FrostThornEntity.class,
				scan, e -> e.isHover() && ownerId.equals(e.getOwnerUuid().orElse(null)))) {
			int slot = t.getSlot();
			if (slot >= 0 && slot < 5) used[slot] = true;
		}
		for (int i = 0; i < 5; i++) if (!used[i]) return i;
		return -1;
	}

	/** 客户端版 oldestSlot：实体 age 最大（=生成最早=剩余最短；客户端实体同样自增，与服务端同源）的槽位。 */
	private static int oldestSlot(ClientWorld world, net.minecraft.util.math.Box scan, UUID ownerId) {
		int idx = -1;
		int max = -1;
		for (FrostThornEntity t : world.getEntitiesByClass(FrostThornEntity.class,
				scan, e -> e.isHover() && ownerId.equals(e.getOwnerUuid().orElse(null)))) {
			if (t.age > max) { max = t.age; idx = t.getSlot(); }
		}
		return idx;
	}
}
