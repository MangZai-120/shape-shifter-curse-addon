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
		// 红色卷轴不可单独使用
		if (!spell.getRarity().canUseSolo()) {
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
			float damage = spell.getBaseDamage() * spell.getSoloDamageMultiplier();
			int cd = Math.round(spell.getBaseCooldownTicks() * spell.getSoloCooldownMultiplier());
			spell.cast(sp, damage, true);
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
		Integer cv = spell.getRarity().color.getColorValue();
		return cv == null ? 0xFFFFFF : cv;
	}

	@Override
	public Text getName(ItemStack stack) {
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			return super.getName(stack);
		}
		return Text.translatable("item.ssc_addon.magic_scroll.format", Text.translatable(spell.getNameKey()))
				.formatted(spell.getRarity().color);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_empty").formatted(Formatting.DARK_GRAY));
			return;
		}
		SpellRarity r = spell.getRarity();
		tooltip.add(Text.translatable(r.getTranslationKey()).formatted(r.color));
		tooltip.add(Text.translatable(spell.getDescKey()).formatted(Formatting.GRAY));
		// 装书内数值
		String cdSec = formatSeconds(spell.getBaseCooldownTicks());
		tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_in_book",
				(int) spell.getBaseDamage(), cdSec, spell.getManaCost()).formatted(Formatting.GRAY));
		if (r.canUseSolo()) {
			tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_uses",
					ScrollData.getUses(stack), r.soloUses).formatted(Formatting.YELLOW));
			int soloDmg = Math.round(spell.getBaseDamage() * spell.getSoloDamageMultiplier());
			String soloCd = formatSeconds(Math.round(spell.getBaseCooldownTicks() * spell.getSoloCooldownMultiplier()));
			tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_solo", soloDmg, soloCd).formatted(Formatting.DARK_GRAY));
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
