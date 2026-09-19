package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * ItemGroup 位置字段写入器（jackcooper）：Fabric 的创造页分页（ItemGroupsMixin.paginateGroups）
 * 把模组物品组按完整 id 字典序排好后，通过自己的 accessor 写入 row / column。
 * 本附属需要在分页完成后再校正自己两个组的位置（保证「法术」页紧挨附属主页，见
 * {@code client.CreativeTabNeighborMixin}），故同样需要这两个字段的 setter。
 * getter 用原版公开 API（{@code row()} / {@code column()}）即可，无需在这里重复声明。
 */
@Mixin(ItemGroup.class)
public interface ItemGroupPositionAccessor {

	/** 写入行（TOP / BOTTOM）。 */
	@Accessor("row")
	void ssc_addon$setRow(ItemGroup.Row row);

	/** 写入列（0-4，每行 5 格）。 */
	@Accessor("column")
	void ssc_addon$setColumn(int column);
}
