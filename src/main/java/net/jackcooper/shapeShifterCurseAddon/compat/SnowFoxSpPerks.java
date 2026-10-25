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

/** Shared SP/Frostspine purchases, key-3 dive and complete interaction-distance multiplier. */
public final class SnowFoxSpPerks {
    public static final Identifier FROST_DIVE = new Identifier("my_addon", "perk_snow_fox_sp_frost_dive");
    public static final Identifier ELUSIVE_PAWS = new Identifier("my_addon", "perk_snow_fox_sp_elusive_paws");
    private static final Identifier ORIGINAL_FROST_DIVE = new Identifier("shape-shifter-curse", "perks/snow_fox_frost_dive");
    private static final Identifier ORIGINAL_ELUSIVE_PAWS = new Identifier("shape-shifter-curse", "perks/snow_fox_elusive_paws");

    private SnowFoxSpPerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && (FormIdentifiers.SNOW_FOX_SP.equals(form.getFormID())
                || FormIdentifiers.SNOW_FOX_FROSTSPINE.equals(form.getFormID()));
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !(RegPerks.P_SnowFoxFrostDive.equals(perk.getID())
                || RegPerks.P_SnowFoxElusivePaws.equals(perk.getID()))) return false;
        Identifier source = form.getFormLayer().getRight();
        for (Identifier power : perk.powerAdd) {
            Identifier effective = ORIGINAL_FROST_DIVE.equals(power) ? FROST_DIVE
                    : ORIGINAL_ELUSIVE_PAWS.equals(power) ? ELUSIVE_PAWS : power;
            FormUtils.applyPower(player, effective, source);
        }
        for (Identifier power : perk.powerRemove) FormUtils.removePower(player, power, source);
        return true;
    }

    public static boolean hasElusivePaws(PlayerEntity player) {
        if (player == null || !PowerTypeRegistry.contains(ELUSIVE_PAWS)
                || !isSupported(FormUtils.getPlayerForm(player))) return false;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(ELUSIVE_PAWS));
        return power != null && power.isActive();
    }
}
