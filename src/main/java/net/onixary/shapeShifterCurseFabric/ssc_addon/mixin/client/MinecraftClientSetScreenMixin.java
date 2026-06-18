package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.colorpicker.AdvancedColorScreen;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
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

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("resource") 
    private void ssc_addon(Screen screen, CallbackInfo ci) {
        if (screen == null) return;
        if (screen instanceof AdvancedColorScreen) return; // 防递归
        SSCAddonClientConfig cfg = AutoConfig.getConfigHolder(SSCAddonClientConfig.class).getConfig();
        if (!cfg.enableColorEditor) return;
        if (!"net.onixary.shapeShifterCurseFabric.custom_ui.FormColorSelectMenu".equals(screen.getClass().getName())) return;
        // 关键：FormColorSelectMenu 的构造函数已经把 SSC 的 instance / useTempTexture / tempTextureProcessor 三个全局
        // 状态置位，如果这里直接 cancel 而不清理，screen.close() 永远不会被调用，下次再开就触发：
        //   "FormColorSelectMenu is already in use, only one instance is allowed"
        // 因此先反射把这三个静态字段还原，再 cancel + 用我们自己的界面替换。
        cleanupSscColorMenuState(screen);
        MinecraftClient mc = (MinecraftClient) (Object) this;
        Screen parent = mc.currentScreen;
        mc.setScreen(new AdvancedColorScreen(parent));
        ci.cancel();
    }
    /**
     * 反射方式清理 SSC 的 FormColorSelectMenu / FormTextureUtils 静态状态，使其表现得像 close() 已经被调用。
     * 这里不直接调用 screen.close()，因为 close() 会顺带保存、发包、嵌套 setScreen(parsetScreen)，副作用太大。
     * 注意：避免对 SSC 类的硬编译依赖，附属包仍兼容旧版本 SSC（没有 FormColorSelectMenu 的版本）。
     */
    @org.spongepowered.asm.mixin.Unique
    private static void cleanupSscColorMenuState(Screen formColorSelectMenu) {
        try {
            Class<?> menuCls = formColorSelectMenu.getClass();
            // 1) 还原 isUsingTempTexture（实例字段，避免 instance 重用时 close() 误清第二次）
            try {
                java.lang.reflect.Field iu = menuCls.getDeclaredField("isUsingTempTexture");
                iu.setAccessible(true);
                iu.setBoolean(formColorSelectMenu, false);
            } catch (NoSuchFieldException ignored) {}
            // 2) 清 static instance
            try {
                java.lang.reflect.Field inst = menuCls.getDeclaredField("instance");
                inst.setAccessible(true);
                inst.set(null, null);
            } catch (NoSuchFieldException ignored) {}
            // 3) 清 FormTextureUtils.useTempTexture / tempTextureProcessor
            Class<?> texUtils = Class.forName("net.onixary.shapeShifterCurseFabric.util.FormTextureUtils");
            try {
                java.lang.reflect.Field useTT = texUtils.getDeclaredField("useTempTexture");
                useTT.setAccessible(true);
                useTT.setBoolean(null, false);
            } catch (NoSuchFieldException ignored) {}
            try {
                java.lang.reflect.Field proc = texUtils.getDeclaredField("tempTextureProcessor");
                proc.setAccessible(true);
                proc.set(null, null);
            } catch (NoSuchFieldException ignored) {}
        } catch (Throwable ignored) {
            // 反射失败不应阻断界面切换
        }
    }
}