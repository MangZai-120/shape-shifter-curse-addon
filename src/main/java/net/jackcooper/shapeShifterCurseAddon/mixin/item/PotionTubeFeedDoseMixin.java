package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.jackcooper.shapeShifterCurseAddon.item.PotionTubeDose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.HungerManager;
import net.onixary.shapeShifterCurseFabric.status_effects.other_effects.FeedEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 仅在PotionTubeDose限定上下文内调整压缩能量试管，普通瓶及原喷溅衰减保持原规则。 */
@Mixin(FeedEffect.class)
public abstract class PotionTubeFeedDoseMixin {
    @ModifyExpressionValue(method = "applyInstantEffect", at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;max(DD)D", remap = false))
    private double ssca$tubeFeedFraction(double original, @Local(argsOnly = true) LivingEntity target) {
        return PotionTubeDose.feedFraction(target, original);
    }

    @WrapOperation(method = "applyInstantEffect", at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;ceil(D)D", remap = false))
    private double ssca$tubeFeedFood(double amount, Operation<Double> original,
                                    @Local(argsOnly = true) LivingEntity target) {
        PotionTubeDose.captureFeedFood(target, amount);
        return original.call(amount);
    }

    @WrapOperation(method = "applyInstantEffect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/HungerManager;add(IF)V"))
    private void ssca$tubeFeedSaturation(HungerManager hunger, int food, float saturation,
                                         Operation<Void> original, @Local(argsOnly = true) LivingEntity target) {
        original.call(hunger, food, PotionTubeDose.feedSaturation(target, food, saturation));
    }
}
