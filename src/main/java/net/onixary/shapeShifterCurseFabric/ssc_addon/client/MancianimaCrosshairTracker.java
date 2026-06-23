package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

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
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

import java.util.UUID;
import java.util.function.Predicate;

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

	public static UUID getCurrentTarget() { return currentTarget; }

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
			IForm form = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;
			return form != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID());
		} catch (Exception e) { return false; }
	}
}
