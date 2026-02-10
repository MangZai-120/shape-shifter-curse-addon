package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CursedMoon.class)
public class CursedMoonSpMessageMixin {

    /**
     * 在诅咒之月效果应用时，为SP形态显示额外消息
     * SP形态（Index 5或7）不会被诅咒之月影响，显示特殊消息
     * 修改：在 'The cursed moon has risen' 消息之后显示 (注入在 setMoonEffectApplied 之前)
     */
    @Inject(method = "applyMoonEffect", 
            at = @At(value = "INVOKE", 
                     target = "Lnet/onixary/shapeShifterCurseFabric/player_form/ability/PlayerFormComponent;setMoonEffectApplied(Z)V",
                     shift = At.Shift.BEFORE), 
            remap = false)
    private static void onApplyMoonEffect(ServerPlayerEntity player, CallbackInfo ci) {
        PlayerFormComponent formComp = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (formComp == null) return;
        
        PlayerFormBase currentForm = formComp.getCurrentForm();
        if (currentForm == null) return;
        
        int formIndex = currentForm.getIndex();
        
        // SP形态（Index 5或7）显示特殊消息
        // 只在第一次应用效果时显示（检查moonEffectApplied标记）
        if ((formIndex == 5 || formIndex == 7) && !formComp.isMoonEffectApplied()) {
            player.sendMessage(Text.translatable("message.ssc_addon.cursed_moon_sp_special").formatted(Formatting.YELLOW), false);
        }
    }
}
