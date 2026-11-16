package net.jackcooper.shapeShifterCurseAddon.forms;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

/** Reuses SSC's cat behavior while keeping the addon's saved Origin identity. */
public final class Form_FeralCatSP extends net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralCatSP {
    public Form_FeralCatSP(Identifier formId) {
        super(formId);
    }

    @Override
    public Pair<Identifier, Identifier> getFormLayer() {
        return FormIdMigration.originLayer(getFormID());
    }
}
