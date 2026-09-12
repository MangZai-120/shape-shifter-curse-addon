package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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

	/** 蓄力时长（tick），与进化石/回城卷轴一致。 */
	private static final int CHARGE_TICKS = 32;

	public FormationItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		FormationElement element = FormationData.getElement(stack);
		// 空白法阵（无系别绑定）不响应
		if (element == null) {
			return TypedActionResult.pass(stack);
		}
		// 起手即查「已记录同级」：直接提示并拒绝，避免玩家白等 1.6 秒蓄力
		// （FormationKnowledgeComponent 是 AutoSyncedComponent，客户端可读，研究台 GUI 同款依赖）
		if (FormationKnowledgeComponent.get(user).hasRecorded(element, FormationData.getLevel(stack))) {
			if (world.isClient) {
				user.sendMessage(Text.translatable("message.ssc_addon.formation.already_recorded",
						Text.translatable(element.getNameKey()), FormationData.getLevel(stack)).formatted(Formatting.YELLOW), true);
			}
			return TypedActionResult.fail(stack);
		}
		// 进入蓄力（长按右键，进度条满 32t 后触发 finishUsing）
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return CHARGE_TICKS;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		// 服务端权威：蓄满后完成记录（客户端直接原样返回）
		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			FormationElement element = FormationData.getElement(stack);
			if (element != null) {
				int level = FormationData.getLevel(stack);
				FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(player);
				// 二次校验（蓄力期间理论上不会变化，防御性保留）
				if (!knowledge.hasRecorded(element, level)) {
					knowledge.record(element, level);
					FormationKnowledgeComponent.sync(player);
					if (!player.getAbilities().creativeMode) {
						stack.decrement(1);
					}
					player.sendMessage(Text.translatable("message.ssc_addon.formation.recorded",
							Text.translatable(element.getNameKey()), level).formatted(Formatting.GREEN), true);
					world.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.4f, 1.6f);
				} else {
					player.sendMessage(Text.translatable("message.ssc_addon.formation.already_recorded",
							Text.translatable(element.getNameKey()), level).formatted(Formatting.YELLOW), true);
				}
			}
		}
		return stack;
	}

	@Override
	public Text getName(ItemStack stack) {
		FormationElement element = FormationData.getElement(stack);
		if (element != null) {
			int level = FormationData.getLevel(stack);
			// 名称按品质色（白/绿/蓝/紫/橙，与卷轴一致）；系别靠后缀名区分
			return Text.translatable("item.ssc_addon.formation.named",
					Text.translatable(element.getNameKey()), level).formatted(FormationData.getRarity(level).color);
		}
		return super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		FormationElement element = FormationData.getElement(stack);
		if (element == null) {
			tooltip.add(Text.translatable("item.ssc_addon.formation.tip_empty").formatted(Formatting.DARK_GRAY));
			return;
		}
		int level = FormationData.getLevel(stack);
		// 通用系：形态能量转化文案（不参与伤害/CD/耗蓝）
		if (element == FormationElement.UNIVERSAL) {
			int pct = (int) Math.round(FormationData.universalThreshold(level) * 100);
			tooltip.add(Text.translatable("item.ssc_addon.formation.tip_universal",
					pct,
					(int) FormationData.UNIVERSAL_MANA_DRAIN_PER_SEC,
					(int) FormationData.UNIVERSAL_BOOK_MANA_PER_SEC).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("item.ssc_addon.formation.tip_use").formatted(Formatting.DARK_GRAY));
			tooltip.add(Text.translatable("item.ssc_addon.formation.tip_hint").formatted(Formatting.DARK_GRAY));
			return;
		}
		int pct = Math.round(FormationData.DAMAGE_BONUS_PER_LEVEL * level * 100);
		int cdPct = Math.round(FormationData.COOLDOWN_REDUCTION_PER_LEVEL * level * 100);
		int manaPct = Math.round(FormationData.MANA_COST_PER_LEVEL * level * 100);
		tooltip.add(Text.translatable("item.ssc_addon.formation.tip_effect",
				Text.translatable(element.getNameKey()), pct,
				Text.translatable(element.opponent().getNameKey()), pct,
				cdPct, manaPct).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.formation.tip_use").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.formation.tip_hint").formatted(Formatting.DARK_GRAY));
	}
}
