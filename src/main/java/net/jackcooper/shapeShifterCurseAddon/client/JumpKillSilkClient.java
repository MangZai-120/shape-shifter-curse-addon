package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跳蛛「安全丝」客户端渲染器。
 *
 * <p>服务端 {@code syncJumpKillSilk} 广播锚点状态（建立/断丝），本类维护
 * UUID→锚点镜像表，在 {@code WorldRenderEvents.AFTER_ENTITIES} 逐帧从玩家躯干到锚点画一条
 * 蛛丝（复用月织蛛蛛丝荡漾的绳索贴图 {@code web_swing_rope.png} 与双面 quad 几何）。</p>
 */
@Environment(EnvType.CLIENT)
public final class JumpKillSilkClient {

	private static final Identifier ROPE_TEXTURE = new Identifier("my_addon", "textures/entity/web_swing_rope.png");
	private static final float ROPE_HALF_WIDTH = 0.04f;

	/** UUID → 锚点坐标（active 镜像；断丝即移除）。 */
	private static final Map<UUID, Vec3d> ANCHORS = new ConcurrentHashMap<>();

	private JumpKillSilkClient() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(
				net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.PACKET_JUMP_KILL_SILK_STATE,
				(client, handler, buf, responseSender) -> {
					UUID uuid = buf.readUuid();
					boolean active = buf.readBoolean();
					double ax = buf.readDouble(), ay = buf.readDouble(), az = buf.readDouble();
					client.execute(() -> {
						if (active) {
							ANCHORS.put(uuid, new Vec3d(ax, ay, az));
						} else {
							ANCHORS.remove(uuid);
						}
					});
				});
		// 换世界/断线清空镜像
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ANCHORS.clear());
	}

	public static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
		VertexConsumerProvider vcp = ctx.consumers();
		if (vcp == null) return;
		World world = MinecraftClient.getInstance().world;
		if (world == null || ANCHORS.isEmpty()) return;

		float tickDelta = ctx.tickDelta();
		Camera cam = ctx.camera();
		Vec3d camPos = cam.getPos();
		MatrixStack ms = ctx.matrixStack();
		VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(ROPE_TEXTURE));
		float age = (float) world.getTime() + tickDelta;

		Iterator<Map.Entry<UUID, Vec3d>> it = ANCHORS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Vec3d> me = it.next();
			net.minecraft.entity.player.PlayerEntity e = world.getPlayerByUuid(me.getKey());
			if (e == null || e.isRemoved()) continue; // 玩家不在渲染范围：不画（锚点保留等回视野）
			Vec3d anchor = me.getValue();
			Vec3d hand = e.getLerpedPos(tickDelta).add(0, e.getHeight() * 0.5, 0);
			ms.push();
			ms.translate(anchor.x - camPos.x, anchor.y - camPos.y, anchor.z - camPos.z);
			drawRope(ms, vc, hand.x - anchor.x, hand.y - anchor.y, hand.z - anchor.z, age);
			ms.pop();
		}
	}

	/** 从锚点本地原点沿 (relX,relY,relZ) 到玩家躯干画蛛丝（双面 quad，贴图滚动）。 */
	private static void drawRope(MatrixStack ms, VertexConsumer vc, double relX, double relY, double relZ, float age) {
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
		float scroll = -(age * 0.05f); // 缓慢滚动（静止保命丝）
		float v0 = scroll;
		float v1 = len * 1.5f + scroll;
		float r = 0.93f, g = 0.93f, b = 0.96f;
		float a = 0.8f;

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
							float x, float y, float z, float u, float v, float r, float g, float b, float a) {
		vc.vertex(pose, x, y, z).color(r, g, b, a).texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
				.normal(nrm, 0f, 1f, 0f).next();
	}
}
