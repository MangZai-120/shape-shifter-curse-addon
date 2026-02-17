package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to cancel Iron's Spellbooks casting animation for specific forms (SP/Red).
 * Uses Pseudo to avoid crash if mod is missing, and string target to avoid compile/load dependency errors if not present directly.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.player.ClientSpellCastHelper")
public class IronsSpellbooksAnimationMixin {

    @Inject(method = "animatePlayerStart", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onAnimatePlayerStart(PlayerEntity player, Identifier resourceLocation, CallbackInfo ci) {
        try {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            if (currentForm != null && currentForm.FormID != null) {
                String path = currentForm.FormID.getPath();
                // Check if the form is an SP form (contains "_sp") or Red form (contains "red")
                if ((path.contains("_sp") || path.contains("red")) && !path.contains("axolotl_sp")) {
                    ci.cancel();
                }
            }
        } catch (Throwable e) {
            // Ignore if FormAbilityManager is unavailable or other errors occur
        }
    }
}
