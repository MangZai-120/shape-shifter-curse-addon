package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.*;

public final class RuneTooltips {
    private RuneTooltips() {}
    public static Text stat(RuneModifiers.Stat stat,int value){
        String signed=value>0?"+"+value:Integer.toString(value);
        return Text.translatable("research.ssc_addon.runes.stat."+stat.name().toLowerCase(Locale.ROOT),signed);
    }
    public static void append(ItemStack stack,List<Text> lines){
        if(stack.getNbt()==null||!stack.getNbt().contains(RuneScheme.KEY))return;
        var n=stack.getNbt().getCompound(RuneScheme.KEY);
        lines.add(Text.translatable("research.ssc_addon.runes.profile_level",n.getInt("Level")).formatted(Formatting.GOLD));
        lines.add(Text.translatable("research.ssc_addon.runes.stability",n.getInt("Stability")).formatted(Formatting.GRAY));
        var modifiers=RuneModifiers.read(n.getCompound("Modifiers"));
        if(n.contains("Summary"))lines.add(Text.translatable("research.ssc_addon.runes.summary.raw").formatted(Formatting.DARK_GRAY));
        for(var stat:RuneModifiers.Stat.values())if(modifiers.get(stat)!=0)
            lines.add(stat(stat,modifiers.get(stat)).copy().formatted(Formatting.GRAY));
        if(n.contains("Summary"))lines.addAll(RuneSummary.effects(n.getCompound("Summary"),false));
        lines.add(Text.translatable("research.ssc_addon.runes.lower_level_tip").formatted(Formatting.DARK_GRAY));
    }
}
