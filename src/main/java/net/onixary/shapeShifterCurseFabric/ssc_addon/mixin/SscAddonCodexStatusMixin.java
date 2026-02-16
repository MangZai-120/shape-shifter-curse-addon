package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.data.CodexData;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CodexData.class)
public class SscAddonCodexStatusMixin {

    private SscAddonCodexStatusMixin() {
        // This utility class should not be instantiated
    }

    @Inject(method = "getPlayerStatusText", at = @At("HEAD"), cancellable = true)
    private static void getPlayerStatusText(PlayerEntity player, CallbackInfoReturnable<Text> cir) {
        PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (component != null) {
            PlayerFormBase currentForm = component.getCurrentForm();
            if (currentForm != null && currentForm.FormID != null) {
                String formPath = currentForm.FormID.getPath();
	            switch (formPath) {
		            case "axolotl_sp", "familiar_fox_sp" ->
				            cir.setReturnValue(Text.translatable("codex.status.my_addon.SP_status"));
		            case "wild_cat_sp" ->
				            cir.setReturnValue(Text.translatable("codex.status.my_addon.wild_cat_sp_status"));
		            case "snow_fox_sp" ->
				            cir.setReturnValue(Text.translatable("codex.status.my_addon.snow_fox_sp_status"));
		            case "familiar_fox_red" ->
				            cir.setReturnValue(Text.translatable("codex.status.my_addon.familiar_fox_red_status"));
                    default ->
                            cir.setReturnValue(Text.translatable("codex.status.normal"));
	            }
            }
        }
    }
}
