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
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 海晶荧光坠：荧光幼灵形态专属项链。
 * 装备后强化两个主动技能——潮汐球变一次性爆炸球（加速、范围伤害与减速），
 * 法阵激光变缩范围三连发（施法期仅半速而非完全定身）。
 * 具体效果在 TidalOrbEntity / LaserBeamEntity / 两个 Manager 里通过
 * TrinketUtils.isWearing(owner, SscAddon.SEA_CRYSTAL_PENDANT) 判定生效。
 *
 * <p>饰品后端无关：与附属其余饰品一致继承 SSC 的 {@link AccessoryItem} 抽象层
 * （Trinkets / Curios 桥接自动适配），不直接依赖 Trinkets 类——附属对 trinkets 为弱依赖。
 */
public class SeaCrystalPendantItem extends AccessoryItem {
	public SeaCrystalPendantItem(Settings settings) {
		super(settings);
	}

	// 沉船宝藏箱 + 藏宝图（埋藏的宝藏）箱，均以 15% 概率掉落；本饰品无法合成，仅此两种途径获得
	private static final Identifier SHIPWRECK_TREASURE_LOOT = new Identifier("minecraft", "chests/shipwreck_treasure");
	private static final Identifier BURIED_TREASURE_LOOT = new Identifier("minecraft", "chests/buried_treasure");

	/** 注册海晶荧光坠到沉船宝藏箱 + 藏宝图宝藏箱战利品表（各 15% 概率）；本饰品无法合成，仅此两种途径获得。 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (!SHIPWRECK_TREASURE_LOOT.equals(id) && !BURIED_TREASURE_LOOT.equals(id)) return;
			LootPool.Builder pool = LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(0.15F))
					.with(ItemEntry.builder(SscAddon.SEA_CRYSTAL_PENDANT));
			tableBuilder.pool(pool);
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		// 专属限制：只有荧光幼灵（含阿澪）能装备。
		// 登录装载瞬间（age==0）宽容放行（防重开世界被 Curios/Trinkets 校验吐出），
		// 形态不符的由 AddonAccessoryGuard.tick 全局兜底自动卸下归还。
		return AddonAccessoryGuard.canEquip(entity, FormUtils::isAxolotlFluorescent);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.sea_crystal_pendant.desc").formatted(Formatting.BLUE));
		tooltip.add(Text.translatable("item.ssc_addon.sea_crystal_pendant.tooltip.exclusive").formatted(Formatting.LIGHT_PURPLE));
	}
}
