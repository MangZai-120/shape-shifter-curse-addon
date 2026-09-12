package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.evolution.AxolotlTree;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class SkillHudCatalog {
    public record Skill(Identifier icon, Identifier cooldown, Identifier internalCooldown,
                        int internalTicks, String talent, boolean primary) {}

    private static final Map<String, List<Skill>> FORMS = new HashMap<>();
    private static final String PRIMARY = "form_sp_primary_cd";
    private static final String SECONDARY = "form_sp_secondary_cd";
    private static final List<Skill> SNOW_MELEE = List.of(
            skill("snow_teleport", "form_snow_fox_sp_melee_secondary_cd", true),
            skill("snow_dash", "form_snow_fox_sp_melee_primary_cd", false),
            skill("snow_mode_melee", "form_snow_fox_sp_toggle", "form_snow_fox_sp_gain_cooldown", 300, null, false));
    private static final List<Skill> SNOW_RANGED = List.of(
            skill("snow_storm", "form_snow_fox_sp_ranged_secondary_cd", true),
            skill("snow_ball", "form_snow_fox_sp_ranged_primary_cd", false),
            skill("snow_mode_ranged", "form_snow_fox_sp_toggle", "form_snow_fox_sp_gain_cooldown", 300, null, false));

    static {
        pair("familiar_fox_sp", "blue_fire_ring", "fox_fire_breath");
        pair("familiar_fox_red", "red_fire_ring", "red_fire_breath");
        pair("familiar_fox_mancianima", "contract_mark", "soul_teleport");
        FORMS.put("axolotl_fluorescent", List.of(
                skill("fluorescent_laser", PRIMARY, "form_axolotl_fluorescent_shot_cd", 8, null, true),
                skill("tidal_wave", SECONDARY, false),
                skill("water_dash", "shape-shifter-curse:form_axolotl_2_water_spurt", false)));
        FORMS.put("axolotl_aling", FORMS.get("axolotl_fluorescent"));
        FORMS.put("anubis_wolf_sp", List.of(
                skill("death_domain", PRIMARY, true), skill("summon_wolves", SECONDARY, false),
                skill("wither_brewing", null, false)));
        pair("bat_desmodus", "blood_mist", "sonic_wave");
        pair("bat_parasitic_fruit", "parasitic_seed", "spore_bomb");
        pair("golden_sandstorm_sp", "wither_sand", "brand_detonation");
        pair("ocelot_nova", "nova_explosion", "spirit_leap");
        pair("ocelot_wind_spirit", "wind_dash", "wind_claws");
        pair("spider_moon_weaver", "moon_web", "web_swing");
        pair("spider_salticidae", "jump_kill", "venom_strike");
        pair("wild_cat_nightmare", "nightmare_fear", "nightmare_spook");
        FORMS.put("wild_cat_sp", List.of(
                skill("invisibility", PRIMARY, true), skill("intimidating_dash", SECONDARY, false),
                skill("ink_blind", "wild_cat_sp_ink_blind", false)));
        FORMS.put("allay_sp", List.of(
                skill("purify", "form_allay_sp_purify_cooldown_timer", true),
                skill("group_heal", "form_allay_sp_group_heal_cooldown_timer", false),
                skill("amethyst_craft", null, false)));
        FORMS.put("fallen_allay_sp", List.of(
                skill("summon_vex", "form_fallen_allay_sp_vex_cd", true),
                skill("shadow_scream", "form_fallen_allay_sp_active_scream_cooldown_timer", false)));
        FORMS.put("snow_fox_frostspine", List.of(
                skill("frost_spikes", null, PRIMARY, 4, null, true)));
        FORMS.put("axolotl_sp", List.of(
                skill("vortex_impact", PRIMARY, true), skill("play_dead", SECONDARY, false),
                skill("water_burst", "form_axolotl_sp_water_ball", false),
                skill("water_spear_craft", "form_axolotl_sp_water_spear_craft_spear", false),
                skill("water_jump", "form_axolotl_sp_jump_out_water", false),
                skill("water_dash", "shape-shifter-curse:form_axolotl_2_water_spurt", false)));
        FORMS.put("upgrade_axolotl", List.of(
                skill("water_spear", PRIMARY, null, 0, AxolotlTree.NODE_WATER_SPEAR, true),
                skill("vortex_guide", SECONDARY, null, 0, AxolotlTree.NODE_VORTEX_GUIDE, false),
                skill("water_dash", "form_upgrade_axolotl_dash_hud_water", null, 0, AxolotlTree.NODE_WATER_SPURT, false),
                skill("land_dash", "form_upgrade_axolotl_dash_hud_land", null, 0, AxolotlTree.NODE_WATER_SPURT, false),
                skill("water_jump", "form_upgrade_axolotl_jump_out_water", null, 0, "aquatic_adapt", false)));
        FORMS.put("upgrade_familiar_fox", List.of(
                skill("fire_spark", "shape-shifter-curse:form_familiar_fox_fire_explode_cooldown", null, 0, "spark", true),
                skill("fire_ring", "shape-shifter-curse:form_familiar_fox_fire_explode_cooldown", null, 0, "fire_ring", true),
                skill("fire_rocket", "shape-shifter-curse:form_familiar_fox_fire_arrow_cooldown", null, 0, "rocket", false),
                skill("alchemy", null, null, 0, "alchemy", false)));
    }

    private SkillHudCatalog() {}

    private static Identifier powerId(String path) {
        return path == null ? null : new Identifier(path.contains(":") ? path : "my_addon:" + path);
    }

    private static Skill skill(String icon, String cooldown, boolean primary) {
        return skill(icon, cooldown, null, 0, null, primary);
    }

    private static Skill skill(String icon, String cooldown, String internal, int ticks, String talent, boolean primary) {
        return new Skill(new Identifier("my_addon", "textures/gui/skill_icons/" + icon + ".png"),
                powerId(cooldown), powerId(internal), ticks, talent, primary);
    }

    private static void pair(String form, String primaryIcon, String secondaryIcon) {
        FORMS.put(form, List.of(skill(primaryIcon, PRIMARY, true), skill(secondaryIcon, SECONDARY, false)));
    }

    public static List<Skill> forForm(Identifier form, PlayerEntity player) {
        if (!"my_addon".equals(form.getNamespace())) return List.of();
        if (FormIdentifiers.SNOW_FOX_SP.equals(form)) {
            return PowerUtils.getClientResourceValue(player, FormIdentifiers.SNOW_FOX_SWITCH_STATE) == 1
                    ? SNOW_RANGED : SNOW_MELEE;
        }
        return FORMS.getOrDefault(form.getPath(), List.of());
    }

    public static boolean isUnlocked(Skill skill, PlayerEntity player) {
        if (skill.talent() == null) return true;
        var evolution = RegEvolutionComponent.EVOLUTION.get(player);
        if ("spark".equals(skill.talent()) && evolution.isUnlocked("fire_ring")) return false;
        return evolution.isUnlocked(skill.talent());
    }

        public static List<Skill> forHud(Identifier form, PlayerEntity player) {
                List<Skill> unlocked = forForm(form, player).stream().filter(skill -> isUnlocked(skill, player)).toList();
                java.util.ArrayList<Skill> result = new java.util.ArrayList<>(2);
                unlocked.stream().filter(Skill::primary).findFirst().ifPresent(result::add);
                unlocked.stream().filter(skill -> !skill.primary()).findFirst().ifPresent(result::add);
                return List.copyOf(result);
        }
}