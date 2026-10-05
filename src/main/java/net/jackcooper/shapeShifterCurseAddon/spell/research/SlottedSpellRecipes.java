package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.jackcooper.shapeShifterCurseAddon.spell.*;
import java.util.*;

public final class SlottedSpellRecipes {
    public record Recipe(String spell, int family, RuneRole behavior, RuneRole modifier) {}
    // 2026-10-01 阶段A语义重排：行为=能量形态（看法术长什么样），修饰=追加性质（问题序①-⑨首问命中）。
    // 行为规则：引能=单点直投、增幅=自身扩散、分流=一次多路、稳定=凝驻自身、汇合=聚合实体/联结、
    // 转化=锁定蓄力后转变、感悟=指向印记、跨越=贯通两点、恢复=能量回流。
    // 修饰问题序：①负面状态/变形→转化 ②延时封存→储存 ③联结友方→汇合 ④波及多目标→分流
    // ⑤直接提升威力→增幅 ⑥延长持续→稳定 ⑦锚定信息→感悟 ⑧立即生效→引能 ⑨回复→恢复。
    // 系内惯例：火系统一修饰=储存（火即燃烧）；空间系统一行为=跨越（修饰依次=引能/稳定/感悟/储存）。
    private static final List<Recipe> RECIPES = List.of(
            new Recipe("fire_bolt",0,RuneRole.SOURCE,RuneRole.STORE),
            new Recipe("flame_nova",0,RuneRole.GAIN,RuneRole.STORE),
            new Recipe("meteor",0,RuneRole.CONVERT,RuneRole.STORE),
            new Recipe("frost_spike",1,RuneRole.SOURCE,RuneRole.GAIN),
            new Recipe("ice_barrage",1,RuneRole.SPLIT,RuneRole.GAIN),
            new Recipe("frost_nova",1,RuneRole.GAIN,RuneRole.CONVERT),
            new Recipe("frost_armor",1,RuneRole.STABLE,RuneRole.STORE),
            new Recipe("moonlight_arrow",2,RuneRole.SOURCE,RuneRole.GAIN),
            new Recipe("lunar_mend",2,RuneRole.RECOVER,RuneRole.CONVERT),
            new Recipe("lunar_veil",2,RuneRole.STABLE,RuneRole.MERGE),
            new Recipe("lunar_phase",2,RuneRole.CONVERT,RuneRole.STORE),
            new Recipe("curse_mark",3,RuneRole.INSIGHT,RuneRole.GAIN),
            new Recipe("dread_whisper",3,RuneRole.SPLIT,RuneRole.CONVERT),
            new Recipe("corrupt_mist",3,RuneRole.STABLE,RuneRole.STORE),
            new Recipe("summon_lunar_spirit",4,RuneRole.MERGE,RuneRole.STABLE),
            new Recipe("companion_resonance",4,RuneRole.MERGE,RuneRole.GAIN),
            new Recipe("beep_sheep",4,RuneRole.SOURCE,RuneRole.CONVERT),
            new Recipe("void_devour",5,RuneRole.CONVERT,RuneRole.STORE),
            new Recipe("void_erosion",5,RuneRole.GAIN,RuneRole.CONVERT),
            new Recipe("space_blink",6,RuneRole.BRIDGE,RuneRole.SOURCE),
            new Recipe("space_stride",6,RuneRole.BRIDGE,RuneRole.STABLE),
            new Recipe("space_recall",6,RuneRole.BRIDGE,RuneRole.INSIGHT),
            new Recipe("pocket_space",6,RuneRole.BRIDGE,RuneRole.STORE));
    /** 阶段A重排前的旧签名（仅阶段E旧图纸迁移对照用，禁止用于运行时判定）。键=法术id，值=[行为,修饰]。 */
    static final Map<String,RuneRole[]> LEGACY_SIGNATURES = Map.ofEntries(
            Map.entry("fire_bolt",new RuneRole[]{RuneRole.SOURCE,RuneRole.GAIN}),
            Map.entry("flame_nova",new RuneRole[]{RuneRole.GAIN,RuneRole.SPLIT}),
            Map.entry("meteor",new RuneRole[]{RuneRole.SOURCE,RuneRole.STORE}),
            Map.entry("frost_spike",new RuneRole[]{RuneRole.SOURCE,RuneRole.STABLE}),
            Map.entry("ice_barrage",new RuneRole[]{RuneRole.SOURCE,RuneRole.SPLIT}),
            Map.entry("frost_nova",new RuneRole[]{RuneRole.GAIN,RuneRole.SPLIT}),
            Map.entry("lunar_phase",new RuneRole[]{RuneRole.CONVERT,RuneRole.INSIGHT}),
            Map.entry("dread_whisper",new RuneRole[]{RuneRole.GAIN,RuneRole.SPLIT}),
            Map.entry("corrupt_mist",new RuneRole[]{RuneRole.GAIN,RuneRole.STORE}),
            Map.entry("summon_lunar_spirit",new RuneRole[]{RuneRole.SOURCE,RuneRole.MERGE}),
            Map.entry("companion_resonance",new RuneRole[]{RuneRole.GAIN,RuneRole.MERGE}),
            Map.entry("void_devour",new RuneRole[]{RuneRole.CONVERT,RuneRole.GAIN}),
            Map.entry("void_erosion",new RuneRole[]{RuneRole.CONVERT,RuneRole.STORE}));
    /** 阶段E惰性迁移：按旧签名计算指定法术/等级在旧配方表下的逐格序列（未变更法术返回 null）。 */
    public static int[] legacyGlyphs(String spell,int level,RuneLanguage language){
        Recipe current=get(spell);RuneRole[] legacy=LEGACY_SIGNATURES.get(spell);
        if(current==null||legacy==null)return null;
        return glyphs(new Recipe(spell,current.family(),legacy[0],legacy[1]),level,language);
    }
    /** 阶段E惰性迁移：slots 若为旧配方序列则返回等价新序列，否则返回 null（未变更法术/不匹配均为 null）。 */
    public static int[] migrateIfLegacy(String spell,int level,int[] slots,RuneLanguage language){
        int[] legacy=legacyGlyphs(spell,level,language);
        return legacy!=null&&java.util.Arrays.equals(slots,legacy)?glyphs(get(spell),level,language):null;
    }
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
        for(Recipe recipe:RECIPES)if(available(recipe,level)&&matches(recipe,level,slots,language)){
            if(found!=null)throw new IllegalStateException("法术配方重复");found=recipe;
        }
        return found;
    }
    /**
     * 阶段D（2026-10-01 获批）：L4 大环与 L5 超大环支持整体旋转——环形结构没有固定起点，
     * 玩家可从任意一格起笔；内层（核心/小环/L5 中间大环）仍逐格精确。
     * 旋转不改变符文多重集，材料结算（SlottedFormation.ink）天然一致。
     */
    static boolean matches(Recipe recipe,int level,int[] slots,RuneLanguage language){
        int[] canonical=glyphs(recipe,level,language);
        if(Arrays.equals(slots,canonical))return true;
        if(level<4)return false;
        int[] counts=SlottedFormation.layers(level);
        int innerSize=SlottedFormation.size(level)-counts[counts.length-1];
        for(int i=0;i<innerSize;i++)if(slots[i]!=canonical[i])return false;
        return ringMatches(canonical,innerSize,slots);
    }
    /** 环形序列比较：actual 的最外环是 canonical 最外环的任意整体旋转则真。 */
    static boolean ringMatches(int[] canonical,int from,int[] actual){
        int length=canonical.length-from;
        for(int shift=0;shift<length;shift++){
            boolean same=true;
            for(int i=0;i<length;i++)if(actual[from+i]!=canonical[from+(i+shift)%length]){same=false;break;}
            if(same)return true;
        }
        return false;
    }
}