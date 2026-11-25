package net.jackcooper.shapeShifterCurseAddon.forms;

import java.util.Map;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import static net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.*;

/**
 * Resting eye anchors measured from the rendered eyeRoot vertices, including the player renderer's
 * 0.9375 scale and SSC's pose transforms. Values are BEFORE Pehkui HEIGHT/EYE_HEIGHT multiplication.
 * Correct the shared entity eye height so camera, ray casts and server projectiles agree; never
 * write a world-space camera offset or multiply the form's size a second time.
 */
public final class FormEyeHeights {
    private FormEyeHeights() {}

    private record Profile(float standing, float crouching) {}
    private static final Profile AXOLOTL = new Profile(1.524375f, 0.419211f / 0.35f);
    private static final Profile FERAL = new Profile(0.88765625f / 0.6f, 0.88765625f / 0.6f);
    private static final Profile BAT = new Profile(1.055625f / 0.7f, 0.87203125f / 0.7f);
    private static final Profile ALLAY = new Profile(1.58296875f, 1.165f);
    private static final Profile SPIDER = new Profile(1.524375f, 1.211875f);
    // Sheep's eyes are the texture row at y=19.5 px; its renderer uses 1.3 / 1.375.
    private static final float SHEEP_EYE = 19.5f / 16 * (1.3f / 1.375f) / (1.235f / 1.62f);
    private static final Profile SHEEP = new Profile(SHEEP_EYE, SHEEP_EYE - 0.125f / (1.235f / 1.62f));
    private static final Map<Identifier, Profile> PROFILES = Map.ofEntries(
            Map.entry(AXOLOTL_SP, AXOLOTL), Map.entry(UPGRADE_AXOLOTL, AXOLOTL),
            Map.entry(AXOLOTL_FLUORESCENT, AXOLOTL), Map.entry(AXOLOTL_ALING, AXOLOTL),
            Map.entry(FAMILIAR_FOX_SP, FERAL), Map.entry(UPGRADE_FAMILIAR_FOX, FERAL),
            Map.entry(FAMILIAR_FOX_MANCIANIMA, FERAL), Map.entry(FAMILIAR_FOX_RED, FERAL),
            Map.entry(UPGRADE_SNOW_FOX, FERAL), Map.entry(SNOW_FOX_SP, FERAL), Map.entry(SNOW_FOX_FROSTSPINE, FERAL),
            Map.entry(WILD_CAT_SP, FERAL), Map.entry(WILD_CAT_NIGHTMARE, FERAL),
            Map.entry(OCELOT_SP, FERAL), Map.entry(OCELOT_NOVA, FERAL),
            Map.entry(ANUBIS_WOLF_SP, FERAL), Map.entry(GOLDEN_SANDSTORM_SP, FERAL),
            Map.entry(BAT_DESMODUS, BAT), Map.entry(BAT_PARASITIC_FRUIT, BAT),
            Map.entry(ALLAY_SP, ALLAY), Map.entry(FALLEN_ALLAY_SP, ALLAY),
            Map.entry(SPIDER_MOON_WEAVER, SPIDER), Map.entry(SPIDER_SALTICIDAE, SPIDER),
            Map.entry(SHEEP_FORM, SHEEP));

    public static float correct(PlayerEntity player, EntityPose pose, float original) {
        if (pose != EntityPose.STANDING && pose != EntityPose.CROUCHING) return original;
        // Mist owns its own physical eye height while the normal model is hidden.
        if (player.hasStatusEffect(SscAddon.MIST_FORM)) return original;
        var form = FormUtils.getPlayerForm(player);
        if (form == null) return original;
        var profile = PROFILES.get(form.getFormID());
        if (profile == null) return original;
        return original * (pose == EntityPose.CROUCHING ? profile.crouching / 1.27f : profile.standing / 1.62f);
    }
}
