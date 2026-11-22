package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import static net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.*;

/** Additional effect requirements. SSC's accessory power data determines the native form and tier. */
public final class AccessoryFormEffects {
    private AccessoryFormEffects() {}

    public static @Nullable Identifier formOf(LivingEntity entity) {
        var form = FormUtils.getCurrentForm(entity);
        return form == null ? null : form.getFormID();
    }

    public static boolean allows(@Nullable Identifier form, Identifier item) {
        if (item.getNamespace().equals("ssc_addon")) {
            return switch (item.getPath()) {
                case "active_coral_necklace" -> AXOLOTL_SP.equals(form);
                case "sea_crystal_pendant" -> AXOLOTL_FLUORESCENT.equals(form) || AXOLOTL_ALING.equals(form);
                case "portable_moisturizer" -> AXOLOTL_SP.equals(form) || UPGRADE_AXOLOTL.equals(form)
                        || AXOLOTL_FLUORESCENT.equals(form) || AXOLOTL_ALING.equals(form);
                case "ankh_stone", "anubis_crystal" -> ANUBIS_WOLF_SP.equals(form);
                case "erosion_sand_prism", "withered_sand_ring" -> GOLDEN_SANDSTORM_SP.equals(form);
                case "binding_anklet" -> FAMILIAR_FOX_MANCIANIMA.equals(form);
                case "blue_fire_amulet", "phantom_bell" -> FAMILIAR_FOX_SP.equals(form) || FAMILIAR_FOX_RED.equals(form);
                case "blood_garnet", "bloodlust_ring" -> BAT_DESMODUS.equals(form);
                case "humus_ring", "twin_pod" -> BAT_PARASITIC_FRUIT.equals(form);
                case "frost_amulet", "portable_fridge" -> SNOW_FOX_SP.equals(form);
                case "frost_spine_collar" -> SNOW_FOX_FROSTSPINE.equals(form);
                case "invisibility_cloak" -> WILD_CAT_SP.equals(form);
                case "lifesaving_cat_tail" -> WILD_CAT_SP.equals(form) || WILD_CAT_NIGHTMARE.equals(form);
                case "nightmare_ring" -> WILD_CAT_NIGHTMARE.equals(form);
                case "nova_revive_necklace" -> OCELOT_NOVA.equals(form);
                case "wind_spirit_stamina_necklace" -> OCELOT_SP.equals(form);
                case "venom_gland" -> SPIDER_SALTICIDAE.equals(form);
                default -> true;
            };
        }
        if (!item.getNamespace().equals("shape-shifter-curse")) return true;
        boolean addonForm = form != null && form.getNamespace().equals("my_addon");
        return switch (item.getPath()) {
            case "charm_of_night_crystal", "charm_of_night_crystal_plus" -> !BAT_PARASITIC_FRUIT.equals(form);
            case "amulet_bracelet_plus" -> !FAMILIAR_FOX_MANCIANIMA.equals(form);
            case "frost_pawglove_plus" -> !SNOW_FOX_SP.equals(form);
            case "attach_hook_plus" -> !BAT_DESMODUS.equals(form);
            case "fountain_belt_plus" -> !addonForm || AXOLOTL_SP.equals(form);
            case "collar_of_whiskers_plus" -> !OCELOT_NOVA.equals(form);
            case "withered_bandage_plus" -> !GOLDEN_SANDSTORM_SP.equals(form);
            // Web projectiles check the spindle directly, including when the caster transforms in flight.
            case "venom_spindle", "venom_spindle_plus" -> form != null && (addonForm ? SPIDER_MOON_WEAVER.equals(form)
                    : !form.getNamespace().equals("shape-shifter-curse") || switch (form.getPath()) {
                        case "spider_0", "spider_1", "spider_2", "spider_3" -> true;
                        default -> false;
                    });
            default -> true;
        };
    }
}
