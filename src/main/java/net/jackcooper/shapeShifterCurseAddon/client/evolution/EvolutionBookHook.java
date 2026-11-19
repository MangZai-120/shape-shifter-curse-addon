package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.custom_ui.BookOfShapeShifterScreenV2_P1;

/** 在幻形者之书的模型预览框上方显示当前进化路线的加点入口。 */
public final class EvolutionBookHook {
    private EvolutionBookHook() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof BookOfShapeShifterScreenV2_P1)) {
                return;
            }
            // 定位到书内形态模型预览框上方一点。书布局：BookSizeX=350/Y=220，预览框 in-book Pos(35,15) Size(70,66)。
            // 按钮放在预览框正上方（in-book Pos(35,2) Size(70,12)），随大书开关(BookScale)缩放。
            int bookScale = ShapeShifterCurseFabric.clientConfig.newStartBookForBiggerScreen ? 2 : 1;
            int bookPosX = scaledWidth / 2 - (BookOfShapeShifterScreenV2_P1.BookSizeX * bookScale) / 2;
            int bookPosY = scaledHeight / 2 - (BookOfShapeShifterScreenV2_P1.BookSizeY * bookScale) / 2;
            int btnX = bookPosX + 35 * bookScale;
            int btnY = bookPosY + 2 * bookScale;
            int btnW = 70 * bookScale;
            int btnH = 12 * bookScale;
            ButtonWidget button = ButtonWidget.builder(
                    Text.translatable("text.ssc_addon.evolution.book.button"),
                    b -> client.setScreen(new EvolutionScreen(screen))
            ).dimensions(btnX, btnY, btnW, btnH).build();
            updateVisibility(client, button);
            Screens.getButtons(screen).add(button);
            // 形态和服务端路线可能在开书后才同步，也可能在书页打开时变更。
            ScreenEvents.afterTick(screen).register(book -> updateVisibility(client, button));
        });
    }

    private static void updateVisibility(MinecraftClient client, ButtonWidget button) {
        var form = FormUtils.getCurrentForm(client.player);
        var route = form == null ? null : EvolutionRegistry.CLIENT.getRouteByStartForm(form.getFormID());
        button.visible = route != null && route.enabled;
        button.active = button.visible;
    }
}
