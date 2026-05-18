package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.IllusionerEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 绑定脚环（Binding Anklet）—— 契灵首个专属饰品。
 *
 * 槽位：复用 SSC 守御脚环的 trinkets:feet/aglet 槽（与守御脚环互斥）。
 * 装备限制：仅契灵形态可装备，其他形态拒绝。
 * 被动效果：在 16 格范围内为其他**劫掠阵营 NPC**（pillager / vindicator / evoker /
 *           illusioner / ravager / witch）提供 +20% 造成伤害加成；
 *           佩戴者本人（玩家/契灵）不享受此加成。
 * 获取途径：仅 25% 概率出现在劫掠者哨塔战利品箱中。
 *
 * 加成的伤害侧由 {@link net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity.BindingAnkletAuraMixin}
 * 在 LivingEntity#damage 入口 ModifyVariable，调用本类静态方法判定。
 */
public class BindingAnkletItem extends TrinketItem {

	/** 灵气范围（格） */
	public static final double AURA_RADIUS = 16.0D;
	/** 加成倍数 */
	public static final float DAMAGE_MULTIPLIER = 1.20F;

	public BindingAnkletItem(Settings settings) {
		super(settings);
	}

	/* ------------------------------------------------------------ */
	/*  装备限制                                                       */
	/* ------------------------------------------------------------ */

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		// 仅契灵形态可装备
		return FormUtils.isForm(entity, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
	}

	/* ------------------------------------------------------------ */
	/*  灵气加成判定（供 Mixin 调用）                                    */
	/* ------------------------------------------------------------ */

	/**
	 * 判定 attacker 是否属于劫掠阵营 NPC（不含玩家、不含玩家的召唤物 / 宠物）。
	 */
	public static boolean isRaiderFaction(LivingEntity attacker) {
		if (attacker instanceof PlayerEntity) return false;
		if (attacker instanceof RaiderEntity) return true; // 含 pillager / vindicator / evoker / illusioner / ravager / witch（注：witch 在 1.20 也是 RaiderEntity 子类）
		// 兜底：直接列举（避免某些 mod 替换继承链时漏判）
		return attacker instanceof PillagerEntity
				|| attacker instanceof VindicatorEntity
				|| attacker instanceof EvokerEntity
				|| attacker instanceof IllusionerEntity
				|| attacker instanceof WitchEntity
				|| attacker instanceof IllagerEntity;
	}

	/**
	 * 检测 attacker 周围 AURA_RADIUS 格内是否存在佩戴绑定脚环的契灵玩家。
	 * 多人环境下：必须在服务器线程调用；佩戴者自身的攻击不在此函数判定（attacker
	 * 必为劫掠 NPC，玩家本人天然不满足 {@link #isRaiderFaction}）。
	 */
	public static boolean hasAnkletAuraNearby(LivingEntity attacker) {
		World world = attacker.getWorld();
		if (world.isClient) return false;
		Box box = attacker.getBoundingBox().expand(AURA_RADIUS);
		// getEntitiesByClass 已自带 box 过滤，再补距离平方过滤保证球形范围
		double r2 = AURA_RADIUS * AURA_RADIUS;
		List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, box, p -> !p.isSpectator());
		for (PlayerEntity p : players) {
			if (p.squaredDistanceTo(attacker) > r2) continue;
			// 必须是契灵形态（否则装备早就被拒绝，但热切换形态时双保险）
			if (!FormUtils.isForm(p, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) continue;
			if (isAnkletEquipped(p)) return true;
		}
		return false;
	}

	private static boolean isAnkletEquipped(PlayerEntity player) {
		Optional<TrinketComponent> opt = TrinketsApi.getTrinketComponent(player);
		if (opt.isEmpty()) return false;
		return opt.get().isEquipped(SscAddon.BINDING_ANKLET);
	}

	/* ------------------------------------------------------------ */
	/*  战利品注入：劫掠者哨塔 25%                                       */
	/* ------------------------------------------------------------ */

	private static final Identifier PILLAGER_OUTPOST_LOOT = new Identifier("minecraft", "chests/pillager_outpost");

	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (!PILLAGER_OUTPOST_LOOT.equals(id)) return;
			LootPool.Builder pool = LootPool.builder()
					.rolls(ConstantLootNumberProvider.create(1.0F))
					.conditionally(RandomChanceLootCondition.builder(0.25F).build())
					.with(ItemEntry.builder(SscAddon.BINDING_ANKLET));
			tableBuilder.pool(pool);
		});
	}

	/* ------------------------------------------------------------ */
	/*  Tooltip                                                       */
	/* ------------------------------------------------------------ */

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.binding_anklet.tooltip_1").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("item.ssc_addon.binding_anklet.tooltip_2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.binding_anklet.tooltip_3").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.binding_anklet.tooltip_4").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
