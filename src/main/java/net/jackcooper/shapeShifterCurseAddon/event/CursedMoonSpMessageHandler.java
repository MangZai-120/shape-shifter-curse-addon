package net.jackcooper.shapeShifterCurseAddon.event;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.config.CommonConfig;
import net.onixary.shapeShifterCurseFabric.event.SSCEvent;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/**
 * 诅咒之月开始时，为 SP 形态（special_form flag）显示额外提示。
 * <p>
 * 由原来的 CursedMoonSpMessageMixin 迁移而来，改用原版官方事件 SSCEvent.CURSED_MOON_BEGIN，
 * 不再侵入 CursedMoon 类。
 * <p>
 * CURSED_MOON_BEGIN 在 CursedMoon.applyStartCursedMoonEffect 末尾触发，而该方法开头有
 * isCursedMoonApplied 幂等守卫，因此每次诅咒之月只会触发一次——等价于原 mixin 的
 * “!isCursedMoonApplied 首次” 判断，故这里无需再判 isCursedMoonApplied。
 */
public final class CursedMoonSpMessageHandler {

    private CursedMoonSpMessageHandler() {
    }

    /** 在附属主初始化时调用，注册诅咒之月开始事件监听。 */
    public static void register() {
        SSCEvent.CURSED_MOON_BEGIN.register(CursedMoonSpMessageHandler::onCursedMoonBegin);
    }

    private static void onCursedMoonBegin(PlayerEntity player) {
        // 判定放服务端（事件本身仅服务端触发，这里再次保险）
        if (!(player instanceof ServerPlayerEntity)) {
            return;
        }
        PlayerFormComponent formComp = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (formComp == null) {
            return;
        }
        IForm currentForm = formComp.nowForm;
        if (currentForm == null) {
            return;
        }
        // SP 形态（special_form flag）显示特殊提示。
        // 若管理员关闭了诅咒之月变形（enableCursedMoonTransform=false），所有玩家都不会变形，
        // 此时不再暗示“你形态特殊”，避免误导。
        if (currentForm.getFormFlag().contains("special_form")) {
            CommonConfig commonConfig = ShapeShifterCurseFabric.commonConfig;
            if (commonConfig != null && !commonConfig.enableCursedMoonTransform) {
                return;
            }
            player.sendMessage(Text.translatable("message.ssc_addon.cursed_moon_sp_special").formatted(Formatting.YELLOW), false);
        }
    }
}
