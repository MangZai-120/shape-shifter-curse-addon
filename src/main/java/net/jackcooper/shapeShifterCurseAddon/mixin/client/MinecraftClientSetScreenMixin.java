package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.jackcooper.shapeShifterCurseAddon.client.colorpicker.AdvancedColorScreen;
import net.jackcooper.shapeShifterCurseAddon.client.colorpicker.ColorPreviewTextures;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 当 SSCA 颜色编辑开关 ON 时，拦截 setScreen(FormColorSelectMenu)，替换为 AdvancedColorScreen。
 * 用类名字符串判断避免对原版 SSC 的硬依赖。
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientSetScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void ssc_addon(Screen screen, CallbackInfo ci) {
        if (screen == null) return;
        if (screen instanceof AdvancedColorScreen) return; // 防递归
        SSCAddonClientConfig cfg = AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig();
        if (!cfg.enableColorEditor) return;
        if (!"net.onixary.shapeShifterCurseFabric.custom_ui.FormColorSelectMenu".equals(screen.getClass().getName())) return;
        // SSC acquires its preview processors in the constructor. Its removal hook releases only those it owns.
        if (screen != ((MinecraftClient) (Object) this).currentScreen) screen.removed();
        Screen parent = ((MinecraftClient) (Object) this).currentScreen;
        ((MinecraftClient) (Object) this).setScreen(new AdvancedColorScreen(parent));
        ci.cancel();
    }

    @Inject(method = "setScreen", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void ssca$changeColorPreview(Screen next, CallbackInfo ci) {
        ColorPreviewTextures.changeScreen(next);
    }
}
