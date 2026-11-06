package net.jackcooper.shapeShifterCurseAddon.tutorial;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.util.Identifier;

import java.util.Set;

/** Playable addon forms with an introductory skill lesson; temporary spell forms are excluded. */
public final class FormTutorialCatalog {
    private static final Set<Identifier> FORMS = Set.of(
            FormIdentifiers.AXOLOTL_SP,
            FormIdentifiers.UPGRADE_AXOLOTL,
            FormIdentifiers.AXOLOTL_FLUORESCENT,
            FormIdentifiers.AXOLOTL_ALING,
            FormIdentifiers.FAMILIAR_FOX_SP,
            FormIdentifiers.UPGRADE_FAMILIAR_FOX,
            FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,
            FormIdentifiers.FAMILIAR_FOX_RED,
            FormIdentifiers.SNOW_FOX_SP,
            FormIdentifiers.SNOW_FOX_FROSTSPINE,
            FormIdentifiers.ALLAY_SP,
            FormIdentifiers.WILD_CAT_SP,
            FormIdentifiers.WILD_CAT_NIGHTMARE,
            FormIdentifiers.OCELOT_SP,
            FormIdentifiers.OCELOT_NOVA,
            FormIdentifiers.FALLEN_ALLAY_SP,
            FormIdentifiers.ANUBIS_WOLF_SP,
            FormIdentifiers.GOLDEN_SANDSTORM_SP,
            FormIdentifiers.BAT_DESMODUS,
            FormIdentifiers.SPIDER_MOON_WEAVER,
            FormIdentifiers.SPIDER_SALTICIDAE,
            FormIdentifiers.BAT_PARASITIC_FRUIT);

    private FormTutorialCatalog() {}

    public static boolean supports(Identifier form) {
        return form != null && FORMS.contains(form);
    }

    public static Set<Identifier> forms() {
        return FORMS;
    }

    /** These powers use SSC's native skill binding instead of SSCA's per-form virtual binding. */
    public static boolean usesSscKeys(Identifier form) {
        return FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(form);
    }
}
