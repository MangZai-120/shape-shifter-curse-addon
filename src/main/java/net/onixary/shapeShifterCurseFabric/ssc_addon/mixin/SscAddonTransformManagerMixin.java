package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransformManager.class)
public class SscAddonTransformManagerMixin {
    @Inject(method = "handleProgressiveTransform", at = @At("HEAD"), cancellable = true)
    private static void onHandleProgressiveTransform(ServerPlayerEntity player, boolean isByCursedMoon, CallbackInfo ci) {
        PlayerFormBase currentForm = FormAbilityManager.getForm(player);
        // SP forms logic during Cursed Moon
        if (currentForm != null && currentForm.getPhase() == PlayerFormPhase.PHASE_SP) {
            if (isByCursedMoon) {
                // Send the specific message for SP forms
                player.sendMessage(Text.translatable("info.shape-shifter-curse.on_cursed_moon_special").formatted(Formatting.GOLD), true);
                
                // Prevent the original logic (which might handle index 5 but not others, or default logic)
                ci.cancel();
            }
        }
    }
}
