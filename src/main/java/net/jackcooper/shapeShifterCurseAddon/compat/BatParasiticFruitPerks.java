package net.jackcooper.shapeShifterCurseAddon.compat;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** 寄生果蝠共用蝙蝠购买记录，独立加载夜间回声及技能3振翼。 */
public final class BatParasiticFruitPerks {
    public static final Identifier ECHOLOCATION = new Identifier("my_addon", "perk_bat_parasitic_fruit_echolocation");
    public static final Identifier WINGBEAT = new Identifier("my_addon", "perk_bat_parasitic_fruit_wingbeat");
    private static final Identifier ORIGINAL_ECHOLOCATION = original("bat_echolocation");
    private static final Identifier ORIGINAL_WINGBEAT = original("bat_wingbeat");

    private BatParasiticFruitPerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && FormIdentifiers.BAT_PARASITIC_FRUIT.equals(form.getFormID());
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !RegPerks.getPerkTree(RegPerks.T_Bat3PerkTree).getAllPerks().contains(perk.getID())) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_Bat3PerkTree);
        // 保存记录的加载顺序不固定，已购买Ⅱ级时不能再加载Ⅰ级覆盖它。
        if (purchased != null && (RegPerks.P_BatPosture_1.equals(perk.getID()) && purchased.contains(RegPerks.P_BatPosture_2)
                || RegPerks.P_BatSunResistance_1.equals(perk.getID()) && purchased.contains(RegPerks.P_BatSunResistance_2))) return true;
        var source = form.getFormLayer().getRight();
        for (var removed : perk.powerRemove) FormUtils.removePower(player, removed, source);
        for (var added : perk.powerAdd) {
            var mapped = ORIGINAL_ECHOLOCATION.equals(added) ? ECHOLOCATION : ORIGINAL_WINGBEAT.equals(added) ? WINGBEAT : added;
            FormUtils.applyPower(player, mapped, source);
        }
        return true;
    }

    private static Identifier original(String path) { return new Identifier("shape-shifter-curse", "perks/" + path); }
}
