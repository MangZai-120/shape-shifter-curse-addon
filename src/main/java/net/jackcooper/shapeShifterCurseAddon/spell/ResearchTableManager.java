package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

/**
 * 法术研究台服务端核心（jackcooper）。抄写/学习全部由 C2S 包触发、此处服务端权威重验：
 * <ul>
 *   <li><b>抄写</b>：已学习 + 纸×1 + 对应系油墨×等级 → 产出法阵（放产出槽）；</li>
 *   <li><b>学习</b>：已记录 + 未学习到位 + 未加工月之尘×(2×等级) → 提升学习等级。</li>
 * </ul>
 * <p>后续小游戏（完美完成省 40% 月尘）预留 {@code discount} 参数接口，当前恒 0。</p>
 */
public final class ResearchTableManager {
	private ResearchTableManager() {
	}

	/** 抄写：产出对应系别等级的法阵。服务端重验：已学习 + 纸 + 对应系墨×等级 + 产出槽空。 */
	public static void scribe(ServerPlayerEntity player, String elementId, int level) {
		if (!(player.currentScreenHandler instanceof SpellResearchTableScreenHandler sh)
				|| !(sh.getInventory() instanceof SpellResearchTableBlockEntity be)) {
			return;
		}
		FormationElement element = FormationElement.byId(elementId);
		if (element == null || level < 1 || level > FormationData.MAX_FORMATION_LEVEL) {
			return;
		}
		FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(player);
		if (!knowledge.hasLearned(element, level)) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.not_learned").formatted(Formatting.RED), true);
			return;
		}
		// 材料重验：纸×1 + 对应系油墨×level（普通墨不可）
		if (!(be.getStack(SpellResearchTableBlockEntity.SLOT_PAPER).getItem()
				instanceof net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem)) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.no_paper").formatted(Formatting.RED), true);
			return;
		}
		ItemStack ink = be.getStack(SpellResearchTableBlockEntity.SLOT_INK);
		if (!(ink.getItem() instanceof FormationInkItem inkItem) || inkItem.getType() == FormationInkItem.Type.NORMAL
				|| inkItem.getType().element != element || ink.getCount() < level) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.no_ink",
					Text.translatable(element.getNameKey()), level).formatted(Formatting.RED), true);
			return;
		}
		// 产出槽需空（或同物品可叠——法阵 maxCount 1，故要求空）
		if (!be.getStack(SpellResearchTableBlockEntity.SLOT_OUTPUT).isEmpty()) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.output_full").formatted(Formatting.RED), true);
			return;
		}
		// 扣材料 + 产出
		be.getStack(SpellResearchTableBlockEntity.SLOT_PAPER).decrement(1);
		ink.decrement(level);
		be.setStack(SpellResearchTableBlockEntity.SLOT_OUTPUT, FormationData.create(element, level));
		be.markDirty();
		player.getWorld().playSound(null, be.getPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1.0f, 1.0f);
		player.sendMessage(Text.translatable("message.ssc_addon.research.scribed",
				Text.translatable(element.getNameKey()), level).formatted(Formatting.GREEN), true);
	}

	/**
	 * 学习：消耗未加工月之尘提升学习等级。服务端重验：已记录 + 未学习到位 + 尘够。
	 *
	 * @param discount 折扣（0-40，百分号；小游戏完美完成 = 40，当前恒 0）
	 */
	public static void learn(ServerPlayerEntity player, String elementId, int level, int discount) {
		if (!(player.currentScreenHandler instanceof SpellResearchTableScreenHandler sh)
				|| !(sh.getInventory() instanceof SpellResearchTableBlockEntity be)) {
			return;
		}
		FormationElement element = FormationElement.byId(elementId);
		if (element == null || level < 1 || level > FormationData.MAX_FORMATION_LEVEL) {
			return;
		}
		FormationKnowledgeComponent knowledge = FormationKnowledgeComponent.get(player);
		if (!knowledge.hasRecorded(element, level)) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.not_recorded").formatted(Formatting.RED), true);
			return;
		}
		if (knowledge.hasLearned(element, level)) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.already_learned").formatted(Formatting.YELLOW), true);
			return;
		}
		int baseCost = level * 2;
		int cost = Math.max(1, baseCost - baseCost * Math.max(0, Math.min(40, discount)) / 100);
		ItemStack dust = be.getStack(SpellResearchTableBlockEntity.SLOT_MOONDUST);
		if (dust.getItem() != RegCustomItem.UNTREATED_MOONDUST || dust.getCount() < cost) {
			player.sendMessage(Text.translatable("message.ssc_addon.research.no_dust", cost).formatted(Formatting.RED), true);
			return;
		}
		dust.decrement(cost);
		knowledge.learn(element, level);
		FormationKnowledgeComponent.sync(player);
		be.markDirty();
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.5f, 1.2f);
		player.sendMessage(Text.translatable("message.ssc_addon.research.learned",
				Text.translatable(element.getNameKey()), level).formatted(Formatting.GREEN), true);
	}
}
