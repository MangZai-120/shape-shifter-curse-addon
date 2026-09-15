package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritBoltEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * 月灵光弹渲染器（jackcooper）：程序化小八面体发光体（Lightning 层 POSITION_COLOR，
 * Sodium 安全——不手填 normal/uv 等元素）。按变体染色（粉/蓝/绿），飞行中自转。
 * 拖尾粒子由实体服务端撒（END_ROD），渲染器只画本体。
 */
public class LunarSpiritBoltRenderer extends EntityRenderer<LunarSpiritBoltEntity> {
	/** 占位贴图（Lightning 层不采样，仅满足抽象方法）。 */
	private static final Identifier TEXTURE = new Identifier("my_addon", "textures/entity/moon_spirit_green.png");
	/** 光弹尺寸（格）。 */
	private static final float BOLT_SIZE = 0.18f;

	public LunarSpiritBoltRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.0f;
	}

	@Override
	public Identifier getTexture(LunarSpiritBoltEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(LunarSpiritBoltEntity entity, float entityYaw, float partialTick,
	                   MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		float age = entity.age + partialTick;
		// 按变体取色（0 粉红 / 1 青蓝 / 2 绿）
		int variant = entity.getVariant();
		float r = variant == 0 ? 1.0f : variant == 1 ? 0.55f : 0.62f;
		float g = variant == 0 ? 0.62f : variant == 1 ? 0.88f : 0.92f;
		float b = variant == 0 ? 0.75f : variant == 1 ? 0.96f : 0.65f;
		matrices.push();
		// 高速自转（光弹旋转感）
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 30.0f));
		float size = BOLT_SIZE * (1.0f + (float) Math.sin(age * 0.5f) * 0.1f);
		matrices.scale(size, size, size);
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLightning());
		renderOctahedron(consumer, matrices.peek(), r, g, b);
		matrices.pop();
		super.render(entity, entityYaw, partialTick, matrices, vertexConsumers, light);
	}

	/** 程序化八面体（上下双锥）。POSITION_COLOR 格式：每顶点位置 + 颜色即可。 */
	private static void renderOctahedron(VertexConsumer consumer, MatrixStack.Entry entry,
	                                     float r, float g, float b) {
		var top = new Vec3d(0, 1, 0);
		var bottom = new Vec3d(0, -1, 0);
		var eq0 = new Vec3d(0.71, 0, 0.71);
		var eq1 = new Vec3d(-0.71, 0, 0.71);
		var eq2 = new Vec3d(-0.71, 0, -0.71);
		var eq3 = new Vec3d(0.71, 0, -0.71);
		tri(consumer, entry, top, eq0, eq1, r, g, b);
		tri(consumer, entry, top, eq1, eq2, r, g, b);
		tri(consumer, entry, top, eq2, eq3, r, g, b);
		tri(consumer, entry, top, eq3, eq0, r, g, b);
		tri(consumer, entry, bottom, eq1, eq0, r, g, b);
		tri(consumer, entry, bottom, eq2, eq1, r, g, b);
		tri(consumer, entry, bottom, eq3, eq2, r, g, b);
		tri(consumer, entry, bottom, eq0, eq3, r, g, b);
	}

	private static void tri(VertexConsumer consumer, MatrixStack.Entry entry,
	                        Vec3d a, Vec3d b, Vec3d c, float r, float g, float b2) {
		vertex(consumer, entry, a, r, g, b2);
		vertex(consumer, entry, b, r, g, b2);
		vertex(consumer, entry, c, r, g, b2);
	}

	private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry,
	                           Vec3d v, float r, float g, float b) {
		consumer.vertex(entry.getPositionMatrix(), (float) v.x, (float) v.y, (float) v.z)
				.color(r, g, b, 1.0f)
				.next();
	}
}
