package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.item.WaterSpearItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

	protected ItemEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Shadow
	public abstract ItemStack getStack();

	@Shadow
	public abstract int getItemAge();

	@Inject(method = "tick", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		if (!this.getWorld().isClient) {
			ItemStack stack = this.getStack();
			if (net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon.isActiveStack(stack)) {
				stack = stack.copy();
				net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon.clearStack(stack);
				((ItemEntity) (Object) this).setStack(stack);
			}
			// 激活的不死图腾掉落在地即失去激活（R3：离开 SP 悦灵本人即失效）
			if (net.jackcooper.shapeShifterCurseAddon.ability.AllaySPTotem.isMarkedActiveTotem(stack)) {
				stack = stack.copy();
				net.jackcooper.shapeShifterCurseAddon.ability.AllaySPTotem.clearTotemMark(stack);
				((ItemEntity) (Object) this).setStack(stack);
			}
			if (stack.getItem() instanceof WaterSpearItem && this.getItemAge() >= 20) {
				this.discard();
			}
			if (stack.isOf(SscAddon.ALLAY_HEAL_WAND) || stack.isOf(SscAddon.ALLAY_JUKEBOX) || stack.isOf(SscAddon.POTION_BAG)) {
				this.discard();
			}
		}
	}
}
