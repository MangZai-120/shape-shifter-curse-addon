package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
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
					|| !CastingVisualState.hasCircle(player.getUuid())) continue;
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
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getDebugQuads());
			int color = CastingVisualState.circleColor(player.getUuid());
			float age = player.age + context.tickDelta();
			float alpha = CastingVisualState.circleAlpha(player.getUuid()) * (0.88F + 0.06F * (float) Math.sin(age * 0.1));
			ring(vertices, matrix, 0.72, 0.036, 0, color, alpha);
			ring(vertices, matrix, 0.64, 0.024, 0, color, alpha * 0.85F);
			ring(vertices, matrix, 0.25, 0.030, 0, color, alpha);
			double rotation = age * 0.006;
			for (int point = 0; point < 6; point++) {
				double angle = rotation + point * Math.PI / 3;
				double next = angle + Math.PI * 2 / 3;
				line(vertices, matrix, Math.cos(angle) * 0.54, Math.sin(angle) * 0.54,
						Math.cos(next) * 0.54, Math.sin(next) * 0.54, 0.027, color, alpha * 0.85F);
				double radialX = Math.cos(angle), radialZ = Math.sin(angle);
				line(vertices, matrix, radialX * 0.65, radialZ * 0.65,
						radialX * 0.77, radialZ * 0.77, 0.042, color, alpha);
				line(vertices, matrix, radialX * 0.70 - radialZ * 0.035, radialZ * 0.70 + radialX * 0.035,
						radialX * 0.70 + radialZ * 0.035, radialZ * 0.70 - radialX * 0.035, 0.030, color, alpha);
			}
			matrices.pop();
		}
	}

	private static void ring(VertexConsumer vertices, Matrix4f matrix, double radius, double width,
	                         double rotation, int color, float alpha) {
		for (int segment = 0; segment < 64; segment++) {
			double start = rotation + segment * Math.PI / 32;
			double end = rotation + (segment + 1) * Math.PI / 32;
			line(vertices, matrix, Math.cos(start) * radius, Math.sin(start) * radius,
					Math.cos(end) * radius, Math.sin(end) * radius, width, color, alpha);
		}
	}

	private static void line(VertexConsumer vertices, Matrix4f matrix, double startX, double startZ,
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