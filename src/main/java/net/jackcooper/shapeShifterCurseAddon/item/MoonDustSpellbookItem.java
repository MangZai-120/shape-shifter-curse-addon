package net.jackcooper.shapeShifterCurseAddon.item;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 月尘魔法书。装入 Trinkets、Curios 或 Accessories 的独立法术书槽后可用快捷键释放书内魔法。
 * Accessories 的数据槽名复用 tclayer 对 moonlit/spellbook 的转换结果，保留旧槽库存并避免重复槽。
 *
 * <p>书等级、升级经验、卷轴、法阵及冷却存书 NBT；满级后的精通存玩家档案。
 * 见 {@link SpellbookData}。任何形态均可佩戴（继承 {@link AccessoryItem}，canEquip 默认放行）。</p>
 */
public class MoonDustSpellbookItem extends AccessoryItem {

	public MoonDustSpellbookItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		// 常驻原版附魔流光（仅视觉，不占用真实附魔）
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		// 普通右键交给饰品后端处理装备；槽位已满时也不误开配置界面。
		if (!user.isSneaking()) {
			return super.use(world, user, hand);
		}
		// 保留寄生果蝠的播种回能，但不再吞掉潜行开书操作。
		if (!world.isClient && user instanceof ServerPlayerEntity sp) {
			net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.trySeedSow(sp, stack);
		}
		// 潜行 + 右键：打开魔法书配置界面。
		if (!world.isClient) {
			user.openHandledScreen(new ExtendedScreenHandlerFactory() {
				@Override
				public Text getDisplayName() {
					return stack.getName();
				}

				@Override
				public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
					return new SpellbookScreenHandler(syncId, inv, stack);
				}

				@Override
				public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
					buf.writeInt(SpellbookData.getSlotCount(stack));
					buf.writeInt(SpellbookData.getLevel(stack));
					buf.writeInt(SpellbookData.getExpTen(stack));
					buf.writeInt(SpellbookData.getMana(stack));
					buf.writeInt(SpellbookData.getMaxMana(stack));
					buf.writeInt(SpellbookScreenHandler.findBookSlot(player.getInventory(), stack));
					buf.writeNbt(net.jackcooper.shapeShifterCurseAddon.network.NeoNetwork.hudState(player));
				}
			});
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		int level = SpellbookData.getLevel(stack);
		int slots = SpellbookData.getSlotCount(stack);
		tooltip.add(Text.translatable("item.ssc_addon.moon_dust_spellbook.tip_level", level, slots).formatted(Formatting.AQUA));
		tooltip.add(net.jackcooper.shapeShifterCurseAddon.spell.NeoMagic.text("book_bonus",String.format(java.util.Locale.ROOT,"%.1f",net.jackcooper.shapeShifterCurseAddon.spell.NeoLegacyCasting.safetyBonus(stack)),String.format(java.util.Locale.ROOT,"%.2f",net.jackcooper.shapeShifterCurseAddon.spell.NeoLegacyCasting.recoveryMultiplier(stack))).formatted(Formatting.BLUE));
		int need = SpellbookData.getExpToNext(stack);
		if (need > 0) {
			// 未满级：显示升级进度（1 位小数）
			tooltip.add(Text.translatable("item.ssc_addon.moon_dust_spellbook.tip_exp",
					String.format(java.util.Locale.ROOT, "%.1f", SpellbookData.getExpFloat(stack)),
					String.format(java.util.Locale.ROOT, "%.1f", need / 10.0f)).formatted(Formatting.GRAY));
		}
		// 从服务端同步的玩家档案展示精通，查看任何一本书都显示同一玩家进度。
		int mastery=net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.clientExpTen();
		int tier=net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.tier(mastery);
		int bonus=net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.bonus(mastery);
		{
			int unit=net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.thresholdTen(tier);
			tooltip.add(Text.translatable("text.ssc_addon.player_mastery.progress",tier,
					String.format(java.util.Locale.ROOT,"%.1f",net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.progressTen(mastery)/10.0),
					String.format(java.util.Locale.ROOT,"%.1f",unit/10.0),bonus).formatted(Formatting.GOLD));
		}
		tooltip.add(Text.translatable("text.ssc_addon.player_mastery.tip").formatted(Formatting.DARK_GRAY));
		tooltip.add(net.jackcooper.shapeShifterCurseAddon.spell.NeoMagic.text("book_help").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
