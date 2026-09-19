package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.fabricmc.fabric.impl.itemgroup.FabricItemGroup;
import net.jackcooper.shapeShifterCurseAddon.mixin.ItemGroupPositionAccessor;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 创造页 tab 相邻校正（jackcooper）：保证「法术」页（{@code ssc_addon:spells}）
 * 与附属主页（{@code ssc_addon:group}）在创造物品栏里同页紧邻。
 *
 * <p><b>背景机制</b>（反编译 fabric-item-group-api-v1 确认）：Fabric 把非原版物品组按
 * <b>完整 id 字典序</b>排序，每 10 个一页（上排 5 + 下排 5）。{@code ssc_addon:group} 与
 * {@code ssc_addon:spells} 字典序连续（同命名空间，中间不可能插入第三方 id），<b>同页时天然相邻</b>；
 * 唯一被拆开的情形是「页边界」——主页恰好落在某页第 10 格、法术页被推到下一页，
 * 是否发生随环境里模组物品组数量变化（14/24/34 个……时触发）。</p>
 *
 * <p><b>挂点选择</b>：{@code CreativeInventoryScreen.init} HEAD。Fabric 的分页注入在
 * {@code ItemGroups.collect()}（资源加载期，远早于任何屏幕打开），此处分页结果早已就绪，
 * 与 Fabric 的 mixin 零注入点竞争。屏幕每次打开都会 init，逻辑幂等（已相邻则直接跳过）。</p>
 *
 * <p><b>校正算法（只动自己两个组 + 至多一次对调）</b>：
 * 设主页位置 (p1, r1, c1)、法术页位置 (p2, r2, c2)，不同页时：
 * <ol>
 *   <li>目标格 = 主页右格 (p1, r1, c1+1)；主页在行尾列（c1==4）时改用左格 (p1, r1, c1-1)；</li>
 *   <li>目标格若被第三方组占用 → 与法术页<b>原地对调</b>（第三方组只换位置/页，功能无损）；
 *       空格则直接落位。原版 14 组全在第 0 页且位置固定，不可能被命中；</li>
 *   <li>法术页写入目标格（page 用 Fabric 的 {@link FabricItemGroup#setPage}，
 *       row/column 用 {@link ItemGroupPositionAccessor}）。</li>
 * </ol>
 * page 坐标必须与 (row, column) 匹配，否则 Fabric 客户端 {@code fabric_getPage} 会拿页码过滤显示。</p>
 */
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeTabNeighborMixin {

	/** 附属主页 id。 */
	private static final Identifier MAIN_GROUP_ID = new Identifier("ssc_addon", "group");
	/** 法术页 id。 */
	private static final Identifier SPELL_GROUP_ID = new Identifier("ssc_addon", "spells");

	@Inject(method = "init", at = @At("HEAD"))
	private void ssc_addon$ensureNeighborTabs(CallbackInfo ci) {
		ItemGroup main = Registries.ITEM_GROUP.stream()
				.filter(g -> MAIN_GROUP_ID.equals(Registries.ITEM_GROUP.getId(g)))
				.findFirst().orElse(null);
		ItemGroup spells = Registries.ITEM_GROUP.stream()
				.filter(g -> SPELL_GROUP_ID.equals(Registries.ITEM_GROUP.getId(g)))
				.findFirst().orElse(null);
		if (main == null || spells == null) {
			return; // 注册表里找不到（理论不可能），安静让路
		}
		int p1 = pageOf(main);
		int p2 = pageOf(spells);
		if (p1 < 0 || p2 < 0) {
			return; // 页码异常（Fabric 分页后异常注册的第三方组），安静让路
		}
		ItemGroup.Row r1 = main.getRow();
		int c1 = main.getColumn();
		if (p1 == p2 && spells.getRow() == r1 && Math.abs(spells.getColumn() - c1) == 1) {
			return; // 已同行紧邻：理想状态，幂等跳过
		}
		// 注意：仅「同页」不够——连续索引跨上/下排边界时（上排末→下排首）视觉相距最远，
		// 必须校正到主页同一行的相邻格。
		// 目标格：右格优先；行尾列用左格
		int targetColumn = c1 < 4 ? c1 + 1 : c1 - 1;
		ItemGroup.Row targetRow = r1;
		// 占位者：同页同格的第三方组（第 0 页原版组不可能在此——本分支只在 p1>=1 时进入）
		ItemGroup occupant = null;
		for (ItemGroup g : Registries.ITEM_GROUP) {
			if (g == main || g == spells) {
				continue;
			}
			if (pageOf(g) == p1 && g.getRow() == targetRow && g.getColumn() == targetColumn) {
				occupant = g;
				break;
			}
		}
		if (occupant != null) {
			// 对调：占位第三方组挪去法术页原位置（功能无损，仅换页/换格）
			((FabricItemGroup) occupant).setPage(p2);
			((ItemGroupPositionAccessor) occupant).ssc_addon$setRow(spells.getRow());
			((ItemGroupPositionAccessor) occupant).ssc_addon$setColumn(spells.getColumn());
		}
		// 法术页落位主页旁
		((FabricItemGroup) spells).setPage(p1);
		((ItemGroupPositionAccessor) spells).ssc_addon$setRow(targetRow);
		((ItemGroupPositionAccessor) spells).ssc_addon$setColumn(targetColumn);
	}

	/** 安全读取组的页码：无页码（Fabric 分页后异常注册的第三方组）返回 -1，不抛异常。 */
	private static int pageOf(ItemGroup group) {
		try {
			return ((FabricItemGroup) group).getPage();
		} catch (IllegalStateException e) {
			return -1;
		}
	}
}
