package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;

import java.util.UUID;
import java.util.function.Predicate;
import net.jackcooper.shapeShifterCurseAddon.compat.ssc192.Compat1_9_2;

/**
 * 客户端：契灵准星目标缓存。
 * 每客户端 tick 重新做一次 32 格射线检测，命中第一个非自身/非旁观 LivingEntity，
 * 用于绿色高亮 entity_glow（仅本地玩家可见）。
 */
@Environment(EnvType.CLIENT)
public final class MancianimaCrosshairTracker {
	private MancianimaCrosshairTracker() {}

	private static volatile UUID currentTarget = null;
	private static final double MAX_DIST = 32.0;
	// 上次 raycast 时的视角/位置快照：均未变时跳过重算（每 4t 强制刷新兜底目标自身移动）
	private static int tickCounter = 0;
	private static float lastYaw = Float.NaN, lastPitch = Float.NaN;
	private static double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;

	public static boolean isCurrent(UUID uuid) {
		UUID cur = currentTarget;
		return cur != null && uuid != null && cur.equals(uuid);
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MancianimaCrosshairTracker::onTick);
	}

	private static void onTick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) { currentTarget = null; return; }
		PlayerEntity player = mc.player;
		if (!isMancianima(player)) { currentTarget = null; return; }

		// 性能：准星指向 + 位置都没变时跳过双 raycast（原每 tick 无条件重算）；
		// 每 4t 强制刷新兜底（目标自己移动/死亡不会被视角检测捕获）
		if (tickCounter++ % 4 == 0
				|| player.getYaw() != lastYaw || player.getPitch() != lastPitch
				|| player.getX() != lastX || player.getY() != lastY || player.getZ() != lastZ) {
			lastYaw = player.getYaw(); lastPitch = player.getPitch();
			lastX = player.getX(); lastY = player.getY(); lastZ = player.getZ();
			raycastTarget(mc, player);
		}
	}

	private static void raycastTarget(MinecraftClient mc, PlayerEntity player) {
		Vec3d eye = player.getCameraPosVec(1.0f);
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(MAX_DIST));

		// 先做方块遮挡判定，命中方块则限制最远可标记距离
		BlockHitResult blockHit = mc.world.raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		double maxDistSq = MAX_DIST * MAX_DIST;
		if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
			maxDistSq = eye.squaredDistanceTo(blockHit.getPos());
		}

		Box box = player.getBoundingBox().stretch(look.multiply(MAX_DIST)).expand(1.0);
		// 过滤：跳过自身、玩家、已驯服宠物（与服务端默认白名单行为对齐）
		Predicate<Entity> filter = e -> e != player && e.isAlive() && e instanceof LivingEntity
				&& !(e instanceof PlayerEntity)
				&& !(e instanceof TameableEntity tame && tame.getOwnerUuid() != null);
		EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(player, eye, end, box, filter, maxDistSq);
		if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
			currentTarget = hit.getEntity().getUuid();
		} else {
			currentTarget = null;
		}
	}

	private static boolean isMancianima(PlayerEntity player) {
		try {
			PlayerFormBase form = Compat1_9_2.nowForm(player);
			return form != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.FormID);
		} catch (Exception e) { return false; }
	}
}
