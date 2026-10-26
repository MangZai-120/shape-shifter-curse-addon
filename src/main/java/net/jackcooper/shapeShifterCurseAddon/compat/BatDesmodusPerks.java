package net.jackcooper.shapeShifterCurseAddon.compat;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** Shared bat purchases with Desmodus-only sonar and key-3 wingbeat. */
public final class BatDesmodusPerks {
    public static final Identifier ECHOLOCATION = new Identifier("my_addon", "perk_bat_desmodus_echolocation");
    public static final Identifier WINGBEAT = new Identifier("my_addon", "perk_bat_desmodus_wingbeat");
    private static final Identifier BASE_ECHOLOCATION = new Identifier("my_addon", "form_bat_desmodus_echolocation");
    private static final Identifier ORIGINAL_ECHOLOCATION = original("bat_echolocation");
    private static final Identifier ORIGINAL_WINGBEAT = original("bat_wingbeat");

    private BatDesmodusPerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && FormIdentifiers.BAT_DESMODUS.equals(form.getFormID());
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !RegPerks.getPerkTree(RegPerks.T_Bat3PerkTree).getAllPerks().contains(perk.getID())) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_Bat3PerkTree);
        // Reload order is not guaranteed: rank I must never undo an already purchased rank II.
        if (purchased != null && (RegPerks.P_BatPosture_1.equals(perk.getID()) && purchased.contains(RegPerks.P_BatPosture_2)
                || RegPerks.P_BatSunResistance_1.equals(perk.getID()) && purchased.contains(RegPerks.P_BatSunResistance_2))) return true;
        var source = form.getFormLayer().getRight();
        for (var removed : perk.powerRemove) FormUtils.removePower(player, removed, source);
        if (RegPerks.P_BatEcholocation.equals(perk.getID())) FormUtils.removePower(player, BASE_ECHOLOCATION, source);
        for (var added : perk.powerAdd) {
            var mapped = ORIGINAL_ECHOLOCATION.equals(added) ? ECHOLOCATION : ORIGINAL_WINGBEAT.equals(added) ? WINGBEAT : added;
            FormUtils.applyPower(player, mapped, source);
        }
        return true;
    }

    private static Identifier original(String path) { return new Identifier("shape-shifter-curse", "perks/" + path); }
}
