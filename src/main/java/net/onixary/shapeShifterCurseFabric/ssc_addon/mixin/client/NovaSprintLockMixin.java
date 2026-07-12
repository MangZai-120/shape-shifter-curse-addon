/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import net.minecraft.client.network.ClientPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 朔望「舍身爆炸」蓄力期间严格禁止疾跑（客户端）。
 * <p>
 * 读取同步到客户端的 apoli 资源 form_ocelot_nova_charging：为 1 时表示正在蓄力，
 * 此时阻止开始疾跑，并每 tick 强制取消当前疾跑——实现严格禁疾跑，而非仅靠减速近似。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class NovaSprintLockMixin {

    private boolean ssca$novaCharging() {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        return PowerUtils.getClientResourceValue(self, FormIdentifiers.OCELOT_NOVA_CHARGING) > 0;
    }

    // 禁止开始疾跑
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void ssca$novaBlockSprintStart(CallbackInfoReturnable<Boolean> cir) {
        if (ssca$novaCharging()) {
            cir.setReturnValue(false);
        }
    }

    // 每 tick 强制取消当前疾跑（覆盖蓄力开始前已在疾跑的情况）
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void ssca$novaStopSprint(CallbackInfo ci) {
        if (ssca$novaCharging()) {
            ((ClientPlayerEntity) (Object) this).setSprinting(false);
        }
    }
}
