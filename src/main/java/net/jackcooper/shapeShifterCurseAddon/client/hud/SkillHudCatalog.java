package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.ability.MancianimaTeleport;
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
                        int internalTicks, String talent, boolean primary,
                        java.util.function.Predicate<PlayerEntity> condition,
                        java.util.function.Function<PlayerEntity, Identifier> iconOverride) {

        /** 渲染时解析实际图标（支持野猫双态等动态切换；无 override 返回静态 icon）。 */
        public Identifier resolveIcon(PlayerEntity player) {
            return iconOverride == null ? icon : iconOverride.apply(player);
        }
    }

    /** 门槛：SSC 主包 mana 系（CCA AutoSyncedComponent，客户端直读同步值）。 */
    private static boolean hasMana(PlayerEntity p, double need) {
        var m = net.onixary.shapeShifterCurseFabric.mana.RegManaComponent.MANA.get(p);
        return m != null && m.getMana() >= need;
    }

    /** 门槛：Apoli 资源（霜寒值/悦灵能量等，走每 tick 客户端缓存）。 */
    private static boolean hasResource(PlayerEntity p, Identifier id, int need) {
        return PowerUtils.getClientResourceValue(p, id) >= need;
    }

    /** 野猫SP震慑冲刺门槛：真实隐身中才可用（状态效果服务端同步，客机即时可见）。 */
    private static boolean wildCatInvisible(PlayerEntity p) {
        return p.hasStatusEffect(net.jackcooper.shapeShifterCurseAddon.SscAddon.TRUE_INVISIBILITY);
    }

    private static final Identifier ICON_INVISIBILITY =
            new Identifier("my_addon", "textures/gui/skill_icons/invisibility.png");
    private static final Identifier ICON_WEAKNESS =
            new Identifier("my_addon", "textures/gui/skill_icons/weakness_spot.png");

    private static final Map<String, List<Skill>> FORMS = new HashMap<>();
    private static final String PRIMARY = "form_sp_primary_cd";
    private static final String SECONDARY = "form_sp_secondary_cd";
        // SP雪狐四主动技能门槛：霜寒值（近战冲刺15 / 瞬移20 / 冰球15 / 冰风暴30）
        private static final java.util.function.Predicate<PlayerEntity> SNOW_15 =
                p -> hasResource(p, FormIdentifiers.SNOW_FOX_RESOURCE, 15);
        private static final java.util.function.Predicate<PlayerEntity> SNOW_20 =
                p -> hasResource(p, FormIdentifiers.SNOW_FOX_RESOURCE, 20);
        private static final java.util.function.Predicate<PlayerEntity> SNOW_30 =
                p -> hasResource(p, FormIdentifiers.SNOW_FOX_RESOURCE, 30);
        private static final List<Skill> SNOW_MELEE = List.of(
            skill("snow_teleport", "form_snow_fox_sp_melee_secondary_cd", true, SNOW_20),
            skill("snow_dash", "form_snow_fox_sp_melee_primary_cd", false, SNOW_15),
            skill("snow_mode_melee", "form_snow_fox_sp_toggle", "form_snow_fox_sp_gain_cooldown", 300, null, false));
        private static final List<Skill> SNOW_RANGED = List.of(
            skill("snow_storm", "form_snow_fox_sp_ranged_secondary_cd", true, SNOW_30),
            skill("snow_ball", "form_snow_fox_sp_ranged_primary_cd", false, SNOW_15),
            skill("snow_mode_ranged", "form_snow_fox_sp_toggle", "form_snow_fox_sp_gain_cooldown", 300, null, false));

    static {
        FORMS.put("familiar_fox_sp", List.of(
                skill("blue_fire_ring", "form_familiar_fox_sp_blue_fire_ring_cooldown_timer",
                        "form_familiar_fox_sp_blue_fire_ring_key_activation", 0, null, true, p -> hasMana(p, 99.0)),
                skill("fox_fire_breath", "form_familiar_fox_sp_fox_fire_breath", false, p -> hasMana(p, 15.0))));
        FORMS.put("familiar_fox_red", List.of(
                skill("red_fire_ring", "form_familiar_fox_red_blue_fire_ring_cooldown_timer",
                        "form_familiar_fox_red_blue_fire_ring_key_activation", 0, null, true, p -> hasMana(p, 99.0)),
                skill("red_fire_breath", "form_familiar_fox_red_fox_fire_breath", false, p -> hasMana(p, 20.0))));
        FORMS.put("familiar_fox_sp_amulet", List.of(
                skill("blue_fire_ring", "form_familiar_fox_sp_blue_fire_ring_amulet_cooldown_timer",
                        "form_familiar_fox_sp_blue_fire_ring_amulet_key_activation", 0, null, true, p -> hasMana(p, 99.0)),
                FORMS.get("familiar_fox_sp").get(1)));
        FORMS.put("familiar_fox_red_amulet", List.of(
                skill("red_fire_ring", "form_familiar_fox_red_blue_fire_ring_amulet_cooldown_timer",
                        "form_familiar_fox_red_blue_fire_ring_amulet_key_activation", 0, null, true, p -> hasMana(p, 119.0)),
                FORMS.get("familiar_fox_red").get(1)));
        // 契灵：烙印最低 5 mana；魂跃 5 mana；魂跃辅助栏 = 10s 联动攻击 CD 进度
        FORMS.put("familiar_fox_mancianima", List.of(
                skill("contract_mark", PRIMARY, true, p -> hasMana(p, 5.0)),
                skill("soul_teleport", SECONDARY,
                        "form_mancianima_link_cd", MancianimaTeleport.RED_LINK_CD_TICKS, null, false,
                        p -> hasMana(p, 5.0))));
        // 月织蛛：织网术起手 6 mana；蛛丝荡漾 1 mana
        pair("spider_moon_weaver", "moon_web", "web_swing",
                p -> hasMana(p, 6.0), p -> hasMana(p, 1.0));

        FORMS.put("axolotl_fluorescent", List.of(
                skill("fluorescent_laser", PRIMARY, "form_axolotl_fluorescent_shot_cd", 8, null, true),
                skill("tidal_wave", SECONDARY, false),
                skill("water_dash", "shape-shifter-curse:form_axolotl_2_water_spurt", false)));
        FORMS.put("axolotl_aling", FORMS.get("axolotl_fluorescent"));
        FORMS.put("anubis_wolf_sp", List.of(
                skill("death_domain", PRIMARY, true), skill("summon_wolves", SECONDARY, false)));
        // 纯能力标识（wither_brewing 凋零酿造）同上移出技能栏（无 CD 无交互，进化树可见）。
        pair("bat_desmodus", "blood_mist", "sonic_wave");
        pair("bat_parasitic_fruit", "parasitic_seed", "spore_bomb");
        pair("golden_sandstorm_sp", "wither_sand", "brand_detonation");
        pair("ocelot_nova", "nova_explosion", "spirit_leap");
        pair("ocelot_wind_spirit", "wind_dash", "wind_claws");
        pair("spider_salticidae", "jump_kill", "venom_strike");
        pair("wild_cat_nightmare", "nightmare_fear", "nightmare_spook");
        // 野猫SP：主技能双态图标——隐身中再按主键=破隐+弱点识破，切 weakness_spot；
        // 副技能震慑冲刺必须隐身中才可用；墨囊致盲非主/副槽不遮
        FORMS.put("wild_cat_sp", List.of(
                new Skill(new Identifier("my_addon", "textures/gui/skill_icons/invisibility.png"),
                        powerId(PRIMARY), null, 0, null, true, null,
                        p -> wildCatInvisible(p) ? ICON_WEAKNESS : ICON_INVISIBILITY),
                skill("intimidating_dash", SECONDARY, false, SkillHudCatalog::wildCatInvisible),
                skill("ink_blind", "wild_cat_sp_ink_blind", false)));
        FORMS.put("allay_sp", List.of(
                skill("purify", "form_allay_sp_purify_cooldown_timer", true),
                // 群体治疗：需能量 100（服务端 mana_resource >= 100）
                skill("group_heal", "form_allay_sp_group_heal_cooldown_timer", false,
                        p -> hasResource(p, new Identifier("my_addon", "form_allay_sp_mana_resource"), 100))));
        FORMS.put("fallen_allay_sp", List.of(
                skill("summon_vex", "form_fallen_allay_sp_vex_cd", true),
                skill("shadow_scream", "form_fallen_allay_sp_active_scream_cooldown_timer", false)));
        FORMS.put("snow_fox_frostspine", List.of(
                skill("frost_spikes", null, true),
                // 凝棘：需身上有环绕冰锥 ≥1（扫本地 FrostThornEntity，DataTracker 同步）
                skill("frost_forge", null, false, SkillHudCatalog::hasHoverThorn)));
        FORMS.put("axolotl_sp", List.of(
                // 涡流冲击：湿润度（air）≥8 才够扣一次（服务端 AIR_PER_HIT=8）
                skill("vortex_impact", PRIMARY, true, p -> p.getAir() >= 8),
                skill("play_dead", SECONDARY, false),
                skill("water_burst", "form_axolotl_sp_water_ball", false),
                skill("water_spear_craft", "form_axolotl_sp_water_spear_craft_spear", false),
                skill("water_jump", "form_axolotl_sp_jump_out_water", false),
                skill("water_dash", "shape-shifter-curse:form_axolotl_2_water_spurt", false)));
        FORMS.put("upgrade_axolotl", List.of(
                // 投掷水矛：湿润度（air）≥18（服务端 AIR_COST=18）
                skill("water_spear", PRIMARY, null, 0, AxolotlTree.NODE_WATER_SPEAR, true,
                        p -> p.getAir() >= 18),
                skill("vortex_guide", SECONDARY, null, 0, AxolotlTree.NODE_VORTEX_GUIDE, false),
                skill("water_dash", "form_upgrade_axolotl_dash_hud_water", null, 0, AxolotlTree.NODE_WATER_SPURT, false),
                skill("land_dash", "form_upgrade_axolotl_dash_hud_land", null, 0, AxolotlTree.NODE_WATER_SPURT, false),
                skill("water_jump", "form_upgrade_axolotl_jump_out_water", null, 0, "aquatic_adapt", false)));
        FORMS.put("upgrade_familiar_fox", List.of(
                // 火花/火环：has_mana 11；火箭：has_mana 2.5（副手持箭的 stick_fireball）
                skill("fire_spark", "shape-shifter-curse:form_familiar_fox_fire_explode_cooldown", null, 0, "spark", true,
                        p -> hasMana(p, 11.0)),
                skill("fire_ring", "shape-shifter-curse:form_familiar_fox_fire_explode_cooldown", null, 0, "fire_ring", true,
                        p -> hasMana(p, 11.0)),
                skill("fire_rocket", "shape-shifter-curse:form_familiar_fox_fire_arrow_cooldown", null, 0, "rocket", false,
                        p -> hasMana(p, 2.5))));
        // 纯能力标识（alchemy 炼药、amethyst_craft 紫水晶工艺）已按用户定稿移出技能栏：
        // 它们无 CD 无交互，仅静态展示；进化树 GUI 仍可看到对应节点。
    }

    private SkillHudCatalog() {}

    private static Identifier powerId(String path) {
        return path == null ? null : new Identifier(path.contains(":") ? path : "my_addon:" + path);
    }

    private static Skill skill(String icon, String cooldown, boolean primary) {
        return skill(icon, cooldown, null, 0, null, primary);
    }

    private static Skill skill(String icon, String cooldown, boolean primary,
                                java.util.function.Predicate<PlayerEntity> condition) {
        return skill(icon, cooldown, null, 0, null, primary, condition);
    }

    private static Skill skill(String icon, String cooldown, String internal, int ticks, String talent, boolean primary) {
        return skill(icon, cooldown, internal, ticks, talent, primary, null);
    }

    private static Skill skill(String icon, String cooldown, String internal, int ticks, String talent, boolean primary,
                               java.util.function.Predicate<PlayerEntity> condition) {
        return new Skill(new Identifier("my_addon", "textures/gui/skill_icons/" + icon + ".png"),
                powerId(cooldown), powerId(internal), ticks, talent, primary, condition, null);
    }

    private static void pair(String form, String primaryIcon, String secondaryIcon) {
        FORMS.put(form, List.of(skill(primaryIcon, PRIMARY, true), skill(secondaryIcon, SECONDARY, false)));
    }

    private static void pair(String form, String primaryIcon, String secondaryIcon,
                             java.util.function.Predicate<PlayerEntity> primaryCond,
                             java.util.function.Predicate<PlayerEntity> secondaryCond) {
        FORMS.put(form, List.of(skill(primaryIcon, PRIMARY, true, primaryCond),
                skill(secondaryIcon, SECONDARY, false, secondaryCond)));
    }

    /** 寒棘狐凝棘门槛：本地扫描玩家身边的环绕冰锥（HOVER 态，DataTracker 同步，与服务端判定同源）。 */
    private static boolean hasHoverThorn(PlayerEntity player) {
        return !player.getWorld().getEntitiesByClass(net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity.class,
                player.getBoundingBox().expand(3.0),
                t -> t.isHover() && player.getUuid().equals(t.getOwnerUuid().orElse(null))).isEmpty();
    }

    public static List<Skill> forForm(Identifier form, PlayerEntity player) {
        if (!"my_addon".equals(form.getNamespace())) return List.of();
                if ((FormIdentifiers.FAMILIAR_FOX_SP.equals(form) || FormIdentifiers.FAMILIAR_FOX_RED.equals(form))
                                && net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils.isWearing(
                                                player, net.jackcooper.shapeShifterCurseAddon.SscAddon.BLUE_FIRE_AMULET)) {
                        return FORMS.get(form.getPath() + "_amulet");
                }
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