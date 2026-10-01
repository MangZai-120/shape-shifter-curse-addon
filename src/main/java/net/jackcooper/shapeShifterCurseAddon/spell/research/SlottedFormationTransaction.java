package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.jackcooper.shapeShifterCurseAddon.item.*;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import java.util.*;
import java.util.function.Predicate;

public final class SlottedFormationTransaction {
    private record Take(Inventory inventory,int slot,int count){}
    public record Result(String key,int school) { public boolean success(){return key.equals("completed");} }
    private SlottedFormationTransaction(){}
    public static Result complete(Inventory table,Inventory backpack,FormationKnowledgeComponent knowledge,WorldRuneState world,
                                  int level,int[] slots,UUID operation){
        var progress=knowledge.research();
        if(!progress.completionToken.equals(operation))return new Result("duplicate",-1);
        var recipe=SlottedSpellRecipes.identify(level,slots,world.language());
        if(recipe==null)return new Result("invalid",-1);
        if(progress.rank<level)return new Result("rank",-1);
        if(!table.getStack(3).isEmpty())return new Result("output",-1);
        List<Take> plan=new ArrayList<>();
        if(!collect(table,backpack,0,stack->stack.getItem() instanceof BlankFormationPaperItem,1,plan))return new Result("paper",-1);
        int[] inks=SlottedFormation.ink(world.language(),slots);
        for(int school=0;school<8;school++){
            final int expected=school;
            if(!collect(table,backpack,1,stack->stack.getItem() instanceof FormationInkItem ink&&school(ink)==expected,inks[school],plan))return new Result("ink",school);
        }
        if(!collect(table,backpack,2,stack->stack.isOf(RegCustomItem.UNTREATED_MOONDUST),slots.length,plan))return new Result("dust",-1);
        ItemStack result=SpellFormationItem.create(world,recipe.spell(),level,slots);
        for(Take take:plan)take.inventory.getStack(take.slot).decrement(take.count);
        table.setStack(3,result);table.markDirty();backpack.markDirty();
        knowledge.recordSpell(recipe.spell());String key=recipe.spell()+":"+level;
        progress.slotReferences.put(key,slots.clone());progress.slotCompleted.add(key);progress.completionToken=UUID.randomUUID();
        return new Result("completed",recipe.family());
    }
    public static int school(FormationInkItem ink){return ink.getType().element==null?7:ResearchTarget.FAMILIES.indexOf(ink.getType().element.id);}
    public static int count(Inventory table,Inventory backpack,int tableSlot,Predicate<ItemStack> predicate){
        int total=predicate.test(table.getStack(tableSlot))?table.getStack(tableSlot).getCount():0;
        for(int slot=0;slot<Math.min(36,backpack.size());slot++)if(predicate.test(backpack.getStack(slot)))total+=backpack.getStack(slot).getCount();
        return total;
    }
    private static boolean collect(Inventory table,Inventory backpack,int tableSlot,Predicate<ItemStack> predicate,int needed,List<Take> plan){
        if(needed==0)return true;
        ItemStack stored=table.getStack(tableSlot);
        if(predicate.test(stored)){int amount=Math.min(needed,stored.getCount());plan.add(new Take(table,tableSlot,amount));needed-=amount;}
        for(int slot=0;slot<Math.min(36,backpack.size())&&needed>0;slot++){
            ItemStack stack=backpack.getStack(slot);if(!predicate.test(stack))continue;
            int amount=Math.min(needed,stack.getCount());plan.add(new Take(backpack,slot,amount));needed-=amount;
        }
        return needed==0;
    }
}