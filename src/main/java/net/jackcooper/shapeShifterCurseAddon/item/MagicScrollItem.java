package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
import net.jackcooper.shapeShifterCurseAddon.spell.ArcaneAnalysis;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
		if (ArcaneAnalysis.isUnanalyzed(stack)) {
			if (!world.isClient) user.sendMessage(Text.translatable("message.ssc_addon.analysis.required").formatted(Formatting.RED), true);
			return TypedActionResult.fail(stack);
		}
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			return TypedActionResult.pass(stack);
		}
		if (user.isSneaking()) {
			if (!world.isClient && user instanceof ServerPlayerEntity sp) {
				sp.sendMessage(Text.translatable("research.ssc_addon.slotted.analyze_scroll"), true);
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
		if (world.getTime() < net.jackcooper.shapeShifterCurseAddon.spell.SharedSpellCooldowns.getEffectiveCooldownEnd(user, stack)) {
			if (user instanceof ServerPlayerEntity player) SpellChannelManager.playFailureSound(player);
			return TypedActionResult.fail(stack);
		}
		if (ScrollData.getUses(stack) <= 0) {
			if (user instanceof ServerPlayerEntity player) SpellChannelManager.playFailureSound(player);
			return TypedActionResult.fail(stack);
		}
		if (!world.isClient && user instanceof ServerPlayerEntity sp) {
			if (SpellChannelManager.isCasting(sp)||!RuneScheme.validScroll(sp,stack,spell)) return TypedActionResult.fail(stack);
            int level = ScrollData.getLevel(stack);
            RuneModifiers runeModifiers=RuneScheme.modifiers(sp,stack,level);
			if (spell.getCastingMode() == SpellCastingRules.Mode.AUTOMATIC && !RuneCastContext.with(runeModifiers,()->spell.canCast(sp))) {
				SpellChannelManager.playFailureSound(sp);
				sp.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_target"), true);
				return TypedActionResult.fail(stack);
			}
			if (!spell.prepareScroll(sp, stack)) {
				SpellChannelManager.playFailureSound(sp);
				return TypedActionResult.fail(stack);
			}
			float damage = runeModifiers.power(spell.getBaseDamage() * spell.getSoloDamageMultiplier() * spell.getDamageMultiplier(level),RuneCapabilities.of(spell.getId().getPath()).contains(RuneModifiers.Stat.DAMAGE),RuneCapabilities.of(spell.getId().getPath()).contains(RuneModifiers.Stat.HEAL));
			// 阶段 B（§6.2）：统一冷却公式（solo 惩罚倍率并入等级基准；双层下限与书内一致）
			int cd = runeModifiers.cooldown(net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers.finalSoloCooldownTicks(spell, level));
			ItemStack snapshot = stack.copy();
			SpellChannelManager.start(sp, spell, snapshot, level, true, hand.ordinal(), 0, cd,
					() -> sp.getStackInHand(hand) == stack && ItemStack.areEqual(stack, snapshot),
					target -> spell.castAtTarget(sp, damage, true, level, snapshot, target),
					duration -> {
						long end = sp.getWorld().getTime() + duration;
						stack.getOrCreateNbt().putInt("RuneCdTotal",duration);
                    ScrollData.setCooldownEnd(stack, end);
						net.jackcooper.shapeShifterCurseAddon.spell.SharedSpellCooldowns.record(sp, spell, end);
					},
					() -> { if (ScrollData.consumeSoloUse(stack)) stack.decrement(1); }, runeModifiers);
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
		if (ArcaneAnalysis.isUnanalyzed(stack)) return Text.translatable("item.ssc_addon.unfamiliar_spell");
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			return super.getName(stack);
		}
		// 模板第一行：「法术名 卷轴 · 系名系」（无系别的法术回退不带系名的旧格式）
		var element = spell.getElement();
		net.minecraft.text.MutableText name = element == null
				? Text.translatable("item.ssc_addon.magic_scroll.format", Text.translatable(spell.getNameKey()))
				: Text.translatable("item.ssc_addon.magic_scroll.format_element",
						Text.translatable(spell.getNameKey()), Text.translatable(element.getNameKey()));
		if (stack.getNbt() != null && stack.getNbt().contains(RuneScheme.KEY)) {
			name.append(Text.translatable("research.ssc_addon.runes.modified_suffix"));
		}
		return name.formatted(spell.getRarity(ScrollData.getLevel(stack)).color);
	}

	@Override
	public Optional<TooltipData> getTooltipData(ItemStack stack) {
		if (ArcaneAnalysis.isUnanalyzed(stack)) return Optional.empty();
		Spell spell = ScrollData.getSpell(stack);
		return spell == null
				? Optional.empty()
				: Optional.of(new SpellIconTooltipData(spell.getIconTexture()));
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		if (ArcaneAnalysis.isUnanalyzed(stack)) {
			tooltip.add(Text.translatable("message.ssc_addon.analysis.required").formatted(Formatting.GRAY));
			return;
		}
		Spell spell = ScrollData.getSpell(stack);
		if (spell == null) {
			tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_empty").formatted(Formatting.DARK_GRAY));
			return;
		}
		// 模板第二行：品质 ｜ 档位（品质色随等级白→橙；custom 档显示「特殊」）
		SpellRarity r = spell.getRarity(ScrollData.getLevel(stack));
		var tier = spell.getConfig().spellTier;
		tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.rarity_tier",
				Text.translatable(r.getTranslationKey()).formatted(r.color),
				Text.translatable("spell.ssc_addon.tier." + tier.name().toLowerCase(java.util.Locale.ROOT)).formatted(Formatting.GRAY)));
		// 模板第三行：主要效果 + 效果限制（lang 内 \n 手动换行，中文每行 ≤20 字）
		for (String line : Text.translatable(spell.getDescKey()).getString().split("\n")) {
			tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
		}
		// 红色卷轴的使用限制（必要效果限制，保留红字提示）
		RuneTooltips.append(stack,tooltip);
        if (!r.canUseSolo()) {
			tooltip.add(Text.translatable("item.ssc_addon.magic_scroll.tip_no_solo").formatted(Formatting.RED));
		}
	}

	public record SpellIconTooltipData(Identifier texture) implements TooltipData {
	}
}
