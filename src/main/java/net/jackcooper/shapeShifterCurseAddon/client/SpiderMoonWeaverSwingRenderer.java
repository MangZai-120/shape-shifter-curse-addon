package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jackcooper.shapeShifterCurseAddon.client.SpiderMoonWeaverSwingClient.LocalSwing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 月织蛛「蛛丝荡漾」绳索渲染器（纯客户端）。
 *
 * <p>逐帧从 {@link SpiderMoonWeaverSwingClient} 镜像读取每个摆荡玩家的销点 + 躯干位置，
 * 在 {@code WorldRenderEvents.AFTER_ENTITIES} 画一条蛛丝绳索（贴图沿绳身滚动形成延伸/流动感）。
 */
@Environment(EnvType.CLIENT)
public final class SpiderMoonWeaverSwingRenderer {

	private static final Identifier ROPE_TEXTURE = new Identifier("my_addon", "textures/entity/web_swing_rope.png");
	private static final float ROPE_HALF_WIDTH = 0.04f;

	private SpiderMoonWeaverSwingRenderer() {}

	public static void render(WorldRenderContext ctx) {
		VertexConsumerProvider vcp = ctx.consumers();
		if (vcp == null) return;
		World world = MinecraftClient.getInstance().world;
		if (world == null) return;

		float tickDelta = ctx.tickDelta();
		Camera cam = ctx.camera();
		Vec3d camPos = cam.getPos();
		MatrixStack ms = ctx.matrixStack();
		VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(ROPE_TEXTURE));
		float age = (float) world.getTime() + tickDelta;

		Iterator<Map.Entry<UUID, LocalSwing>> it = SpiderMoonWeaverSwingClient.iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, LocalSwing> me = it.next();
			LocalSwing sw = me.getValue();
			if (!sw.active) continue;
			PlayerEntity entity = world.getPlayerByUuid(me.getKey());
			if (entity == null) continue;

			// 蛛丝起点 = 玩家手部高度（躯干中心），终点 = 销点(SWINGING) 或 目标生物(TETHER)
			Vec3d hand = entity.getLerpedPos(tickDelta).add(0, entity.getHeight() * 0.5, 0);
			Vec3d anchor;
			if (sw.state == SpiderMoonWeaverSwingClient.STATE_TETHER) {
				net.minecraft.entity.Entity target = world.getEntityById(sw.tetherEntityId);
				if (target == null) continue;
				anchor = target.getLerpedPos(tickDelta).add(0, target.getHeight() * 0.5, 0);
			} else {
				anchor = new Vec3d(sw.anchorX, sw.anchorY, sw.anchorZ);
			}

			ms.push();
			ms.translate(anchor.x - camPos.x, anchor.y - camPos.y, anchor.z - camPos.z);
			drawRope(ms, vc, hand.x - anchor.x, hand.y - anchor.y, hand.z - anchor.z, age, sw.state);
			ms.pop();
		}

		// 飞弹飞行期：从 owner 手部到飞行中的蛛丝弹画蛛丝（未断开才连玩家；miss 断开后弹自由飞不连）
		// 性能：仅当最近 3t 内有蛛丝弹存活（tick 标记）时才扫实体，替代原「每帧无条件 instanceof
		// 全实体扫描」；飞行期 ≤2s，扫全实体成本可接受。扫描方式用原版已验证的 getEntities 迭代。
		if (net.jackcooper.shapeShifterCurseAddon.entity.SpiderSwingBullet.isBulletRecentlySeen(world.getTime())) {
			if (world instanceof net.minecraft.client.world.ClientWorld cw) {
				for (net.minecraft.entity.Entity e : cw.getEntities()) {
					if (!(e instanceof net.jackcooper.shapeShifterCurseAddon.entity.SpiderSwingBullet bullet)) continue;
					net.minecraft.entity.Entity ownerE = bullet.getOwner();
					if (ownerE == null) continue;
					Vec3d bp = bullet.getLerpedPos(tickDelta);
					Vec3d hand = ownerE.getLerpedPos(tickDelta).add(0, ownerE.getHeight() * 0.5, 0);
					ms.push();
					ms.translate(hand.x - camPos.x, hand.y - camPos.y, hand.z - camPos.z);
					drawRope(ms, vc, bp.x - hand.x, bp.y - hand.y, bp.z - hand.z, age, SpiderMoonWeaverSwingClient.STATE_FIRING);
					ms.pop();
				}
			}
		}
	}

	/** 从本地原点（销点）沿 (relX,relY,relZ) 到玩家手部画一条蛛丝。 */
	private static void drawRope(MatrixStack ms, VertexConsumer vc, double relX, double relY, double relZ, float age, int state) {
		double full = Math.sqrt(relX * relX + relY * relY + relZ * relZ);
		if (full < 0.05) return;
		double inv = 1.0 / full;
		double dny = relY * inv;
		float pitch = (float) Math.acos(MathHelper.clamp(dny, -1.0, 1.0));
		float yaw = (float) Math.atan2(relZ * inv, relX * inv);

		ms.push();
		ms.multiply(RotationAxis.POSITIVE_Y.rotation(1.5707964f - yaw));
		ms.multiply(RotationAxis.POSITIVE_X.rotation(pitch));

		MatrixStack.Entry e = ms.peek();
		Matrix4f pose = e.getPositionMatrix();
		Matrix3f nrm = e.getNormalMatrix();

		float len = (float) full;
		float halfW = ROPE_HALF_WIDTH;
		// 贴图沿绳身滚动；FIRING 期滚动更快（蛛丝正在延伸）
		float scroll = -(age * (state == SpiderMoonWeaverSwingClient.STATE_FIRING ? 0.3f : 0.05f));
		float v0 = scroll;
		float v1 = len * 1.5f + scroll;
		float r = 0.93f, g = 0.93f, b = 0.96f;
		float a = state == SpiderMoonWeaverSwingClient.STATE_FIRING ? 0.95f : 0.8f;

		for (int q = 0; q < 2; q++) {
			float ox = (q == 0) ? halfW : 0f;
			float oz = (q == 0) ? 0f : halfW;
			vtx(vc, pose, nrm, -ox, 0f, -oz, 0f, v1, r, g, b, a);
			vtx(vc, pose, nrm, -ox, len, -oz, 0f, v0, r, g, b, a);
			vtx(vc, pose, nrm, ox, len, oz, 1f, v0, r, g, b, a);
			vtx(vc, pose, nrm, ox, 0f, oz, 1f, v1, r, g, b, a);
		}
		ms.pop();
	}

	private static void vtx(VertexConsumer vc, Matrix4f pose, Matrix3f nrm,
	                        float x, float y, float z, float u, float v,
	                        float r, float g, float b, float a) {
		vc.vertex(pose, x, y, z).color(r, g, b, a).texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(nrm, 0f, 1f, 0f).next();
	}
}
