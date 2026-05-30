package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.onixary.shapeShifterCurseFabric.additional_power.BatBlockAttachPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 血雾化形期间禁用吸血蝙蝠的“右键贴墙攀爬（附着）”。
 * 注入主包 BatBlockAttachPower#tryAttach：当玩家处于 ssc_addon:mist_form 状态时，
 * 直接取消附着尝试（返回 false），使右键无法贴墙，避免附着锁定与化雾飞行相互冲突。
 * tryAttach 仅在服务端被 BatAttachEventHandler 的右键事件调用，故主客机表现一致。
 * 仅拦截“新附着入口”；进入血雾时若已处于附着状态，由 MistFormAbilityPower#enterMist 主动解除。
 */
@Mixin(BatBlockAttachPower.class)
public class SscAddonBatAttachMistMixin {

    @Inject(method = "tryAttach", at = @At("HEAD"), cancellable = true)
    private void sscAddon$disableAttachInMist(PlayerEntity player, BlockHitResult hitResult, CallbackInfoReturnable<Boolean> cir) {
        // 血雾化形期间禁止右键贴墙攀爬
        if (player.hasStatusEffect(SscAddon.MIST_FORM)) {
            cir.setReturnValue(false);
        }
    }
}
