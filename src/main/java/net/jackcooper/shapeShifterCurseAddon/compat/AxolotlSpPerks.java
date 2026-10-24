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

/** Per-form effects over the original tree's saved unlocks. SSC's registry stays intact. */
public final class AxolotlSpPerks {
    public static final Identifier WATER_MAGIC = new Identifier("my_addon", "perk_axolotl_sp_water_magic_efficiency");
    public static final Identifier WATER_MAGIC_ATTACK = new Identifier("my_addon", "perk_axolotl_sp_water_magic_efficiency_attack");
    public static final Identifier TIDAL_PULL = new Identifier("my_addon", "perk_axolotl_sp_tidal_pull");
    private static final Identifier ORIGINAL_WATER_MAGIC = new Identifier("shape-shifter-curse", "perks/axolotl_water_magic_efficiency");
    private static final Identifier ORIGINAL_TIDAL_PULL = new Identifier("shape-shifter-curse", "perks/axolotl_tidal_pull");

    private AxolotlSpPerks() { }

    public static boolean isSp(IForm form) {
        return form != null && FormIdentifiers.AXOLOTL_SP.equals(form.getFormID());
    }

    public static Identifier effectivePower(IForm form, Identifier power) {
        if (!isSp(form)) return power;
        if (ORIGINAL_WATER_MAGIC.equals(power)) return WATER_MAGIC;
        if (ORIGINAL_TIDAL_PULL.equals(power)) return TIDAL_PULL;
        return power;
    }

    /** Called only for the two mapped Perks; retain SSC's normal add/remove ordering. */
    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSp(form) || !(RegPerks.P_AxolotlWaterMagicEfficiency.equals(perk.getID())
                || RegPerks.P_AxolotlTidalPull.equals(perk.getID()))) return false;
        Identifier source = form.getFormLayer().getRight();
        for (Identifier power : perk.powerAdd) FormUtils.applyPower(player, effectivePower(form, power), source);
        for (Identifier power : perk.powerRemove) FormUtils.removePower(player, power, source);
        return true;
    }

    public static boolean hasWaterMagicEfficiency(PlayerEntity player) {
        if (player == null || !isSp(FormUtils.getPlayerForm(player)) || !PowerTypeRegistry.contains(WATER_MAGIC_ATTACK)) return false;
        var type = PowerTypeRegistry.get(WATER_MAGIC_ATTACK);
        var holder = PowerHolderComponent.KEY.get(player);
        var power = holder.getPower(type);
        return power != null && power.isActive();
    }

    /** Charge air is integral: round the cumulative 60% bill up once, never each pulse. */
    public static int vortexAirCost(int baseTotal, boolean discounted) {
        if (baseTotal <= 0) return 0;
        return discounted ? (int) (((long) baseTotal * 3 + 4) / 5) : baseTotal;
    }
}
