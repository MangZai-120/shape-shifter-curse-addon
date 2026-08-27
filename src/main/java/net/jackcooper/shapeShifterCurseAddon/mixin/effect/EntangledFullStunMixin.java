package net.jackcooper.shapeShifterCurseAddon.mixin.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.onixary.shapeShifterCurseFabric.status_effects.EntangledEffectUtils;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 蛛丝结茧定身：任何生物被蜘蛛系裹成茧（获得 ENTANGLED_FULL_EFFECT）的瞬间，
 * 自动施加一份与茧等长的 STUN 定身 debuff，让茧内生物完全无法移动 / 攻击 / 思考（AI 停）。
 * 茧时长玩家 5s（100t）、怪物 15s（300t），STUN 与之同步，茧消 STUN 也随之到期。
 */
@Mixin(EntangledEffectUtils.class)
public class EntangledFullStunMixin {

    // applyEntangledEffect 只有「本次刚裹满成茧」的路径才会走到方法末尾（TAIL）；
    // 「已经是茧」会在中途 return 不触发 TAIL，故此处即精确的成茧瞬间。
    @Inject(method = "applyEntangledEffect", at = @At("TAIL"))
    private static void ssca$applyCocoonStun(Entity owner, LivingEntity target, int Time, CallbackInfo ci) {
        // 服务端判定（原方法已在服务端调用，这里再判一次保底）
        if (target.getWorld().isClient) {
            return;
        }
        StatusEffectInstance full = target.getStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT);
        if (full == null) {
            // 本次只是累积缠绕、尚未成茧，不施加定身
            return;
        }
        // 茧内定身：STUN 时长与茧保持一致，茧存在期间始终无法动弹
        int duration = full.getDuration();
        // 食梦魔「入梦」：裹茧者已入梦且目标正是把它打入梦的食梦魔 → 茧 STUN 无效（茧本体仍在，仅免定身 debuff）
        if (owner instanceof LivingEntity livingOwner
                && net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager.isBlocked(livingOwner, target)) {
            return;
        }
        // 带 source 归因：让 SscAddonLivingEntityMixin 的入梦 debuff 拦截也能按来源归因
        target.addStatusEffect(new StatusEffectInstance(SscAddon.STUN, duration, 0, false, false, true), owner);
    }
}
