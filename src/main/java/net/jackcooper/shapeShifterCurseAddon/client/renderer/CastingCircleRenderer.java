package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

public final class CastingCircleRenderer {
	private CastingCircleRenderer() {}

	public static void render(WorldRenderContext context) {
		var client = MinecraftClient.getInstance();
		if (client.world == null || context.consumers() == null) return;
		Vec3d camera = context.camera().getPos();
		for (var player : client.world.getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || player.isInvisible()
					|| !CastingVisualState.hasCircle(player.getUuid()) || DomainRenderer.isCharging(player.getUuid())
					|| CastingVisualState.isExplosion(player.getUuid())) continue;
			Vec3d feet = player.getLerpedPos(context.tickDelta());
			if (feet.squaredDistanceTo(camera) > 32 * 32) continue;
			var ground = client.world.raycast(new RaycastContext(feet.add(0, 0.15, 0),
					feet.add(0, -0.35, 0), RaycastContext.ShapeType.COLLIDER,
					RaycastContext.FluidHandling.NONE, player));
			double circleY = ground.getType() == HitResult.Type.BLOCK
					&& ground.getSide() == net.minecraft.util.math.Direction.UP
					&& Math.abs(ground.getPos().y - feet.y) <= 0.16 ? ground.getPos().y : feet.y;
			var matrices = context.matrixStack();
			matrices.push();
			matrices.translate(feet.x - camera.x, circleY + 0.025 - camera.y, feet.z - camera.z);
			VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getDebugQuads());
			// 2026-09-23 用户定稿：普通施法脚下法阵改用爆裂魔法同款地面设计（等比缩放到原 0.72 外径），
			// 主色随卷轴稀有度、辅色恒紫（groundStrokes 内置换色并按色缓存）；旋转速度与爆裂地面圈一致。
			int color = CastingVisualState.circleColor(player.getUuid());
			float age = player.age + context.tickDelta();
			float alpha = CastingVisualState.circleAlpha(player.getUuid()) * (0.88F + 0.06F * (float) Math.sin(age * 0.1));
			drawGround(vertices, matrices, 0.72, 0, age * 0.02, alpha, color);
			matrices.pop();
		}
	}

	public static void ring(VertexConsumer vertices, Matrix4f matrix, double radius, double width,
	                         double rotation, int color, float alpha) {
		for (int segment = 0; segment < 64; segment++) {
			double start = rotation + segment * Math.PI / 32;
			double end = rotation + (segment + 1) * Math.PI / 32;
			line(vertices, matrix, Math.cos(start) * radius, Math.sin(start) * radius,
					Math.cos(end) * radius, Math.sin(end) * radius, width, color, alpha);
		}
	}

	/** Canonical ground circle: ordinary casting, domain and explosion all use this drawing. */
	public static void drawGround(VertexConsumer vertices, MatrixStack matrices, double radius, double height,
	                              double rotation, float alpha, int primaryColor) {
		drawPattern(vertices, matrices, radius, height, rotation, alpha,
				MagicCircleGeometry.groundStrokes(primaryColor), primaryColor);
	}

	/** Shared cached-geometry path for ordinary casting, domain and explosion circles. */
	public static void drawPattern(VertexConsumer vertices, MatrixStack matrices, double radius, double height,
	                               double rotation, float alpha, java.util.List<MagicCircleGeometry.Stroke> strokes,
	                               int glowColor) {
		if (radius <= 0.05 || alpha <= 0) return;
		matrices.push();
		matrices.translate(0, height, 0);
		matrices.scale((float) radius, 1, (float) radius);
		matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation((float) rotation));
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		// 频闪修复（2026-09-23）：getDebugQuads 是 translucent 层（反编译确认 buffer.setSorter 逐帧按相机
		// 距离排序四边形），发光层与主线同平面且到相机距离大量相等（排序平局），相机微动即翻转混合顺序
		// → 亮度逐帧交替。两层保险：① 发光层下移 0.002 格打破共面（确定的主线在上关系，排序稳定）；
		// ② 小尺寸法阵（半径 < 1 格，如施法者脚下 0.72 格）跳过发光层——小尺寸下发光本就糊成一片，
		// 且贡献了绝大多数重叠四边形。爆裂/领域的大法阵（≥2 格）保留发光层，仅受益于 ①。
		boolean glow = radius >= 1.0;
		if (glow) {
			// 发光层矩阵：在当前矩阵基础上再向下平移 0.002 格（除以半径抵消上方 scale）
			matrices.push();
			matrices.translate(0, -0.002 / radius, 0);
			Matrix4f glowMatrix = matrices.peek().getPositionMatrix();
			for (var stroke : strokes) {
				drawStroke(vertices, glowMatrix, stroke, stroke.width() * MagicCircleGeometry.GLOW_WIDTH,
						glowColor, alpha * stroke.alpha() * MagicCircleGeometry.GLOW_ALPHA);
			}
			matrices.pop();
		}
		for (var stroke : strokes) {
			drawStroke(vertices, matrix, stroke, stroke.width(), stroke.color(), alpha * stroke.alpha());
		}
		matrices.pop();
	}

	private static void drawStroke(VertexConsumer vertices, Matrix4f matrix,
	                               MagicCircleGeometry.Stroke stroke, double width, int color, float alpha) {
		double half = width / 2;
		vertex(vertices, matrix, stroke.x1() + stroke.nx1() * half, stroke.z1() + stroke.nz1() * half, color, alpha);
		vertex(vertices, matrix, stroke.x2() + stroke.nx2() * half, stroke.z2() + stroke.nz2() * half, color, alpha);
		vertex(vertices, matrix, stroke.x2() - stroke.nx2() * half, stroke.z2() - stroke.nz2() * half, color, alpha);
		vertex(vertices, matrix, stroke.x1() - stroke.nx1() * half, stroke.z1() - stroke.nz1() * half, color, alpha);
	}

	public static void line(VertexConsumer vertices, Matrix4f matrix, double startX, double startZ,
	                         double endX, double endZ, double width, int color, float alpha) {
		double length = Math.hypot(endX - startX, endZ - startZ);
		double offsetX = -(endZ - startZ) / length * width * 0.5;
		double offsetZ = (endX - startX) / length * width * 0.5;
		vertex(vertices, matrix, startX + offsetX, startZ + offsetZ, color, alpha);
		vertex(vertices, matrix, endX + offsetX, endZ + offsetZ, color, alpha);
		vertex(vertices, matrix, endX - offsetX, endZ - offsetZ, color, alpha);
		vertex(vertices, matrix, startX - offsetX, startZ - offsetZ, color, alpha);
	}

	private static void vertex(VertexConsumer vertices, Matrix4f matrix, double horizontal, double depth, int color, float alpha) {
		vertices.vertex(matrix, (float) horizontal, 0, (float) depth)
				.color(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, alpha).next();
	}
}
