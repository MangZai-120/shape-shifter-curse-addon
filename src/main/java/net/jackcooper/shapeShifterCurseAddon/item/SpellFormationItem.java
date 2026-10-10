package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import java.util.List;

public final class SpellFormationItem extends Item {
    public SpellFormationItem(Settings settings){super(settings);}
    public static ItemStack create(WorldRuneState world,String spell,int level,int[] slots){
        ItemStack stack=new ItemStack(SscAddon.SPELL_FORMATION);var data=stack.getOrCreateNbt();
        data.putUuid("World",world.worldId());data.putInt("Version",SlottedFormation.VERSION);
        data.putString("Spell",spell);data.putInt("Level",level);data.putIntArray("Slots",slots);return stack;
    }
    public static ItemStack createEnhanced(WorldRuneState world,int level,int[] slots,RuneBuildEvaluator.Result result){
        if (!result.valid()) throw new IllegalArgumentException("Invalid rune scheme");
        if (!RuneLayout.hasEnhancements(level, slots, RuneLayout.VERSION))
            return ScrollData.create(result.recipe().spell(), level);
        ItemStack stack=create(world,result.recipe().spell(),level,slots);
        stack.getOrCreateNbt().putInt("Version",RuneLayout.VERSION);
        stack.getOrCreateNbt().put(RuneScheme.KEY,RuneScheme.create(world,level,slots,result));return stack;
    }
    @Override public Text getName(ItemStack stack){
        var data=stack.getNbt();Spell spell=data==null?null:SpellRegistry.get(data.getString("Spell"));
        if (spell != null && !RuneScheme.isModified(stack)) return SscAddon.MAGIC_SCROLL.getName(stack);
        return spell==null?super.getName(stack):Text.translatable("research.ssc_addon.slotted.product",Text.translatable(spell.getNameKey()),data.getInt("Level"))
                .append(Text.translatable("research.ssc_addon.runes.modified_suffix"))
                .formatted(spell.getRarity(ScrollData.getLevel(stack)).color);
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        if (!RuneScheme.isModified(stack)) {
            SscAddon.MAGIC_SCROLL.appendTooltip(stack, world, lines, context);
            return;
        }
        RuneTooltips.append(stack,lines);
    }
}
