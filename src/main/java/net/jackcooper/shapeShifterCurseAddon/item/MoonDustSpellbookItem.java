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
 * 月尘魔法书（jackcooper）。装入自建 Trinkets 饰品槽 {@code moonlit/spellbook} 后可用快捷键释放书内魔法。
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
		// 潜行 + 右键：寄生果蝠「播种」优先（耗 1 种子回 10 书法术值；未命中条件回落开配置界面）
		if (user.isSneaking() && !world.isClient && user instanceof ServerPlayerEntity sp
				&& net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.trySeedSow(sp, stack)) {
			return TypedActionResult.success(stack);
		}
		// 潜行 + 右键：播种未触发（非寄生果蝠/无种子/书满）时不回落开界面，保留潜行手势语义干净
		if (user.isSneaking()) {
			if (user instanceof ServerPlayerEntity sp) net.ssca.neomagic.NeoNetwork.open(sp,null);
            return TypedActionResult.success(stack,world.isClient);
		}
		// 普通右键：打开魔法书配置界面（2026-09-17 由潜行右键改为右键，用户指定）
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
					buf.writeNbt(net.ssca.neomagic.NeoNetwork.hudState(player));
				}
			});
		}
		return TypedActionResult.success(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		int level = SpellbookData.getLevel(stack);
		int slots = SpellbookData.getSlotCount(stack);
		int mana = SpellbookData.getMana(stack);
		int maxMana = SpellbookData.getMaxMana(stack);
		tooltip.add(Text.translatable("item.ssc_addon.moon_dust_spellbook.tip_level", level, slots).formatted(Formatting.AQUA));
		tooltip.add(net.ssca.neomagic.NeoMagic.text("book_bonus",String.format(java.util.Locale.ROOT,"%.1f",net.ssca.neomagic.NeoLegacyCasting.safetyBonus(stack)),String.format(java.util.Locale.ROOT,"%.2f",net.ssca.neomagic.NeoLegacyCasting.recoveryMultiplier(stack))).formatted(Formatting.BLUE));
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
		tooltip.add(net.ssca.neomagic.NeoMagic.text("book_help").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
