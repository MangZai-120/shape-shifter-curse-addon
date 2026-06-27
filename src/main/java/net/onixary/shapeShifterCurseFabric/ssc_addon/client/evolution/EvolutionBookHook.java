package net.onixary.shapeShifterCurseFabric.ssc_addon.client.evolution;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.custom_ui.BookOfShapeShifterScreenV2_P1;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

/**
 * SSCA 进化加点系统 - 在幻形者之书界面内注入「进化加点」入口按钮。
 * 纯客户端，用 Fabric ScreenEvents 注入，不修改原版源码。
 *
 * 仅当玩家当前为「进化使魔」(upgrade_familiar_fox) 形态时显示，按钮位于书内形态模型预览框上方。
 */
public final class EvolutionBookHook {
    private EvolutionBookHook() {
    }





    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof BookOfShapeShifterScreenV2_P1)) {
                return;
            }
            if (client.player == null) {
                return;
            }
            IForm currentForm = RegPlayerFormComponent.PLAYER_FORM.get(client.player).nowForm;
            Identifier formId = (currentForm == null) ? null : currentForm.getFormID();
            // 仅「进化使魔」形态显示进化加点入口
            if (!FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(formId)) {
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
            Screens.getButtons(screen).add(button);

        });
    }
}
