package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.jackcooper.shapeShifterCurseAddon.spell.*;
import java.util.*;

public final class SlottedSpellRecipes {
    public record Recipe(String spell, int family, RuneRole behavior, RuneRole modifier) {}
    private static final List<Recipe> RECIPES = List.of(
            new Recipe("fire_bolt",0,RuneRole.SOURCE,RuneRole.GAIN),
            new Recipe("flame_nova",0,RuneRole.GAIN,RuneRole.SPLIT),
            new Recipe("meteor",0,RuneRole.SOURCE,RuneRole.STORE),
            new Recipe("frost_spike",1,RuneRole.SOURCE,RuneRole.STABLE),
            new Recipe("ice_barrage",1,RuneRole.SOURCE,RuneRole.SPLIT),
            new Recipe("frost_nova",1,RuneRole.GAIN,RuneRole.SPLIT),
            new Recipe("frost_armor",1,RuneRole.STABLE,RuneRole.STORE),
            new Recipe("moonlight_arrow",2,RuneRole.SOURCE,RuneRole.GAIN),
            new Recipe("lunar_mend",2,RuneRole.RECOVER,RuneRole.CONVERT),
            new Recipe("lunar_veil",2,RuneRole.STABLE,RuneRole.MERGE),
            new Recipe("lunar_phase",2,RuneRole.CONVERT,RuneRole.INSIGHT),
            new Recipe("curse_mark",3,RuneRole.INSIGHT,RuneRole.GAIN),
            new Recipe("dread_whisper",3,RuneRole.GAIN,RuneRole.SPLIT),
            new Recipe("corrupt_mist",3,RuneRole.GAIN,RuneRole.STORE),
            new Recipe("summon_lunar_spirit",4,RuneRole.SOURCE,RuneRole.MERGE),
            new Recipe("companion_resonance",4,RuneRole.GAIN,RuneRole.MERGE),
            new Recipe("beep_sheep",4,RuneRole.SOURCE,RuneRole.CONVERT),
            new Recipe("void_devour",5,RuneRole.CONVERT,RuneRole.GAIN),
            new Recipe("void_erosion",5,RuneRole.CONVERT,RuneRole.STORE),
            new Recipe("space_blink",6,RuneRole.BRIDGE,RuneRole.SOURCE),
            new Recipe("space_stride",6,RuneRole.BRIDGE,RuneRole.STABLE),
            new Recipe("space_recall",6,RuneRole.BRIDGE,RuneRole.INSIGHT),
            new Recipe("pocket_space",6,RuneRole.BRIDGE,RuneRole.STORE));
    private SlottedSpellRecipes() {}
    public static List<Recipe> all() { return RECIPES; }
    public static Recipe get(String id) { return RECIPES.stream().filter(recipe -> recipe.spell.equals(id)).findFirst().orElse(null); }
    public static boolean available(Recipe recipe, int level) {
        Spell spell = recipe == null ? null : SpellRegistry.get(recipe.spell);
        return spell != null && spell.getRarity() != SpellRarity.RED && level >= 1 && level <= Math.min(5,spell.getMaxLevel());
    }
    public static List<RuneRole> roles(Recipe recipe, int level) {
        int[] counts=SlottedFormation.layers(level);List<RuneRole> roles=new ArrayList<>();
        RuneRole element=RuneRole.values()[recipe.family];
        roles.addAll(List.of(element,recipe.behavior,recipe.modifier));
        if(counts[0]>=5)roles.addAll(List.of(recipe.behavior,RuneRole.STABLE));
        if(counts[0]==6)roles.add(element);
        if(counts.length>=2)roles.addAll(List.of(RuneRole.SOURCE,element,recipe.behavior,recipe.modifier,RuneRole.STABLE));
        if(counts.length>=3)roles.addAll(List.of(RuneRole.SOURCE,element,RuneRole.STORE,recipe.behavior,recipe.modifier,element,RuneRole.STABLE,RuneRole.MERGE));
        if(counts.length>=4)roles.addAll(List.of(RuneRole.SOURCE,element,recipe.behavior,RuneRole.GAIN,RuneRole.STORE,RuneRole.STABLE,RuneRole.MERGE,recipe.modifier,element,RuneRole.CONVERT,RuneRole.RECOVER,RuneRole.STABLE));
        return List.copyOf(roles);
    }
    public static int[] glyphs(Recipe recipe,int level,RuneLanguage language){return roles(recipe,level).stream().mapToInt(language::glyph).toArray();}
    public static Recipe identify(int level,int[] slots,RuneLanguage language){
        if(!SlottedFormation.validDraft(level,slots))return null;
        Recipe found=null;
        for(Recipe recipe:RECIPES)if(available(recipe,level)&&Arrays.equals(slots,glyphs(recipe,level,language))){
            if(found!=null)throw new IllegalStateException("法术配方重复");found=recipe;
        }
        return found;
    }
}