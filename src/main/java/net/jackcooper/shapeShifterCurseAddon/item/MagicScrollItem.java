package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

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
		// 阶段 C（§8.1）：潜行右键 = 记录法术图谱（不消耗卷轴、幂等；解锁研究台定向制作）
		if (user.isSneaking()) {
			if (!world.isClient && user instanceof ServerPlayerEntity sp) {
				net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent knowledge =
					net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent.get(sp);
				String path = spell.getId().getPath();
				if (!knowledge.hasSpell(path)) {
					knowledge.recordSpell(path);
					net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent.sync(sp);
					sp.sendMessage(Text.translatable("message.ssc_addon.scroll.atlas_recorded",
						Text.translatable(spell.getNameKey())).formatted(Formatting.GREEN), true);
					sp.getWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
						SoundEvents.UI_TOAST_IN, SoundCategory.PLAYERS, 0.8f, 1.4f);
				} else {
					sp.sendMessage(Text.translatable("message.ssc_addon.scroll.atlas_already",
						Text.translatable(spell.getNameKey())).formatted(Formatting.YELLOW), true);
				}
			}
			return TypedActionResult.success(stack);
		}
		// 红色卷轴不可单独使用（按等级对应的有效品质判定）
		SpellRarity effectiveRarity = spell.getRarity(ScrollData.getLevel(stack));
		if (!effectiveRarity.canUseSolo()) {
			if (user instanceof ServerPlayerEntity player) SpellChannelManager.playFailureSound(player);
			if (world.isClient) {
				user.sendMessage(Text.translatable("message.ssc_addon.scroll.cannot_solo").formatted(Formatting.RED), true);
			}
			return TypedActionResult.fail(stack);
		}
		// 单独使用冷却中（卷轴 NBT + 玩家共享表双源判定，阶段 B §15.2）
		if (ScrollData.isOnCooldown(stack, world)
				|| (!world.isClient && user instanceof ServerPlayerEntity sp0
					&& net.jackcooper.shapeShifterCurseAddon.spell.SharedSpellCooldowns.isOnSharedCooldown(sp0, spell))) {
			if (user instanceof ServerPlayerEntity player) SpellChannelManager.playFailureSound(player);
			return TypedActionResult.fail(stack);
		}
		if (ScrollData.getUses(stack) <= 0) {
			if (user instanceof ServerPlayerEntity player) SpellChannelManager.playFailureSound(player);
			return TypedActionResult.fail(stack);
		}
		if (!world.isClient && user instanceof ServerPlayerEntity sp) {
			if (SpellChannelManager.isCasting(sp)) return TypedActionResult.fail(stack);
			if (spell.getCastingMode() == SpellCastingRules.Mode.AUTOMATIC && !spell.canCast(sp)) {
				SpellChannelManager.playFailureSound(sp);
				sp.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_target"), true);
				return TypedActionResult.fail(stack);
			}
			if (!spell.prepareScroll(sp, stack)) {
				SpellChannelManager.playFailureSound(sp);
				return TypedActionResult.fail(stack);
			}
			int level = ScrollData.getLevel(stack); // 魔法等级（1-5，开箱固定）与卷轴一体，单独使用同样生效
			float damage = spell.getBaseDamage() * spell.getSoloDamageMultiplier() * spell.getDamageMultiplier(level);
			// 阶段 B（§6.2）：统一冷却公式（solo 惩罚倍率并入等级基准；双层下限与书内一致）
			int cd = net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers.finalSoloCooldownTicks(spell, level);
			ItemStack snapshot = stack.copy();
			SpellChannelManager.start(sp, spell, snapshot, level, true, hand.ordinal(), 0, cd,
					() -> sp.getStackInHand(hand) == stack && ItemStack.areEqual(stack, snapshot),
					ignored -> true,
					target -> spell.castAtTarget(sp, damage, true, level, snapshot, target),
					duration -> {
						long end = sp.getWorld().getTime() + duration;
						ScrollData.setCooldownEnd(stack, end);
						net.jackcooper.shapeShifterCurseAddon.spell.SharedSpellCooldowns.record(sp, spell, end);
					},
					() -> { if (ScrollData.consumeSoloUse(stack)) stack.decrement(1); });
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 72000;
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, net.minecraft.entity.LivingEntity user, int remainingUseTicks) {
		if (user instanceof ServerPlayerEntity player) {
			SpellChannelManager.release(player, player.getActiveHand().ordinal(), true);
		}
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
	public Optional<TooltipData> getTooltipData(ItemStack stack) {
		Spell spell = ScrollData.getSpell(stack);
		return spell == null
				? Optional.empty()
				: Optional.of(new SpellIconTooltipData(spell.getIconTexture()));
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
		if (spell.getMaxLevel() > 1) tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.level", level).formatted(Formatting.AQUA));
		var tier = spell.getConfig().spellTier;
		tooltip.add((tier == SpellCastingRules.Tier.CUSTOM
				? Text.translatable("item.ssc_addon.magic_scroll.casting_custom")
				: Text.translatable("item.ssc_addon.magic_scroll.casting",
						Text.translatable("spell.ssc_addon.tier." + tier.name().toLowerCase(java.util.Locale.ROOT)),
						formatSeconds(tier.profile.ticks()))).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.interrupt_mode",
				Text.translatable("spell.ssc_addon.interrupt." + spell.getConfig().interruptMode)).formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable(spell.getDescKey()).formatted(Formatting.GRAY));
		// 装书内数值（按等级倍率折算为实际值；buff 型法术走专用文案，如「获得 x 点吸收」）
		// 耗蓝同样乘等级倍率（与服务端扣费同式），保证面板与实扣一致
		String cdSec = formatSeconds(Math.round(spell.getBaseCooldownTicks() * spell.getCooldownMultiplier(level)));
		int shownDmg = Math.round(spell.getBaseDamage() * spell.getDamageMultiplier(level));
		int shownMana = Math.round(spell.getManaCost() * spell.getConfig().manaCostMultiplier(level));
		tooltip.add(Text.translatable(spell.getInBookTooltipKey(),
				shownDmg, cdSec, shownMana).formatted(Formatting.GRAY));
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

	public record SpellIconTooltipData(Identifier texture) implements TooltipData {
	}
}
