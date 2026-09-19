package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 诅咒标记头顶 2D 图标渲染器（纯客户端，jackcooper，2026-09-17 用户定稿）。
 *
 * <p>中 CURSE_MARK 状态的实体头顶常显一枚 2D 咒印图标，Billboard 固定朝向相机
 * （客机渲染）：状态效果本身经原版 DataTracker 同步到客户端，故<b>无需自建网络同步</b>，
 * 客户端本地扫可见实体即可。扫描按 10 tick（0.5s）节流缓存 entityId 列表，
 * 渲染帧只遍历缓存，逐帧开销 O(缓存数)。</p>
 *
 * <p>几何：图标中心在实体头顶 +0.45 格，尺寸 0.5×0.5 格；四角直接用<b>相机右/上基向量</b>张成
 * （不用矩阵旋转做 billboard——与背面剔除层交互后会被当背面剔掉），天然正对观察者；
 * 渲染层用无剔除的 getEntityTranslucent，双面可见。光效满亮度，贴图带透明通道。</p>
 */
@Environment(EnvType.CLIENT)
public final class CurseMarkIconRenderer {

	/** 图标贴图（用户提供素材，1284 字节 PNG）。 */
	private static final Identifier ICON_TEXTURE = new Identifier("ssc_addon", "textures/gui/curse_mark_icon.png");

	/** 图标边长（格）。 */
	private static final float SIZE = 0.5f;
	/** 图标中心相对实体头顶的抬升（格）。 */
	private static final float LIFT = 0.45f;

	/** 扫描节流：上次扫描的世界 tick 与结果缓存（entityId 列表）。 */
	private static long lastScanTick = -1;
	private static final List<Integer> CACHED_IDS = new ArrayList<>();

	private CurseMarkIconRenderer() {
	}

	/** {@code WorldRenderEvents.AFTER_ENTITIES} 回调：画所有带诅咒标记实体的头顶图标。 */
	public static void render(WorldRenderContext ctx) {
		VertexConsumerProvider vcp = ctx.consumers();
		if (vcp == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return;

		long now = client.world.getTime();
		if (now != lastScanTick && now % 10 == 0) {
			scanMarkedEntities(client);
			lastScanTick = now;
		}
		if (CACHED_IDS.isEmpty()) return;

		float tickDelta = ctx.tickDelta();
		Camera cam = ctx.camera();
		Vec3d camPos = cam.getPos();
		MatrixStack ms = ctx.matrixStack();
		// 无剔除层（双面可见）：图标不会因背面剔除从任何视角消失
		VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(ICON_TEXTURE));
		// 相机基向量（世界空间）：右/上——图标四角用它们张成，永远正对观察者
		org.joml.Vector3f camRight = new org.joml.Vector3f(1, 0, 0).rotate(cam.getRotation());
		org.joml.Vector3f camUp = new org.joml.Vector3f(0, 1, 0).rotate(cam.getRotation());

		for (int id : CACHED_IDS) {
			var entity = client.world.getEntityById(id);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
			if (!living.hasStatusEffect(SscAddon.CURSE_MARK)) continue; // 扫描间隙已净化的跳过
			Vec3d pos = living.getLerpedPos(tickDelta)
					.add(0.0, living.getHeight() + LIFT - SIZE * 0.5f, 0.0);
			ms.push();
			ms.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
			drawCameraFacingQuad(ms, vc, SIZE, camRight, camUp);
			ms.pop();
		}
	}

	/** 扫描玩家周围 64 格内带 CURSE_MARK 的生物，缓存 entityId。 */
	private static void scanMarkedEntities(MinecraftClient client) {
		CACHED_IDS.clear();
		var world = client.world;
		if (world == null || client.player == null) return;
		for (var entity : world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
			if (!living.hasStatusEffect(SscAddon.CURSE_MARK)) continue;
			if (living.squaredDistanceTo(client.player) > 64.0 * 64.0) continue;
			CACHED_IDS.add(living.getId());
		}
	}

	/**
	 * 以当前矩阵原点为中心，用相机右/上基向量直接张四角画正方形贴图面片。
	 * 四角都在相机平面内 → 面片永远正对观察者，且绕观察方向不会反向（上始终朝屏幕上方）。
	 * 零临时对象分配。满亮度不受环境光影响。
	 */
	private static void drawCameraFacingQuad(MatrixStack ms, VertexConsumer vc, float size,
			org.joml.Vector3f right, org.joml.Vector3f up) {
		float h = size * 0.5f;
		float rx = right.x * h, ry = right.y * h, rz = right.z * h;
		float ux = up.x * h, uy = up.y * h, uz = up.z * h;
		int light = 15728880; // 满亮度（发光体语义）
		Matrix4f mat = ms.peek().getPositionMatrix();
		Matrix3f normal = ms.peek().getNormalMatrix();
		// 左上（UV 0,0）→ 左下 → 右下 → 右上，逆时针
		vc.vertex(mat, -rx + ux, -ry + uy, -rz + uz).color(255, 255, 255, 255).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0, 1, 0).next();
		vc.vertex(mat, -rx - ux, -ry - uy, -rz - uz).color(255, 255, 255, 255).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0, 1, 0).next();
		vc.vertex(mat, rx - ux, ry - uy, rz - uz).color(255, 255, 255, 255).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0, 1, 0).next();
		vc.vertex(mat, rx + ux, ry + uy, rz + uz).color(255, 255, 255, 255).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0, 1, 0).next();
	}

	/** 断线 / 切世界清缓存（防残留）。 */
	public static void clear() {
		CACHED_IDS.clear();
		lastScanTick = -1;
	}
}
