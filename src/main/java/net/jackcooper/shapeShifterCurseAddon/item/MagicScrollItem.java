package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 魔法卷轴（jackcooper）。一个通用物品，通过 NBT（{@link ScrollData}）绑定具体魔法与稀有度。
 *
 * <p><b>单独使用</b>（直接右键）：按固定裸用惩罚释放（伤害 ×0.5、冷却 ×2），每次消耗 1 次数，耗尽销毁；
 * 红色卷轴禁止单独使用。<b>放入魔法书</b>：无次数限制、只耗书法力、效果按剩余次数比例缩放（见魔法书逻辑）。</p>
 */
public class MagicScrollItem extends Item {

	public MagicScrollItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			return TypedActionResult.pass(stack);
		}
		// 红色卷轴不可单独使用（按等级对应的有效品质判定）
		SpellRarity effectiveRarity = spell.getRarity(ScrollData.getLevel(stack));
		if (!effectiveRarity.canUseSolo()) {
			if (world.isClient) {
				user.sendMessage(Text.translatable("message.ssc_addon.scroll.cannot_solo").formatted(Formatting.RED), true);
			}
			return TypedActionResult.fail(stack);
		}
		// 单独使用冷却中
		if (ScrollData.isOnCooldown(stack, world)) {
			return TypedActionResult.fail(stack);
		}
		if (ScrollData.getUses(stack) <= 0) {
			return TypedActionResult.fail(stack);
		}
		if (!world.isClient && user instanceof ServerPlayerEntity sp) {
			int level = ScrollData.getLevel(stack); // 魔法等级（1-5，开箱固定）与卷轴一体，单独使用同样生效
			float damage = spell.getBaseDamage() * spell.getSoloDamageMultiplier() * spell.getDamageMultiplier(level);
			int cd = Math.round(spell.getBaseCooldownTicks() * spell.getSoloCooldownMultiplier() * spell.getCooldownMultiplier(level));
			// 统一四参入口：法术内部自行决定是否按等级缩放速度/外观/范围
			spell.cast(sp, damage, true, level);
			ScrollData.setCooldownEnd(stack, world.getTime() + cd);
			boolean exhausted = ScrollData.consumeSoloUse(stack);
			if (exhausted) {
				stack.decrement(1);
			}
		}
		return TypedActionResult.success(stack);
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		int max = ScrollData.getMaxUses(stack);
		return max > 0 && ScrollData.getUses(stack) < max;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		int max = ScrollData.getMaxUses(stack);
		if (max <= 0) {
			return 0;
		}
		return Math.round(13.0f * ScrollData.getUses(stack) / max);
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			return 0xFFFFFF;
		}
		Integer cv = spell.getRarity(ScrollData.getLevel(stack)).color.getColorValue();
		return cv == null ? 0xFFFFFF : cv;
	}

	@Override
	public Text getName(ItemStack stack) {
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			return super.getName(stack);
		}
		return Text.translatable("item.ssc_addon.magic_scroll.format", Text.translatable(spell.getNameKey()))
				.formatted(spell.getRarity(ScrollData.getLevel(stack)).color);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_empty").formatted(Formatting.DARK_GRAY));
			return;
		}
		// 有效品质按等级派生（冰锥：1白/2绿/3蓝/4紫/5橙）
		SpellRarity r = spell.getRarity(ScrollData.getLevel(stack));
		tooltip.add(Text.translatable(r.getTranslationKey()).formatted(r.color));
		// 魔法等级（始终显示，便于区分开箱获得的卷轴等级；固定不可升级）
		int level = ScrollData.getLevel(stack);
		tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.level", level).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable(spell.getDescKey()).formatted(Formatting.GRAY));
		// 装书内数值（按等级倍率折算为实际值；buff 型法术走专用文案，如「获得 x 点吸收」）
		String cdSec = formatSeconds(Math.round(spell.getBaseCooldownTicks() * spell.getCooldownMultiplier(level)));
		int shownDmg = Math.round(spell.getBaseDamage() * spell.getDamageMultiplier(level));
		tooltip.add(Text.translatable(spell.getInBookTooltipKey(),
				shownDmg, cdSec, spell.getManaCost()).formatted(Formatting.GRAY));
		if (r.canUseSolo()) {
			tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_uses",
					ScrollData.getUses(stack), r.soloUses).formatted(Formatting.YELLOW));
			int soloDmg = Math.round(spell.getBaseDamage() * spell.getSoloDamageMultiplier() * spell.getDamageMultiplier(level));
			String soloCd = formatSeconds(Math.round(spell.getBaseCooldownTicks() * spell.getSoloCooldownMultiplier() * spell.getCooldownMultiplier(level)));
			tooltip.add(Text.translatable(spell.getSoloTooltipKey(), soloDmg, soloCd).formatted(Formatting.DARK_GRAY));
		} else {
			tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_no_solo").formatted(Formatting.RED));
		}
		tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_hint").formatted(Formatting.DARK_GRAY));
	}

	private static String formatSeconds(int ticks) {
		float sec = ticks / 20.0f;
		if (sec == Math.floor(sec)) {
			return String.valueOf((int) sec);
		}
		return String.format("%.1f", sec);
	}
}
