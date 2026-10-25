package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** Wind Spirit effects over the original ocelot purchase records. */
public final class WindSpiritPerks {
    public static final Identifier METABOLISM_1 = addon("perk_wind_spirit_metabolism_1");
    public static final Identifier METABOLISM_2 = addon("perk_wind_spirit_metabolism_2");
    public static final Identifier OVERLOAD = addon("perk_wind_spirit_metabolic_overload");
    public static final Identifier LONG_POUNCE = addon("perk_wind_spirit_long_pounce");

    private WindSpiritPerks() { }

    public static boolean isWindSpirit(IForm form) {
        return form != null && FormIdentifiers.OCELOT_SP.equals(form.getFormID());
    }

    public static Identifier effectivePower(Identifier power) {
        if (original("perks/ocelot_metabolism_1").equals(power)) return METABOLISM_1;
        if (original("perks/ocelot_metabolism_2").equals(power)) return METABOLISM_2;
        if (original("perks/ocelot_metabolic_overload").equals(power)) return OVERLOAD;
        if (original("perks/ocelot_long_pounce").equals(power)) return LONG_POUNCE;
        return power;
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isWindSpirit(form)) return false;
        Identifier id = perk.getID();
        if (!(RegPerks.P_OcelotArmor_1.equals(id) || RegPerks.P_OcelotArmor_2.equals(id)
                || RegPerks.P_OcelotMetabolism_1.equals(id) || RegPerks.P_OcelotMetabolism_2.equals(id)
                || RegPerks.P_OcelotMetabolicOverload.equals(id) || RegPerks.P_OcelotLongPounce.equals(id))) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_Ocelot3PerkTree);
        if (purchased != null && (RegPerks.P_OcelotArmor_1.equals(id) && purchased.contains(RegPerks.P_OcelotArmor_2)
                || RegPerks.P_OcelotMetabolism_1.equals(id) && purchased.contains(RegPerks.P_OcelotMetabolism_2))) return true;
        Identifier source = form.getFormLayer().getRight();
        for (Identifier power : perk.powerRemove) FormUtils.removePower(player, effectivePower(power), source);
        if (RegPerks.P_OcelotMetabolism_2.equals(id)) FormUtils.removePower(player, METABOLISM_1, source);
        for (Identifier power : perk.powerAdd) FormUtils.applyPower(player, effectivePower(power), source);
        ((WindSpiritHungerState) player.getHungerManager()).ssca$setOwner(player);
        return true;
    }

    public static int hungerDiscount(PlayerEntity player) {
        if (player == null || !isWindSpirit(FormUtils.getPlayerForm(player))) return 0;
        if (has(player, METABOLISM_2)) return 25;
        return has(player, METABOLISM_1) ? 15 : 0;
    }

    public static boolean hasLongPounce(PlayerEntity player) {
        return player != null && isWindSpirit(FormUtils.getPlayerForm(player)) && has(player, LONG_POUNCE);
    }

    private static boolean has(PlayerEntity player, Identifier id) {
        if (!PowerTypeRegistry.contains(id)) return false;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(id));
        return power != null && power.isActive();
    }

    public static double dashRange(double base, boolean enhanced) { return enhanced ? base * 1.5 : base; }
    public static double landingRadius(double base, boolean enhanced) { return enhanced ? base * 4.0 / 3.0 : base; }
    public static float landingDamage(float base, boolean enhanced) { return enhanced ? base * 4.0f / 3.0f : base; }

    private static Identifier addon(String path) { return new Identifier("my_addon", path); }
    private static Identifier original(String path) { return new Identifier("shape-shifter-curse", path); }
}
