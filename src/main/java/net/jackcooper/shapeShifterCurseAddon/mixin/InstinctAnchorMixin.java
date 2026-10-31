package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.HashMap;
import net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将独立固定叠加到 SSC 判定结果，永不向原版锁表写入 false。 */
@Mixin(value = InstinctUtils.class, remap = false)
public abstract class InstinctAnchorMixin {
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", ordinal = 1,
            target = "Ljava/util/HashMap;getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object ssca$includeAnchor(HashMap<?, ?> map, Object key, Object fallback,
                                            Operation<Object> original, @Local ServerPlayerEntity player) {
        return InstinctAnchors.locked(player) ? Boolean.TRUE : original.call(map, key, fallback);
    }

    // 两个公开重载最终都进入此入口；类型化选择器同时适用于开发端和重映射后的客户端。
    @Inject(target = @Desc(value = "addInstinctEffect", args = {PlayerEntity.class, InstinctUtils.InstinctEffect.class, boolean.class}),
            at = @At("HEAD"), cancellable = true)
    private static void ssca$freezeImmediate(PlayerEntity player, InstinctUtils.InstinctEffect effect,
                                            boolean immediate, CallbackInfo ci) {
        if (immediate && InstinctAnchors.locked(player)) ci.cancel();
    }

    @Inject(method = "clearInstinct", at = @At("HEAD"), cancellable = true)
    private static void ssca$freezeClear(PlayerEntity player, CallbackInfo ci) {
        if (InstinctAnchors.locked(player)) ci.cancel();
    }

    @Inject(method = "checkThreshold", at = @At("HEAD"), cancellable = true)
    private static void ssca$freezeThreshold(ServerPlayerEntity player, float value, CallbackInfo ci) {
        if (InstinctAnchors.locked(player)) ci.cancel();
    }
}
