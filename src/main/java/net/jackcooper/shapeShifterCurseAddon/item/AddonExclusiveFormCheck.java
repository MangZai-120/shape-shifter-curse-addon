package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.function.Predicate;

/**
 * SSCA 专属饰品与 SSC 增强饰品的形态门槛映射（jackcooper）。
 * 供 {@link AddonAccessoryGuard} 服务端兜底：登录宽容放行后，按此表判断
 * 「当前形态是否允许继续佩戴」；形态不符的自动卸下归还。
 * 返回 {@code null} 表示没有附属定义的形态门槛，不做兜底处理。
 */
final class AddonExclusiveFormCheck {

	private AddonExclusiveFormCheck() {}

	/**
	 * 判断佩戴者当前形态是否允许佩戴该饰品。
	 *
	 * @return true=允许 / false=不允许（应卸下）/ null=没有附属定义的形态限制
	 */
	static Boolean check(LivingEntity entity, AccessoryItem item) {
		Predicate<LivingEntity> p = predicateOf(item);
		if (p == null) return null;
		return p.test(entity);
	}

	private static Predicate<LivingEntity> predicateOf(AccessoryItem item) {
		// 增强版使用独立的 FormTrinket 类，不能通过普通版的 instanceof 识别。
		// 只限制这三个具体的物品/形态组合，普通版原有提示和佩戴行为保持不变。
		switch (Registries.ITEM.getId(item).toString()) {
			case "shape-shifter-curse:charm_of_night_crystal_plus":
				return e -> !FormUtils.isBatParasiticFruit(e);
			case "shape-shifter-curse:amulet_bracelet_plus":
				return e -> !FormUtils.isForm(e, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
			case "shape-shifter-curse:frost_pawglove_plus":
				return e -> !FormUtils.isSnowFoxSP(e);
			// 吸血蝙蝠没有脱手惩罚（走钻石挖掘爪路线），戴上无意义且易误导，直接禁戴
			case "shape-shifter-curse:attach_hook_plus":
				return e -> !FormUtils.isBatDesmodus(e);
			// 喷泉腰带＋只给美西螈SP用（用户决策）：其余形态一律禁戴
			case "shape-shifter-curse:fountain_belt_plus":
				return FormUtils::isAxolotlSP;
			// 朔望没有原版箭矢闪避可替换（走自家伤害否决闪避），禁戴颈环＋（用户决策）
			case "shape-shifter-curse:collar_of_whiskers_plus":
				return e -> !FormUtils.isForm(e, FormIdentifiers.OCELOT_NOVA);
			// 金沙岚无受击自伤凋零机制（不吃凋零增益），禁戴裹布＋（用户决策）
			case "shape-shifter-curse:withered_bandage_plus":
				return e -> !FormUtils.isForm(e, FormIdentifiers.GOLDEN_SANDSTORM_SP);
			// 跳蛛无蛛丝弹载体（近战毒牙系），纺锤无从触发，禁戴（用户决策）
			case "shape-shifter-curse:venom_spindle_plus":
				return e -> !FormUtils.isForm(e, FormIdentifiers.SPIDER_SALTICIDAE);
			default:
				break;
		}
		// 与各饰品 canEquip 的形态门槛一一对应（维护时同步更新）
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.ActiveCoralNecklaceItem) return FormUtils::isAxolotlSP;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.AnkhStoneItem) return FormUtils::isAnubisWolfSP;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.AnubisCrystalItem) return FormUtils::isAnubisWolfSP;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.BindingAnkletItem) return e -> FormUtils.isForm(e, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.BloodGarnetItem) return FormUtils::isBatDesmodus;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.BloodlustRingItem) return FormUtils::isBatDesmodus;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.BlueFireAmuletItem) return FormUtils::isFamiliarFoxForm;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.ErosionSandPrismItem) return FormUtils::isGoldenSandstormSP;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.FrostAmuletItem) return FormUtils::isSnowFoxSP;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.HumusRingItem) return FormUtils::isBatParasiticFruit;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.InvisibilityCloakItem) return FormUtils::isWildCatSP;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.LifesavingCatTailItem) return e -> FormUtils.isWildCatSP(e) || FormUtils.isForm(e, FormIdentifiers.WILD_CAT_NIGHTMARE);
		if (item instanceof NightmareRingItem) return e -> FormUtils.isForm(e, FormIdentifiers.WILD_CAT_NIGHTMARE);
		if (item instanceof FrostSpineCollarItem) return e -> FormUtils.isForm(e, FormIdentifiers.SNOW_FOX_FROSTSPINE);
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.NovaReviveNecklaceItem) return e -> FormUtils.isForm(e, FormIdentifiers.OCELOT_NOVA);
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.PhantomBellItem) return FormUtils::isFamiliarFoxForm;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.PortableFridgeItem) return FormUtils::isSnowFoxSP;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.PortableMoisturizerItem) return FormUtils::isMoistureDependent;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.TwinPodItem) return FormUtils::isBatParasiticFruit;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.WindSpiritStaminaNecklaceItem) return FormUtils::isOcelotSP;
		if (item instanceof net.jackcooper.shapeShifterCurseAddon.item.WitheredSandRingItem) return FormUtils::isGoldenSandstormSP;
		if (item instanceof SeaCrystalPendantItem) return FormUtils::isAxolotlFluorescent;
		return null; // 其余主包饰品没有附属定义的形态限制
	}
}
