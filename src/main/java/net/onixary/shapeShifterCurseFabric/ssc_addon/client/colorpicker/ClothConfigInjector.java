package net.onixary.shapeShifterCurseFabric.ssc_addon.client.colorpicker;

import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 在原版 SSC 的 cloth-config（ModMenu 进入的"玩家修改"那一栏所在的）配置界面上，
 * 右上角注入一个"打开高级调色"按钮。
 *
 * 实现思路：
 *  - 监听 END_CLIENT_TICK，每帧检查 currentScreen 是否为 ClothConfigScreen 且 title 看起来是 SSC 的；
 *  - 通过 fabric-screen-api-v1 的 Screens.getButtons 拿到 Screen 的可点击控件列表；
 *  - 用一个 marker 子类 SscaAdvButton 来判别按钮是否已存在——若 cloth-config 因为 init 重建丢失了按钮，下一帧自动重新 add，
 *    这样从高级调色返回 cloth-config 时按钮不会消失；
 *  - 不动 cloth-config 内部 entry 列表，零反射、零 mixin，cloth-config 升级也不会炸。
 */
public final class ClothConfigInjector {
    private ClothConfigInjector() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen s = client.currentScreen;
            if (!(s instanceof ClothConfigScreen)) return;
            String title = s.getTitle() == null ? "" : s.getTitle().getString();
            if (!isSscConfig(title)) return;
            try {
                List<ClickableWidget> buttons = Screens.getButtons(s);
                if (containsSscaButton(buttons)) return; // 已有，无需重复 add
                int btnW = 120;
                ButtonWidget btn = new SscaAdvButton(
                        s.width - btnW - 6, 6, btnW, 20,
                        Text.translatable("text.ssc_addon.adv_color.title"),
                        b -> client.setScreen(new AdvancedColorScreen(s)));
                buttons.add(btn);
            } catch (Throwable ignored) {
                // 任何异常都不应让原界面崩，等下一帧再试
            }
        });
    }

    private static boolean containsSscaButton(List<ClickableWidget> buttons) {
        for (ClickableWidget w : buttons) {
            if (w instanceof SscaAdvButton) return true;
        }
        return false;
    }

    /** 通过 title 文本粗匹配 SSC 自己的 config screen。多语言/未本地化 fallback 都覆盖一下。 */
    private static boolean isSscConfig(String t) {
        if (t == null || t.isEmpty()) return false;
        return t.contains("Shape Shifter Curse")
                || t.contains("幻形者诅咒")
                || t.contains("shape-shifter-curse")
                || t.contains("shapeShifterCurse");
    }

    /** 仅作 marker 用，便于扫描时去重判别。 */
    private static final class SscaAdvButton extends ButtonWidget {
        private SscaAdvButton(int x, int y, int w, int h, Text msg, PressAction onPress) {
            super(x, y, w, h, msg, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        }
    }
}
