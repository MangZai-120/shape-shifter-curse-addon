package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyPotionStackPower;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 创造模式物品栏：让使魔等「药水可叠加」形态在创造界面拿取药水时，把叠加上限从原版的 1
 * 提升为形态赋予的 {@link ModifyPotionStackPower} 的 count（N），使三键手感与普通可叠加物品一致：
 * 左键逐个 +1、中键(CLONE)/Shift 一次拿满 N、右键 -1。
 *
 * <p>原理：原版 {@code CreativeInventoryScreen.onMouseClick} 用 {@link ItemStack#getMaxCount()}（药水固定 1）
 * 判定堆叠/拿取上限，不走生存物品栏那条 {@code Slot.getMaxItemCount} 路径，所以创造模式叠不起来。
 * 这里仅对药水、且玩家持有 {@link ModifyPotionStackPower} 时把该上限改为 N；非药水或未持有时保持原版行为。
 */
@Mixin(CreativeInventoryScreen.class)
public class CreativePotionStackMixin {

	@WrapOperation(
			method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxCount()I")
	)
	private int ssc_addon$potionCreativeStackLimit(ItemStack stack, Operation<Integer> original) {
		if (stack.getItem() instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitherPotionItem) {
			return Math.max(net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitherPotionItem
					.getStackLimitFor(MinecraftClient.getInstance().player), original.call(stack));
		}
		if (stack.getItem() instanceof PotionItem) {
			PlayerEntity player = MinecraftClient.getInstance().player;
			if (player != null) {
				int n = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
						.stream()
						.mapToInt(ModifyPotionStackPower::getCount)
						.max()
						.orElse(0);
				if (n > 0) {
					return Math.max(n, original.call(stack));
				}
			}
		}
		return original.call(stack);
	}
}
