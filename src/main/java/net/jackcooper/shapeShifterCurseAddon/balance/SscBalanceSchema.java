package net.jackcooper.shapeShifterCurseAddon.balance;

/**
 * SSCA 生产 schema 登记（阶段 1 产出，阶段 3/4 消费）。
 *
 * 登记原则（制作流程 §4）：同一数值只有一个权威来源——
 * 已由 Apoli power 工厂参数或法术 JSON 提供的（如音波 cooldown、雾化 duration、
 * 烈焰新星 base_damage/CD/耗蓝、雪狐姿态移速两个加成）**不在此重复登记**，
 * 只登记当前写死在 Java 行为代码里的玩法参数。
 *
 * 来源注解：每个登记都标注 (srcClass, srcField) 指向代码常量——pin 测试自动反射对照，
 * 改任一侧不同步即构建红；字面量参数（无常量）不标注，由 pin 表显式维护。
 * 纯逻辑类：不依赖 MC，双端可各自构造。
 */
public final class SscBalanceSchema {

    private SscBalanceSchema() {}

    /** 代码常量来源类前缀（相对 net.jackcooper.shapeShifterCurseAddon. 的短名）。 */
    private static final String P = "power.";
    private static final String S = "spell.spells.";
    private static final String AB = "ability.";
    private static final String EN = "entity.";
    private static final String EF = "effect.";

