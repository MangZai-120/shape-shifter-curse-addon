package net.jackcooper.shapeShifterCurseAddon.mixin.entity.windspirit;

import net.minecraft.entity.projectile.ProjectileEntity;
import net.jackcooper.shapeShifterCurseAddon.ability.WindSpiritWindPressureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 风灵「风压领域」：拦所有弹射物的 tick，调用 WindSpiritWindPressureManager 判定并应用减速。
 * 拦基类 ProjectileEntity，覆盖箭矢/火球/三叉戟等所有子类。
 */
@Mixin(ProjectileEntity.class)
public abstract class WindSpiritProjectilePressureMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void ssc_addon$applyWindPressure(CallbackInfo ci) {
        WindSpiritWindPressureManager.tryApplySlow((ProjectileEntity) (Object) this);
    }
}
