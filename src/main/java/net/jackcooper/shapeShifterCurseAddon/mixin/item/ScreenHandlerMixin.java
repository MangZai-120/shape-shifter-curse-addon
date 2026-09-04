package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.item.PotionItem;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyPotionStackPower;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.item.AllayJukeboxItem;
import net.jackcooper.shapeShifterCurseAddon.item.PotionBagItem;
import net.jackcooper.shapeShifterCurseAddon.item.UniversalEnergyPotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

	@Shadow
	public final DefaultedList<Slot> slots = DefaultedList.of();

	@Shadow
	public abstract Slot getSlot(int index);

	@Shadow
	public abstract ItemStack getCursorStack();

	/** 当前点击处理链所属玩家（internalOnSlotClick HEAD 暂存、RETURN 清空；insertItem 无 player 参数，只能这样传）。 */
	@Unique
	private PlayerEntity ssc_addon$clickPlayer;

	@Inject(
			method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
			at = @At("HEAD")
	)
	private void ssc_addon$captureClickPlayer(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		this.ssc_addon$clickPlayer = player;
	}

	@Inject(
			method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
			at = @At("RETURN")
	)
	private void ssc_addon$clearClickPlayer(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		this.ssc_addon$clickPlayer = null;
	}

	@Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
	private void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		// 药水可叠加形态：双击(PICKUP_ALL)合并同类药水。原版 PICKUP_ALL 走静态 canInsertItemIntoSlot，
		// 其 getMaxCount 不受本类 internalOnSlotClick 重定向影响(药水原版=1)，导致 2+ 堆叠被跳过、无法合并。
		// 故对持有该 Power 的玩家自行实现合并并取消原版处理，背包与箱子等任意容器界面通用。
		if (actionType == SlotActionType.PICKUP_ALL && ssc_addon$tryMergeStackablePotions(button, player)) {
			ci.cancel();
			return;
		}
		if (slotIndex >= 0 && slotIndex < this.slots.size()) {
			Slot slot = this.getSlot(slotIndex);
			if (slot != null && slot.hasStack()) {
				ItemStack stack = slot.getStack();

				// Potion Bag: 光标拿着药水时放入袋中（左/右键均可，优先非快捷消耗栏），否则锁定不可移动
				if (stack.isOf(SscAddon.POTION_BAG)) {
					if (actionType == SlotActionType.PICKUP) {
						ItemStack cursorStack = this.getCursorStack();
						if (!cursorStack.isEmpty() && PotionBagItem.isStorable(cursorStack)
								&& PotionBagItem.insertIntoBag(stack, cursorStack) > 0) {
							player.playSound(SoundEvents.ITEM_BUNDLE_INSERT, 0.8F,
									0.8F + player.getWorld().getRandom().nextFloat() * 0.4F);
							slot.markDirty();
						}
					}
					ci.cancel();
					return;
				}

				// Block moving Allay Heal Wand
				if (stack.isOf(SscAddon.ALLAY_HEAL_WAND)) {
					ci.cancel();
					return;
				}

				// Allay Jukebox: allow disc charging, block other interactions
				if (stack.isOf(SscAddon.ALLAY_JUKEBOX)) {
					// Check if cursor has a music disc - allow charging
					ItemStack cursorStack = this.getCursorStack();
					// Try to charge the jukebox with the disc
					if (cursorStack != null && !cursorStack.isEmpty() && cursorStack.getItem() instanceof MusicDiscItem &&
							AllayJukeboxItem.tryChargeWithDisc(stack, cursorStack)) {
						// Play charge sound
						player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
								SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
					}
					ci.cancel();
				}
			}
		} else if (actionType == SlotActionType.SWAP && button >= 0 && button < 9) {
			ItemStack hotbarStack = player.getInventory().getStack(button);
			if (hotbarStack.isOf(SscAddon.POTION_BAG) || hotbarStack.isOf(SscAddon.ALLAY_HEAL_WAND) || hotbarStack.isOf(SscAddon.ALLAY_JUKEBOX)) {
				ci.cancel();
			}
		}
	}

	/**
	 * 为「药水可叠加」形态(持有 {@link ModifyPotionStackPower})的玩家自行实现双击合并同类药水，叠到 N。
	 * 与原版双击一致采用两轮收集(先取未满堆叠、保留满堆叠，再取满堆叠)，对 {@code button==0/其它} 取不同遍历方向。
	 * 仅处理光标为药水且持有该 Power 的情形；返回 {@code true} 表示已接管本次 PICKUP_ALL，应取消原版。
	 * 双端一致(用方法参数 player、与原版相同的 takeStackRange/increment 操作，不引用客户端类)。
	 */
	@Unique
	private boolean ssc_addon$tryMergeStackablePotions(int button, PlayerEntity player) {
		ItemStack cursor = this.getCursorStack();
		if (cursor.isEmpty()) {
			return false;
		}
		int maxStack;
		if (cursor.getItem() instanceof PotionItem) {
			int n = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream().mapToInt(ModifyPotionStackPower::getCount).max().orElse(0);
			if (n <= 0) {
				return false;
			}
			maxStack = Math.max(n, cursor.getMaxCount());
		} else if (cursor.getItem() instanceof UniversalEnergyPotionItem) {
			// 通用能量药水：与原版药水叠放同源，非水瓶限定 power 的最大 count（未持有则不接管）
			int n = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream()
					
					.mapToInt(ModifyPotionStackPower::getCount)
					.max()
					.orElse(0);
			if (n <= 0) {
				return false;
			}
			maxStack = n;
		} else if (cursor.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem) {
			maxStack = net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem.getStackLimitFor(player);
			if (maxStack <= 1) {
				return false;
			}
		} else {
			return false;
		}
		if (cursor.getCount() >= maxStack) {
			return false;
		}
		int start = button == 0 ? 0 : this.slots.size() - 1;
		int step = button == 0 ? 1 : -1;
		for (int pass = 0; pass < 2; ++pass) {
			for (int q = start; q >= 0 && q < this.slots.size() && cursor.getCount() < maxStack; q += step) {
				Slot slot = this.slots.get(q);
				if (!slot.hasStack() || !slot.canTakeItems(player)) {
					continue;
				}
				ItemStack slotStack = slot.getStack();
				if (!ItemStack.canCombine(cursor, slotStack)) {
					continue;
				}
				if (pass == 0 && slotStack.getCount() >= maxStack) {
					continue;
				}
				ItemStack taken = slot.takeStackRange(slotStack.getCount(), maxStack - cursor.getCount(), player);
				cursor.increment(taken.getCount());
			}
		}
		return true;
	}

	/**
	 * 让「药水可叠加」形态(持有 {@link ModifyPotionStackPower})的玩家在任意容器界面操作药水时，
	 * 把 {@code internalOnSlotClick} 内用 {@link ItemStack#getMaxCount()}(药水原版=1)判定的上限改为 N。
	 * 主要修复创造模式「物品栏」标签页 / 中键复制(CLONE) 等不走 {@code Slot.getMaxItemCount} 的路径，
	 * 使其与生存物品栏(原版 PotionStackMixin)一致叠到 N。仅对药水且持有该 Power 时生效，
	 * 双端安全(用方法参数 player，不引用客户端类)。
	 */
	@WrapOperation(
			method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxCount()I"),
			require = 0
	)
	private int ssc_addon$potionStackLimit(ItemStack stack, Operation<Integer> original, @Local(argsOnly = true) PlayerEntity player) {
		if (stack.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem) {
			return Math.max(net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem.getStackLimitFor(player), original.call(stack));
		}
		// 通用能量药水：与 GUI 槽位同源，按非水瓶限定 power 抬升
		if (stack.getItem() instanceof UniversalEnergyPotionItem) {
			int n = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream()
					
					.mapToInt(ModifyPotionStackPower::getCount)
					.max()
					.orElse(0);
			if (n > 0) {
				return Math.max(n, original.call(stack));
			}
			return original.call(stack);
		}
		if (stack.getItem() instanceof PotionItem) {
			int n = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream()
					.mapToInt(ModifyPotionStackPower::getCount)
					.max()
					.orElse(0);
			if (n > 0) {
				return Math.max(n, original.call(stack));
			}
		}
		return original.call(stack);
	}

	/**
	 * 统一计算某玩家对指定药水堆的实际叠放上限（insertItem 帧内使用，覆盖全部药水）：
	 * 通用能量药水 / 原版药水按 {@link ModifyPotionStackPower}（水瓶限定 power 仅对水瓶生效，
	 * 与原版 PotionStackMixin 同源）；凋零药水按形态分档。非药水返回 -1。
	 */
	@Unique
	private int ssc_addon$potionInsertLimit(ItemStack stack, PlayerEntity player) {
		if (stack.getItem() instanceof UniversalEnergyPotionItem) {
			return Math.max(1, PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream()
					
					.mapToInt(ModifyPotionStackPower::getCount)
					.max()
					.orElse(1));
		}
		if (stack.getItem() instanceof PotionItem) {
			boolean isWater = PotionUtil.getPotion(stack).equals(Potions.WATER);
			return Math.max(1, PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class)
					.stream()
					.filter(power -> !isWater)
					.mapToInt(ModifyPotionStackPower::getCount)
					.max()
					.orElse(1));
		}
		if (stack.getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem) {
			return net.jackcooper.shapeShifterCurseAddon.item.WitherPotionItem.getStackLimitFor(player);
		}
		return -1;
	}

	/**
	 * 修复 shift 快速移动（QUICK_MOVE → quickMove → insertItem）路径的药水叠放（覆盖全部药水）：
	 * ① 合并循环只用 {@code stack.getMaxCount()}（药水=1）——有 power 形态抬到 N 恢复合并；
	 * ② 空槽放置只用无参 {@code Slot.getMaxItemCount()}（玩家背包=64）——不经过任何 power 门控，
	 * 导致从存储箱等容器 shift 出的多瓶堆（>1）会整堆落进无 power 形态的背包（越权叠放）。
	 * 这里对「目标是玩家背包槽且移动堆是药水」的情形把空槽放置上限钳到该玩家的实际资格；
	 * 非 PlayerInventory 槽（存储箱 / 装瓶器等自有容器）保持原值（容器叠放不论形态）。
	 */
	@WrapOperation(
			method = "insertItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;getMaxItemCount()I"),
			require = 0
	)
	private int ssc_addon$insertItemEmptySlotCap(Slot slot, Operation<Integer> original,
			@Local(argsOnly = true) ItemStack movingStack) {
		PlayerEntity player = this.ssc_addon$clickPlayer;
		if (player != null && slot.inventory instanceof PlayerInventory) {
			int limit = ssc_addon$potionInsertLimit(movingStack, player);
			if (limit > 0) {
				return Math.min(limit, original.call(slot));
			}
		}
		return original.call(slot);
	}

	@WrapOperation(
			method = "insertItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxCount()I"),
			require = 0
	)
	private int ssc_addon$insertItemAddonPotionLimit(ItemStack stack, Operation<Integer> original) {
		PlayerEntity player = this.ssc_addon$clickPlayer;
		if (player != null) {
			int limit = ssc_addon$potionInsertLimit(stack, player);
			if (limit > 1) {
				return Math.max(limit, original.call(stack));
			}
		}
		return original.call(stack);
	}
}
