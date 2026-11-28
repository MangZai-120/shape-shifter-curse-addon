package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionManager;
import net.jackcooper.shapeShifterCurseAddon.util.AdvancementUtils;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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
    private static final String ADDON_NAMESPACE = "ssc_addon";
    private static final Identifier EVOLUTION_FORM = new Identifier("ssc_addon", "evolution_form");
    private static final Identifier FOX_ADVANCEMENT = new Identifier(ADDON_NAMESPACE, "on_transform_upgrade_familiar_fox");
    private static final Identifier AXOLOTL_ADVANCEMENT = new Identifier(ADDON_NAMESPACE, "on_transform_upgrade_axolotl");

    private AddonFormAdvancementHandler() {
    }

    /** 在附属主初始化时调用，注册变形设定形态事件监听。 */
    public static void register() {
        SSCEvent.TRANSFORM_MANAGER_SET_FORM.register(AddonFormAdvancementHandler::onSetForm);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> restoreEvolutionAdvancement(handler.player));
    }

    private static void restoreEvolutionAdvancement(ServerPlayerEntity player) {
        if (hasAdvancement(player, EVOLUTION_FORM)) return;
        var evolution = EvolutionManager.get(player);
        // 旧存档可能已经转职或离开路线。两个首次变身子成就会保留。
        if ((evolution.hasStarted() && ("familiar_fox".equals(evolution.getRoute()) || "axolotl".equals(evolution.getRoute())))
                || hasAdvancement(player, FOX_ADVANCEMENT) || hasAdvancement(player, AXOLOTL_ADVANCEMENT)) {
            AdvancementUtils.grant(player, EVOLUTION_FORM);
        }
    }

    private static boolean hasAdvancement(ServerPlayerEntity player, Identifier id) {
        var advancement = player.getServerWorld().getServer().getAdvancementLoader().get(id);
        return advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone();
    }

    private static IForm onSetForm(PlayerEntity player, IForm oldForm, IForm newForm, IForm finalForm) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && finalForm != null
                && ADDON_NAMESPACE.equals(finalForm.getFormID().getNamespace())) {
            Identifier formId = finalForm.getFormID();
            if (FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(formId) || FormIdentifiers.UPGRADE_AXOLOTL.equals(formId)) {
                AdvancementUtils.grant(serverPlayer, EVOLUTION_FORM);
            }
            SscAddon.ON_TRANSFORM_ADDON_FORM.trigger(serverPlayer, formId);
        }
        // 原样返回，不改写玩家最终形态
        return finalForm;
    }
}
