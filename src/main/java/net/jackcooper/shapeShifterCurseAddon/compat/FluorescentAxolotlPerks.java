package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Fluorescent/Aling effects over the same purchased SSC axolotl Perks. */
public final class FluorescentAxolotlPerks {
    public static final Identifier WATER_MAGIC = new Identifier("my_addon", "perk_axolotl_fluorescent_water_magic_efficiency");
    public static final Identifier WATER_MAGIC_ATTACK = new Identifier("my_addon", "perk_axolotl_fluorescent_water_magic_efficiency_attack");
    public static final Identifier DRY_TOLERANCE = new Identifier("my_addon", "perk_axolotl_fluorescent_dry_tolerance");
    public static final Identifier MOISTURE_RETURN = new Identifier("my_addon", "perk_axolotl_fluorescent_moisture_return");
    public static final Identifier LASER_REFUND = new Identifier("my_addon", "perk_axolotl_fluorescent_moisture_return_laser");
    public static final Identifier TIDAL_REFUND = new Identifier("my_addon", "perk_axolotl_fluorescent_moisture_return_tidal");
    private static final Identifier ORIGINAL_WATER_MAGIC = new Identifier("shape-shifter-curse", "perks/axolotl_water_magic_efficiency");
    private static final Identifier ORIGINAL_TIDAL_PULL = new Identifier("shape-shifter-curse", "perks/axolotl_tidal_pull");
    private static final Identifier ORIGINAL_DRY_TOLERANCE = new Identifier("shape-shifter-curse", "perks/axolotl_dry_tolerance");

    private FluorescentAxolotlPerks() { }

    public static boolean isFluorescent(IForm form) {
        return form != null && (FormIdentifiers.AXOLOTL_FLUORESCENT.equals(form.getFormID())
                || FormIdentifiers.AXOLOTL_ALING.equals(form.getFormID()));
    }

    public static boolean hasWaterMagicEfficiency(PlayerEntity player) {
        if (player == null || !isFluorescent(FormUtils.getPlayerForm(player))
                || !PowerTypeRegistry.contains(WATER_MAGIC_ATTACK)) return false;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(WATER_MAGIC_ATTACK));
        return power != null && power.isActive();
    }

    public static Identifier effectivePower(IForm form, Identifier power) {
        if (!isFluorescent(form)) return power;
        if (ORIGINAL_WATER_MAGIC.equals(power)) return WATER_MAGIC;
        if (ORIGINAL_TIDAL_PULL.equals(power)) return AxolotlSpPerks.TIDAL_PULL;
        if (ORIGINAL_DRY_TOLERANCE.equals(power)) return DRY_TOLERANCE;
        return power;
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        boolean moistureReturn = RegPerks.P_AxolotlMoistureReturn_1.equals(perk.getID())
                || RegPerks.P_AxolotlMoistureReturn_2.equals(perk.getID());
        if (!isFluorescent(form) || !(RegPerks.P_AxolotlWaterMagicEfficiency.equals(perk.getID())
                || RegPerks.P_AxolotlTidalPull.equals(perk.getID())
                || RegPerks.P_AxolotlDryTolerance.equals(perk.getID()) || moistureReturn)) return false;
        Identifier source = form.getFormLayer().getRight();
        for (Identifier power : perk.powerAdd) FormUtils.applyPower(player, effectivePower(form, power), source);
        // Dry tolerance is an unconditional +6 on top of every original moisture-health tier.
        if (!RegPerks.P_AxolotlDryTolerance.equals(perk.getID())) {
            for (Identifier power : perk.powerRemove) FormUtils.removePower(player, effectivePower(form, power), source);
        }
        if (moistureReturn) FormUtils.applyPower(player, MOISTURE_RETURN, source);
        return true;
    }

    public static int laserChargeTicks(int baseTicks, boolean efficient) {
        return Math.max(1, baseTicks - (efficient ? 40 : 0));
    }

    public static double tidalFlightSpeed(double baseSpeed, boolean efficient) {
        return efficient ? baseSpeed * 1.25 : baseSpeed;
    }

    public static float enhancedLaserDamage(float baseDamage, boolean efficient) {
        return efficient ? baseDamage * 1.25f : baseDamage;
    }

    public static boolean onLaserHit(PlayerEntity player) {
        return restoreMoisture(player, LASER_REFUND, 10);
    }

    public static boolean onTidalHit(PlayerEntity player) {
        return restoreMoisture(player, TIDAL_REFUND, 3);
    }

    private static boolean restoreMoisture(PlayerEntity player, Identifier timer, int amount) {
        if (player == null || player.getWorld().isClient || !player.isAlive()
                || !isFluorescent(FormUtils.getPlayerForm(player)) || !PowerTypeRegistry.contains(timer)) return false;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(timer));
        if (!(power instanceof CooldownPower cooldown) || !cooldown.canUse()) return false;
        // One timer per player/skill across all targets, ticks and both Perk ranks. Apoli persists it in NBT.
        cooldown.use();
        player.setAir(Math.min(player.getMaxAir(), player.getAir() + amount));
        return true;
    }
}
