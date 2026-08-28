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
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
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

	/** 佩戴者是否正戴着梦魇戒指（框架无关检测：SSC 抽象层 + Curios 反射兜底，统一走 TrinketUtils.isWearingAuto）。 */
	public static boolean isWearingBy(LivingEntity entity) {
		return net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils.isWearingAuto(entity, SscAddon.NIGHTMARE_RING);
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
