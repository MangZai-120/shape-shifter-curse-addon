package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CursedMoon.class)
public class SscAddonCursedMoonMixin {
    @Inject(method = "applyEndMoonEffect", at = @At("HEAD"), cancellable = true)
    private static void onApplyEndMoonEffect(ServerPlayerEntity player, CallbackInfo ci) {
        PlayerFormComponent formComp = RegPlayerFormComponent.PLAYER_FORM.get(player);
        // Ensure we are in the correct state to apply end effect
        if (!formComp.isEndMoonEffectApplied() && formComp.isMoonEffectApplied()) {
            PlayerFormBase currentForm = FormAbilityManager.getForm(player);
            // Check for SP Phase
            if (currentForm != null && currentForm.getPhase() == PlayerFormPhase.PHASE_SP) {
                // Send the special Gold message
                player.sendMessage(Text.translatable("info.shape-shifter-curse.end_cursed_moon_special").formatted(Formatting.GOLD));
                
                // Trigger standard end logic (which handles flags internally usually, but we called it manually)
                // Original code: calls TransformManager.handleMoonEndTransform(player) then sets formComp.setEndMoonEffectApplied(true)
                
                TransformManager.handleMoonEndTransform(player);
                
                formComp.setEndMoonEffectApplied(true);
                RegPlayerFormComponent.PLAYER_FORM.sync(player);
                
                // Cancel the original method to override its behavior
                ci.cancel();
            }
        }
    }
}
