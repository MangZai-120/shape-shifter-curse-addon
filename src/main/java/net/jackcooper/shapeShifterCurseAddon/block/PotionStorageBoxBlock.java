package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 药品存储箱方块（jackcooper）：专存压缩能量药水（feed_potion），支持漏斗互通。
 * <p>右键打开界面（8 个存储槽）；破坏时掉落槽内物品。
 */
@SuppressWarnings("deprecation") // 覆写 vanilla @Deprecated 的 Block 交互/状态替换方法，统一抑制
public class PotionStorageBoxBlock extends BlockWithEntity {

	public PotionStorageBoxBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PotionStorageBoxBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		// 仅服务端 tick（自动合并同类药水）
		return world.isClient ? null
				: checkType(type, RegAddonBlockEntities.POTION_STORAGE_BOX_BE, PotionStorageBoxBlockEntity::tick);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof PotionStorageBoxBlockEntity box) {
				player.openHandledScreen(box);
			}
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof PotionStorageBoxBlockEntity box) {
				dropInventory(world, pos, box);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	/** 破坏时把槽内物品在方块中心掉落。 */
	private static void dropInventory(World world, BlockPos pos, PotionStorageBoxBlockEntity be) {
		for (int i = 0; i < be.size(); i++) {
			ItemStack stack = be.getStack(i);
			if (!stack.isEmpty()) {
				world.spawnEntity(new ItemEntity(world,
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
			}
		}
		be.clear();
	}
}
