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
 * 寒棘项圈（寒棘狐专属，jackcooper）。
 * <p><b>加强</b>：主技能「冰刺」扔出的普通冰锥命中敌人时，立刻在身边免费回补 1 根环绕冰锥
 * （走与主动凝聚一致的槽位逻辑：优先空位，满 5 根替换最旧）。</p>
 * <p><b>削弱</b>：①普通冰锥命中伤害 ×80%（8 → 6.4）；②凝聚间隔 ×1.75（1.2 秒 → 2.1 秒）。
 * 次技能「凝棘」强化冰锥不受伤害削弱影响（其伤害来自消耗的环绕冰锥）。</p>
 * <p>数值逻辑散点：{@code FrostThornEntity.onEntityHit}（伤害减半 + 命中回补）、
 * {@code FrostSpikeManager.tick}（凝聚间隔）。</p>
 *
 * <p>获取途径：雪屋（igloo）战利品箱 15% 概率 + 地牢（怪物房间）战利品箱 10% 概率。</p>
 */
public class FrostSpineCollarItem extends AccessoryItem {

	/** 雪屋（igloo 楼上chest）战利品表 id。 */
	private static final Identifier IGLOO_LOOT = new Identifier("minecraft", "chests/igloo_chest");

	/** 地牢（怪物房间）战利品表 id。 */
	private static final Identifier DUNGEON_LOOT = new Identifier("minecraft", "chests/simple_dungeon");

	/** 凝聚间隔倍率（×1.75）：1.2s → 2.1s。 */
	public static final float CHARGE_INTERVAL_MULTIPLIER = 1.75f;

	/** 普通冰锥伤害倍率（×80%）。 */
	public static final float DAMAGE_MULTIPLIER = 0.8f;

	public FrostSpineCollarItem(Settings settings) {
		super(settings);
	}

	/**
	 * 注册寒棘项圈到雪屋 / 地牢战利品表。
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			float chance;
			if (IGLOO_LOOT.equals(id)) {
				chance = 0.15F;
			} else if (DUNGEON_LOOT.equals(id)) {
				chance = 0.10F;
			} else {
				return;
			}
			LootPool.Builder poolBuilder = LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(chance))
					.with(ItemEntry.builder(SscAddon.FROST_SPINE_COLLAR));
			tableBuilder.pool(poolBuilder);
		});
	}

	/** 佩戴者是否正戴着寒棘项圈（框架无关检测：SSC 抽象层 + Curios 反射兜底，统一走 TrinketUtils.isWearingAuto）。 */
	public static boolean isWearingBy(LivingEntity entity) {
		return net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils.isWearingAuto(entity, SscAddon.FROST_SPINE_COLLAR);
	}

	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		return AddonAccessoryGuard.canEquip(entity, e -> FormUtils.isForm(e, FormIdentifiers.SNOW_FOX_FROSTSPINE));
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.frost_spine_collar.tooltip_1").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.ssc_addon.frost_spine_collar.tooltip_2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.frost_spine_collar.tooltip_3").formatted(Formatting.RED));
		tooltip.add(Text.translatable("item.ssc_addon.frost_spine_collar.tooltip_4").formatted(Formatting.RED));
		tooltip.add(Text.translatable("item.ssc_addon.frost_spine_collar.tooltip_exclusive").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
