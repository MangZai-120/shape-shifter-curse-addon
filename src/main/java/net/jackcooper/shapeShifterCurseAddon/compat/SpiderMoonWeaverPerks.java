package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.state.SpiderMoonWeaverStateComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** 月织蛛共用原蜘蛛购买记录，仅替换两种蓄力和悬丝诊脉的实现。 */
public final class SpiderMoonWeaverPerks {
    public static final Identifier BRIDGE_WEAVER = addon("perk_spider_moon_weaver_bridge_weaver");
    public static final Identifier RAPID_SPINNER = addon("perk_spider_moon_weaver_rapid_spinner");
    public static final Identifier PERCEPTUAL_WEB = addon("perk_spider_moon_weaver_perceptual_web");

    private SpiderMoonWeaverPerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && FormIdentifiers.SPIDER_MOON_WEAVER.equals(form.getFormID());
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !RegPerks.getPerkTree(RegPerks.T_Spider3PerkTree).getAllPerks().contains(perk.getID())) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_Spider3PerkTree);
        // 重新加载时无论购买记录顺序如何，Ⅰ级都不能覆盖已购买的Ⅱ级。
        if (purchased != null && (RegPerks.P_SpiderCocoonDigestion_1.equals(perk.getID()) && purchased.contains(RegPerks.P_SpiderCocoonDigestion_2)
                || RegPerks.P_SpiderSilkSecretion_1.equals(perk.getID()) && purchased.contains(RegPerks.P_SpiderSilkSecretion_2))) return true;
        var source = form.getFormLayer().getRight();
        for (var removed : perk.powerRemove) FormUtils.removePower(player, removed, source);
        for (var added : perk.powerAdd) {
            Identifier mapped = switch (added.toString()) {
                case "shape-shifter-curse:perks/spider_bridge_weaver" -> BRIDGE_WEAVER;
                case "shape-shifter-curse:perks/spider_rapid_spinner" -> RAPID_SPINNER;
                case "shape-shifter-curse:perks/spider_perceptual_web" -> PERCEPTUAL_WEB;
                default -> added;
            };
            FormUtils.applyPower(player, mapped, source);
        }
        return true;
    }

    public static boolean acceleratesCharge(PlayerEntity player, int mode) {
        if (!isSupported(FormUtils.getPlayerForm(player))) return false;
        Identifier power = mode == SpiderMoonWeaverStateComponent.MODE_ATTACK ? RAPID_SPINNER : BRIDGE_WEAVER;
        return PowerTypeRegistry.contains(power) && PowerHolderComponent.KEY.get(player).hasPower(PowerTypeRegistry.get(power));
    }

    private static Identifier addon(String path) { return new Identifier("my_addon", path); }
}
