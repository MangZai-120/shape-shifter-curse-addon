package net.jackcooper.shapeShifterCurseAddon.item;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 梦魇戒指（食梦魔专属，jackcooper）。
 * 好处：「恐惧」持续时间 +35%（15 秒 → 20.25 秒，300t → 405t）。
 * 坏处：恐惧期间的「首次伤害翻倍」效果被移除（整轮恐惧均不触发 ×2）。
 * 数值逻辑在 {@code NightmareFearManager}：施加恐惧瞬间快照佩戴状态进 FearState，
 * 进行中的恐惧不受中途戴/摘影响（下次施加才按新状态结算）。
 *
 * 获取途径：15% 概率出现在地牢（怪物房间）战利品箱中。
 */
public class NightmareRingItem extends AccessoryItem {

	/** 地牢（怪物房间）战利品表 id。 */
	private static final Identifier DUNGEON_LOOT = new Identifier("minecraft", "chests/simple_dungeon");

	/** 「恐惧」持续时长增幅（+35%）。 */
	public static final float FEAR_DURATION_BONUS = 0.35f;

	public NightmareRingItem(Settings settings) {
		super(settings);
	}

	/**
	 * 注册梦魇戒指到地牢战利品表（15% 概率）。
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (!DUNGEON_LOOT.equals(id)) return;
			LootPool.Builder poolBuilder = LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(0.15F))
					.with(ItemEntry.builder(SscAddon.NIGHTMARE_RING));
			tableBuilder.pool(poolBuilder);
		});
	}

	/** 佩戴者是否正戴着梦魇戒指（框架无关检测：trinkets/tclayer 走 TrinketUtils，Curios 反射兜底）。 */
	public static boolean isWearingBy(LivingEntity entity) {
		// ① 原生 Trinkets / Accessories(tclayer) 链路
		if (net.onixary.shapeShifterCurseFabric.ssc_addon.util.TrinketUtils.isWearing(entity, SscAddon.NIGHTMARE_RING)) {
			return true;
		}
		// ② Curios（Kilt/Connector 转载的 Forge 版）反射兜底：
		// CuriosApi.getCuriosInventory(living).resolve().isEquipped(item)
		// 方法名均为 Curios 自有 API 名（getCuriosInventory/resolve/isEquipped），不受 MC 映射重映射影响；
		// 类未加载（未装 Curios）或任何异常一律返回 false，安全兜底。
		try {
			Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
			Object lazyOptional = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, entity);
			if (lazyOptional == null) return false;
			Object resolved = lazyOptional.getClass().getMethod("resolve").invoke(lazyOptional);
			if (!(resolved instanceof java.util.Optional<?> opt) || opt.isEmpty()) return false;
			Object handler = opt.get();
			Object equipped = handler.getClass().getMethod("isEquipped", net.minecraft.item.Item.class).invoke(handler, thisItem());
			return Boolean.TRUE.equals(equipped);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** this 的 Item 形式（isEquipped 参数用）。 */
	private static net.minecraft.item.Item thisItem() {
		return SscAddon.NIGHTMARE_RING;
	}

	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		return AddonAccessoryGuard.canEquip(entity, e -> FormUtils.isForm(e, FormIdentifiers.WILD_CAT_NIGHTMARE));
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.nightmare_ring.tooltip_1").formatted(Formatting.DARK_PURPLE));
		tooltip.add(Text.translatable("item.ssc_addon.nightmare_ring.tooltip_2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.nightmare_ring.tooltip_exclusive").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
