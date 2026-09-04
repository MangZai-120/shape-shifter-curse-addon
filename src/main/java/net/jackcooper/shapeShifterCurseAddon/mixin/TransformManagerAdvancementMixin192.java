package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SSC 1.9.2 分支专属：附属形态「首次变身」成就触发 mixin。
 *
 * <p>主线 v8（SSC 1.10）用官方事件 SSCEvent.TRANSFORM_MANAGER_SET_FORM；1.9.2 无该事件系统，
 * 改回 v7 时代验证过的方案——在主包 handleDirectTransform 末尾注入（每当玩家变身到附属形态时
 * 触发统一成就触发器）。handleDirectTransform 是 1.9.2 所有主动变身（月髓环/进化石/剧情/指令）
 * 的公共入口，登录/重生不会调用，不会误发成就。</p>
 *
 * <p>方案来源：v7.0.0-SSC-1.9.2 分支的 TransformManagerAdvancementMixin（同注入点，仅包名
 * 与触发器实例迁移到 jackcooper 命名空间下的 SscAddon.ON_TRANSFORM_ADDON_FORM）。</p>
 */
@Mixin(TransformManager.class)
public abstract class TransformManagerAdvancementMixin192 {

    @Inject(method = "handleDirectTransform", at = @At("TAIL"), remap = false, require = 0)
    private static void sscAddon$onAddonFormTransform(PlayerEntity player, PlayerFormBase toForm, boolean isByCure, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        if (toForm == null || toForm.FormID == null) return;
        // 仅对 my_addon 命名空间生效，避免误触发主包/其它附属形态的成就
        if (!"my_addon".equals(toForm.FormID.getNamespace())) return;
        SscAddon.ON_TRANSFORM_ADDON_FORM.trigger(sp, toForm.FormID);
    }
}
