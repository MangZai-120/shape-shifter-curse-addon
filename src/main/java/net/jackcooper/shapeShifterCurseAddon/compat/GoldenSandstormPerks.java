package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.ability.GoldenSandstormErosionBrand;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** Shared SSC wolf purchases, mapped to Golden Sandstorm's own abilities. */
public final class GoldenSandstormPerks {
    public static final Identifier RESPONSE_1 = addon("perk_golden_sandstorm_response_1");
    public static final Identifier RESPONSE_2 = addon("perk_golden_sandstorm_response_2");
    public static final Identifier AMPLIFICATION = addon("perk_golden_sandstorm_amplification");
    public static final Identifier EXCITATION = addon("perk_golden_sandstorm_excitation");
    public static final Identifier VITALITY_1 = addon("perk_golden_sandstorm_vitality_1");
    public static final Identifier VITALITY_2 = addon("perk_golden_sandstorm_vitality_2");
    public static final Identifier RECALL = addon("perk_golden_sandstorm_recall");

    private GoldenSandstormPerks() { }
    public static boolean isSupported(IForm form) {
        return form != null && FormIdentifiers.GOLDEN_SANDSTORM_SP.equals(form.getFormID());
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !RegPerks.getPerkTree(RegPerks.T_AnubisWolf3PerkTree).getAllPerks().contains(perk.getID())) return false;
        var id = perk.getID();
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_AnubisWolf3PerkTree);
        var source = form.getFormLayer().getRight();
        Identifier mapped;
        if (RegPerks.P_AnubisWolfPackResponse_1.equals(id)) {
            if (purchased != null && purchased.contains(RegPerks.P_AnubisWolfPackResponse_2)) return true;
            mapped = RESPONSE_1;
        } else if (RegPerks.P_AnubisWolfPackResponse_2.equals(id)) {
            FormUtils.removePower(player, RESPONSE_1, source);
            mapped = RESPONSE_2;
        } else if (RegPerks.P_AnubisWolfPackAmplification.equals(id)) mapped = AMPLIFICATION;
        else if (RegPerks.P_AnubisWolfSoulExcitation.equals(id)) mapped = EXCITATION;
        else if (RegPerks.P_AnubisWolfWitherTolerance_1.equals(id)) {
            if (purchased != null && purchased.contains(RegPerks.P_AnubisWolfWitherTolerance_2)) return true;
            mapped = VITALITY_1;
        } else if (RegPerks.P_AnubisWolfWitherTolerance_2.equals(id)) {
            FormUtils.removePower(player, VITALITY_1, source);
            mapped = VITALITY_2;
        } else if (RegPerks.P_AnubisWolfSoulRecall.equals(id)) mapped = RECALL;
        else if (RegPerks.P_AnubisWolfUndeadDiscernment.equals(id)) {
            for (var removed : perk.powerRemove) FormUtils.removePower(player, removed, source);
            return true;
        } else return false;
        // Never load the original summon/withering powers in this form.
        FormUtils.applyPower(player, mapped, source);
        return true;
    }

    public static int responseRank(PlayerEntity player) {
        return enabled(player, RESPONSE_2) ? 2 : enabled(player, RESPONSE_1) ? 1 : 0;
    }
    public static int normalCooldown(int baseTicks, int rank) {
        return Math.max(0, baseTicks - 60 * Math.max(0, Math.min(2, rank)));
    }
    public static int sandstormStacks(PlayerEntity player) { return enabled(player, EXCITATION) ? 2 : 1; }
    public static float damagePercent(PlayerEntity player, float base) { return scaledDamage(base, enabled(player, AMPLIFICATION)); }
    public static float damageCap(PlayerEntity player, float base) { return scaledDamage(base, enabled(player, AMPLIFICATION)); }
    public static float scaledDamage(float base, boolean amplified) { return amplified ? base * 1.25f : base; }
    public static float recallHealing(float maximumHealth, int layers) {
        return Math.max(0, maximumHealth) * (Math.min(10, Math.max(0, layers)) * 0.02f);
    }
    public static boolean recall(ServerPlayerEntity player) {
        return enabled(player, RECALL) && !AbilityUseGuard.blocked(player)
                && GoldenSandstormErosionBrand.recallAll(player) > 0;
    }
    private static boolean enabled(PlayerEntity player, Identifier id) {
        if (player == null || !isSupported(FormUtils.getPlayerForm(player)) || !PowerTypeRegistry.contains(id)) return false;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(id));
        return power != null && power.isActive();
    }
    private static Identifier addon(String path) { return new Identifier("my_addon", path); }
}
