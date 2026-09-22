package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class DomainRenderer {
	private record View(UUID owner, Vec3d center, double headHeight, boolean active, int elapsed) {}
	private static List<View> fields = List.of();
	private static ClientWorld world;
	private static long receivedAt;
	private DomainRenderer() {}

	public static void register() {
		net.jackcooper.shapeShifterCurseAddon.client.DomainSound.register();
		ClientPlayNetworking.registerGlobalReceiver(DomainManager.STATE, (client, handler, buf, sender) -> {
			var dimension = buf.readIdentifier();
			int count = buf.readVarInt();
			List<View> next = new ArrayList<>();
			for (int index = 0; index < count; index++) {
				next.add(new View(buf.readUuid(), new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
						buf.readDouble(), buf.readBoolean(), buf.readVarInt()));
			}
			client.execute(() -> {
				if (client.world == null || !client.world.getRegistryKey().getValue().equals(dimension)) return;
				world = client.world;
				receivedAt = world.getTime();
				fields = List.copyOf(next);
				shellCacheTick = Long.MIN_VALUE; // 新同步包：失效壳缓存（下一 tick 重建）
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			fields = List.of();
			world = null;
			shellCache = null;
			shellCacheTick = Long.MIN_VALUE;
		});
		WorldRenderEvents.AFTER_ENTITIES.register(DomainRenderer::render);
	}

	private static boolean valid() {
		return world != null && MinecraftClient.getInstance().world == world && world.getTime() - receivedAt < 40;
	}

	public static boolean isCharging(UUID owner) {
		return valid() && fields.stream().anyMatch(field -> !field.active && field.owner.equals(owner));
	}

	/**
	 * 客户端预测墙（2026-09-21 需求）：本地玩家所在客户端世界存在领域壳时，
	 * 返回以壳心为原点的坐标列表（含扩张期半径），供移动缩放判定；否则 null。
	 * 数据源为服务端 STATE 同步表（40t 过期兜底），与服务器同一套 DomainRules 几何。
	 *
	 * <p>热路径缓存（2026-09-22）：本方法被每个客户端实体的 move 每 tick 调用
	 * （领域期 ~实体数×20 次/秒），壳数据每 tick 只变一次——按 world.getTime() 缓存
	 * 构建结果（含空结果负缓存），消除每实体每 tick 的 ArrayList/Shell 分配。</p>
	 */
	private static List<DomainRules.Shell> shellCache;
	private static long shellCacheTick = Long.MIN_VALUE;

	public static List<DomainRules.Shell> clientShells() {
		if (!valid()) return null;
		long now = world.getTime();
		if (shellCacheTick == now) return shellCache;
		List<DomainRules.Shell> shells = new ArrayList<>();
		for (View field : fields) {
			float elapsed = field.elapsed + now - receivedAt;
			if (field.active) {
				if (elapsed < DomainRules.DURATION_TICKS) shells.add(new DomainRules.Shell(field.center, DomainRules.INNER_RADIUS, true));
			} else if (elapsed >= DomainRules.EXPAND_START_TICK && elapsed < DomainRules.CHARGE_TICKS + 20) {
				double radius = DomainRules.expansionRadius(Math.min(elapsed, DomainRules.CHARGE_TICKS));
				if (radius > 0.1) shells.add(new DomainRules.Shell(field.center, radius, false));
			}
		}
		shellCache = shells.isEmpty() ? null : shells;
		shellCacheTick = now;
		return shellCache;
	}

	/**
	 * 相机壳内钳制（2026-09-21 反馈）：第三人称拉远目标若穿出领域壳，缩回到贴壳内侧。
	 * 玩家眼睛（from）不在任何壳内 → 不干预返回原目标。与服务端同一套 crosses/crossesOutward 几何。
	 */
	public static Vec3d clampCamera(Vec3d from, Vec3d target) {
		if (!valid()) return target;
		Vec3d best = target;
		for (View field : fields) {
			float elapsed = field.elapsed + world.getTime() - receivedAt;
			double inner;
			if (field.active) {
				if (elapsed >= DomainRules.DURATION_TICKS) continue;
				inner = DomainRules.INNER_RADIUS;
			} else {
				if (elapsed < DomainRules.EXPAND_START_TICK) continue;
				inner = DomainRules.expansionRadius(Math.min(elapsed, DomainRules.CHARGE_TICKS));
				if (inner <= 0.1) continue;
			}
			Vec3d start = from.subtract(field.center), end = target.subtract(field.center);
			boolean fromInside = start.lengthSquared() <= (inner + 1) * (inner + 1);
			if (!fromInside) continue;
			boolean cross = field.active
					? DomainRules.crosses(start.x, start.y, start.z, end.x, end.y, end.z, 0.3)
					: DomainRules.crossesOutward(start.x, start.y, start.z, end.x, end.y, end.z, inner);
			if (!cross) continue;
			Vec3d delta = target.subtract(from);
			double low = 0, high = 1;
			for (int iteration = 0; iteration < 18; iteration++) {
				double middle = (low + high) * 0.5;
				Vec3d mid = from.add(delta.multiply(middle)).subtract(field.center);
				boolean midCross = field.active
						? DomainRules.crosses(start.x, start.y, start.z, mid.x, mid.y, mid.z, 0.3)
						: DomainRules.crossesOutward(start.x, start.y, start.z, mid.x, mid.y, mid.z, inner);
				if (midCross) high = middle; else low = middle;
			}
			best = from.add(delta.multiply(Math.max(0, low - 0.0001)));
		}
		return best;
	}

	/**
	 * 相机终检投影（2026-09-22 反馈修正）：玩家眼睛在壳内、相机当前位置在壳外时，
	 * 把相机沿「眼睛→相机」射线压回壳内侧（留 0.3 余量）。返回 null = 无需修正。
	 * 这是第三人称出不去的最终兜底，不依赖 moveBy 链路（第三方相机模组改写后也会被压回）。
	 */
	public static Vec3d projectInside(Vec3d eye, Vec3d cameraPos) {
		if (!valid()) return null;
		for (View field : fields) {
			float elapsed = field.elapsed + world.getTime() - receivedAt;
			double inner;
			if (field.active) {
				if (elapsed >= DomainRules.DURATION_TICKS) continue;
				inner = DomainRules.INNER_RADIUS;
			} else {
				if (elapsed < DomainRules.EXPAND_START_TICK) continue;
				inner = DomainRules.expansionRadius(Math.min(elapsed, DomainRules.CHARGE_TICKS));
				if (inner <= 0.1) continue;
			}
			Vec3d eyeRel = eye.subtract(field.center);
			double limit = Math.max(0.1, inner - 0.3);
			if (eyeRel.lengthSquared() > limit * limit) continue; // 眼睛不在壳内：不干预
			Vec3d camRel = cameraPos.subtract(field.center);
			if (camRel.lengthSquared() <= limit * limit) continue; // 相机已在壳内：无需修正
			Vec3d clampedRel = DomainRules.slideInside(eyeRel, camRel.subtract(eyeRel), limit).add(eyeRel);
			return field.center.add(clampedRel);
		}
		return null;
	}

	/**
	 * 方块跨界隔离的客户端判定（与服务器 blocksCrossBoundary 同几何）：眼睛与目标分属
	 * 任一壳内外 → 跨界。用于交互预测层 FAIL，避免先发包后回滚的闪断表现。
	 */
	public static boolean blocksCrossBoundaryClient(Vec3d eye, Vec3d target) {
		if (!valid()) return false;
		for (View field : fields) {
			float elapsed = field.elapsed + world.getTime() - receivedAt;
			double inner;
			if (field.active) {
				if (elapsed >= DomainRules.DURATION_TICKS) continue;
				inner = DomainRules.INNER_RADIUS;
			} else {
				if (elapsed < DomainRules.EXPAND_START_TICK) continue;
				inner = DomainRules.expansionRadius(Math.min(elapsed, DomainRules.CHARGE_TICKS));
				if (inner <= 0.1) continue;
			}
			if (DomainRules.separates(eye.subtract(field.center), target.subtract(field.center), inner + 1)) return true;
		}
		return false;
	}

	public static boolean blocksTargetingClient(net.minecraft.entity.Entity target) {
		var player = MinecraftClient.getInstance().player;
		return player != null && target != null && target != player
				&& blocksCrossBoundaryClient(player.getPos(), target.getPos());
	}

	/**
	 * 声音隔离判定（2026-09-21 需求）：本地玩家与声音源分属壳内外 → 拦截。
	 * 壳间（内层~外层）算"内"，与服务器 enclosedComplete 语义一致。
	 */
	public static boolean blocksSoundForListener(double x, double y, double z) {
		if (!valid()) return false;
		var listener = MinecraftClient.getInstance().player;
		if (listener == null) return false;
		Vec3d sound = new Vec3d(x, y, z);
		// 豁免（2026-09-22 需求②）：领域自身的蓄力/开启音从壳心发出，应穿壳可闻，
		// 不被自己的隔音墙拦掉——声源距任一领域心 <2 格即视为领域自身音。
		for (View field : fields) {
			if (field.center.squaredDistanceTo(sound) < 4) return false;
		}
		boolean listenerInside = false, soundInside = false;
		for (View field : fields) {
			float elapsed = field.elapsed + world.getTime() - receivedAt;
			double inner;
			if (field.active) {
				if (elapsed >= DomainRules.DURATION_TICKS) continue;
				inner = DomainRules.INNER_RADIUS;
			} else {
				if (elapsed < DomainRules.EXPAND_START_TICK) continue;
				inner = DomainRules.expansionRadius(Math.min(elapsed, DomainRules.CHARGE_TICKS));
				if (inner <= 0.1) continue;
			}
			double outer = inner + 1;
			if (field.center.squaredDistanceTo(listener.getPos()) <= outer * outer) listenerInside = true;
			if (field.center.squaredDistanceTo(sound) <= outer * outer) soundInside = true;
		}
		return listenerInside != soundInside;
	}

	private static void render(WorldRenderContext context) {
		if (!valid() || context.consumers() == null) return;
		Vec3d camera = context.camera().getPos();
		var matrices = context.matrixStack();
		VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getDebugQuads());
		for (View field : fields) {
			float elapsed = field.elapsed + world.getTime() - receivedAt + context.tickDelta();
			if (elapsed >= (field.active ? DomainRules.DURATION_TICKS : DomainRules.CHARGE_TICKS + 20)) continue;
			matrices.push();
			matrices.translate(field.center.x - camera.x, field.center.y - camera.y, field.center.z - camera.z);
			// 扩张期球壳（2006-09-21 需求）：第 10s 起从 0.75 格生长，15s 到 16/17 双壳完全体；
			// active 后半径固定 INNER/OUTER（服务端 activate 在 300t 触发，与扩张终点无缝衔接）。
			double shellRadius = field.active
					? DomainRules.INNER_RADIUS
					: DomainRules.expansionRadius(Math.min(elapsed, DomainRules.CHARGE_TICKS));
			if (field.active) {
				sphere(vertices, matrices.peek().getPositionMatrix(), DomainRules.INNER_RADIUS);
				sphere(vertices, matrices.peek().getPositionMatrix(), DomainRules.OUTER_RADIUS);
			} else if (shellRadius > 0.1) {
				sphere(vertices, matrices.peek().getPositionMatrix(), shellRadius);
				sphere(vertices, matrices.peek().getPositionMatrix(), shellRadius + 1);
			}
			float alpha = field.active ? 0.9f : Math.min(1, elapsed / 12f);
			CastingCircleRenderer.drawGround(vertices, matrices, DomainRules.INNER_RADIUS, 0.04,
					elapsed * 0.002, alpha, MagicCircleGeometry.RED_PRIMARY);
			if (!field.active) {
				circle(vertices, matrices, 3, field.headHeight, elapsed * 0.013, alpha);
				double second = DomainRules.layerProgress(elapsed, 60);
				if (second > 0) circle(vertices, matrices, 5 * second,
						field.headHeight + 3 * second, -elapsed * 0.009, (float) second);
				double third = DomainRules.layerProgress(elapsed, 120);
				if (third > 0) circle(vertices, matrices, 2 * third,
						field.headHeight + 3 + 3 * third, elapsed * 0.017, (float) third);
			}
			matrices.pop();
		}
	}

	private static void circle(VertexConsumer vertices, MatrixStack matrices, double radius, double height,
	                           double rotation, float alpha) {
		matrices.push();
		matrices.translate(0, height, 0);
		matrices.scale((float) radius, 1, (float) radius);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		double width = Math.max(0.009, 0.085 / radius);
		int crimson = 0xF52246, bright = 0xFF797D;
		for (double ring : new double[]{1, 0.97, 0.85, 0.80, 0.58, 0.21}) {
			CastingCircleRenderer.ring(vertices, matrix, ring, width, 0, ring == 0.97 ? bright : crimson, alpha);
		}
		CastingCircleRenderer.ring(vertices, matrix, 0.36, width * 0.75, 0, bright, alpha * 0.55f);
		for (int index = 0; index < 12; index++) {
			double angle = rotation + index * Math.PI / 6;
			double next = angle + Math.PI * 5 / 6;
			CastingCircleRenderer.line(vertices, matrix, Math.cos(angle) * 0.78, Math.sin(angle) * 0.78,
					Math.cos(next) * 0.78, Math.sin(next) * 0.78, width, crimson, alpha * 0.85f);
			double radialX = Math.cos(-rotation + index * Math.PI / 6);
			double radialZ = Math.sin(-rotation + index * Math.PI / 6);
			for (int stroke = 0; stroke < 3; stroke++) {
				double distance = 0.865 + stroke * 0.029;
				CastingCircleRenderer.line(vertices, matrix,
						radialX * distance - radialZ * 0.022, radialZ * distance + radialX * 0.022,
						radialX * distance + radialZ * 0.022, radialZ * distance - radialX * 0.022,
						width * 0.85, bright, alpha);
			}
			CastingCircleRenderer.line(vertices, matrix, radialX * 0.86, radialZ * 0.86,
					radialX * 0.95, radialZ * 0.95, width, crimson, alpha);
		}
		matrices.pop();
	}

	private static void sphere(VertexConsumer vertices, Matrix4f matrix, double radius) {
		for (int latitude = 0; latitude < 32; latitude++) {
			double lower = -Math.PI / 2 + latitude * Math.PI / 32;
			double upper = lower + Math.PI / 32;
			for (int longitude = 0; longitude < 64; longitude++) {
				double start = longitude * Math.PI / 32, end = start + Math.PI / 32;
				sphereVertex(vertices, matrix, radius, lower, start);
				sphereVertex(vertices, matrix, radius, upper, start);
				sphereVertex(vertices, matrix, radius, upper, end);
				sphereVertex(vertices, matrix, radius, lower, end);
			}
		}
	}

	private static void sphereVertex(VertexConsumer vertices, Matrix4f matrix, double radius, double latitude, double longitude) {
		vertices.vertex(matrix, (float) (Math.cos(latitude) * Math.cos(longitude) * radius),
				(float) (Math.sin(latitude) * radius), (float) (Math.cos(latitude) * Math.sin(longitude) * radius))
				.color(0, 0, 0, 255).next();
	}
}
