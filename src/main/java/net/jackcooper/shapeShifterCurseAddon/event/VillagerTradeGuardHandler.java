package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;

/**
 * 阻止特定形态与村民交易（由 SscAddonVillagerInteractionMixin 迁移到官方 {@link UseEntityCallback}）。
 * <p>进化使魔：任何右键村民都禁止交易；灵界之主（form_familiar_fox_sp）：潜行右键村民时禁止交易。
 * <p>返回 {@link ActionResult#FAIL} 取消交互（等价于原 mixin 在 interactMob 返回 PASS 拦下开交易），
 * 客户端与服务端均生效（客户端拦下即不发交互包，服务端兜底），与原 mixin 双端行为一致。
 */
public final class VillagerTradeGuardHandler {

	private static final Identifier FAMILIAR_FOX_SP = new Identifier("my_addon", "form_familiar_fox_sp");

	private VillagerTradeGuardHandler() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!(entity instanceof VillagerEntity)) {
				return ActionResult.PASS;
			}
			try {
				PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
				if (component == null || component.nowForm == null) {
					return ActionResult.PASS;
				}
				Identifier formId = component.nowForm.getFormID();
				// 进化使魔：任何右键村民都禁止交易
				if (FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(formId)) {
					return ActionResult.FAIL;
				}
				// 灵界之主：潜行右键村民时禁止交易
				if (player.isSneaking() && FAMILIAR_FOX_SP.equals(formId)) {
					return ActionResult.FAIL;
				}
			} catch (Exception ignored) {
				// 组件读取极端状态兜底：不影响正常交互
			}
			return ActionResult.PASS;
		});
	}
}
