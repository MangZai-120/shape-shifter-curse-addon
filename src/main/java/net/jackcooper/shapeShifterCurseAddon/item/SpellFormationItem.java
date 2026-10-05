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
        ItemStack stack=create(world,result.recipe().spell(),level,slots);
        stack.getOrCreateNbt().putInt("Version",RuneLayout.VERSION);
        stack.getOrCreateNbt().put(RuneScheme.KEY,RuneScheme.create(world,level,slots,result));return stack;
    }
    @Override public Text getName(ItemStack stack){
        var data=stack.getNbt();Spell spell=data==null?null:SpellRegistry.get(data.getString("Spell"));
        return spell==null?super.getName(stack):Text.translatable("research.ssc_addon.slotted.product",Text.translatable(spell.getNameKey()),data.getInt("Level"))
                .formatted(spell.getRarity(ScrollData.getLevel(stack)).color);
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        RuneTooltips.append(stack,lines);
    }
}
