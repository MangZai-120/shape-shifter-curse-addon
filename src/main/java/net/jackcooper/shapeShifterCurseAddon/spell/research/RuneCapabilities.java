package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.*;
import static net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers.Stat.*;

/** Advertise only properties with real consumers; additional spell adapters register here. */
public final class RuneCapabilities {
    private RuneCapabilities() {}
    public static EnumSet<RuneModifiers.Stat> of(String spell) {
        return switch (spell) {
            case "fire_bolt" -> EnumSet.of(POWER, DAMAGE, SPEED, BURN);
            case "flame_nova" -> EnumSet.of(POWER, DAMAGE, AREA, BURN);
            case "frost_armor" -> EnumSet.of(POWER, SHIELD_DURATION);
            case "meteor" -> EnumSet.of(POWER,DAMAGE,AREA,DISTANCE,BURN);
            case "frost_spike", "moonlight_arrow" -> EnumSet.of(POWER,DAMAGE,SPEED);
            case "ice_barrage" -> EnumSet.of(POWER,DAMAGE,SPEED,AREA);
            case "frost_nova" -> EnumSet.of(POWER,DAMAGE,AREA,NEGATIVE);
            case "lunar_mend" -> EnumSet.of(POWER,HEAL);
            case "lunar_veil", "companion_resonance" -> EnumSet.of(AREA,ALLY_DURATION);
            case "lunar_phase", "space_blink" -> EnumSet.of(DISTANCE);
            case "curse_mark" -> EnumSet.of(SPEED,MARK,NEGATIVE);
            case "dread_whisper" -> EnumSet.of(AREA,DISTANCE,NEGATIVE);
            case "corrupt_mist", "void_erosion" -> EnumSet.of(AREA,NEGATIVE);
            case "summon_lunar_spirit" -> EnumSet.of(SUMMON_DURATION);
            case "beep_sheep" -> EnumSet.of(SPEED,NEGATIVE);
            case "void_devour" -> EnumSet.of(POWER,DAMAGE,AREA,DISTANCE,NEGATIVE);
            case "space_stride" -> EnumSet.of(ALLY_DURATION);
            case "space_recall", "pocket_space" -> EnumSet.noneOf(RuneModifiers.Stat.class);
            default -> EnumSet.noneOf(RuneModifiers.Stat.class);
        };
    }
}
