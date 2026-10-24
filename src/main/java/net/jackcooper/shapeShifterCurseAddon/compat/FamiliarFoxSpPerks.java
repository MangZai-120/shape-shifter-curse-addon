package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.additional_power.PerkActions;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** Shared SP/Red/Mancianima effects over the purchased SSC familiar tree. */
public final class FamiliarFoxSpPerks {
    public static final Identifier SIPHON_1 = addon("perk_familiar_fox_sp_siphon_1");
    public static final Identifier SIPHON_2 = addon("perk_familiar_fox_sp_siphon_2");
    public static final Identifier FIELD_1 = addon("perk_familiar_fox_sp_permeable_field_1");
    public static final Identifier FIELD_2 = addon("perk_familiar_fox_sp_permeable_field_2");
    public static final Identifier BASE_IMMUNITY = addon("form_familiar_fox_sp_no_buff_effect");
    public static final Identifier MANCIANIMA_IMMUNITY = addon("form_familiar_fox_mancianima_no_buff_effect");
    private static final Identifier ORIGINAL_FORM = original("familiar_fox_3");

    private FamiliarFoxSpPerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && (FormIdentifiers.FAMILIAR_FOX_SP.equals(form.getFormID())
                || FormIdentifiers.FAMILIAR_FOX_RED.equals(form.getFormID())
                || FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID()));
    }

    public static Identifier baseImmunity(IForm form) {
        return form != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID())
                ? MANCIANIMA_IMMUNITY : BASE_IMMUNITY;
    }

    public static Identifier effectivePower(IForm form, Identifier power) {
        if (!isSupported(form)) return power;
        if (original("perks/familiar_fox_siphon_1").equals(power)) return SIPHON_1;
        if (original("perks/familiar_fox_siphon_2").equals(power)) return SIPHON_2;
        if (original("perks/familiar_fox_permeable_field_1").equals(power)) return FIELD_1;
        if (original("perks/familiar_fox_permeable_field_2").equals(power)) return FIELD_2;
        if (original("form_familiar_fox_3_no_buff_effect").equals(power)) return baseImmunity(form);
        return power;
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !(RegPerks.P_FamiliarFoxSiphon_1.equals(perk.getID())
                || RegPerks.P_FamiliarFoxSiphon_2.equals(perk.getID())
                || RegPerks.P_FamiliarFoxPermeableField_1.equals(perk.getID())
                || RegPerks.P_FamiliarFoxPermeableField_2.equals(perk.getID())
                || RegPerks.P_FamiliarFoxManaCapacity_1.equals(perk.getID())
                || RegPerks.P_FamiliarFoxManaCapacity_2.equals(perk.getID()))) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_FamiliarFox3PerkTree);
        // A save/admin grant may list rank I after rank II. Keep the rank-I purchase, not its replaced effect.
        if (purchased != null && (RegPerks.P_FamiliarFoxSiphon_1.equals(perk.getID()) && purchased.contains(RegPerks.P_FamiliarFoxSiphon_2)
                || RegPerks.P_FamiliarFoxPermeableField_1.equals(perk.getID()) && purchased.contains(RegPerks.P_FamiliarFoxPermeableField_2)
                || RegPerks.P_FamiliarFoxManaCapacity_1.equals(perk.getID()) && purchased.contains(RegPerks.P_FamiliarFoxManaCapacity_2))) return true;
        Identifier source = form.getFormLayer().getRight();
        if (FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID())
                && (RegPerks.P_FamiliarFoxPermeableField_1.equals(perk.getID())
                || RegPerks.P_FamiliarFoxPermeableField_2.equals(perk.getID()))) {
            // Mancianima has both the original and addon immunity powers in its origin.
            FormUtils.removePower(player, original("form_familiar_fox_3_no_buff_effect"), source);
        }
        // Capacity ranks share one modifier ID: removing rank I after adding II would erase II as well.
        for (Identifier power : perk.powerRemove) FormUtils.removePower(player, effectivePower(form, power), source);
        for (Identifier power : perk.powerAdd) FormUtils.applyPower(player, effectivePower(form, power), source);
        return true;
    }

    public static int siphonAmount(PlayerEntity player) {
        if (player == null || !isSupported(FormUtils.getPlayerForm(player))) return 0;
        return hasPower(player, SIPHON_2) ? 4 : hasPower(player, SIPHON_1) ? 2 : 0;
    }

    /** Called once from vanilla's final health-damage path, after armor/resistance/absorption. */
    public static void onHealthDamage(LivingEntity victim, DamageSource source, float damage) {
        // DamageTracker runs before setHealth: overkill cannot count more health than the victim has left.
        if (!(Math.min(damage, victim.getHealth()) > 0.1f) || victim.getWorld().isClient
                || !(source.getAttacker() instanceof ServerPlayerEntity player)) return;
        int refund = siphonAmount(player);
        if (refund > 0 && PerkActions.isEnemy(player, victim)) ManaUtils.gainPlayerMana(player, refund);
    }

    public static boolean preservesEffectDuration(LivingEntity entity, StatusEffect effect) {
        if (!(entity instanceof PlayerEntity player)) return false;
        boolean second = hasPower(player, FIELD_2);
        if (!second && !hasPower(player, FIELD_1)) return false;
        if (!isSupported(FormUtils.getPlayerForm(player))) return false;
        return effect == StatusEffects.SPEED || effect == StatusEffects.HASTE || effect == StatusEffects.INVISIBILITY
                || second && (effect == StatusEffects.STRENGTH || effect == StatusEffects.WATER_BREATHING
                        || effect == StatusEffects.NIGHT_VISION);
    }

    public static StatusEffectInstance levelOneWithOriginalDuration(StatusEffectInstance effect) {
        return new StatusEffectInstance(effect.getEffectType(), effect.getDuration(), 0, effect.isAmbient(),
                effect.shouldShowParticles(), effect.shouldShowIcon(), null, effect.getFactorCalculationData());
    }

    public static boolean canUsePotionCharm(Identifier form) {
        return FormIdentifiers.FAMILIAR_FOX_SP.equals(form) || FormIdentifiers.FAMILIAR_FOX_RED.equals(form)
                || FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form)
                || ORIGINAL_FORM.equals(form);
    }

    private static boolean hasPower(PlayerEntity player, Identifier id) {
        if (!PowerTypeRegistry.contains(id)) return false;
        var component = PowerHolderComponent.KEY.maybeGet(player).orElse(null);
        if (component == null) return false;
        var power = component.getPower(PowerTypeRegistry.get(id));
        return power != null && power.isActive();
    }

    private static Identifier addon(String path) { return new Identifier("my_addon", path); }
    private static Identifier original(String path) { return new Identifier("shape-shifter-curse", path); }
}
