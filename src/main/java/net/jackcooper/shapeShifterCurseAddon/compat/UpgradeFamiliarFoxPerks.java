package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** 进化使魔共用 SSC 购买记录；每项效果仍由对应进化天赋动态控制。 */
public final class UpgradeFamiliarFoxPerks {
    public static final Identifier SIPHON_1 = mapped("familiar_fox_siphon_1");
    public static final Identifier SIPHON_2 = mapped("familiar_fox_siphon_2");
    public static final Identifier FIELD_1 = mapped("familiar_fox_permeable_field_1");
    public static final Identifier FIELD_2 = mapped("familiar_fox_permeable_field_2");
    public static final Identifier BASE_EFFECT_IMMUNITY = new Identifier("my_addon", "form_upgrade_familiar_fox_buff_immunity_effect_immunity");

    private UpgradeFamiliarFoxPerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(form.getFormID());
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !RegPerks.getPerkTree(RegPerks.T_FamiliarFox3PerkTree).getAllPerks().contains(perk.getID())) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_FamiliarFox3PerkTree);
        // 保留两级购买记录；即使旧存档逆序保存，也只加载二级效果。
        if (purchased != null && (RegPerks.P_FamiliarFoxSiphon_1.equals(perk.getID()) && purchased.contains(RegPerks.P_FamiliarFoxSiphon_2)
                || RegPerks.P_FamiliarFoxManaCapacity_1.equals(perk.getID()) && purchased.contains(RegPerks.P_FamiliarFoxManaCapacity_2)
                || RegPerks.P_FamiliarFoxPermeableField_1.equals(perk.getID()) && purchased.contains(RegPerks.P_FamiliarFoxPermeableField_2))) return true;
        var source = form.getFormLayer().getRight();
        // 只移除原免疫子 power：保留同组的火焰免疫、火中耗魔和天赋容量加成。
        if (RegPerks.P_FamiliarFoxPermeableField_1.equals(perk.getID()) || RegPerks.P_FamiliarFoxPermeableField_2.equals(perk.getID())) {
            FormUtils.removePower(player, BASE_EFFECT_IMMUNITY, source);
        }
        // 容量两级共用 modifier ID，必须先移除低级再添加高级。
        for (Identifier removed : perk.powerRemove) {
            if (removed.equals(new Identifier("shape-shifter-curse", "form_familiar_fox_3_no_buff_effect"))) continue;
            FormUtils.removePower(player, effectivePower(removed), source);
        }
        for (Identifier added : perk.powerAdd) FormUtils.applyPower(player, effectivePower(added), source);
        return true;
    }

    public static boolean enabled(PlayerEntity player, Identifier id) {
        if (player == null || !isSupported(FormUtils.getPlayerForm(player)) || !PowerTypeRegistry.contains(id)) return false;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(id));
        return power != null && power.isActive();
    }

    public static boolean preservesEffectDuration(PlayerEntity player, StatusEffect effect) {
        boolean second = enabled(player, FIELD_2);
        if (!second && !enabled(player, FIELD_1)) return false;
        return effect == StatusEffects.SPEED || effect == StatusEffects.HASTE || effect == StatusEffects.INVISIBILITY
                || second && (effect == StatusEffects.STRENGTH || effect == StatusEffects.WATER_BREATHING || effect == StatusEffects.NIGHT_VISION);
    }

    public static boolean canUsePotionCharm(PlayerEntity player) {
        return isSupported(FormUtils.getPlayerForm(player)) && RegEvolutionComponent.EVOLUTION.get(player).isUnlocked("alchemy");
    }

    public static Identifier mapped(String nativePerkPath) {
        return new Identifier("my_addon", "perk_upgrade_" + nativePerkPath);
    }

    private static Identifier effectivePower(Identifier nativePower) {
        return nativePower.getNamespace().equals("shape-shifter-curse") && nativePower.getPath().startsWith("perks/familiar_fox_")
                ? mapped(nativePower.getPath().substring("perks/".length())) : nativePower;
    }
}
