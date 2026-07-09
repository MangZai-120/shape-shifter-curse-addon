package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

public class FormUtils {
	// 通过实体类型ID判断，避免在Mixin类中直接引用原版模组类导致类加载级联
	private static final Identifier TRANSFORMATIVE_WOLF_TYPE_ID = new Identifier("shape-shifter-curse", "t_wolf");

	private FormUtils() {
	}

	public static IForm getCurrentForm(LivingEntity entity) {
		if (entity instanceof PlayerEntity player) {
			PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
			if (component != null) {
				return component.nowForm;
			}
		}
		return null;
	}

	public static boolean hasForm(LivingEntity entity) {
		IForm currentForm = getCurrentForm(entity);
		return currentForm != null && currentForm.getFormID() != null;
	}

	public static boolean isForm(LivingEntity entity, Identifier formId) {
		IForm currentForm = getCurrentForm(entity);
		return currentForm != null && currentForm.getFormID() != null && currentForm.getFormID().equals(formId);
	}

	public static boolean isAnyForm(LivingEntity entity, Identifier... formIds) {
		IForm currentForm = getCurrentForm(entity);
		if (currentForm == null || currentForm.getFormID() == null) {
			return false;
		}
		for (Identifier formId : formIds) {
			if (currentForm.getFormID().equals(formId)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isFamiliarFoxSP(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.FAMILIAR_FOX_SP);
	}

	public static boolean isFamiliarFoxRed(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.FAMILIAR_FOX_RED);
	}

	/** 使魔系形态（使魔SP / 进化使魔 / 契灵 / 红堕落使魔）。 */
	public static boolean isFamiliarFoxFamily(LivingEntity entity) {
		return isAnyForm(entity, FormIdentifiers.FAMILIAR_FOX_SP, FormIdentifiers.UPGRADE_FAMILIAR_FOX,
				FormIdentifiers.FAMILIAR_FOX_RED, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
	}

	public static boolean isSnowFoxSP(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.SNOW_FOX_SP);
	}

	public static boolean isAllaySP(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.ALLAY_SP);
	}

	public static boolean isWildCatSP(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.WILD_CAT_SP);
	}

	public static boolean isOcelotSP(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.OCELOT_SP);
	}

	public static boolean isAxolotlSP(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.AXOLOTL_SP);
	}

	/** 荧光幼灵（Axolotl Fluorescent）形态判断（含阿澪 Aling，技能一致）。 */
	public static boolean isAxolotlFluorescent(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.AXOLOTL_FLUORESCENT) || isForm(entity, FormIdentifiers.AXOLOTL_ALING);
	}

	/** 需要保湿的形态判断（SP 美西螈 或 荧光幼灵），用于加湿器生效判定。 */
	public static boolean isMoistureDependent(LivingEntity entity) {
		return isAxolotlSP(entity) || isAxolotlFluorescent(entity);
	}

	public static boolean isAnubisWolfSP(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.ANUBIS_WOLF_SP);
	}

	public static boolean isGoldenSandstormSP(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.GOLDEN_SANDSTORM_SP);
	}

	public static boolean isFamiliarFoxForm(LivingEntity entity) {
		return isAnyForm(entity, FormIdentifiers.FAMILIAR_FOX_SP, FormIdentifiers.FAMILIAR_FOX_RED);
	}

	public static boolean isBatDesmodus(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.BAT_DESMODUS);
	}

	public static boolean isBatParasiticFruit(LivingEntity entity) {
		return isForm(entity, FormIdentifiers.BAT_PARASITIC_FRUIT);
	}

	/**
	 * 判断实体是否为咒文胡狼（通过注册表ID判断，避免直接引用原版类）
	 */
	public static boolean isTransformativeWolf(LivingEntity entity) {
		return Registries.ENTITY_TYPE.getId(entity.getType()).equals(TRANSFORMATIVE_WOLF_TYPE_ID);
	}
}
