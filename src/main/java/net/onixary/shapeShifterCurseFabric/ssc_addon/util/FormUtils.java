package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;

public class FormUtils {
    private FormUtils() {
    }

    public static PlayerFormBase getCurrentForm(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
            if (component != null) {
                return component.getCurrentForm();
            }
        }
        return null;
    }

    public static boolean hasForm(LivingEntity entity) {
        PlayerFormBase currentForm = getCurrentForm(entity);
        return currentForm != null && currentForm.FormID != null;
    }

    public static boolean isForm(LivingEntity entity, Identifier formId) {
        PlayerFormBase currentForm = getCurrentForm(entity);
        return currentForm != null && currentForm.FormID != null && currentForm.FormID.equals(formId);
    }

    public static boolean isAnyForm(LivingEntity entity, Identifier... formIds) {
        PlayerFormBase currentForm = getCurrentForm(entity);
        if (currentForm == null || currentForm.FormID == null) {
            return false;
        }
        for (Identifier formId : formIds) {
            if (currentForm.FormID.equals(formId)) {
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

    public static boolean isSnowFoxSP(LivingEntity entity) {
        return isForm(entity, FormIdentifiers.SNOW_FOX_SP);
    }

    public static boolean isAllaySP(LivingEntity entity) {
        return isForm(entity, FormIdentifiers.ALLAY_SP);
    }

    public static boolean isWildCatSP(LivingEntity entity) {
        return isForm(entity, FormIdentifiers.WILD_CAT_SP);
    }

    public static boolean isAxolotlSP(LivingEntity entity) {
        return isForm(entity, FormIdentifiers.AXOLOTL_SP);
    }

    public static boolean isFamiliarFoxForm(LivingEntity entity) {
        return isAnyForm(entity, FormIdentifiers.FAMILIAR_FOX_SP, FormIdentifiers.FAMILIAR_FOX_RED);
    }
}
