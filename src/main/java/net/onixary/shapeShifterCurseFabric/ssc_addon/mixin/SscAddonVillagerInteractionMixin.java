package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public class SscAddonVillagerInteractionMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void ssc_addon$preventTrade(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (player.isSneaking()) {
            try {
                PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
                if (component != null && component.getCurrentForm() != null && 
                    new Identifier("my_addon", "form_familiar_fox_sp").equals(component.getCurrentForm().FormID)) {
                    // Prevent opening trade GUI
                    cir.setReturnValue(ActionResult.PASS);
                }
            } catch (Exception ignored) {}
        }
    }
}
