package net.jackcooper.shapeShifterCurseAddon.evolution;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/**
 * Current talent eligibility shared by powers, Java abilities, perks and the HUD.
 * Acquisition history, purchase prerequisites and migrations use EvolutionComponent.isUnlocked.
 */
public final class EvolutionTalents {
    private EvolutionTalents() { }

    public static boolean isActive(PlayerEntity player, String talentId) {
        if (player == null || talentId == null || talentId.isEmpty()) return false;
        EvolutionComponent progress = RegEvolutionComponent.EVOLUTION.get(player);
        EvolutionRoute route = EvolutionRegistry.forWorld(player.getWorld()).getRoute(progress.getRoute());
        if (route == null || route.startForm == null || !route.isValidNode(talentId)) return false;

        var form = RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm;
        var currentForm = form == null ? null : FormIdMigration.canonical(form.getFormID());
        if (!route.startForm.equals(currentForm)) return false;
        String enteredForm = FormIdMigration.canonical(progress.getRouteStartForm());
        if (!enteredForm.isEmpty() && !enteredForm.equals(currentForm.toString())) return false;

        // enabled controls new entry. Previously acquired talents do not re-pay or re-check
        // changed prerequisites/levels. Admin unlocks still require the valid current route above.
        // Before a job-change midpoint, the old form/route remain active; after it they mismatch
        // until settlement. Initial entry becomes active on reaching the form, before started ticks.
        return progress.isUnlocked(talentId);
    }
}
