package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.hud.InGameHud;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.ClawClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 风灵「疾风连爪」准星攻击冷却条接管。
 *
 * 仅重定向 {@code InGameHud.renderCrosshair} 里那一次 {@code getAttackCooldownProgress} 调用（=渲染准星
 * 攻击充能指示器用的进度）：风灵爪击/过热时返回自定义进度（爪击间隔充能 / 过热回复），否则返回原值。
 *
 * ⚠ 关键：不改 {@code PlayerEntity.attack()} 里那处 getAttackCooldownProgress → MC 原版攻击冷却减伤照常
 * 生效（渲染与减伤分离）。只用这一个原版准星条，不额外画自定义横条。
 *
 * 注：{@code renderCrosshair} 里调用的接收者是 {@code this.client.player}（静态类型 ClientPlayerEntity），
 * 故 INVOKE owner 必须是 ClientPlayerEntity 而非 PlayerEntity（否则扫不到目标、崩溃）。
 * {@code require = 0}：即便被其它 mod 抢注或字节码差异导致未匹配，也只是准星条不显示、不崩溃（优雅降级）。
 */
@Mixin(InGameHud.class)
public class ClawCrosshairMixin {

    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getAttackCooldownProgress(F)F"),
            require = 0)
    private float ssc_addon$clawCrosshairProgress(float original) {
        if (ClawClientState.isActive()) {
            return ClawClientState.getCrosshairProgress();
        }
        return original;
    }
}
