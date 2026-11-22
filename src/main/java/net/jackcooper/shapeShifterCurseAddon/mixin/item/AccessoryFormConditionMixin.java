package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.jackcooper.shapeShifterCurseAddon.item.AccessoryFormEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.additional_power.TrinketsConditionAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/** SSC powers and web projectiles also query equipment directly, outside accessory callbacks. */
@Mixin(value = TrinketsConditionAction.class, remap = false)
public abstract class AccessoryFormConditionMixin {
    @Inject(method = "isEquipped", at = @At("HEAD"), cancellable = true)
    private static void ssca$checkForm(Entity entity, String backend, Identifier item, CallbackInfoReturnable<Boolean> cir) {
        if (item != null && entity instanceof LivingEntity wearer
                && !AccessoryFormEffects.allows(AccessoryFormEffects.formOf(wearer), item)) cir.setReturnValue(false);
    }

    @WrapOperation(method = "CheckEquipped", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
    private static boolean ssca$checkSlotForm(Predicate<ItemStack> condition, Object value, Operation<Boolean> original,
                                               @Local(argsOnly = true) Entity entity) {
        ItemStack stack = (ItemStack) value;
        return entity instanceof LivingEntity wearer
                && AccessoryFormEffects.allows(AccessoryFormEffects.formOf(wearer), Registries.ITEM.getId(stack.getItem()))
                && original.call(condition, stack);
    }
}
