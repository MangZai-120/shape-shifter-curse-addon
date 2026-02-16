package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformRelatedItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransformRelatedItems.class)
public class TransformRelatedItemsMixin {

    private TransformRelatedItemsMixin() {
        // This utility class should not be instantiated
    }

    @Inject(method = "OnUseCure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onUseCure(PlayerEntity player, CallbackInfo ci) {
        PlayerFormBase currentForm = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).getCurrentForm();
        int currentFormIndex = currentForm.getIndex();
        
        // Block suppressor usage for SP form (Index 5)
        if (currentFormIndex == 5) {
            player.sendMessage(Text.translatable("message.ssc_addon.inhibitor.fail.sp_form").formatted(Formatting.RED), true);
            ci.cancel();
        }
    }

    @Inject(method = "OnUseCureFinal", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onUseCureFinal(PlayerEntity player, CallbackInfo ci) {
        PlayerFormBase currentForm = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).getCurrentForm();
        int currentFormIndex = currentForm.getIndex();
        
        // Block suppressor usage for SP form (Index 5)
        if (currentFormIndex == 5) {
            player.sendMessage(Text.translatable("message.ssc_addon.inhibitor.fail.sp_form").formatted(Formatting.RED), true);
            ci.cancel();
        }
    }
}
