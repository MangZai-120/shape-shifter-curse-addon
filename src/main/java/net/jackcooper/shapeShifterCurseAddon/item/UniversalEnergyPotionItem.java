package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBarDef;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 通用能量药水（jackcooper）：饮用回复 25 点魔力，判定逻辑与 SSC 压缩能量药水（feed_potion）同源。
 *
 * <p>持有 SSCA 资源条（悦灵 mana / 蝙蝠血 / 阿努比斯灵魂 / 雪狐寒霜）的形态回复对应资源条
 * （恒 25）；寄生果蝠的种子条上限仅 10，固定回复 2 点；使魔 / 蜘蛛系 / 契灵（原版
 * ManaComponent 体系，契灵挂 familiar_fox_mana 的 mana_type_power）回复标准 mana 条——与
 * 压缩能量药水的 familiar_fox_mana 判定同源，但数值恒为 25 且不加饥饿（压缩能量药水饮用
 * 另 +8 饥饿 / +0.6 饱和）。朔望与无能量体系的形态（人类）饮用只消耗药水不回复，并动作栏提示。
 *
 * <p>实现参照项目内 {@code InfiniteEnergyPotionItem}：不继承 PotionItem（避免可堆叠类模组放开叠加），
 * finishUsing 服务端判定后回复，饮毕返还空玻璃瓶。
 * 由能量装瓶器产出（{@code EnergyBottlerBlockEntity.makeEnergyBottle}）。
 */
public class UniversalEnergyPotionItem extends Item {

	/** 回复的 mana 点数。 */
	public static final double MANA_RESTORE = 25.0;

	public UniversalEnergyPotionItem(Settings settings) {
		super(settings);
	}

	/**
	 * 是否可从此药水受益（判定思路与压缩能量药水同源，不再依赖手动挂的标记 power）：
	 * 持有任意 SSCA 资源条（悦灵 mana / 蝙蝠血 / 阿努比斯灵魂 / 雪狐寒霜 / 果蝠种子），
	 * 或身上存在原版 mana 类型（ManaComponent 非空——使魔系 familiar_fox_mana /
	 * 蜘蛛系 spider_mana 的所有阶段自动覆盖，含原版使魔 2/3 阶、红使魔、进化使魔、
	 * SP 使魔、月织蛛、契灵等）。朔望（ocelot_nova）无能量体系，但切形态后
	 * ManaComponent 可能残留上一形态的 mana_type 导致误判，显式排除。
	 */
	public static boolean canRestore(LivingEntity entity) {
		if (!(entity instanceof PlayerEntity player)) {
			return false;
		}
		// SSCA 资源条形态：持有哪条回复哪条
		for (ResourceBarDef bar : BarKeys.ALL) {
			if (ResourceBars.has(player, bar)) {
				return true;
			}
		}
		// 朔望（ocelot_nova）：无能量体系；ManaComponent 若残留旧形态 mana_type 会误判，显式排除
		if (net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(
				entity, net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.OCELOT_NOVA)) {
			return false;
		}
		// 原版 mana 体系兜底：有任何 mana_type 即可（仅玩家有 ManaComponent；契灵走此分支）
		try {
			return net.onixary.shapeShifterCurseFabric.mana.ManaUtils.getPlayerManaTypeID(player) != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 回复 mana：依次检查各 apoli resource 型能量条（悦灵 mana / 蝙蝠血 / 阿努比斯灵魂 / 雪狐寒霜），
	 * 持有哪个就给哪个加值（clamp 到各自 max）；都不是则走原版 ManaComponent（使魔系标准 mana 条）。
	 * 全部经统一门面 {@link ResourceBars}（SSCA-ResourceKit）。
	 */
	private static void restoreMana(net.minecraft.server.network.ServerPlayerEntity player) {
		// 寄生果蝠：种子条上限仅 10，固定回 2 点（喝一瓶约 1/5 条，与其它形态 25 点的体感比例相当）
		if (ResourceBars.has(player, BarKeys.SEED)) {
			ResourceBars.gain(player, BarKeys.SEED, 2);
			return;
		}
		for (ResourceBarDef bar : BarKeys.ALL) {
			if (ResourceBars.has(player, bar)) {
				ResourceBars.gain(player, bar, (int) MANA_RESTORE);
				return;
			}
		}
		// 标准型：原版 ManaComponent（gainMana 内部 clamp 到 max；含契灵的 familiar_fox_mana）
		net.onixary.shapeShifterCurseFabric.mana.ManaUtils.gainPlayerMana(player, MANA_RESTORE);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient && user instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			if (canRestore(user)) {
				// 与压缩能量药水一致：仅回复，无额外完成音效（饮用音由 UseAction.DRINK 原生提供）
				restoreMana(serverPlayer);
			} else {
				// 无门控 power：不回复，动作栏提示（走 lang key）
				serverPlayer.sendMessage(Text.translatable("message.ssc_addon.universal_potion.no_effect"), true);
			}
		}
		if (user instanceof PlayerEntity playerEntity) {
			playerEntity.incrementStat(Stats.USED.getOrCreateStat(this));
			if (!playerEntity.getAbilities().creativeMode) {
				stack.decrement(1);
				ItemStack glassBottle = new ItemStack(Items.GLASS_BOTTLE);
				if (!playerEntity.getInventory().insertStack(glassBottle)) {
					playerEntity.dropItem(glassBottle, false);
				}
			}
		} else {
			stack.decrement(1);
		}
		return stack;
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 32; // 与原版药水一致的饮用读条
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.DRINK;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return net.minecraft.item.ItemUsage.consumeHeldItem(world, user, hand);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		tooltip.add(Text.translatable("tooltip.ssc_addon.universal_potion").formatted(net.minecraft.util.Formatting.AQUA));
	}
}
