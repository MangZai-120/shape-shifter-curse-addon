package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.custom_ui.StartBookScreenV2;

/**
 * SSCA 进化路线 - 开局入口：在原版「翻开幻形者之书」初始界面（{@link StartBookScreenV2}）
 * 的主按钮下方注入一个较小的「进入 SSCA 进化路线」按钮。
 *
 * <p>纯客户端，用 Fabric {@link ScreenEvents} 注入，不修改原版源码（与 {@link EvolutionBookHook} 同思路）。</p>
 *
 * <p>点击流程：入口按钮 → 确认对话框（说明 SSCA 进化路线与 SSC 本体的区别）→
 * {@link SscaFormSelectScreen 可翻页形态选择界面} → 选定后发包进化。</p>
 */
public final class SscaStartBookHook {
    /** 入口按钮尺寸（比原版主按钮 200×30 更小）。 */
    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 20;
    /** 与主按钮的纵向间距。 */
    private static final int GAP = 4;

    private SscaStartBookHook() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof StartBookScreenV2)) {
                return;
            }
            if (client.player == null) {
                return;
            }
            // 复刻 StartBookScreenV2.init() 主按钮的纵向位置，把入口按钮放在其正下方
            boolean bigger = ShapeShifterCurseFabric.clientConfig.newStartBookForBiggerScreen;
            int buttonPosYFix = bigger ? -50 : -100;
            int bookBottomY = scaledHeight / 2 + StartBookScreenV2.BookSizeY / 2;
            int mainButtonY = bookBottomY + buttonPosYFix;
            int myButtonY = mainButtonY + StartBookScreenV2.ButtonSizeY + GAP;
            int myButtonX = scaledWidth / 2 - BUTTON_W / 2;

            ButtonWidget button = ButtonWidget.builder(
                    Text.translatable("evolution.my_addon.start.button"),
                    b -> openConfirm(client, screen)
            ).dimensions(myButtonX, myButtonY, BUTTON_W, BUTTON_H).build();
            Screens.getButtons(screen).add(button);
        });
    }

    /** 打开确认对话框：说明 SSCA 进化路线与 SSC 本体的区别。 */
    private static void openConfirm(net.minecraft.client.MinecraftClient client, net.minecraft.client.gui.screen.Screen startScreen) {
        ConfirmScreen confirm = new ConfirmScreen(
                accepted -> {
                    if (accepted) {
                        client.setScreen(new SscaFormSelectScreen(startScreen));
                    } else {
                        client.setScreen(startScreen);
                    }
                },
                Text.translatable("evolution.my_addon.start.confirm.title"),
                Text.translatable("evolution.my_addon.start.confirm.message"),
                Text.translatable("evolution.my_addon.start.confirm.yes"),
                Text.translatable("evolution.my_addon.start.confirm.no"));
        client.setScreen(confirm);
    }
}
