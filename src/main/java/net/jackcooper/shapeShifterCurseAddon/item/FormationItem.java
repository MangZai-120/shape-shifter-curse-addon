package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
 * <p>右键使用 =「记录魔法」：物品消失、法阵记录进玩家数据（{@link FormationKnowledgeComponent}），
 * 之后可在法术研究台消耗月尘学习、抄写。已记录过同系同级时物品不消耗、仅提示（防误损）；
 * 高低等级互不冲突（各等级独立记录）。</p>
 */
public class FormationItem extends Item {

	public FormationItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		FormationElement element = FormationData.getElement(stack);
		if (element == null || world.isClient) {
			return TypedActionResult.success(stack);
		}
		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.pass(stack);
		}
		int level = FormationData.getLevel(stack);
		FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(player);
		if (knowledge.hasRecorded(element, level)) {
			// 已记录过同级：不消耗物品，仅提示（玩家可把物品留着装箱/给别人）
			player.sendMessage(Text.translatable("message.ssc_addon.formation.already_recorded",
					Text.translatable(element.getNameKey()), level).formatted(Formatting.YELLOW), true);
			return TypedActionResult.fail(stack);
		}
		knowledge.record(element, level);
		FormationKnowledgeComponent.sync(player);
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		player.sendMessage(Text.translatable("message.ssc_addon.formation.recorded",
				Text.translatable(element.getNameKey()), level).formatted(Formatting.GREEN), true);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.4f, 1.6f);
		return TypedActionResult.success(stack);
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
