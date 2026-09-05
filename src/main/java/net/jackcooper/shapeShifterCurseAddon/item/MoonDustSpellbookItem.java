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
 * <p>手持<b>潜行右键</b>打开配置界面（放入 / 取出魔法卷轴）。数据（等级/经验/法力/卷轴/冷却）全部存书自身 NBT，
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
		// 潜行 + 右键：打开魔法书配置界面
		if (user.isSneaking()) {
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
						buf.writeInt(SpellbookData.getExp(stack));
						buf.writeInt(SpellbookData.getMana(stack));
						buf.writeInt(SpellbookData.getMaxMana(stack));
					}
				});
			}
			return TypedActionResult.success(stack);
		}
		return TypedActionResult.pass(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		int level = SpellbookData.getLevel(stack);
		int slots = SpellbookData.getSlotCount(stack);
		int mana = SpellbookData.getMana(stack);
		int maxMana = SpellbookData.getMaxMana(stack);
		tooltip.add(Text.translatable("item.ssc_addon.moon_dust_spellbook.tip_level", level, slots).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.ssc_addon.moon_dust_spellbook.tip_mana", mana, maxMana).formatted(Formatting.BLUE));
		int need = SpellbookData.getExpToNext(stack);
		if (need > 0) {
			tooltip.add(Text.translatable("item.ssc_addon.moon_dust_spellbook.tip_exp",
					SpellbookData.getExp(stack), need).formatted(Formatting.GRAY));
		}
		tooltip.add(Text.translatable("item.ssc_addon.moon_dust_spellbook.tip_hint").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
