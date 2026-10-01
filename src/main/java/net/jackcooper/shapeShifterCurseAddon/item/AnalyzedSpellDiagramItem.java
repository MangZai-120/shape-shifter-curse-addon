package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.item.TooltipData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import java.util.*;

public final class AnalyzedSpellDiagramItem extends Item {
    public AnalyzedSpellDiagramItem(Settings settings){super(settings);}
    public static ItemStack create(WorldRuneState world,String spell,int level){
        var recipe=SlottedSpellRecipes.get(spell);
        if(!SlottedSpellRecipes.available(recipe,level))return ItemStack.EMPTY;
        ItemStack stack=new ItemStack(SscAddon.ANALYZED_SPELL_DIAGRAM);var data=stack.getOrCreateNbt();
        data.putUuid("World",world.worldId());data.putInt("Version",SlottedFormation.VERSION);
        data.putString("Spell",spell);data.putInt("Level",level);data.putInt("CustomModelData",level);
        data.putIntArray("Slots",SlottedSpellRecipes.glyphs(recipe,level,world.language()));return stack;
    }
    public static boolean valid(ItemStack stack,UUID world){
        NbtCompound data=stack.getNbt();
        return stack.isOf(SscAddon.ANALYZED_SPELL_DIAGRAM)&&data!=null&&data.containsUuid("World")
                &&data.getUuid("World").equals(world)&&data.getInt("Version")==SlottedFormation.VERSION
                &&SlottedFormation.validDraft(data.getInt("Level"),data.getIntArray("Slots"))
                &&Arrays.stream(data.getIntArray("Slots")).allMatch(glyph->glyph>=0)
                &&SlottedSpellRecipes.available(SlottedSpellRecipes.get(data.getString("Spell")),data.getInt("Level"));
    }
    @Override public Text getName(ItemStack stack){
        var data=stack.getNbt();Spell spell=data==null?null:SpellRegistry.get(data.getString("Spell"));
        if(spell==null)return super.getName(stack);
        var element=spell.getElement();
        return (element==null?Text.translatable("research.ssc_addon.slotted.diagram_format",Text.translatable(spell.getNameKey()))
            :Text.translatable("research.ssc_addon.slotted.diagram_format_element",Text.translatable(spell.getNameKey()),Text.translatable(element.getNameKey())))
            .formatted(spell.getRarity(Math.max(1,Math.min(5,data.getInt("Level")))).color);
    }
    @Override public Optional<TooltipData> getTooltipData(ItemStack stack){
        var data=stack.getNbt();Spell spell=data==null?null:SpellRegistry.get(data.getString("Spell"));
        return spell==null?Optional.empty():Optional.of(new MagicScrollItem.SpellIconTooltipData(spell.getIconTexture()));
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        var data=stack.getNbt();Spell spell=data==null?null:SpellRegistry.get(data.getString("Spell"));
        if(spell!=null){
            SpellRarity rarity=spell.getRarity(Math.max(1,Math.min(5,data.getInt("Level"))));
            lines.add(Text.translatable("item.ssc_addon.magic_scroll.rarity_tier",Text.translatable(rarity.getTranslationKey()).formatted(rarity.color),
                    Text.translatable("spell.ssc_addon.tier."+spell.getConfig().spellTier.name().toLowerCase(Locale.ROOT)).formatted(Formatting.GRAY)));
            for(String line:Text.translatable(spell.getDescKey()).getString().split("\n"))lines.add(Text.literal(line).formatted(Formatting.GRAY));
        }
        for(String line:Text.translatable("research.ssc_addon.slotted.diagram_tip").getString().split("\n"))lines.add(Text.literal(line).formatted(Formatting.DARK_GRAY));
    }
}