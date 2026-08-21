package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.particle.ParticleManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寒棘狐蓄力粒子客户端自算（网络优化）。
 *
 * <p>原服务端逐 6t 广播汇聚粒子波（count=0 单粒包 ~40包/秒/玩家）已删除——
 * 改为服务端只发<b>事件级状态包</b>（蓄力开始/结束各 1 包，{@code syncFrostSpikeChargeVisual}），
 * 客户端收到后本地持续自算汇聚流：</p>
 * <ul>
 *   <li><b>主技能凝聚（mode 1）</b>：下一个将生成冰锥的环绕位（{@code hoverTarget}）向内汇聚；</li>
 *   <li><b>次技能凝棘（mode 2）</b>：头顶法阵中心（{@code secondaryFocus} 同款几何 = hoverTarget(0)）向内汇聚；</li>
 *   <li>粒子用自定义 {@code ssc_addon:inward_ice}（匀速直线、抵达中心即消失），几何与服务端原实现一致。</li>
 * </ul>
 * <p>多人一致：各客户端按同一套「玩家位置/身体朝向（已同步）+ 时间」独立计算，天然一致。</p>
 */
@Environment(EnvType.CLIENT)
public final class FrostSpikeChargeVisualClient {

	/** 蓄力模式：0=停止，1=主技能凝聚，2=次技能凝棘。 */
	private static final Map<UUID, Integer> CHARGING = new ConcurrentHashMap<>();

	/** 汇聚半径（格）：与原服务端 spawnInwardIceParticles 一致。 */
	private static final double RADIUS = 1.0;
	/** 每 6 tick 一波（与原服务端节奏一致）。 */
	private static final int INTERVAL = 6;
	/** 常规波粒子数。 */
	private static final int COUNT = 12;

	private FrostSpikeChargeVisualClient() {}

	public static void setMode(UUID playerId, int mode) {
		if (mode == 0) CHARGING.remove(playerId);
		else CHARGING.put(playerId, mode);
	}

	public static void clear() {
		CHARGING.clear();
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FrostSpikeChargeVisualClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		if (client.world == null || CHARGING.isEmpty()) return;
		if (client.world.getTime() % INTERVAL != 0) return; // 每 6t 一波
		ParticleManager pm = client.particleManager;
		for (Map.Entry<UUID, Integer> e : CHARGING.entrySet()) {
			if (!(client.world.getPlayerByUuid(e.getKey()) instanceof ClientPlayerEntity p)) continue;
			if (!FormUtils.isForm(p, FormIdentifiers.SNOW_FOX_FROSTSPINE)) continue;
			// 汇聚中心：主技能=头顶环绕位（各客户端近似取 slot 0 系列位置）；次技能=头顶法阵中心（hoverTarget(0)）
			net.minecraft.util.math.Vec3d center;
			if (e.getValue() == 2) {
				center = FrostThornEntity.hoverTarget(p, 0);
			} else {
				center = FrostThornEntity.hoverTarget(p, 0); // 主技能：头顶位（冰锥成形位）——与实体成形粒子同点
			}
			// 球面均匀随机向内汇聚（与服务端原几何一致：1格/20t 抵达中心即消失）
			for (int i = 0; i < COUNT; i++) {
				double u = client.world.random.nextDouble() * 2 - 1;
				double theta = client.world.random.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(1 - u * u);
				double dx = r * Math.cos(theta), dy = u, dz = r * Math.sin(theta);
				double speed = RADIUS / 20.0;
				pm.addParticle(SscAddon.INWARD_ICE_PARTICLE,
						center.x + dx, center.y + dy, center.z + dz,
						-dx * speed, -dy * speed, -dz * speed);
			}
		}
	}
}
