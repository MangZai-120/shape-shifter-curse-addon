package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SSC 1.9.2 分支专属：诅咒之月开始时为 SP 形态显示额外提示。
 *
 * <p>主线 v8（SSC 1.10）用官方事件 SSCEvent.CURSED_MOON_BEGIN；1.9.2 无该事件系统，
 * 改回 v7 时代验证过的 CursedMoonSpMessageMixin 方案——注入 applyMoonEffect 的
 * setMoonEffectApplied 调用前（每次诅咒之月只应用一次效果，天然幂等）。</p>
 *
 * <p>SP 判定从 1.10 的 special_form flag 映射为 1.9.2 的 phase == PHASE_SP。</p>
 */
@Mixin(CursedMoon.class)
public abstract class CursedMoonSpMessageMixin192 {

    @Inject(method = "applyMoonEffect",
                    at = @At(value = "INVOKE",
                                    target = "Lnet/onixary/shapeShifterCurseFabric/player_form/ability/PlayerFormComponent;setMoonEffectApplied(Z)V",
                                    shift = At.Shift.BEFORE),
                    remap = false, require = 0)
    private static void sscAddon$onApplyMoonEffect(ServerPlayerEntity player, CallbackInfo ci) {
        PlayerFormComponent formComp = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (formComp == null) return;

        PlayerFormBase currentForm = formComp.getCurrentForm();
        if (currentForm == null) return;

        // 只在第一次应用效果时显示（isMoonEffectApplied 幂等标记）
        if (currentForm.getPhase() == PlayerFormPhase.PHASE_SP && !formComp.isMoonEffectApplied()) {
            // SSC 1.9.2 无 enableCursedMoonTransform 配置项（月变始终生效），无需关闭检查
            player.sendMessage(Text.translatable("message.ssc_addon.cursed_moon_sp_special").formatted(Formatting.YELLOW), false);
        }
    }
}
