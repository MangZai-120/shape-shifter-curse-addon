package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import io.github.apace100.apoli.component.PowerHolderComponentImpl;
import net.jackcooper.shapeShifterCurseAddon.util.IrisShadowPassDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

/**
 * 配合 LivingEntityRendererShadowFixMixin 修复 Apoli × Iris 阴影渲染崩溃。
 *
 * 当处于 Iris 阴影 pass 的 LivingEntityRenderer.render 作用域内时，
 * 让 getPowers(Class) 返回空列表，使 Apoli 的 preventFeatureRendering
 * （内部对 PreventFeatureRenderPower / InvisibilityPower 的 stream 判断）
 * 短路为 false，绕开 sourceStage==null 的 NPE 路径。
 *
 * 仅在阴影 pass 生效，正常主渲染 pass 返回原始 power 列表，不影响 Apoli 功能。
 *
 * 注意：PowerHolderComponentImpl 是 Apoli 自有类（非 MC intermediary），
 * method/remap 均设为 false，直接用 devtime 方法名 getPowers。
 */
@Mixin(value = PowerHolderComponentImpl.class, remap = false)
public class PowerHolderComponentShadowFixMixin {

    @Inject(
        method = "getPowers(Ljava/lang/Class;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void sscAddon$returnEmptyDuringShadowPass(Class<?> powerClass, CallbackInfoReturnable<List<?>> cir) {
        if (IrisShadowPassDetector.shouldSuppressPowerQuery()) {
            cir.setReturnValue(Collections.emptyList());
        }
    }
}
