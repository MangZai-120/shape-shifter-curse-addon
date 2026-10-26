package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** 进化美西螈共享原购买记录；强化现有能力，原进化天赋继续决定技能是否开放。 */
public final class UpgradeAxolotlPerks {
    public static final Identifier VEGETATION = addon("perk_upgrade_axolotl_vegetation");
    public static final Identifier DRY_TOLERANCE = addon("perk_upgrade_axolotl_dry_tolerance");
    public static final Identifier PROPULSION = addon("perk_upgrade_axolotl_propulsion_efficiency");
    public static final Identifier WAVE = addon("perk_upgrade_axolotl_wave_efficiency");
    public static final Identifier WATER_MAGIC = addon("perk_upgrade_axolotl_water_magic_efficiency");
    public static final Identifier TIDAL_PULL = addon("perk_upgrade_axolotl_tidal_pull");
    private static final Identifier BASE_DRAIN = addon("form_upgrade_axolotl_moisture_drain");
    private static final Identifier BASE_PROPULSION = addon("form_upgrade_axolotl_sprint_jump");
    private static final int SPEAR_AIR_REDUCTION = 8;
    private static final double LAND_AIR_MULTIPLIER = 0.5;
    private static final BalanceReader BAL = new BalanceReader("abilities.upgrade_axolotl_perks");

    private UpgradeAxolotlPerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && FormIdentifiers.UPGRADE_AXOLOTL.equals(form.getFormID());
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !RegPerks.getPerkTree(RegPerks.T_Axolotl3PerkTree).getAllPerks().contains(perk.getID())) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_Axolotl3PerkTree);
        if (RegPerks.P_AxolotlMoistureReturn_1.equals(perk.getID()) && purchased != null
                && purchased.contains(RegPerks.P_AxolotlMoistureReturn_2)) return true;
        var source = form.getFormLayer().getRight();
        for (var removed : perk.powerRemove) FormUtils.removePower(player, removed, source);
        if (RegPerks.P_AxolotlVegetation.equals(perk.getID())) FormUtils.removePower(player, BASE_DRAIN, source);
        if (RegPerks.P_AxolotlPropulsionEfficiency.equals(perk.getID())) FormUtils.removePower(player, BASE_PROPULSION, source);
        for (var added : perk.powerAdd) {
            Identifier mapped = switch (added.getPath()) {
                case "perks/axolotl_vegetation" -> VEGETATION;
                case "perks/axolotl_dry_tolerance" -> DRY_TOLERANCE;
                case "perks/axolotl_propulsion_efficiency" -> PROPULSION;
                case "perks/axolotl_wave_efficiency" -> WAVE;
                case "perks/axolotl_water_magic_efficiency" -> WATER_MAGIC;
                case "perks/axolotl_tidal_pull" -> TIDAL_PULL;
                default -> added;
            };
            FormUtils.applyPower(player, mapped, source);
        }
        return true;
    }

    public static boolean enabled(PlayerEntity player, Identifier id) {
        if (player == null || !isSupported(FormUtils.getPlayerForm(player)) || !PowerTypeRegistry.contains(id)) return false;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(id));
        return power != null && power.isActive();
    }

    /** 与服务端扣费及客户端可用性判定共用，配置下限为零。 */
    public static int spearAirCost(PlayerEntity player, int base) {
        return Math.max(0, base - (enabled(player, WATER_MAGIC) ? BAL.i("spear_air_reduction", SPEAR_AIR_REDUCTION) : 0));
    }

    public static int landDashAirCost(PlayerEntity player, int base) {
        return enabled(player, WAVE) ? Math.max(0, (int) Math.ceil(base * BAL.d("land_air_multiplier", LAND_AIR_MULTIPLIER))) : Math.max(0, base);
    }

    private static Identifier addon(String path) { return new Identifier("my_addon", path); }
}
