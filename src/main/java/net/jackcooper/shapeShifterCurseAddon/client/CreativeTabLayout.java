package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.impl.itemgroup.FabricItemGroup;
import net.jackcooper.shapeShifterCurseAddon.mixin.ItemGroupPositionAccessor;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Keeps the two SSCA creative tabs adjacent after Fabric has assigned pages. */
public final class CreativeTabLayout {
    private CreativeTabLayout() {}
    public static void register() {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            if (screen instanceof CreativeInventoryScreen) ensureNeighborTabs();
        });
    }

	/** 附属主页 id。 */
	private static final Identifier MAIN_GROUP_ID = new Identifier("ssc_addon", "group");
	/** 法术页 id。 */
	private static final Identifier SPELL_GROUP_ID = new Identifier("ssc_addon", "spells");

	private static void ensureNeighborTabs() {
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
