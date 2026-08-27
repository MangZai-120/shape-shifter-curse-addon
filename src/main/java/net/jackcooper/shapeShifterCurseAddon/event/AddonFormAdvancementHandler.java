package net.jackcooper.shapeShifterCurseAddon.event;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.event.SSCEvent;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

/**
 * 附属形态“首次变身”成就触发器。
 * <p>
 * 由原来缺失的 TransformManagerAdvancementMixin（注入 TransformManager.setForm）迁移而来，
 * 改用原版官方事件 SSCEvent.TRANSFORM_MANAGER_SET_FORM。
 * <p>
 * 该事件仅在主动变形流程（TransformManager.setForm）触发，登录 / 重生 / 切维度不会触发，
 * 因此不会误发成就；这正是原 mixin 注入 setForm 的等价语义。
 * <p>
 * 事件为 MODIFY 型（回调返回值会成为玩家最终形态），这里只做旁路触发成就、
 * 原样返回 finalForm 不改写最终形态。
 */
public final class AddonFormAdvancementHandler {

    /** 附属形态命名空间；只有该命名空间的形态才触发附属变身成就。 */
    private static final String ADDON_NAMESPACE = "my_addon";

    private AddonFormAdvancementHandler() {
    }

    /** 在附属主初始化时调用，注册变形设定形态事件监听。 */
    public static void register() {
        SSCEvent.TRANSFORM_MANAGER_SET_FORM.register(AddonFormAdvancementHandler::onSetForm);
    }

    private static IForm onSetForm(PlayerEntity player, IForm oldForm, IForm newForm, IForm finalForm) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && finalForm != null
                && ADDON_NAMESPACE.equals(finalForm.getFormID().getNamespace())) {
            SscAddon.ON_TRANSFORM_ADDON_FORM.trigger(serverPlayer, finalForm.getFormID());
        }
        // 原样返回，不改写玩家最终形态
        return finalForm;
    }
}
