package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.function.Predicate;

/**
 * SSCA 专属饰品 → 形态门槛映射（jackcooper）。
 * 供 {@link AddonAccessoryGuard} 服务端兜底：登录宽容放行后，按此表判断
 * 「当前形态是否允许继续佩戴」；形态不符的自动卸下归还。
 * 返回 {@code null} 表示非专属饰品（无形态门槛），不做兜底处理。
 */
final class AddonExclusiveFormCheck {

	private AddonExclusiveFormCheck() {}

	/**
	 * 判断佩戴者当前形态是否允许佩戴该饰品。
	 *
	 * @return true=允许 / false=不允许（应卸下）/ null=非 SSCA 专属饰品
	 */
	static Boolean check(LivingEntity entity, AccessoryItem item) {
		Predicate<LivingEntity> p = predicateOf(item);
		if (p == null) return null;
		return p.test(entity);
	}

	private static Predicate<LivingEntity> predicateOf(AccessoryItem item) {
		// 与各饰品 canEquip 的形态门槛一一对应（维护时同步更新）
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.ActiveCoralNecklaceItem) return FormUtils::isAxolotlSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.AnkhStoneItem) return FormUtils::isAnubisWolfSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.AnubisCrystalItem) return FormUtils::isAnubisWolfSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.BindingAnkletItem) return e -> FormUtils.isForm(e, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.BloodGarnetItem) return FormUtils::isBatDesmodus;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.BloodlustRingItem) return FormUtils::isBatDesmodus;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.BlueFireAmuletItem) return FormUtils::isFamiliarFoxForm;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.ErosionSandPrismItem) return FormUtils::isGoldenSandstormSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.FrostAmuletItem) return FormUtils::isSnowFoxSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.HumusRingItem) return FormUtils::isBatParasiticFruit;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.InvisibilityCloakItem) return FormUtils::isWildCatSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.LifesavingCatTailItem) return FormUtils::isWildCatSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.NovaReviveNecklaceItem) return e -> FormUtils.isForm(e, FormIdentifiers.OCELOT_NOVA);
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.PhantomBellItem) return FormUtils::isFamiliarFoxForm;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableFridgeItem) return FormUtils::isSnowFoxSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableMoisturizerItem) return FormUtils::isMoistureDependent;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.TwinPodItem) return FormUtils::isBatParasiticFruit;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.WindSpiritStaminaNecklaceItem) return FormUtils::isOcelotSP;
		if (item instanceof net.onixary.shapeShifterCurseFabric.ssc_addon.item.WitheredSandRingItem) return FormUtils::isGoldenSandstormSP;
		if (item instanceof SeaCrystalPendantItem) return FormUtils::isAxolotlFluorescent;
		return null; // 非 SSCA 专属饰品（含主包饰品）
	}
}
