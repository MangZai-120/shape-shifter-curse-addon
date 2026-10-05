package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.*;
import static net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers.Stat.*;

/** Slot descriptions consume the current server evaluation, never a second client rule calculation. */
public final class RuneSlotTooltips {
    private RuneSlotTooltips() {}
    private static Text text(String key,Object... args){
        return Text.translatable("research.ssc_addon.runes."+key,args);
    }
    public static NbtCompound write(RuneBuildEvaluator.Result result){
        NbtCompound data=new NbtCompound(),slots=new NbtCompound();
        result.contributions().forEach((index,contribution)->{
            NbtCompound slot=new NbtCompound();slot.put("Modifiers",contribution.modifiers().write());
            slot.putInt("Stability",contribution.stability());slot.putString("InactiveReason",contribution.inactiveReason());
            slots.put(Integer.toString(index),slot);
        });
        data.put("Slots",slots);NbtList hints=new NbtList();
        for(var problem:result.problems())addHint(hints,problem,false);
        for(var interaction:result.interactions())addHint(hints,interaction,true);
        data.put("Hints",hints);return data;
    }
    private static void addHint(NbtList hints,RuneBuildEvaluator.Problem problem,boolean interaction){
        if(problem.first()<0)return;
        NbtCompound hint=new NbtCompound();hint.putString("Rule",problem.rule());hint.putInt("First",problem.first());
        hint.putInt("Second",problem.second());hint.putInt("Amount",problem.amount());hint.putBoolean("Interaction",interaction);hints.add(hint);
    }
    public static List<Text> slot(int level,int index,int[] slots,int[] meanings,NbtCompound evaluation,boolean current){
        List<Text> lines=new ArrayList<>();boolean enhanced=RuneLayout.enhancement(level,index);
        int glyph=slots[index],meaning=glyph>=0?meanings[glyph]:-1;
        if(glyph>=0)lines.add((meaning<0?SlottedResearchManager.text("unknown",glyph+1)
                :SlottedResearchManager.text("role."+meaning)).copy().formatted(Formatting.WHITE));
        lines.add(text(enhanced?"slot_enhanced":"slot_base",index+1).copy().formatted(enhanced?Formatting.AQUA:Formatting.GOLD));
        if(glyph<0){
            lines.add(text(enhanced?"slot_empty_outer":"slot_empty_base").copy().formatted(Formatting.GRAY));
            return lines;
        }
        if(!enhanced){lines.add(text("slot_foundation_tip").copy().formatted(Formatting.GRAY));return lines;}
        if(meaning<0){lines.add(text("slot_unknown_tip").copy().formatted(Formatting.GRAY));return lines;}
        var role=RuneRole.values()[meaning];
        lines.add(text("effect."+role.name().toLowerCase(Locale.ROOT)).copy().formatted(Formatting.GRAY));
        var entries=evaluation.getCompound("Slots");String key=Integer.toString(index);
        if(!current||!entries.contains(key,NbtElement.COMPOUND_TYPE)){
            lines.add(text("slot_pending").copy().formatted(Formatting.DARK_GRAY));return lines;
        }
        var data=entries.getCompound(key);var modifiers=RuneModifiers.read(data.getCompound("Modifiers"));
        String reason=data.getString("InactiveReason");
        if(!reason.isEmpty())lines.add(text("inactive."+reason).copy().formatted(Formatting.YELLOW));
        else lines.add(text("slot_contribution").copy().formatted(Formatting.DARK_GRAY));
        for(var stat:RuneModifiers.Stat.values()){
            if(stat==MANA||stat==TIME||stat==CD||stat==FLAT_MANA||modifiers.get(stat)==0)continue;
            lines.add(RuneTooltips.stat(stat,modifiers.get(stat)).copy().formatted(modifiers.get(stat)>0?Formatting.GREEN:Formatting.RED));
        }
        int stability=data.getInt("Stability");
        lines.add(text("slot_stability",signed(stability)).copy().formatted(stability>0?Formatting.GREEN:Formatting.GRAY));
        int mana=modifiers.get(MANA),flat=modifiers.get(FLAT_MANA);
        lines.add(text(mana==0?"slot_mana_flat":"slot_mana",mana==0?signed(flat):signed(mana),flat).copy().formatted(Formatting.GRAY));
        int time=modifiers.get(TIME);
        if(time!=0)lines.add(RuneTooltips.stat(TIME,time).copy().formatted(time<0?Formatting.GREEN:Formatting.GRAY));
        for(var hint:evaluation.getList("Hints",NbtElement.COMPOUND_TYPE)){
            NbtCompound problem=(NbtCompound)hint;
            int first=problem.getInt("First"),second=problem.getInt("Second");
            if(first!=index&&second!=index)continue;
            if(problem.getBoolean("Interaction")){
                lines.add(text("pair."+problem.getString("Rule"),(first==index?second:first)+1).copy().formatted(Formatting.YELLOW));
            }else{
                lines.add(SlottedResearchManager.text("rune_"+problem.getString("Rule"),first+1,second+1,problem.getInt("Amount")).copy().formatted(Formatting.RED));
            }
        }
        return lines;
    }
    private static String signed(int value){return value>0?"+"+value:Integer.toString(value);}
}
