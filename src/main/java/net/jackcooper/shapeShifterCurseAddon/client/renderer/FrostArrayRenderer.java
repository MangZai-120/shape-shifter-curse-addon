package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostArrayEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 寒棘狐「凝棘」蓄力法阵渲染器：在施法者面部前方 0.2 格画青蓝雪花法阵（自发光、随时间自转），
 * 法阵前 0.08 格中央画一根随蓄力等级放大的冰锥（尖朝准星）。仿荧光幼灵法阵渲染方式（本地 +Z=准星）。
 */
@Environment(EnvType.CLIENT)
public class FrostArrayRenderer extends EntityRenderer<FrostArrayEntity> {

	private static final Identifier TEXTURE = new Identifier("minecraft", "textures/misc/white.png");

	// 青蓝主基调
	private static final float[] ICE_CYAN  = {0.45f, 0.85f, 1.00f, 0.85f};
	private static final float[] ICE_BLUE  = {0.35f, 0.60f, 1.00f, 0.80f};
	private static final float[] ICE_WHITE = {0.85f, 0.95f, 1.00f, 0.90f};

	private final ItemRenderer itemRenderer;

	public FrostArrayRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.itemRenderer = ctx.getItemRenderer();
	}

	@Override
	public void render(FrostArrayEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vcp, int light) {
		Entity owner = entity.getWorld().getEntityById(entity.getTrackedOwnerId());
		if (!(owner instanceof LivingEntity living)) return;

		float oyaw = MathHelper.lerp(tickDelta, owner.prevYaw, owner.getYaw());
		float opitch = MathHelper.lerp(tickDelta, owner.prevPitch, owner.getPitch());
		double ex = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
		double ey = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
		double ez = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
		// 法阵位置 = 头顶正上方环绕位（与服务端 FrostSpikeManager.secondaryFocus 严格同点：
		// 粒子汇聚中心/冰锥发射点都在这里，保证「合成位置 == 实际出生位置」）
		Vec3d hp = FrostThornEntity.hoverTarget(living, 0);

		matrices.push();
		matrices.translate(hp.x - ex, hp.y - ey, hp.z - ez);
		// 法阵平面垂直准星（环面朝向玩家）：先对齐 Y 轴再叠俯仰（只转这一次）
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-oyaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(opitch));

		float age = entity.age + tickDelta;
		// 青蓝雪花法阵（发光双面无光照层，任意角度可见）
		VertexConsumer buf = vcp.getBuffer(RenderLayer.getLightning());
		drawSnowArray(buf, matrices.peek().getPositionMatrix(), matrices.peek().getNormalMatrix(), age);
		matrices.pop(); // 撤掉法阵的 translate+旋转：中央冰锥要在未旋转空间里独立定向（否则朝向被叠加两次）

		// 中央冰锥：姿态与 FrostThornEntityRenderer 完全同公式（所见即所得）。
		// 朝向 = 未来飞行方向（头顶→准星远点的直线，发射时实体的初始速度方向），
		// 发射瞬间实体出现的位置/朝向/缩放与此处预览完全一致——消除「先旋转再飞」的姿态跳变。
		Vec3d eye = living.getEyePos();
		Vec3d aim = eye.add(living.getRotationVector().multiply(16.0));
		Vec3d fly = aim.subtract(hp).normalize();
		double horiz = Math.sqrt(fly.x * fly.x + fly.z * fly.z);
		float flyYaw = (float) (Math.atan2(-fly.x, fly.z) * (180.0 / Math.PI));
		float flyPitch = (float) (Math.atan2(-fly.y, horiz) * (180.0 / Math.PI));
		matrices.push();
		matrices.translate(hp.x - ex, hp.y - ey, hp.z - ez); // 重新平移到头顶（上面 pop 已撤掉法阵的 translate）
		float ts = 0.25f * (1.0f + 1.0f * entity.getLevel()); // 每合成一个冰锥 +100% 大小（同实体渲染器）
		matrices.scale(ts, ts, ts);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-flyYaw + 180.0F)); // 模型尖朝 -Z → 转到飞行方向（同实体公式）
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-flyPitch));
		// 模型中心补偿（同 FrostThornEntityRenderer：模型中心在网格 (1.5,0.5,3)）
		matrices.translate((8.0 - 1.5) / 16.0, (8.0 - 0.5) / 16.0, (8.0 - 3.0) / 16.0);
		ItemStack stack = new ItemStack(SscAddon.FROST_THORN);
		stack.getOrCreateNbt().putInt("CustomModelData", 1);
		this.itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, 0xF000F0, OverlayTexture.DEFAULT_UV,
				matrices, vcp, entity.getWorld(), entity.getId());
		matrices.pop();

		super.render(entity, yaw, tickDelta, matrices, vcp, light);
	}

	/** 青蓝雪花法阵：外圈青 + 内圈蓝 + 6 条带 45° 分叉的雪花主枝（XY 平面，随时间自转）。 */
	private void drawSnowArray(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float age) {
		float r = 0.5f;          // 圈小一点
		float spin = age * 0.02f;
		float w = 0.02f;
		ringBand(buf, pose, nrm, r, w * 1.3f, 48, spin, ICE_CYAN);
		ringBand(buf, pose, nrm, r * 0.55f, w, 36, -spin * 1.4f, ICE_BLUE);
		for (int i = 0; i < 6; i++) {
			double a = spin + Math.PI * 2 * i / 6;
			float tx = (float) Math.cos(a) * r * 0.92f, ty = (float) Math.sin(a) * r * 0.92f;
			thickSeg(buf, pose, nrm, 0f, 0f, tx, ty, w * 0.8f, ICE_WHITE); // 主枝
			// 分叉点在主枝 55% 处，向两侧各出 45° 小枝（雪花形）
			float bx = (float) Math.cos(a) * r * 0.55f, by = (float) Math.sin(a) * r * 0.55f;
			float br = r * 0.26f;
			double a1 = a + Math.PI / 4, a2 = a - Math.PI / 4;
			thickSeg(buf, pose, nrm, bx, by, bx + (float) Math.cos(a1) * br, by + (float) Math.sin(a1) * br, w * 0.55f, ICE_WHITE);
			thickSeg(buf, pose, nrm, bx, by, bx + (float) Math.cos(a2) * br, by + (float) Math.sin(a2) * br, w * 0.55f, ICE_WHITE);
		}
	}

	/** XY 平面圆环带。 */
	private void ringBand(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float r, float halfW, int seg, float spin, float[] c) {
		float ri = r - halfW, ro = r + halfW;
		for (int i = 0; i < seg; i++) {
			double a1 = spin + Math.PI * 2 * i / seg;
			double a2 = spin + Math.PI * 2 * (i + 1) / seg;
			float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
			quad(buf, pose, nrm,
					ri * c1, ri * s1, 0f, ro * c1, ro * s1, 0f,
					ro * c2, ro * s2, 0f, ri * c2, ri * s2, 0f, c);
		}
	}

	/** XY 平面上从 (x1,y1) 到 (x2,y2) 的粗线。 */
	private void thickSeg(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float x1, float y1, float x2, float y2, float halfW, float[] c) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0e-4f) len = 1f;
		float px = -dy / len * halfW, py = dx / len * halfW;
		quad(buf, pose, nrm,
				x1 - px, y1 - py, 0f, x2 - px, y2 - py, 0f,
				x2 + px, y2 + py, 0f, x1 + px, y1 + py, 0f, c);
	}

	/** 双面四边形。 */
	private void quad(VertexConsumer buf, Matrix4f pose, Matrix3f nrm,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, float[] col) {
		v(buf, pose, nrm, ax, ay, az, col);
		v(buf, pose, nrm, bx, by, bz, col);
		v(buf, pose, nrm, cx, cy, cz, col);
		v(buf, pose, nrm, dx, dy, dz, col);
		v(buf, pose, nrm, dx, dy, dz, col);
		v(buf, pose, nrm, cx, cy, cz, col);
		v(buf, pose, nrm, bx, by, bz, col);
		v(buf, pose, nrm, ax, ay, az, col);
	}

	private void v(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float x, float y, float z, float[] c) {
		buf.vertex(pose, x, y, z).color(c[0], c[1], c[2], c[3]).texture(0.5f, 0.5f)
				.overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(nrm, 0f, 0f, 1f).next();
	}

	@Override
	public Identifier getTexture(FrostArrayEntity entity) {
		return TEXTURE;
	}
}
