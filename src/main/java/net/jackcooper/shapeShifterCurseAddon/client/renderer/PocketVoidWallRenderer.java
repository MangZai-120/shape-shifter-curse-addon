package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceBlocks;
import net.minecraft.block.Block;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;

/**
 * 隐形基岩虚空面渲染器（末地折跃门同款机制）：用不受光照影响的 END_GATEWAY 渲染层画六个面，
 * 面片本身是完全不透明的虚空星空——透过墙看到的永远是虚空，而方块自身 opacity=0 不挡光。
 * 只画朝向可见空间的面（邻面剔除），墙体相互紧贴时不产生内部面。
 */
@Environment(EnvType.CLIENT)
public final class PocketVoidWallRenderer implements BlockEntityRenderer<PocketSpaceBlocks.VoidWallBlockEntity> {

	public PocketVoidWallRenderer(BlockEntityRendererFactory.Context ctx) {}

	public static void register() {
		BlockEntityRendererFactories.register(PocketSpaceBlocks.VoidWallBlockEntity.TYPE, PocketVoidWallRenderer::new);
	}

	@Override
	public void render(PocketSpaceBlocks.VoidWallBlockEntity entity, float tickDelta, MatrixStack matrices,
	                   VertexConsumerProvider vcp, int light, int overlay) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		VertexConsumer buffer = vcp.getBuffer(RenderLayer.getEndGateway());
		side(entity, matrix, buffer, 0, 1, 0, 1, 1, 1, 1, 1, Direction.SOUTH);
		side(entity, matrix, buffer, 0, 1, 1, 0, 0, 0, 0, 0, Direction.NORTH);
		side(entity, matrix, buffer, 1, 1, 1, 0, 0, 1, 1, 0, Direction.EAST);
		side(entity, matrix, buffer, 0, 0, 0, 1, 0, 1, 1, 0, Direction.WEST);
		side(entity, matrix, buffer, 0, 1, 0, 0, 0, 0, 1, 1, Direction.DOWN);
		side(entity, matrix, buffer, 0, 1, 1, 1, 1, 1, 0, 0, Direction.UP);
	}

	private static void side(PocketSpaceBlocks.VoidWallBlockEntity entity, Matrix4f m, VertexConsumer v,
	                         float x1, float x2, float y1, float y2, float z1, float z2, float z3, float z4, Direction dir) {
		BlockPos pos = entity.getPos();
		if (entity.getWorld() == null
				|| !Block.shouldDrawSide(entity.getCachedState(), entity.getWorld(), pos, dir, pos.offset(dir))) {
			return;
		}
		v.vertex(m, x1, y1, z1).next();
		v.vertex(m, x2, y1, z2).next();
		v.vertex(m, x2, y2, z3).next();
		v.vertex(m, x1, y2, z4).next();
	}

	/** 大房间顶盖距离玩家可达 40+ 格，超出默认 64 格渲染距离前不会被剔除。 */
	@Override
	public int getRenderDistance() {
		return 128;
	}
}