    public static BalanceSchema create() {
        var b = BalanceSchema.create();

        // ==== abilities（形态技能）====

        // 蝙蝠音波（BatSonicWaveAbilityPower）：cooldown 已由 power 工厂参数提供，不重复
        b.scope("abilities.bat_sonic_wave")
                .doubleParam("range", 8.0, 1.0, 64.0, P + "BatSonicWaveAbilityPower", "RANGE")
                .doubleParam("half_width", 1.75, 0.25, 8.0, P + "BatSonicWaveAbilityPower", "HALF_WIDTH")
                .doubleParam("damage", 6.0, 0.0, 1000.0, P + "BatSonicWaveAbilityPower", "DAMAGE")
                .intParam("debuff_ticks", 60, 0, 1200, P + "BatSonicWaveAbilityPower", "DEBUFF_TICKS");

        // 雾化凝聚爆破（MistFormAbilityPower）：cooldown/duration 已由 power 工厂参数提供
        b.scope("abilities.bat_mist_burst")
                .intParam("min_delay_ticks", 20, 0, 200, P + "MistFormAbilityPower", "MIST_BURST_DELAY")
                .intParam("charge_ticks", 20, 0, 200, P + "MistFormAbilityPower", "CHARGE_DURATION")
                .doubleParam("radius", 4.0, 0.5, 16.0, P + "MistFormAbilityPower", "AOE_RADIUS")
                .doubleParam("damage", 12.0, 0.0, 1000.0, P + "MistFormAbilityPower", "AOE_DAMAGE")
                .doubleParam("knockback", 1.0, 0.0, 4.0, P + "MistFormAbilityPower", "AOE_KNOCKBACK");

        // ==== spells（法术专属参数；通用伤害/CD/施法/耗蓝在法术 JSON，不重复）====

        b.scope("spells.flame_nova")
                .doubleParam("base_radius", 4.0, 0.5, 32.0, S + "FlameNovaSpell", "BASE_RADIUS")
                .intParam("fire_ticks", 40, 0, 1200, S + "FlameNovaSpell", "FIRE_TICKS")
                .doubleParam("knockback", 0.8, 0.0, 4.0)
                .doubleParam("rarity_radius_multiplier", 1.25, 1.0, 3.0);
        b.scope("spells.companion_resonance")
                .doubleParam("radius", 8.0, 1.0, 48.0, S + "CompanionResonanceSpell", "RADIUS")
                .intParam("base_duration_ticks", 600, 20, 4800, S + "CompanionResonanceSpell", "BASE_DURATION_TICKS")
                .intParam("duration_per_level", 200, 0, 2400, S + "CompanionResonanceSpell", "DURATION_PER_LEVEL");
        b.scope("spells.corrupt_mist")
                .doubleParam("base_radius", 3.0, 0.5, 48.0, S + "CorruptMistSpell", "BASE_RADIUS")
                .intParam("duration_ticks", 120, 1, 4800, S + "CorruptMistSpell", "DURATION_TICKS")
                .intParam("interval_ticks", 40, 1, 1200, S + "CorruptMistSpell", "INTERVAL_TICKS")
                .intParam("poison_ticks", 80, 0, 2400, S + "CorruptMistSpell", "POISON_TICKS");
        b.scope("spells.dread_whisper")
                .doubleParam("base_range", 6.0, 1.0, 48.0, S + "DreadWhisperSpell", "BASE_RANGE")
                .doubleParam("half_angle_deg", 30.0, 1.0, 90.0, S + "DreadWhisperSpell", "HALF_ANGLE_DEG")
                .intParam("duration_ticks", 120, 1, 2400, S + "DreadWhisperSpell", "DURATION_TICKS");
        b.scope("spells.fire_bolt")
                .intParam("fire_ticks_low", 60, 0, 1200, S + "FireBoltSpell", "FIRE_TICKS_LOW")
                .intParam("fire_ticks_high", 100, 0, 1200, S + "FireBoltSpell", "FIRE_TICKS_HIGH")
                .constraint("低档点燃时长必须小于等于高档", v ->
                        (Long) v.get("fire_ticks_low") > (Long) v.get("fire_ticks_high"));
        b.scope("spells.frost_armor")
                .intParam("duration_ticks", 400, 20, 4800, S + "FrostArmorSpell", "DURATION_TICKS");
        b.scope("spells.frost_nova")
                .doubleParam("base_radius", 4.0, 0.5, 48.0, S + "FrostNovaSpell", "BASE_RADIUS");
        b.scope("spells.ice_barrage")
                .intParam("count", 3, 1, 16, S + "IceBarrageSpell", "COUNT")
                .doubleParam("spread_deg", 12.0, 0.0, 90.0, S + "IceBarrageSpell", "SPREAD_DEG")
                .intParam("pierce_base", 2, 0, 16, S + "IceBarrageSpell", "PIERCE_BASE");
        b.scope("spells.lunar_phase")
                .doubleParam("aim_range", 24.0, 1.0, 128.0, S + "LunarPhaseSpell", "AIM_RANGE")
                .doubleParam("aim_tolerance", 0.5, 0.0, 4.0, S + "LunarPhaseSpell", "AIM_TOLERANCE");
        b.scope("spells.lunar_veil")
                .doubleParam("base_radius", 4.0, 0.5, 48.0, S + "LunarVeilSpell", "BASE_RADIUS")
                .intParam("duration_ticks", 160, 20, 4800, S + "LunarVeilSpell", "DURATION_TICKS");
        b.scope("spells.meteor")
                .doubleParam("max_range", 32.0, 4.0, 128.0, S + "MeteorSpell", "MAX_RANGE")
                .doubleParam("base_radius", 3.0, 0.5, 48.0, S + "MeteorSpell", "BASE_RADIUS");
        b.scope("spells.space_blink")
                .doubleParam("base_range", 8.0, 1.0, 128.0, S + "SpaceBlinkSpell", "BASE_RANGE");
        b.scope("spells.space_stride")
                .intParam("jump_ticks", 200, 20, 2400, S + "SpaceStrideSpell", "JUMP_TICKS")
                .intParam("slow_fall_extra_ticks", 40, 0, 1200, S + "SpaceStrideSpell", "SLOW_FALL_EXTRA_TICKS");
        b.scope("spells.summon_lunar_spirit")
                .intParam("base_life_ticks", 600, 100, 6000, S + "SummonLunarSpiritSpell", "BASE_LIFE_TICKS")
                .intParam("life_per_level", 200, 0, 2400, S + "SummonLunarSpiritSpell", "LIFE_PER_LEVEL");
        b.scope("spells.beep_sheep")
                .intParam("base_duration_ticks", 120, 20, 1200, S + "BeepSheepSpell", "BASE_DURATION_TICKS")
                .intParam("duration_per_level", 30, 0, 600, S + "BeepSheepSpell", "DURATION_PER_LEVEL");
        b.scope("spells.void_devour")
                .doubleParam("base_range", 16.0, 1.0, 128.0, S + "VoidDevourSpell", "BASE_RANGE")
                .doubleParam("impact_radius", 2.0, 0.5, 16.0, S + "VoidDevourSpell", "IMPACT_RADIUS")
                .doubleParam("radius_per_level", 0.75, 0.0, 16.0, S + "VoidDevourSpell", "RADIUS_PER_LEVEL")
                .intParam("base_blindness_ticks", 60, 0, 2400, S + "VoidDevourSpell", "BASE_BLINDNESS_TICKS");
        b.scope("spells.void_erosion")
                .doubleParam("base_radius", 3.0, 0.5, 48.0, S + "VoidErosionSpell", "BASE_RADIUS")
                .intParam("duration_ticks", 160, 20, 4800, S + "VoidErosionSpell", "DURATION_TICKS");

        // 通用法阵补登（FormationData 字面量；汇率 2→10 即 5:1）
        b.scope("systems.formation_extra")
                .doubleParam("universal_drain_per_sec", 2.0, 0.0, 100.0, "spell.FormationData", "UNIVERSAL_MANA_DRAIN_PER_SEC")
                .doubleParam("universal_restore_per_sec", 10.0, 0.0, 100.0, "spell.FormationData", "UNIVERSAL_BOOK_MANA_PER_SEC");

        // ==== 尾部批次（盘点清单 §4 未登记项；几何摆位/物理插值/音效常量不登记）====

        // 雪狐姿态移速（阶段 7 补登：power JSON 仍是 Apoli 构造默认，balance 快照提供运行时覆盖 +
        // 重载代数刷新——「单一权威」语义：未写 balance 时以 power JSON 为准（快照默认=代码常量同值））
        b.scope("forms.snow_fox_sp")
                .doubleParam("melee_speed_bonus", 0.1, -2.0, 2.0)
                .doubleParam("ranged_speed_penalty", -0.1, -2.0, 2.0);

        // 荧光幼灵激光（FluorescentLaserManager；阵列摆位 ARRAY_BACK/SIDE/UP 与 COUNT 为几何，不登记）
        b.scope("abilities.fluorescent_laser")
                .intParam("window_ticks", 100, 20, 1200, AB + "FluorescentLaserManager", "WINDOW_TICKS")
                .intParam("shot_ticks", 8, 1, 100, AB + "FluorescentLaserManager", "SHOT_TICKS")
                .intParam("shot_damage_interval", 2, 1, 20, AB + "FluorescentLaserManager", "SHOT_DAMAGE_INTERVAL")
                .doubleParam("shot_damage", 12.0, 0.0, 1000.0, AB + "FluorescentLaserManager", "SHOT_DAMAGE")
                .doubleParam("speed_penalty", -0.5, -4.0, 0.0, AB + "FluorescentLaserManager", "SPEED_PENALTY")
                .doubleParam("enh_beam_length", 24.0, 4.0, 64.0, AB + "FluorescentLaserManager", "ENH_BEAM_LENGTH")
                .doubleParam("enh_beam_radius", 0.75, 0.1, 8.0, AB + "FluorescentLaserManager", "ENH_BEAM_RADIUS")
                .doubleParam("aim_cone_deg", 10.0, 1.0, 90.0, AB + "FluorescentLaserManager", "AIM_CONE_DEG");
        // 荧光幼灵潮汐（FluorescentTidalManager）
        b.scope("abilities.fluorescent_tidal")
                .intParam("charge_ticks", 25, 1, 200, AB + "FluorescentTidalManager", "CHARGE_TICKS")
                .doubleParam("charge_speed_penalty", -0.5, -4.0, 0.0, AB + "FluorescentTidalManager", "CHARGE_SPEED_PENALTY");
        // 激光束实体（LaserBeamEntity）
        b.scope("abilities.laser_beam")
                .intParam("charge_ticks", 140, 1, 1200, EN + "LaserBeamEntity", "CHARGE_TICKS")
                .intParam("release_ticks", 60, 1, 600, EN + "LaserBeamEntity", "RELEASE_TICKS")
                .intParam("fade_ticks", 30, 1, 200, EN + "LaserBeamEntity", "FADE_TICKS")
                .doubleParam("beam_length", 32.0, 4.0, 128.0, EN + "LaserBeamEntity", "BEAM_LENGTH")
                .doubleParam("beam_radius", 2.5, 0.5, 16.0, EN + "LaserBeamEntity", "BEAM_RADIUS")
                .doubleParam("enh_beam_radius", 0.75, 0.1, 8.0, EN + "LaserBeamEntity", "ENH_BEAM_RADIUS")
                .intParam("enh_shot_ticks", 8, 1, 100, EN + "LaserBeamEntity", "ENH_SHOT_TICKS")
                .intParam("damage_interval", 10, 1, 100, EN + "LaserBeamEntity", "DAMAGE_INTERVAL")
                .doubleParam("damage", 20.0, 0.0, 1000.0, EN + "LaserBeamEntity", "DAMAGE");
        // 契灵标记（MancianimaMarkManager；OUT_OF_COMBAT_TICKS 属脱战去抖一并登记供调）
        b.scope("abilities.mancianima_mark")
                .intParam("mark_duration_ticks", 300, 20, 4800, AB + "MancianimaMarkManager", "MARK_DURATION_TICKS")
                .intParam("range_orange", 24, 4, 64, AB + "MancianimaMarkManager", "RANGE_ORANGE")
                .intParam("range_red_keep", 24, 4, 64, AB + "MancianimaMarkManager", "RANGE_RED_KEEP")
                .intParam("red_relock_cooldown_ticks", 300, 20, 4800, AB + "MancianimaMarkManager", "RED_RELOCK_COOLDOWN_TICKS")
                .intParam("stage_gate_ticks", 60, 1, 600, AB + "MancianimaMarkManager", "STAGE_GATE_TICKS")
                .intParam("out_of_combat_ticks", 100, 20, 1200, AB + "MancianimaMarkManager", "OUT_OF_COMBAT_TICKS")
                .intParam("resist_regen_interval_ticks", 300, 20, 4800, AB + "MancianimaMarkManager", "RESIST_REGEN_INTERVAL_TICKS")
                .intParam("upgrade_fox_mana_regen_interval_ticks", 20, 1, 200, AB + "MancianimaMarkManager", "UPGRADE_FOX_MANA_REGEN_INTERVAL_TICKS")
                .doubleParam("upgrade_fox_mana_regen_amount", 1.0, 0.0, 100.0, AB + "MancianimaMarkManager", "UPGRADE_FOX_MANA_REGEN_AMOUNT");
        // 契灵三段标记主技能（MancianimaPrimary）
        b.scope("abilities.mancianima_primary")
                .intParam("mark_mana_cost", 15, 0, 100, AB + "MancianimaPrimary", "MARK_MANA_COST")
                .intParam("fizzle_mana_cost", 5, 0, 100, AB + "MancianimaPrimary", "FIZZLE_MANA_COST")
                .intParam("red_trigger_interval", 20, 1, 200, AB + "MancianimaPrimary", "RED_TRIGGER_INTERVAL")
                .doubleParam("mark_range", 32.0, 4.0, 128.0, AB + "MancianimaPrimary", "MARK_RANGE")
                .doubleParam("red_lock_range", 24.0, 4.0, 64.0, AB + "MancianimaPrimary", "RED_LOCK_RANGE")
                .intParam("channel_damage_ticks", 40, 1, 600, AB + "MancianimaPrimary", "CHANNEL_DAMAGE_TICKS")
                .doubleParam("damage_percent", 0.20, 0.0, 1.0, AB + "MancianimaPrimary", "DAMAGE_PERCENT")
                .doubleParam("damage_min", 2.0, 0.0, 100.0, AB + "MancianimaPrimary", "DAMAGE_MIN")
                .doubleParam("damage_cap", 37.0, 1.0, 1000.0, AB + "MancianimaPrimary", "DAMAGE_CAP")
                .intParam("mana_regen_pause_ticks", 100, 0, 1200, AB + "MancianimaPrimary", "MANA_REGEN_PAUSE_TICKS");
        // 契灵魂跃瞬移（MancianimaTeleport）
        b.scope("abilities.mancianima_teleport")
                .intParam("mana_cost", 5, 0, 100, AB + "MancianimaTeleport", "MANA_COST")
                .intParam("red_mark_mana_cost", 20, 0, 100, AB + "MancianimaTeleport", "RED_MARK_MANA_COST")
                .doubleParam("max_range", 8.0, 1.0, 64.0, AB + "MancianimaTeleport", "MAX_RANGE")
                .doubleParam("red_mark_target_range", 32.0, 4.0, 128.0, AB + "MancianimaTeleport", "RED_MARK_TARGET_RANGE")
                .intParam("red_mark_channel_ticks", 20, 1, 200, AB + "MancianimaTeleport", "RED_MARK_CHANNEL_TICKS")
                .doubleParam("red_mark_damage_cap", 37.0, 1.0, 1000.0, AB + "MancianimaTeleport", "RED_MARK_DAMAGE_CAP")
                .doubleParam("red_mark_damage_min", 2.0, 0.0, 100.0, AB + "MancianimaTeleport", "RED_MARK_DAMAGE_MIN")
                .doubleParam("red_mark_damage_percent", 0.50, 0.0, 1.0, AB + "MancianimaTeleport", "RED_MARK_DAMAGE_PERCENT")
                .intParam("mana_regen_pause_ticks", 100, 0, 1200, AB + "MancianimaTeleport", "MANA_REGEN_PAUSE_TICKS");
        // 吸血蝙蝠血渴资源（BatDesmodusBloodThirst；ATTACK_HIT_CD 去抖与阶段阈值不登记）
        b.scope("abilities.blood_thirst_resource")
                .intParam("max_blood", 100, 20, 1000, AB + "BatDesmodusBloodThirst", "MAX_BLOOD")
                .intParam("attack_hit_gain", 8, 0, 100, AB + "BatDesmodusBloodThirst", "ATTACK_HIT_GAIN")
                .intParam("skill_hit_base", 12, 0, 100, AB + "BatDesmodusBloodThirst", "SKILL_HIT_BASE")
                .intParam("skill_hit_max_targets", 3, 1, 16, AB + "BatDesmodusBloodThirst", "SKILL_HIT_MAX_TARGETS")
                .intParam("out_of_combat_delay", 240, 20, 1200, AB + "BatDesmodusBloodThirst", "OUT_OF_COMBAT_DELAY")
                .intParam("decay_per_sec", 4, 0, 100, AB + "BatDesmodusBloodThirst", "DECAY_PER_SEC");
        // 装死黄心（PlayingDeadEffect + PlayingDeadAbsorptionManager；默认值与 float 常量逐位一致）
        b.scope("abilities.playing_dead")
                .doubleParam("heal_per_tick", 2.5, 0.0, 100.0, EF + "PlayingDeadEffect", "DEFAULT_HEAL_PER_TICK")
                .doubleParam("necklace_heal_per_tick", 0.8333333, 0.0, 100.0, EF + "PlayingDeadEffect", "NECKLACE_HEAL_PER_TICK")
                .doubleParam("necklace_absorb_per_tick", 3.3333333, 0.0, 100.0, EF + "PlayingDeadEffect", "NECKLACE_ABSORB_PER_TICK")
                .doubleParam("necklace_absorb_max", 40.0, 0.0, 1000.0, EF + "PlayingDeadEffect", "NECKLACE_ABSORB_MAX")
                .intParam("retain_ticks", 600, 20, 4800, AB + "PlayDeadAbsorptionManager", "RETAIN_TICKS")
                .intParam("decay_interval", 20, 1, 200, AB + "PlayDeadAbsorptionManager", "DECAY_INTERVAL")
                .doubleParam("decay_per_sec", 2.0, 0.0, 100.0, AB + "PlayDeadAbsorptionManager", "DECAY_PER_SEC");
        // 蓝火环（blueFireRingEffect）
        b.scope("abilities.blue_fire_ring")
                .doubleParam("freeze_chance", 0.06, 0.0, 1.0, EF + "BlueFireRingEffect", "FREEZE_CHANCE")
                .doubleParam("freeze_radius_default", 6.0, 0.5, 32.0, EF + "BlueFireRingEffect", "FREEZE_RADIUS_DEFAULT")
                .doubleParam("freeze_radius_amulet", 3.6, 0.5, 32.0, EF + "BlueFireRingEffect", "FREEZE_RADIUS_AMULET")
                .intParam("attack_interval", 16, 1, 100, EF + "BlueFireRingEffect", "ATTACK_INTERVAL");
        // 咒印效果（CurseMarkEffect）
        b.scope("abilities.curse_mark")
                .doubleParam("taken_base", 0.2, 0.0, 2.0, EF + "CurseMarkEffect", "BASE_BONUS")
                .doubleParam("taken_per_level", 0.1, 0.0, 1.0, EF + "CurseMarkEffect", "BONUS_PER_LEVEL")
                .doubleParam("output_weaken_base", 0.15, 0.0, 1.0, EF + "CurseMarkEffect", "OUTPUT_WEAKEN_BASE")
                .doubleParam("output_weaken_per_level", 0.0875, 0.0, 1.0, EF + "CurseMarkEffect", "OUTPUT_WEAKEN_PER_LEVEL");
        // 冰霜碎裂/蜘网缠身效果
        b.scope("abilities.frost_shatter")
                .doubleParam("armor_reduction", -0.5, -4.0, 0.0, EF + "FrostShatterEffect", "ARMOR_REDUCTION");
        b.scope("abilities.web_bound")
                .intParam("sub_duration", 40, 1, 600, EF + "SpiderWebBoundEffect", "SUB_DURATION");
        // 幻铃瞬移（PhantomBellTeleportAction）
        b.scope("abilities.phantom_bell")
                .doubleParam("detection_radius", 20.0, 1.0, 64.0, "action.PhantomBellTeleportAction", "DETECTION_RADIUS")
                .intParam("tp_radius", 5, 1, 32, "action.PhantomBellTeleportAction", "TP_RADIUS");
        // 雪狐/寒棘狐实体（FrostArray/FrostBall/FrostStorm/FrostThorn；物理插值与音效常量不登记）
        b.scope("abilities.frost_array")
                .intParam("max_ticks", 400, 20, 4800, EN + "FrostArrayEntity", "MAX_TICKS");
        b.scope("abilities.frost_ball")
                .doubleParam("speed", 0.75, 0.05, 4.0, EN + "FrostBallEntity", "SPEED")
                .doubleParam("max_distance", 50.0, 4.0, 256.0, EN + "FrostBallEntity", "MAX_DISTANCE")
                .intParam("frost_fall_duration", 80, 1, 1200, EN + "FrostBallEntity", "FROST_FALL_DURATION")
                // 回能锁（主 CD 已回归 power JSON fail_aware_active_self 原生管理）
                .intParam("regen_lock_ticks", 100, 0, 1200, "action.SscAddonActions", "FROST_BALL_REGEN_LOCK_TICKS");
        b.scope("abilities.frost_storm")
                .intParam("duration", 200, 20, 4800, EN + "FrostStormEntity", "DURATION")
                .doubleParam("damage_radius", 3.5, 0.5, 16.0, EN + "FrostStormEntity", "DAMAGE_RADIUS")
                .doubleParam("pull_radius_strong", 6.0, 0.5, 32.0, EN + "FrostStormEntity", "PULL_RADIUS_STRONG")
                .doubleParam("pull_radius_weak", 10.0, 0.5, 64.0, EN + "FrostStormEntity", "PULL_RADIUS_WEAK")
                .doubleParam("damage_per_second", 2.0, 0.0, 100.0, EN + "FrostStormEntity", "DAMAGE_PER_SECOND")
                .doubleParam("pull_speed", 0.1, 0.0, 4.0, EN + "FrostStormEntity", "PULL_SPEED");
        b.scope("abilities.frost_thorn")
                .doubleParam("speed", 0.8, 0.05, 4.0, EN + "FrostThornEntity", "SPEED")
                .doubleParam("straight_dist", 16.0, 1.0, 64.0, EN + "FrostThornEntity", "STRAIGHT_DIST")
                .intParam("max_fly_ticks", 100, 5, 1200, EN + "FrostThornEntity", "MAX_FLY_TICKS")
                .intParam("max_hover_ticks", 1200, 20, 24000, EN + "FrostThornEntity", "MAX_HOVER_TICKS")
                .doubleParam("max_fly_dist", 128.0, 8.0, 512.0, EN + "FrostThornEntity", "MAX_FLY_DIST")
                .doubleParam("damage", 8.0, 0.0, 1000.0, EN + "FrostThornEntity", "DAMAGE")
                .intParam("enhanced_max_fly_ticks", 200, 5, 2400, EN + "FrostThornEntity", "ENHANCED_MAX_FLY_TICKS")
                .doubleParam("enhanced_base_damage", 8.0, 0.0, 1000.0, EN + "FrostThornEntity", "ENHANCED_BASE_DAMAGE");
        // 月灵光矢（LunarSpiritBoltEntity）
        b.scope("abilities.lunar_spirit_bolt")
                .doubleParam("speed", 0.7, 0.05, 4.0, EN + "LunarSpiritBoltEntity", "SPEED")
                .doubleParam("turn_rate", 0.14, 0.0, 2.0, EN + "LunarSpiritBoltEntity", "TURN_RATE")
                .intParam("max_ticks", 80, 5, 1200, EN + "LunarSpiritBoltEntity", "MAX_TICKS");
        // 法术投射实体（Spell*Entity；速度/射程统一登记到 spells.* 邻近 scope）
        b.scope("spells.curse_mark_entity")
                .doubleParam("speed", 0.7, 0.05, 4.0, EN + "SpellCurseMarkEntity", "SPEED")
                .doubleParam("max_distance", 30.0, 4.0, 256.0, EN + "SpellCurseMarkEntity", "MAX_DISTANCE");
        b.scope("spells.fire_bolt_entity")
                .doubleParam("speed", 0.8, 0.05, 4.0, EN + "SpellFireBoltEntity", "SPEED")
                .doubleParam("max_distance", 50.0, 4.0, 256.0, EN + "SpellFireBoltEntity", "MAX_DISTANCE");
        b.scope("spells.frost_spike_entity")
                .doubleParam("speed", 0.75, 0.05, 4.0, EN + "SpellFrostSpikeEntity", "SPEED")
                .doubleParam("max_distance", 50.0, 4.0, 256.0, EN + "SpellFrostSpikeEntity", "MAX_DISTANCE");
        b.scope("spells.meteor_entity")
                .intParam("delay_ticks", 10, 1, 100, EN + "SpellMeteorEntity", "DELAY_TICKS")
                .doubleParam("fall_height", 20.0, 4.0, 128.0, EN + "SpellMeteorEntity", "FALL_HEIGHT")
                .doubleParam("fall_speed", 2.5, 0.1, 8.0, EN + "SpellMeteorEntity", "FALL_SPEED");
        b.scope("spells.moonlight_arrow_entity")
                .doubleParam("speed", 1.0, 0.05, 4.0, EN + "SpellMoonlightArrowEntity", "SPEED")
                .doubleParam("max_distance", 50.0, 4.0, 256.0, EN + "SpellMoonlightArrowEntity", "MAX_DISTANCE");
        b.scope("abilities.water_spear_entity")
                .doubleParam("speed", 8.5, 0.5, 32.0, EN + "ThrownWaterSpearEntity", "SPEED")
                .doubleParam("max_distance", 64.0, 4.0, 256.0, EN + "ThrownWaterSpearEntity", "MAX_DISTANCE")
                .doubleParam("direct_damage", 12.0, 0.0, 1000.0, EN + "ThrownWaterSpearEntity", "DIRECT_DAMAGE")
                .doubleParam("aoe_damage", 5.0, 0.0, 1000.0, EN + "ThrownWaterSpearEntity", "AOE_DAMAGE")
                .doubleParam("aoe_radius", 2.0, 0.5, 16.0, EN + "ThrownWaterSpearEntity", "AOE_RADIUS");
        b.scope("abilities.web_membrane")
                .doubleParam("aura_radius", 8.0, 1.0, 64.0, EN + "WebMembraneBullet", "AURA_RADIUS")
                .intParam("aura_duration", 100, 5, 1200, EN + "WebMembraneBullet", "AURA_DURATION");
        b.scope("abilities.parasitic_seed_projectile")
                .intParam("default_field_life", 240, 20, 4800, EN + "ParasiticSeedProjectile", "DEFAULT_FIELD_LIFE");

        // ==== 形态能力批次（§8.2 前四组；默认值 = 代码现值，pin 反射核验）====

        // —— 雪狐/寒棘狐组 ——
        b.scope("abilities.snow_fox_sp_melee")            // SnowFoxSpMeleeAbility：雪刺冲刺
                .doubleParam("dash_distance", 8.0, 1.0, 48.0, AB + "SnowFoxSpMeleeAbility", "DASH_DISTANCE")
                .doubleParam("dash_speed", 1.5, 0.1, 8.0, AB + "SnowFoxSpMeleeAbility", "DASH_SPEED")
                .doubleParam("damage", 8.0, 0.0, 1000.0, AB + "SnowFoxSpMeleeAbility", "DAMAGE")
                .intParam("freeze_ticks", 60, 0, 1200, AB + "SnowFoxSpMeleeAbility", "FROST_FREEZE_DURATION")
                .intParam("mana_cost", 15, 0, 100, AB + "SnowFoxSpMeleeAbility", "MANA_COST")
                // 回能锁（CD 已回归 power JSON fail_aware_active_self 原生管理）
                .intParam("regen_lock_ticks", 100, 0, 1200, AB + "SnowFoxSpMeleeAbility", "MELEE_REGEN_LOCK_TICKS");
        b.scope("abilities.snow_fox_sp_teleport")         // SnowFoxSpTeleportAttack：瞬移攻击
                .doubleParam("range", 10.0, 1.0, 64.0, AB + "SnowFoxSpTeleportAttack", "RANGE")
                .intParam("max_targets", 3, 1, 16, AB + "SnowFoxSpTeleportAttack", "MAX_TARGETS")
                .doubleParam("base_damage", 6.0, 0.0, 1000.0, AB + "SnowFoxSpTeleportAttack", "BASE_DAMAGE")
                .doubleParam("bonus_damage", 3.0, 0.0, 1000.0, AB + "SnowFoxSpTeleportAttack", "BONUS_DAMAGE")
                .intParam("mana_cost_success", 30, 0, 100, AB + "SnowFoxSpTeleportAttack", "MANA_COST_SUCCESS")
                .intParam("mana_cost_fail", 20, 0, 100, AB + "SnowFoxSpTeleportAttack", "MANA_COST_FAIL")
                .intParam("teleport_interval", 10, 1, 100, AB + "SnowFoxSpTeleportAttack", "TELEPORT_INTERVAL")
                .doubleParam("damage_reduction", 0.65, 0.0, 1.0, AB + "SnowFoxSpTeleportAttack", "DAMAGE_REDUCTION")
                // 回能锁（CD 已回归 power JSON fail_aware_active_self 原生管理：cooldown=成功 / fail_cooldown=失败）
                .intParam("regen_lock_ticks", 100, 0, 1200, AB + "SnowFoxSpTeleportAttack", "TELEPORT_REGEN_LOCK_TICKS");
        b.scope("abilities.snow_fox_sp_frost_storm")      // SnowFoxSpFrostStorm：冰风暴
                .intParam("charge_ticks", 30, 1, 200, AB + "SnowFoxSpFrostStorm", "CHARGE_TICKS")
                .doubleParam("max_range", 30.0, 1.0, 128.0, AB + "SnowFoxSpFrostStorm", "MAX_RANGE")
                .intParam("mana_cost", 30, 0, 100, AB + "SnowFoxSpFrostStorm", "MANA_COST")
                // 回能锁（主 CD 已回归 power JSON fail_aware_active_self 原生管理）
                .intParam("regen_lock_ticks", 100, 0, 1200, AB + "SnowFoxSpFrostStorm", "REGEN_LOCK_TICKS");
        b.scope("abilities.frost_spike_manager")          // FrostSpikeManager：寒棘狐冰刺
                .intParam("charge_interval", 24, 1, 200, AB + "FrostSpikeManager", "CHARGE_INTERVAL")
                .intParam("max_thorns", 5, 1, 5, AB + "FrostSpikeManager", "MAX_THORNS")
                .intParam("secondary_consume_interval", 20, 1, 200, AB + "FrostSpikeManager", "SECONDARY_CONSUME_INTERVAL")
                .doubleParam("secondary_slow_amount", -0.90, -4.0, 0.0, AB + "FrostSpikeManager", "SECONDARY_SLOW_AMOUNT");
        b.scope("abilities.frost_armor_manager")          // FrostArmorManager：霜甲
                .doubleParam("per_thorn_reduction", 0.04, 0.0, 1.0, AB + "FrostArmorManager", "PER_THORN_REDUCTION")
                .intParam("burst_at", 3, 1, 16, AB + "FrostArmorManager", "BURST_AT")
                .intParam("layer_expire_ticks", 50, 1, 1200, AB + "FrostArmorManager", "LAYER_EXPIRE_TICKS")
                .intParam("re_layer_gap_ticks", 10, 0, 200, AB + "FrostArmorManager", "RE_LAYER_GAP_TICKS")
                .intParam("burst_freeze_ticks", 20, 0, 1200, AB + "FrostArmorManager", "BURST_FREEZE_TICKS")
                .doubleParam("burst_damage", 2.0, 0.0, 1000.0, AB + "FrostArmorManager", "BURST_DAMAGE");

        // —— 使魔组（狐火/斩杀强化；KillEmpower 的 READY/RING/WINDOW 状态编号不开放）——
        b.scope("abilities.kill_empower_cast")            // KillEmpowerCast：狐火吐息参数
                .doubleParam("breath_distance", 8.0, 1.0, 32.0, AB + "KillEmpowerCast", "BREATH_DISTANCE")
                .doubleParam("breath_damage", 6.0, 0.0, 1000.0, AB + "KillEmpowerCast", "BREATH_DAMAGE")
                .doubleParam("breath_mult_sp", 3.0, 0.0, 10.0, AB + "KillEmpowerCast", "BREATH_NON_PLAYER_MULT_SP")
                .doubleParam("breath_mult_red", 1.5, 0.0, 10.0, AB + "KillEmpowerCast", "BREATH_NON_PLAYER_MULT_RED")
                .intParam("burn_duration", 100, 0, 2400, AB + "KillEmpowerCast", "BURN_DURATION");
        b.scope("abilities.kill_empower_ring")            // KillEmpowerManager：强化环持续
                .intParam("ring_ticks_sp", 232, 1, 2400, AB + "KillEmpowerManager", "EMPOWER_RING_TICKS_SP")
                .intParam("ring_ticks_red", 280, 1, 2400, AB + "KillEmpowerManager", "EMPOWER_RING_TICKS_RED");
        b.scope("abilities.fox_fireball")                 // FoxFireballEntity：狐火火球
                .doubleParam("arm_distance", 12.0, 1.0, 48.0, EN + "FoxFireballEntity", "ARM_DISTANCE")
                .doubleParam("hit_radius", 2.5, 0.5, 8.0, EN + "FoxFireballEntity", "HIT_RADIUS")
                .doubleParam("explode_radius", 6.0, 0.5, 16.0, EN + "FoxFireballEntity", "EXPLODE_RADIUS")
                .doubleParam("chain_radius", 2.0, 0.5, 8.0, EN + "FoxFireballEntity", "CHAIN_RADIUS")
                .doubleParam("pierce_damage", 8.0, 0.0, 1000.0, EN + "FoxFireballEntity", "PIERCE_DAMAGE")
                .doubleParam("explode_damage", 6.0, 0.0, 1000.0, EN + "FoxFireballEntity", "EXPLODE_DAMAGE")
                .doubleParam("chain_damage", 4.0, 0.0, 1000.0, EN + "FoxFireballEntity", "CHAIN_DAMAGE")
                .doubleParam("non_player_mult", 1.5, 0.0, 10.0, EN + "FoxFireballEntity", "NON_PLAYER_MULT")
                .intParam("ring_duration", 7, 1, 100, EN + "FoxFireballEntity", "RING_DURATION")
                .doubleParam("phase2_speed", 2.0, 0.1, 8.0, EN + "FoxFireballEntity", "PHASE2_SPEED")
                .intParam("phase2_duration", 60, 1, 600, EN + "FoxFireballEntity", "PHASE2_DURATION");

        // —— 美西螈组（水枪/水矛/漩涡；AxolotlShifterEntity 为召唤物 AI，整组暂不开放，见台账）——
        b.scope("abilities.axolotl_water_spurt")          // AxolotlWaterSpurtHandler：水枪
                .intParam("cd_ticks", 100, 1, 2400, AB + "AxolotlWaterSpurtHandler", "CD_TICKS")
                .doubleParam("burst", 1.6, 0.1, 8.0, AB + "AxolotlWaterSpurtHandler", "BURST")
                .intParam("land_moisture_cost", 12, 0, 100, AB + "AxolotlWaterSpurtHandler", "LAND_MOISTURE_COST");
        b.scope("abilities.water_spear_leap")             // WaterSpearLeapManager：水矛跳劈
                .intParam("charge_ticks", 27, 1, 200, AB + "WaterSpearLeapManager", "CHARGE_TICKS")
                .intParam("air_cost", 18, 0, 100, AB + "WaterSpearLeapManager", "AIR_COST")
                .doubleParam("leap_back", 0.80, 0.0, 4.0, AB + "WaterSpearLeapManager", "LEAP_BACK")
                .doubleParam("leap_up", 0.62, 0.0, 4.0, AB + "WaterSpearLeapManager", "LEAP_UP")
                .doubleParam("decay", 0.80, 0.0, 1.0, AB + "WaterSpearLeapManager", "DECAY");
        b.scope("abilities.vortex_charge")                // VortexChargeManager：漩涡蓄力
                .intParam("air_per_hit", 8, 0, 100, AB + "VortexChargeManager", "AIR_PER_HIT")
                .intParam("max_air_spent", 60, 0, 300, AB + "VortexChargeManager", "MAX_AIR_SPENT")
                .intParam("max_ticks", 80, 1, 1200, AB + "VortexChargeManager", "MAX_TICKS")
                .intParam("hit_interval", 10, 1, 100, AB + "VortexChargeManager", "HIT_INTERVAL")
                .intParam("damage_per_hit", 2, 0, 1000, AB + "VortexChargeManager", "DAMAGE_PER_HIT")
                .doubleParam("radius", 3.0, 0.5, 16.0, AB + "VortexChargeManager", "RADIUS")
                .doubleParam("pull_radius", 6.0, 0.5, 32.0, AB + "VortexChargeManager", "PULL_RADIUS")
                .doubleParam("pull_force", 0.6, 0.0, 4.0, AB + "VortexChargeManager", "PULL_FORCE");
        b.scope("abilities.vortex_guide")                 // VortexGuideManager：漩涡引导
                .intParam("channel_ticks", 60, 1, 1200, AB + "VortexGuideManager", "CHANNEL_TICKS")
                .intParam("heal_interval", 10, 1, 100, AB + "VortexGuideManager", "HEAL_INTERVAL")
                .doubleParam("heal_per_tick", 2.0, 0.0, 100.0, AB + "VortexGuideManager", "HEAL_PER_TICK")
                .intParam("absorption_duration", 600, 1, 4800, AB + "VortexGuideManager", "ABSORPTION_DURATION");
        b.scope("abilities.tidal_orb")                    // TidalOrbEntity：潮汐宝珠
                .doubleParam("fly_speed", 0.2, 0.05, 4.0, EN + "TidalOrbEntity", "FLY_SPEED")
                .intParam("max_fly_ticks", 160, 10, 1200, EN + "TidalOrbEntity", "MAX_FLY_TICKS")
                .intParam("decel_ticks", 15, 1, 100, EN + "TidalOrbEntity", "DECEL_TICKS")
                .doubleParam("tether_catch_radius", 6.0, 0.5, 16.0, EN + "TidalOrbEntity", "TETHER_CATCH_RADIUS")
                .doubleParam("tether_soft_radius", 6.0, 0.5, 16.0, EN + "TidalOrbEntity", "TETHER_SOFT_RADIUS")
                .doubleParam("tether_hard_radius", 8.0, 0.5, 32.0, EN + "TidalOrbEntity", "TETHER_HARD_RADIUS")
                .intParam("tether_duration_ticks", 170, 10, 1200, EN + "TidalOrbEntity", "TETHER_DURATION_TICKS")
                .doubleParam("tether_pull_per_block", 0.30, 0.0, 4.0, EN + "TidalOrbEntity", "TETHER_PULL_PER_BLOCK")
                .doubleParam("tether_vertical_damp", 0.4, 0.0, 1.0, EN + "TidalOrbEntity", "TETHER_VERTICAL_DAMP")
                .intParam("pop_delay_ticks", 7, 1, 100, EN + "TidalOrbEntity", "POP_DELAY_TICKS");

        // —— 悦灵组（群疗/唱片机/图腾/远程命中被动）——
        b.scope("abilities.allay_sp_group_heal")          // AllaySPGroupHeal
                .doubleParam("heal_radius", 20.0, 1.0, 64.0, AB + "AllaySPGroupHeal", "HEAL_RADIUS")
                .intParam("resistance_ticks", 200, 1, 4800, AB + "AllaySPGroupHeal", "RESISTANCE_TICKS")
                .intParam("solo_blessing_ticks", 400, 1, 4800, AB + "AllaySPGroupHeal", "SOLO_BLESSING_TICKS")
                .doubleParam("heal_percent", 0.75, 0.0, 1.0, AB + "AllaySPGroupHeal", "HEAL_PERCENT")
                .doubleParam("absorption_percent", 0.5, 0.0, 1.0, AB + "AllaySPGroupHeal", "ABSORPTION_PERCENT")
                .intParam("heal_absorption_ticks", 600, 1, 4800, AB + "AllaySPGroupHeal", "HEAL_ABSORPTION_TICKS");
        b.scope("abilities.allay_sp_jukebox")             // AllaySPJukebox
                .doubleParam("range", 20.0, 1.0, 64.0, AB + "AllaySPJukebox", "RANGE")
                .doubleParam("speed_bonus", 0.10, -2.0, 2.0, AB + "AllaySPJukebox", "SPEED_BONUS")
                .intParam("buff_refresh_duration", 20, 1, 200, AB + "AllaySPJukebox", "BUFF_REFRESH_DURATION");
        b.scope("abilities.allay_sp_totem")               // AllaySPTotem
                .doubleParam("range", 20.0, 1.0, 64.0, AB + "AllaySPTotem", "RANGE");
        b.scope("abilities.allay_sp_ranged_hit")          // AllaySPRangedHitPassive
                .intParam("cooldown_ticks", 70, 1, 1200, AB + "AllaySPRangedHitPassive", "COOLDOWN_TICKS")
                .doubleParam("heal_amount", 1.0, 0.0, 100.0, AB + "AllaySPRangedHitPassive", "HEAL_AMOUNT")
                .doubleParam("mana_restore_ratio", 0.08, 0.0, 1.0, AB + "AllaySPRangedHitPassive", "MANA_RESTORE_RATIO");

        // —— 野猫/食梦魔组 ——
        b.scope("abilities.nine_lives")                    // NineLivesManager：九命
                .intParam("max_lives", 8, 1, 9, AB + "NineLivesManager", "MAX_LIVES")
                .intParam("regen_interval", 400, 20, 4800, AB + "NineLivesManager", "REGEN_INTERVAL")
                .intParam("out_of_combat_ticks", 200, 20, 2400, AB + "NineLivesManager", "OUT_OF_COMBAT_TICKS")
                .intParam("revive_cd_ticks", 60, 1, 1200, AB + "NineLivesManager", "REVIVE_CD_TICKS")
                .intParam("invuln_ticks", 20, 1, 200, AB + "NineLivesManager", "INVULN_TICKS")
                .doubleParam("revive_heal", 6.0, 1.0, 100.0, AB + "NineLivesManager", "REVIVE_HEAL")
                .intParam("absorb_duration", 600, 1, 4800, AB + "NineLivesManager", "ABSORB_DURATION")
                .intParam("absorb_amplifier", 2, 0, 5, AB + "NineLivesManager", "ABSORB_AMPLIFIER")
                .doubleParam("revive_heal_necklace", 8.0, 1.0, 100.0, AB + "NineLivesManager", "REVIVE_HEAL_NECKLACE")
                .intParam("invuln_ticks_necklace", 36, 1, 200, AB + "NineLivesManager", "INVULN_TICKS_NECKLACE");
        b.scope("abilities.nightmare_fear")                // NightmareFearManager：恐惧
                .intParam("duration_ticks", 300, 20, 2400, AB + "NightmareFearManager", "FEAR_DURATION_TICKS")
                .intParam("dream_immune_ticks", 400, 20, 4800, AB + "NightmareFearManager", "DREAM_IMMUNE_TICKS")
                .doubleParam("slow_ratio", 0.20, 0.0, 1.0, AB + "NightmareFearManager", "FEAR_SLOW_RATIO")
                .intParam("mob_aggro_window_ticks", 40, 1, 200, AB + "NightmareFearManager", "MOB_AGGRO_WINDOW_TICKS")
                .doubleParam("sight_radius", 16.0, 1.0, 64.0, AB + "NightmareFearManager", "FEAR_SIGHT_RADIUS")
                .intParam("reveal_on_attack_ticks", 30, 1, 200, AB + "NightmareFearManager", "REVEAL_ON_ATTACK_TICKS");
        b.scope("abilities.nightmare_spook")               // NightmareSpookManager：惊吓
                .doubleParam("clone_damage", 12.0, 0.0, 1000.0, AB + "NightmareSpookManager", "CLONE_DAMAGE")
                .intParam("creeper_life_ticks", 60, 1, 600, AB + "NightmareSpookManager", "CREEPER_LIFE_TICKS")
                .intParam("cat_life_ticks", 90, 1, 600, AB + "NightmareSpookManager", "CAT_LIFE_TICKS")
                .doubleParam("cat_run_speed", 0.32, 0.05, 2.0, AB + "NightmareSpookManager", "CAT_RUN_SPEED")
                .doubleParam("cat_leap_distance", 4.0, 0.5, 16.0, AB + "NightmareSpookManager", "CAT_LEAP_DISTANCE");
        b.scope("abilities.nightmare_dream")               // NightmareDreamManager：入梦
                .doubleParam("threshold", 10.0, 1.0, 100.0, AB + "NightmareDreamManager", "DREAM_THRESHOLD")
                .doubleParam("threshold_cursed_moon", 6.0, 1.0, 100.0, AB + "NightmareDreamManager", "DREAM_THRESHOLD_CURSED_MOON")
                .doubleParam("lifesteal_ratio", 0.15, 0.0, 1.0, AB + "NightmareDreamManager", "DREAM_LIFESTEAL_RATIO")
                .doubleParam("kill_heal", 4.0, 0.0, 100.0, AB + "NightmareDreamManager", "DREAM_KILL_HEAL")
                .intParam("duration_ticks", 400, 20, 4800, AB + "NightmareDreamManager", "DREAM_DURATION_TICKS")
                .intParam("fixed_ticks", 200, 1, 2400, AB + "NightmareDreamManager", "DREAM_FIXED_TICKS");
        b.scope("abilities.wild_cat_pace")                 // WildCatPaceManager：夜行移速
                .doubleParam("night_speed", 0.20, -2.0, 2.0, AB + "WildCatPaceManager", "NIGHT_SPEED")
                .intParam("check_interval", 20, 1, 200, AB + "WildCatPaceManager", "CHECK_INTERVAL");

        // —— 风灵/Nova 组 ——
        b.scope("abilities.wind_spirit_claw")              // WindSpiritClawManager：风爪
                .doubleParam("base_damage", 8.0, 0.0, 1000.0, AB + "WindSpiritClawManager", "BASE_DAMAGE")
                .doubleParam("radius", 2.5, 0.5, 16.0, AB + "WindSpiritClawManager", "RADIUS")
                .doubleParam("reach", 2.0, 0.5, 8.0, AB + "WindSpiritClawManager", "REACH")
                .intParam("max_interval", 20, 1, 100, AB + "WindSpiritClawManager", "MAX_INTERVAL")
                .intParam("min_interval", 7, 1, 100, AB + "WindSpiritClawManager", "MIN_INTERVAL")
                .intParam("ramp_ticks", 60, 1, 600, AB + "WindSpiritClawManager", "RAMP_TICKS")
                .intParam("max_claw_ticks", 200, 1, 2400, AB + "WindSpiritClawManager", "MAX_CLAW_TICKS")
                .intParam("recover_ticks", 160, 1, 2400, AB + "WindSpiritClawManager", "RECOVER_TICKS")
                .intParam("recover_ticks_necklace", 110, 1, 2400, AB + "WindSpiritClawManager", "RECOVER_TICKS_NECKLACE")
                .doubleParam("dmg_decay_per_sec", 0.12, 0.0, 1.0, AB + "WindSpiritClawManager", "DMG_DECAY_PER_SEC")
                .doubleParam("dmg_decay_max", 0.36, 0.0, 1.0, AB + "WindSpiritClawManager", "DMG_DECAY_MAX")
                .doubleParam("spd_decay_per_sec", 0.15, 0.0, 1.0, AB + "WindSpiritClawManager", "SPD_DECAY_PER_SEC")
                .doubleParam("spd_decay_max", 0.45, 0.0, 1.0, AB + "WindSpiritClawManager", "SPD_DECAY_MAX")
                .doubleParam("exhaustion_per_hit", 0.64, 0.0, 4.0, AB + "WindSpiritClawManager", "EXHAUSTION_PER_HIT")
                .doubleParam("forward_lunge", 0.28, 0.0, 2.0, AB + "WindSpiritClawManager", "FORWARD_LUNGE")
                .intParam("buff_duration", 10, 1, 100, AB + "WindSpiritClawManager", "BUFF_DURATION")
                .doubleParam("buff_mult", 1.5, 0.0, 10.0, AB + "WindSpiritClawManager", "BUFF_MULT");
        b.scope("abilities.wind_dash")                     // WindDashManager：风驰
                .doubleParam("target_height", 5.0, 1.0, 32.0, AB + "WindDashManager", "TARGET_HEIGHT")
                .doubleParam("rise_fast_speed", 0.45, 0.05, 4.0, AB + "WindDashManager", "RISE_FAST_SPEED")
                .intParam("rise_fast_ticks", 7, 1, 100, AB + "WindDashManager", "RISE_FAST_TICKS")
                .intParam("rise_slow_ticks", 10, 1, 100, AB + "WindDashManager", "RISE_SLOW_TICKS")
                .intParam("hover_ticks", 60, 1, 600, AB + "WindDashManager", "HOVER_TICKS")
                .doubleParam("dash_speed", 1.5, 0.1, 8.0, AB + "WindDashManager", "DASH_SPEED")
                .doubleParam("max_dash_range", 16.0, 1.0, 128.0, AB + "WindDashManager", "MAX_DASH_RANGE")
                .doubleParam("landing_radius", 3.0, 0.5, 16.0, AB + "WindDashManager", "LANDING_RADIUS")
                .doubleParam("landing_damage", 12.0, 0.0, 1000.0, AB + "WindDashManager", "LANDING_DAMAGE")
                .doubleParam("fall_speed", 0.15, 0.05, 2.0, AB + "WindDashManager", "FALL_SPEED");
        b.scope("abilities.wind_landing_surge")            // WindSpiritLandingSurgeManager：落风涌
                .doubleParam("radius", 3.0, 0.5, 16.0, AB + "WindSpiritLandingSurgeManager", "RADIUS")
                .doubleParam("damage", 6.0, 0.0, 1000.0, AB + "WindSpiritLandingSurgeManager", "DAMAGE")
                .intParam("cooldown_ticks", 100, 20, 2400, AB + "WindSpiritLandingSurgeManager", "COOLDOWN_TICKS")
                .doubleParam("min_fall_distance", 1.5, 0.5, 32.0, AB + "WindSpiritLandingSurgeManager", "MIN_FALL_DISTANCE");
        b.scope("abilities.wind_pressure")                 // WindSpiritWindPressureManager：风压
                .doubleParam("range", 8.0, 1.0, 64.0, AB + "WindSpiritWindPressureManager", "RANGE")
                .doubleParam("slow_factor", 0.7, 0.0, 1.0, AB + "WindSpiritWindPressureManager", "SLOW_FACTOR")
                .intParam("apply_within_age", 5, 1, 100, AB + "WindSpiritWindPressureManager", "APPLY_WITHIN_AGE");
        b.scope("abilities.nova")                          // NovaSkillManager：朔望双技能
                .doubleParam("base_dodge", 0.15, 0.0, 1.0, AB + "NovaSkillManager", "BASE_DODGE")
                .doubleParam("dodge_per_leap", 0.20, 0.0, 1.0, AB + "NovaSkillManager", "DODGE_PER_LEAP")
                .intParam("dodge_duration", 60, 1, 600, AB + "NovaSkillManager", "DODGE_DURATION")
                .doubleParam("dodge_cap", 0.85, 0.0, 1.0, AB + "NovaSkillManager", "DODGE_CAP")
                .intParam("leap_window", 100, 1, 600, AB + "NovaSkillManager", "LEAP_WINDOW")
                .doubleParam("leap_power", 1.2, 0.1, 4.0, AB + "NovaSkillManager", "LEAP_POWER")
                .intParam("charge_time", 100, 1, 600, AB + "NovaSkillManager", "CHARGE_TIME")
                .intParam("lethal_radius", 5, 1, 32, AB + "NovaSkillManager", "LETHAL_RADIUS")
                .intParam("max_radius", 12, 1, 64, AB + "NovaSkillManager", "MAX_RADIUS")
                .doubleParam("max_damage", 50.0, 1.0, 1000.0, AB + "NovaSkillManager", "MAX_DAMAGE")
                .doubleParam("charge_slow_amount", -0.70, -4.0, 0.0, AB + "NovaSkillManager", "CHARGE_SLOW_AMOUNT");

        // —— 阿努比斯/金沙岚组 ——
        b.scope("abilities.anubis_death_domain")           // AnubisWolfSpDeathDomain
                .intParam("domain_radius", 24, 4, 64, AB + "AnubisWolfSpDeathDomain", "DOMAIN_RADIUS")
                .intParam("domain_height", 9, 1, 32, AB + "AnubisWolfSpDeathDomain", "DOMAIN_HEIGHT")
                .intParam("charge_ticks", 40, 1, 200, AB + "AnubisWolfSpDeathDomain", "CHARGE_TICKS")
                .doubleParam("charge_slow_factor", 0.3, 0.0, 1.0, AB + "AnubisWolfSpDeathDomain", "CHARGE_SLOW_FACTOR")
                .intParam("domain_duration", 300, 20, 4800, AB + "AnubisWolfSpDeathDomain", "DOMAIN_DURATION")
                .doubleParam("health_reduction", 0.15, 0.0, 1.0, AB + "AnubisWolfSpDeathDomain", "HEALTH_REDUCTION")
                .intParam("enhanced_domain_radius", 32, 4, 64, AB + "AnubisWolfSpDeathDomain", "ENHANCED_DOMAIN_RADIUS")
                .intParam("enhanced_charge_ticks", 20, 1, 200, AB + "AnubisWolfSpDeathDomain", "ENHANCED_CHARGE_TICKS")
                .intParam("enhanced_domain_duration", 500, 20, 4800, AB + "AnubisWolfSpDeathDomain", "ENHANCED_DOMAIN_DURATION")
                .intParam("enhanced_wither_amplifier", 1, 0, 5, AB + "AnubisWolfSpDeathDomain", "ENHANCED_WITHER_AMPLIFIER")
                .intParam("enhanced_auto_summon_count", 6, 1, 16, AB + "AnubisWolfSpDeathDomain", "ENHANCED_AUTO_SUMMON_COUNT");
        b.scope("abilities.anubis_soul_energy")            // AnubisWolfSpSoulEnergy
                .intParam("max_energy", 100, 10, 1000, AB + "AnubisWolfSpSoulEnergy", "MAX_ENERGY")
                .intParam("kill_in_domain_energy", 20, 1, 100, AB + "AnubisWolfSpSoulEnergy", "KILL_IN_DOMAIN_ENERGY")
                .intParam("minion_kill_energy", 10, 1, 100, AB + "AnubisWolfSpSoulEnergy", "MINION_KILL_ENERGY")
                .intParam("regular_kill_energy", 5, 1, 100, AB + "AnubisWolfSpSoulEnergy", "REGULAR_KILL_ENERGY")
                .intParam("wither_kill_bonus_energy", 10, 1, 100, AB + "AnubisWolfSpSoulEnergy", "WITHER_KILL_BONUS_ENERGY");
        b.scope("abilities.anubis_summon_wolves")          // AnubisWolfSpSummonWolves：冥狼裁庭
                .intParam("howl_ticks", 30, 1, 200, AB + "AnubisWolfSpSummonWolves", "HOWL_TICKS")
                .doubleParam("howl_slow_factor", 0.5, 0.0, 1.0, AB + "AnubisWolfSpSummonWolves", "HOWL_SLOW_FACTOR")
                .intParam("summon_interval", 5, 1, 100, AB + "AnubisWolfSpSummonWolves", "SUMMON_INTERVAL")
                .intParam("wolf_duration", 600, 20, 4800, AB + "AnubisWolfSpSummonWolves", "WOLF_DURATION")
                .intParam("max_wolves", 6, 1, 32, AB + "AnubisWolfSpSummonWolves", "MAX_WOLVES")
                .intParam("base_summon_count", 2, 1, 16, AB + "AnubisWolfSpSummonWolves", "BASE_SUMMON_COUNT")
                .intParam("domain_summon_count", 4, 1, 16, AB + "AnubisWolfSpSummonWolves", "DOMAIN_SUMMON_COUNT")
                .intParam("minion_level", 3, 1, 10, AB + "AnubisWolfSpSummonWolves", "MINION_LEVEL")
                .doubleParam("domain_bonus_health", 10.0, 0.0, 100.0, AB + "AnubisWolfSpSummonWolves", "DOMAIN_BONUS_HEALTH")
                .doubleParam("domain_bonus_attack", 2.0, 0.0, 100.0, AB + "AnubisWolfSpSummonWolves", "DOMAIN_BONUS_ATTACK");
        b.scope("abilities.golden_sandstorm_erosion_brand")// GoldenSandstormErosionBrand：侵蚀烙印
                .intParam("brand_duration", 200, 20, 2400, AB + "GoldenSandstormErosionBrand", "BRAND_DURATION")
                .intParam("green_duration", 200, 20, 2400, AB + "GoldenSandstormErosionBrand", "GREEN_DURATION")
                .intParam("brand_stack_cooldown", 100, 1, 1200, AB + "GoldenSandstormErosionBrand", "BRAND_STACK_COOLDOWN")
                .intParam("max_stacks", 3, 1, 10, AB + "GoldenSandstormErosionBrand", "MAX_STACKS")
                .doubleParam("burst_hp_percent", 0.20, 0.0, 1.0, AB + "GoldenSandstormErosionBrand", "BURST_HP_PERCENT")
                .doubleParam("burst_damage_cap", 20.0, 1.0, 1000.0, AB + "GoldenSandstormErosionBrand", "BURST_DAMAGE_CAP")
                .doubleParam("burst_heal_percent", 0.10, 0.0, 1.0, AB + "GoldenSandstormErosionBrand", "BURST_HEAL_PERCENT")
                .doubleParam("spread_range", 5.0, 1.0, 32.0, AB + "GoldenSandstormErosionBrand", "SPREAD_RANGE")
                .doubleParam("splash_brand_range", 4.0, 1.0, 32.0, AB + "GoldenSandstormErosionBrand", "SPLASH_BRAND_RANGE")
                .doubleParam("detonate_heal_lost_percent", 0.20, 0.0, 1.0, AB + "GoldenSandstormErosionBrand", "DETONATE_HEAL_LOST_PERCENT");
        b.scope("abilities.golden_sandstorm_wither_sand")  // GoldenSandstormWitherSand：凋零金沙
                .doubleParam("radius", 15.0, 1.0, 64.0, AB + "GoldenSandstormWitherSand", "RADIUS")
                .intParam("blind_duration", 60, 1, 1200, AB + "GoldenSandstormWitherSand", "BLIND_DURATION")
                .intParam("charge_ticks", 20, 1, 200, AB + "GoldenSandstormWitherSand", "CHARGE_TICKS");
        b.scope("abilities.golden_sandstorm_counter_burst")// GoldenSandstormCounterBurst：反噬冲击
                .doubleParam("burst_range", 4.0, 0.5, 16.0, AB + "GoldenSandstormCounterBurst", "BURST_RANGE")
                .doubleParam("knockback_strength", 0.5, 0.0, 4.0, AB + "GoldenSandstormCounterBurst", "KNOCKBACK_STRENGTH")
                .intParam("wither_duration", 100, 1, 1200, AB + "GoldenSandstormCounterBurst", "WITHER_DURATION")
                .intParam("wither_amplifier", 0, 0, 5, AB + "GoldenSandstormCounterBurst", "WITHER_AMPLIFIER")
                .intParam("cooldown_ticks", 300, 20, 4800, AB + "GoldenSandstormCounterBurst", "COOLDOWN_TICKS");
        b.scope("abilities.golden_sandstorm_regen")        // GoldenSandstormRegen
                .doubleParam("wither_tick_heal", 1.0, 0.0, 100.0, AB + "GoldenSandstormRegen", "WITHER_TICK_HEAL")
                .doubleParam("kill_heal", 4.0, 0.0, 100.0, AB + "GoldenSandstormRegen", "KILL_HEAL")
                .doubleParam("minion_kill_heal", 3.0, 0.0, 100.0, AB + "GoldenSandstormRegen", "MINION_KILL_HEAL")
                .doubleParam("passive_heal", 1.0, 0.0, 100.0, AB + "GoldenSandstormRegen", "PASSIVE_HEAL")
                .intParam("passive_interval_ooc", 200, 20, 2400, AB + "GoldenSandstormRegen", "PASSIVE_INTERVAL_OOC")
                .intParam("passive_interval_ic", 120, 20, 2400, AB + "GoldenSandstormRegen", "PASSIVE_INTERVAL_IC");
        b.scope("abilities.wither_frenzy")                 // WitherFrenzyManager：凋零狂热
                .intParam("t1_max", 40, 1, 2400, AB + "WitherFrenzyManager", "T1_MAX")
                .intParam("t2_max", 80, 1, 2400, AB + "WitherFrenzyManager", "T2_MAX")
                .doubleParam("mult_t1", 1.10, 0.0, 10.0, AB + "WitherFrenzyManager", "MULT_T1")
                .doubleParam("mult_t2", 1.20, 0.0, 10.0, AB + "WitherFrenzyManager", "MULT_T2")
                .doubleParam("mult_t3", 1.30, 0.0, 10.0, AB + "WitherFrenzyManager", "MULT_T3")
                .doubleParam("wither_damage_reduce", 0.8, 0.0, 1.0, AB + "WitherFrenzyManager", "WITHER_DAMAGE_REDUCE");

        // —— 蛛系（月织蛛/跳蛛）——
        b.scope("abilities.jump_kill")                     // JumpKillManager：跳杀（物理插值常量不开放）
                .intParam("charge_max", 60, 1, 600, AB + "JumpKillManager", "CHARGE_MAX")
                .doubleParam("base_dist", 3.0, 1.0, 16.0, AB + "JumpKillManager", "BASE_DIST")
                .doubleParam("max_dist", 16.0, 4.0, 64.0, AB + "JumpKillManager", "MAX_DIST")
                .doubleParam("damage", 8.0, 0.0, 1000.0, AB + "JumpKillManager", "DAMAGE")
                .intParam("poison_duration", 160, 1, 2400, AB + "JumpKillManager", "POISON_DURATION")
                .intParam("poison_amplifier", 1, 0, 5, AB + "JumpKillManager", "POISON_AMPLIFIER")
                .intParam("stun_duration", 7, 1, 100, AB + "JumpKillManager", "STUN_DURATION")
                .doubleParam("charge_slow", -0.5, -4.0, 0.0, AB + "JumpKillManager", "CHARGE_SLOW")
                .doubleParam("leap_speed", 0.85, 0.1, 4.0, AB + "JumpKillManager", "LEAP_SPEED")
                .intParam("max_leap_ticks", 40, 1, 200, AB + "JumpKillManager", "MAX_LEAP_TICKS")
                .doubleParam("hit_radius", 1.3, 0.5, 8.0, AB + "JumpKillManager", "HIT_RADIUS");
        b.scope("abilities.venom_skill")                   // VenomSkillManager：毒雾突进
                .doubleParam("base_damage", 4.0, 0.0, 1000.0, AB + "VenomSkillManager", "BASE_DAMAGE")
                .intParam("base_poison_duration", 300, 20, 2400, AB + "VenomSkillManager", "BASE_POISON_DURATION")
                .doubleParam("area_size", 3.5, 0.5, 16.0, AB + "VenomSkillManager", "AREA_SIZE")
                .doubleParam("dash_distance", 6.0, 1.0, 32.0, AB + "VenomSkillManager", "DASH_DISTANCE")
                .doubleParam("dash_speed", 1.2, 0.1, 4.0, AB + "VenomSkillManager", "DASH_SPEED")
                .doubleParam("dash_hit_damage", 2.0, 0.0, 1000.0, AB + "VenomSkillManager", "DASH_HIT_DAMAGE")
                .doubleParam("burst_damage", 6.0, 0.0, 1000.0, AB + "VenomSkillManager", "BURST_DAMAGE")
                .doubleParam("burst_radius", 4.5, 0.5, 16.0, AB + "VenomSkillManager", "BURST_RADIUS")
                .intParam("burst_poison_duration", 300, 20, 2400, AB + "VenomSkillManager", "BURST_POISON_DURATION")
                .intParam("dash_timeout", 20, 1, 200, AB + "VenomSkillManager", "DASH_TIMEOUT");
        b.scope("abilities.moon_weaver_moon_poison")       // SpiderMoonWeaverMoonPoisonManager
                .doubleParam("scan_radius", 8.0, 1.0, 64.0, AB + "SpiderMoonWeaverMoonPoisonManager", "SCAN_RADIUS")
                .intParam("scan_interval", 60, 1, 600, AB + "SpiderMoonWeaverMoonPoisonManager", "SCAN_INTERVAL")
                .intParam("poison_duration", 80, 1, 1200, AB + "SpiderMoonWeaverMoonPoisonManager", "POISON_DURATION");
        b.scope("abilities.moon_weaver_swing")             // SpiderMoonWeaverSwingManager：蛛丝摆荡
                .doubleParam("bullet_speed", 1.7, 0.1, 8.0, AB + "SpiderMoonWeaverSwingManager", "BULLET_SPEED")
                .doubleParam("mana_per_block", 2.0, 0.0, 100.0, AB + "SpiderMoonWeaverSwingManager", "MANA_PER_BLOCK")
                .doubleParam("tether_hit_mana_cost", 8.0, 0.0, 100.0, AB + "SpiderMoonWeaverSwingManager", "TETHER_HIT_MANA_COST")
                .doubleParam("max_rope_reach", 32.0, 4.0, 128.0, AB + "SpiderMoonWeaverSwingManager", "MAX_ROPE_REACH")
                .doubleParam("tether_max_len", 16.0, 1.0, 64.0, AB + "SpiderMoonWeaverSwingManager", "TETHER_MAX_LEN")
                .doubleParam("tether_soft_buffer", 4.0, 0.0, 16.0, AB + "SpiderMoonWeaverSwingManager", "TETHER_SOFT_BUFFER")
                .doubleParam("tether_pull_gain", 0.2, 0.0, 4.0, AB + "SpiderMoonWeaverSwingManager", "TETHER_PULL_GAIN")
                .doubleParam("tether_hard_gain", 0.8, 0.0, 4.0, AB + "SpiderMoonWeaverSwingManager", "TETHER_HARD_GAIN")
                .doubleParam("min_rope_len", 1.5, 0.5, 8.0, AB + "SpiderMoonWeaverSwingManager", "MIN_ROPE_LEN")
                .doubleParam("reel_speed", 0.16, 0.01, 2.0, AB + "SpiderMoonWeaverSwingManager", "REEL_SPEED");
        b.scope("abilities.moon_weaver_web")               // SpiderMoonWeaverWebManager：结网
                .intParam("max_ticks", 60, 1, 600, AB + "SpiderMoonWeaverWebManager", "MAX_TICKS")
                .intParam("tier1_ticks", 20, 1, 200, AB + "SpiderMoonWeaverWebManager", "TIER1_TICKS")
                .intParam("tier2_ticks", 40, 1, 200, AB + "SpiderMoonWeaverWebManager", "TIER2_TICKS")
                .doubleParam("start_mana", 6.0, 0.0, 100.0, AB + "SpiderMoonWeaverWebManager", "START_MANA")
                .doubleParam("mana_per_tick", 0.25, 0.0, 10.0, AB + "SpiderMoonWeaverWebManager", "MANA_PER_TICK");

        // —— 寄生果蝠组 ——
        b.scope("abilities.infection_spore")               // InfectionSporeManager：感染孢子
                .doubleParam("infect_spread_radius", 1.5, 0.5, 8.0, AB + "InfectionSporeManager", "INFECT_SPREAD_RADIUS")
                .intParam("spread_scan_interval", 10, 1, 100, AB + "InfectionSporeManager", "SPREAD_SCAN_INTERVAL")
                .doubleParam("damage_reduction", 0.15, 0.0, 1.0, AB + "InfectionSporeManager", "DAMAGE_REDUCTION")
                .doubleParam("tick_heal", 1.0, 0.0, 100.0, AB + "InfectionSporeManager", "TICK_HEAL")
                .doubleParam("tick_damage", 1.0, 0.0, 100.0, AB + "InfectionSporeManager", "TICK_DAMAGE")
                .intParam("heal_interval", 60, 1, 600, AB + "InfectionSporeManager", "HEAL_INTERVAL")
                .doubleParam("cloud_radius", 2.0, 0.5, 16.0, AB + "InfectionSporeManager", "CLOUD_RADIUS")
                .intParam("cloud_scan_interval", 30, 1, 200, AB + "InfectionSporeManager", "CLOUD_SCAN_INTERVAL")
                .doubleParam("cloud_purify_reach", 8.0, 1.0, 64.0, AB + "InfectionSporeManager", "CLOUD_PURIFY_REACH");
        b.scope("abilities.parasitic_seed_system")         // ParasiticSeedEnergyRegen / SeedField / Absorption / CombatTracker / EatingHandler
                .intParam("regen_interval_peace", 100, 20, 1200, AB + "ParasiticSeedEnergyRegen", "INTERVAL_PEACE")
                .intParam("regen_interval_combat", 160, 20, 1200, AB + "ParasiticSeedEnergyRegen", "INTERVAL_COMBAT")
                .intParam("max_energy", 10, 1, 100, AB + "ParasiticSeedEnergyRegen", "MAX_ENERGY")
                .doubleParam("field_radius", 1.0, 0.5, 8.0, AB + "ParasiticSeedFieldManager", "FIELD_RADIUS")
                .intParam("absorption_max_duration", 300, 20, 2400, AB + "ParasiticAbsorptionManager", "MAX_DURATION")
                .intParam("combat_ticks", 200, 20, 1200, AB + "ParasiticCombatTracker", "COMBAT_TICKS")
                .intParam("quota_limit", 8, 1, 64, AB + "SeedEnergyEatingHandler", "QUOTA_LIMIT");
        b.scope("abilities.parasitic_seed_power")          // ParasiticFruitSeedPower：寄生种子（duration 工厂默认见上注）
                .intParam("max_seeds", 3, 1, 16, P + "ParasiticFruitSeedPower", "MAX_SEEDS")
                .intParam("rooting_ticks", 20, 1, 200, P + "ParasiticFruitSeedPower", "ROOTING_TICKS")
                .intParam("fruit_interval_ticks", 25, 1, 200, P + "ParasiticFruitSeedPower", "FRUIT_INTERVAL_TICKS")
                .intParam("life_combat", 100, 20, 2400)
                .intParam("life_peace", 300, 20, 4800)
                .intParam("energy_cost", 1, 0, 100, P + "ParasiticFruitSeedPower", "ENERGY_COST");
        b.scope("abilities.parasitic_spore_bomb")          // ParasiticSporeBombPower + InfectionSporeBombEntity
                .doubleParam("projectile_speed", 1.4, 0.1, 8.0, P + "ParasiticSporeBombPower", "PROJECTILE_SPEED")
                .doubleParam("projectile_divergence", 0.5, 0.0, 4.0, P + "ParasiticSporeBombPower", "PROJECTILE_DIVERGENCE")
                .intParam("energy_cost", 1, 0, 100, P + "ParasiticSporeBombPower", "ENERGY_COST")
                .doubleParam("explosion_radius", 4.0, 0.5, 16.0, EN + "InfectionSporeBombEntity", "EXPLOSION_RADIUS")
                .intParam("default_infection_ticks", 200, 20, 2400, EN + "InfectionSporeBombEntity", "DEFAULT_INFECTION_TICKS");

        // ==== 公共系统批次（阶段 1 收尾；跨系统语义已逐文件定性）====

        // —— 领域（DomainRules）：BOUNDARY_EPSILON 为精度常量不开放；施法规格在 domain.json 不重复 ——
        b.scope("systems.domain")
                .intParam("charge_ticks", 300, 20, 1200, "spell.DomainRules", "CHARGE_TICKS")
                .intParam("duration_ticks", 800, 20, 4800, "spell.DomainRules", "DURATION_TICKS")
                .doubleParam("inner_radius", 16.0, 4.0, 64.0, "spell.DomainRules", "INNER_RADIUS")
                .doubleParam("outer_radius", 17.0, 4.0, 64.0, "spell.DomainRules", "OUTER_RADIUS")
                .doubleParam("max_cast_displacement", 3.0, 0.0, 16.0, "spell.DomainRules", "MAX_CAST_DISPLACEMENT")
                .intParam("expand_start_tick", 200, 1, 1200, "spell.DomainRules", "EXPAND_START_TICK")
                .intParam("expand_duration_ticks", 100, 1, 1200, "spell.DomainRules", "EXPAND_DURATION_TICKS")
                .constraint("domain inner radius must be smaller than outer radius", v -> (Double) v.get("inner_radius") >= (Double) v.get("outer_radius"))
                .constraint("domain expansion must end at charge completion", v -> (Long) v.get("expand_start_tick") + (Long) v.get("expand_duration_ticks") != (Long) v.get("charge_ticks"));

        // —— 爆裂（ExplosionRules）：施法规格段（CHARGE/CD/MANA/AIM）与 explosion.json 双源不重复；
        //    视觉时序/音频段（SOUND_START/BEAM_*/BALL_*/FINALE_*/SHOCKWAVE/CIRCLE_*/SOUND_FADE/
        //    THEME_VOLUME_GAIN/VIEW_RANGE）与音频素材长度绑定不开放 ——
        b.scope("systems.explosion")
                .doubleParam("core_radius", 32.0, 4.0, 128.0, "spell.ExplosionRules", "CORE_RADIUS")
                .doubleParam("outer_radius", 64.0, 4.0, 256.0, "spell.ExplosionRules", "OUTER_RADIUS")
                .doubleParam("ignite_near", 42.0, 4.0, 128.0, "spell.ExplosionRules", "IGNITE_NEAR")
                .doubleParam("ignite_far", 50.0, 4.0, 128.0, "spell.ExplosionRules", "IGNITE_FAR")
                .intParam("fire_ticks_near", 200, 0, 2400, "spell.ExplosionRules", "FIRE_TICKS_NEAR")
                .intParam("fire_ticks_far", 100, 0, 2400, "spell.ExplosionRules", "FIRE_TICKS_FAR")
                .doubleParam("sound_full", 64.0, 8.0, 256.0, "spell.ExplosionRules", "SOUND_FULL")
                .doubleParam("sound_range", 164.0, 16.0, 512.0, "spell.ExplosionRules", "SOUND_RANGE")
                .constraint("explosion radii must be ordered", v ->
                        (Double) v.get("core_radius") >= (Double) v.get("outer_radius")
                        || (Double) v.get("ignite_near") < (Double) v.get("core_radius")
                        || (Double) v.get("ignite_far") < (Double) v.get("ignite_near")
                        || (Double) v.get("ignite_far") > (Double) v.get("outer_radius"))
                .constraint("sound_range must exceed sound_full", v -> (Double) v.get("sound_range") <= (Double) v.get("sound_full"));

        // —— 魔法书精通（SpellbookData）：MAX_LEVEL/LEVEL_SLOTS/MAX_SLOTS/NBT 键为结构不开放 ——
        b.scope("systems.spellbook_mastery")
                .intParam("mastery_exp_per_tier", 600 * 10, 100, 60000, "spell.SpellbookData", "MASTERY_EXP_PER_TIER")
                .intParam("mastery_mana_per_tier", 50, 1, 1000, "spell.SpellbookData", "MASTERY_MANA_PER_TIER")
                .intParam("mastery_max_bonus", 600, 0, 10000, "spell.SpellbookData", "MASTERY_MAX_BONUS");

        // —— 法阵（FormationData）：MAX_FORMATION_LEVEL 结构不开放；逐系倍率与加成可开放 ——
        b.scope("systems.formation")
                .doubleParam("damage_bonus_per_level", 0.12, 0.0, 2.0, "spell.FormationData", "DAMAGE_BONUS_PER_LEVEL")
                .doubleParam("damage_penalty_per_level", 0.12, 0.0, 2.0, "spell.FormationData", "DAMAGE_PENALTY_PER_LEVEL")
                .doubleParam("cooldown_reduction_per_level", 0.05, 0.0, 0.9, "spell.FormationData", "COOLDOWN_REDUCTION_PER_LEVEL")
                .doubleParam("cooldown_penalty_per_level", 0.05, 0.0, 2.0, "spell.FormationData", "COOLDOWN_PENALTY_PER_LEVEL")
                .doubleParam("mana_cost_per_level", 0.10, 0.0, 2.0, "spell.FormationData", "MANA_COST_PER_LEVEL")
                .doubleParam("space_range_bonus_per_level", 0.06, 0.0, 1.0, "spell.FormationData", "SPACE_RANGE_BONUS_PER_LEVEL")
                .doubleParam("universal_exp_per_level", 0.10, 0.0, 2.0, "spell.FormationData", "UNIVERSAL_EXP_PER_LEVEL")
                .doubleParam("universal_mana_cap_base", 0.20, 0.0, 2.0, "spell.FormationData", "UNIVERSAL_MANA_CAP_BASE");

        // —— 施法风格（FormCastingStyle）：档位/COMBAT_SWAP_WINDOW/SWAP_STABILIZE 为时序结构，阶段 7 复核 ——
        b.scope("systems.casting_style")
                .doubleParam("base_book_regen_per_sec", 3.0, 0.0, 100.0, "spell.FormCastingStyle", "BASE_BOOK_REGEN_PER_SEC")
                .intParam("regen_delay_ticks", 140, 0, 1200, "spell.FormCastingStyle", "REGEN_DELAY_TICKS");

        // ==== 盘点批次 #6/#7（台账-盘点批次.md；来源注解指向真实常量或 null=函数内字面量）====

        // 形态亲和（FormAffinity if 链；来源多为函数内字面量故部分无注解）
        b.scope("affinity")
                .doubleParam("dmg_ice_snow_fox", 1.15, 1.0, 2.0)
                .doubleParam("dmg_lunar_moon_weaver", 1.15, 1.0, 2.0)
                .doubleParam("dmg_fire_golden_sandstorm", 1.15, 1.0, 2.0)
                .doubleParam("dmg_void_nightmare", 1.15, 1.0, 2.0)
                .doubleParam("mana_mul_familiar_fox_family", 0.85, 0.5, 1.0)
                .doubleParam("mana_mul_wild_cat_sp", 0.75, 0.5, 1.0)
                .doubleParam("cd_lunar_allay_sp", 0.9, 0.5, 1.0)
                .doubleParam("cd_space_ocelot_sp", 0.9, 0.5, 1.0)
                .doubleParam("cd_ice_axolotl", 0.95, 0.5, 1.0)
                .doubleParam("cd_mul_wild_cat_sp", 0.75, 0.5, 1.0)
                .intParam("summon_bonus_level", 1, 0, 2)
                .intParam("summon_level_cap", 5, 1, 5);
        // 红使魔变身
        b.scope("abilities.red_form")
                .doubleParam("transform_chance", 0.05, 0.0, 1.0)
                .intParam("duration_ticks", 12000, 600, 72000);
        // mixin 侧数值（SscAddonLivingEntityMixin 函数内字面量；scope 按机制拆分）
        b.scope("abilities.moon_tether")
                .doubleParam("ally_transfer_share", 0.5, 0.0, 1.0)
                .doubleParam("ally_taken_share", 0.5, 0.0, 1.0)
                .doubleParam("owner_vs_enemy_mul", 1.25, 1.0, 2.0)
                .doubleParam("enemy_vs_owner_mul", 0.75, 0.5, 1.0);
        b.scope("abilities.bat_blood_thirst")
                .doubleParam("lifesteal_stage2", 0.30, 0.0, 1.0)
                .doubleParam("lifesteal_stage3", 0.525, 0.0, 1.0)
                .doubleParam("ring_bonus", 0.15, 0.0, 0.5)
                .doubleParam("stage0_taken_mul", 0.85, 0.5, 1.0)
                .doubleParam("stage3_out_mul", 1.15, 1.0, 2.0);
        b.scope("abilities.mancianima_extra")
                .intParam("resist_cost", 1, 1, 3)
                .intParam("iframes_ticks", 4, 0, 20)
                .doubleParam("redmark_attacker_mul", 1.25, 1.0, 2.0)
                .doubleParam("redmark_victim_mul", 0.75, 0.5, 1.0);
        b.scope("abilities.effect_reduction")
                .doubleParam("dur_l1", 0.4, 0.0, 1.0)
                .doubleParam("dur_l2", 0.6, 0.0, 1.0)
                .doubleParam("dur_l3", 0.8, 0.0, 1.0);
        b.scope("abilities.parasitic_fruit")
                .doubleParam("heal_reduce", 0.5, 0.0, 1.0);
        b.scope("abilities.frost_freeze")
                .doubleParam("taken_mul", 1.35, 1.0, 2.0);
        b.scope("abilities.nine_lives_extra")
                .doubleParam("revive_knockback", 0.4, 0.0, 1.0);
        b.scope("abilities.anubis_wither_extra")
                .doubleParam("attrib_radius", 24.0, 8.0, 64.0);
        b.scope("abilities.allay_sp_extra")
                .doubleParam("damage_cap_ratio", 0.25, 0.05, 1.0);
        b.scope("abilities.nightmare_fear_extra")
                .doubleParam("first_hit_mul", 2.0, 1.0, 3.0);
        b.scope("abilities.upgrade_fox_nodes")
                .doubleParam("potion_node_reduction", 0.25, 0.0, 0.5);
        // 施法规则系统（SpellCastingRules/SpellNumbers/SpellChannelManager 字面量）
        b.scope("systems.casting")
                .intParam("triple_press_window_ms", 1000, 200, 2000)
                .doubleParam("refund_budget_cap", 0.5, 0.0, 1.0)
                .doubleParam("interrupt_cd_refund", 0.2, 0.0, 1.0)
                .doubleParam("relative_cd_floor", 0.2, 0.0, 1.0, "spell.SpellNumbers", "RELATIVE_CD_FLOOR")
                .doubleParam("worn_cd_base", 2.0, 1.0, 3.0)
                .intParam("gcd_ticks", 16, 0, 100, "spell.SpellChannelManager", "CAST_INTERVAL_TICKS")
                .intParam("cancel_hold_ticks", 20, 5, 100)
                .intParam("summon_affinity_cap", 4, 1, 5)
                .intParam("summon_normal_cap", 5, 1, 5);
        // 卷轴工坊（ScrollWorkshopManager 字面量）
        b.scope("systems.workshop")
                .intParam("craft_max_level", 2, 1, 5)
                .doubleParam("ink_per_level", 1.0, 0.0, 10.0)
                .doubleParam("dust_per_level", 1.0, 0.0, 10.0)
                .intParam("catalyst_offset", 2, 0, 4)
                .intParam("paper_cost", 1, 0, 16)
                .intParam("repair_ink", 1, 0, 64)
                .intParam("repair_dust", 2, 0, 64)
                .intParam("salvage_divisor", 2, 1, 4);

        // —— 咬合红技/北极狐雷（RedFormTickManager 等）函数内字面量：见 §5 必查项，未在本批登记，
        //    与 SscAddonLivingEntityMixin/FormAffinity 一起留待阶段 5 逐函数定性 ——

        return b.build();
    }
}
