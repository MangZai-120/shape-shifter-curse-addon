package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InstinctBarRenderer.class)
public class InstinctBarRendererMixin {

    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0)
    private boolean hideInstinctBarForSP(boolean original) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            PlayerFormBase curForm = mc.player.getComponent(RegPlayerFormComponent.PLAYER_FORM).getCurrentForm();
            if (curForm != null && curForm.getPhase() == PlayerFormPhase.PHASE_SP) {
                return false;
            }
        }
        return original;
    }
}
