package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.ArcaneAnalysis;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 增强法阵物品（jackcooper）。类似卷轴的纸片，NBT 绑定系别（火/冰）与等级（1-5）。
 *
 * <p><b>长按右键蓄力</b>（同 waystone 回城卷轴/进化石，32t=1.6 秒）=「记录魔法」：蓄满后物品消失、
 * 法阵记录进玩家数据（{@link FormationKnowledgeComponent}），之后可在法术研究台消耗月尘学习、抄写。
 * 已记录过同系同级时<b>起手即提示</b>、不进入蓄力、物品不消耗（防误损，也不用傻等 1.6 秒）；
 * 高低等级互不冲突（各等级独立记录）。蓄力中途松开/被打断 = 取消，不记录不消耗。</p>
 */
public class FormationItem extends Item {


	public FormationItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack=user.getStackInHand(hand);FormationElement element=FormationData.getElement(stack);
		if(ArcaneAnalysis.isUnanalyzed(stack)){
			if(!world.isClient)user.sendMessage(Text.translatable("message.ssc_addon.analysis.required").formatted(Formatting.RED),true);
			return TypedActionResult.fail(stack);
		}
		if(element==null)return TypedActionResult.pass(stack);
		String variant=FormationData.getVariant(stack);int level=FormationData.getLevel(stack);
		if(FormationKnowledgeComponent.get(user).hasRecorded(element,variant,level)){
			if(world.isClient)user.sendMessage(Text.translatable("message.ssc_addon.formation.already_recorded",displayName(element,variant),level).formatted(Formatting.YELLOW),true);
			return TypedActionResult.fail(stack);
		}
		user.setCurrentHand(hand);return TypedActionResult.consume(stack);
	}

	/** 变体感知显示名（通用系显示变体名，其它系显示系别名）。 */
	private static Text displayName(FormationElement element, String variant) {
		return element == FormationElement.UNIVERSAL
				? Text.translatable(variantNameKey(variant))
				: Text.translatable(element.getNameKey());
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 32;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if(ArcaneAnalysis.isUnanalyzed(stack))return stack;
		if(!world.isClient&&user instanceof ServerPlayerEntity player){
			FormationElement element=FormationData.getElement(stack);
			if(element!=null){
				int level=FormationData.getLevel(stack);String variant=FormationData.getVariant(stack);
				var knowledge=FormationKnowledgeComponent.get(player);
				if(!knowledge.hasRecorded(element,variant,level)){
					knowledge.record(element,variant,level);FormationKnowledgeComponent.sync(player);
					if(!player.getAbilities().creativeMode)stack.decrement(1);
					player.sendMessage(Text.translatable("message.ssc_addon.formation.recorded",displayName(element,variant),level).formatted(Formatting.GREEN),true);
					world.playSound(null,player.getBlockPos(),SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,SoundCategory.PLAYERS,.4f,1.6f);
				}
			}
		}
		return stack;
	}

	@Override
	public Text getName(ItemStack stack) {
		if(ArcaneAnalysis.isUnanalyzed(stack))return Text.translatable("item.ssc_addon.unfamiliar_spell");
		FormationElement element = FormationData.getElement(stack);
		if (element != null) {
			int level = FormationData.getLevel(stack);
			// 名称按品质色（白/绿/蓝/紫/橙，与卷轴一致）；通用系显示变体名，其它系显示系别名
			return Text.translatable(element == FormationElement.UNIVERSAL
					? "item.ssc_addon.formation.named_universal" : "item.ssc_addon.formation.named",
					displayName(element, FormationData.getVariant(stack)), level)
					.formatted(FormationData.getRarity(level).color);
		}
		return super.getName(stack);
	}

	/** 通用系变体名 lang key。 */
	private static String variantNameKey(String variant) {
		String v = FormationData.normalizeVariant(variant);
		return "formation.ssc_addon.variant." + (v != null ? v : FormationData.VARIANT_REGEN);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		if(ArcaneAnalysis.isUnanalyzed(stack)){
			tooltip.add(Text.translatable("message.ssc_addon.analysis.required").formatted(Formatting.GRAY));
			return;
		}
		FormationElement element = FormationData.getElement(stack);
		if (element == null) {
			tooltip.add(Text.translatable("item.ssc_addon.formation.tip_empty").formatted(Formatting.DARK_GRAY));
			return;
		}
		int level = FormationData.getLevel(stack);
		// 通用系：按变体分三套文案（回能=能量转化 / 增能=法力上限 / 经验=exp 效率）
		if (element == FormationElement.UNIVERSAL) {
			String variant = FormationData.getVariant(stack);
			switch (variant == null ? FormationData.VARIANT_REGEN : variant) {
				case FormationData.VARIANT_MANA -> {
					int manaPct = Math.round(FormationData.universalManaBonusPct(level) * 100);
					tooltip.add(Text.translatable("item.ssc_addon.formation.tip_mana",
							manaPct).formatted(Formatting.GRAY));
				}
				case FormationData.VARIANT_EXP -> {
					int expPct = Math.round(FormationData.universalExpPerLevel() * level * 100);
					tooltip.add(Text.translatable("item.ssc_addon.formation.tip_exp",
							expPct).formatted(Formatting.GRAY));
				}
				case FormationData.VARIANT_RECOVERY -> {
					int recPct = Math.round(0.20f * level * 100);
					tooltip.add(Text.translatable("item.ssc_addon.formation.tip_recovery",
							recPct).formatted(Formatting.GRAY));
				}
				default -> {
					int pct = (int) Math.round(FormationData.universalThreshold(level) * 100);
					tooltip.add(Text.translatable("item.ssc_addon.formation.tip_universal",
							pct,
							(int) FormationData.universalManaDrainPerSec(),
							(int) FormationData.universalBookManaPerSec()).formatted(Formatting.GRAY));
				}
			}
			tooltip.add(Text.translatable("item.ssc_addon.formation.tip_use").formatted(Formatting.DARK_GRAY));
			tooltip.add(Text.translatable("item.ssc_addon.formation.tip_hint").formatted(Formatting.DARK_GRAY));
			return;
		}
		int pct = Math.round(FormationData.damageBonusPerLevel() * level * 100);
		int cdPct = Math.round(FormationData.cooldownReductionPerLevel() * level * 100);
		int manaPct = Math.round(FormationData.manaCostPerLevel() * level * 100);
		tooltip.add(Text.translatable("item.ssc_addon.formation.tip_effect",
				Text.translatable(element.getNameKey()), pct,
				Text.translatable(element.opponent().getNameKey()), pct,
				cdPct, manaPct).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.formation.tip_use").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.formation.tip_hint").formatted(Formatting.DARK_GRAY));
	}
}
