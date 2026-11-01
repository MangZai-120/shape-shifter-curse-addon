package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.jackcooper.shapeShifterCurseAddon.item.*;
import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationKnowledgeComponent;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
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
        return complete(table,backpack,knowledge,world,level,slots,operation,false);
    }
    public static Result completeEnhanced(Inventory table,Inventory backpack,FormationKnowledgeComponent knowledge,WorldRuneState world,
                                  int level,int[] slots,UUID operation){return complete(table,backpack,knowledge,world,level,slots,operation,true);}
    private static Result complete(Inventory table,Inventory backpack,FormationKnowledgeComponent knowledge,WorldRuneState world,
                                  int level,int[] slots,UUID operation,boolean enhanced){
        var progress=knowledge.research();
        if(enhanced&&!world.worldId().equals(progress.world))return new Result("invalid",-1);
        if(!progress.completionToken.equals(operation))return new Result("duplicate",-1);
        var evaluation=enhanced?RuneBuildEvaluator.evaluate(level,slots,world.language(),SlottedSpellRecipes.all()):null;
        var recipe=enhanced?(evaluation.valid()?evaluation.recipe():null):SlottedSpellRecipes.identify(level,slots,world.language());
        if(recipe==null)return new Result("invalid",-1);
        if(progress.rank<level)return new Result("rank",-1);
        if(!table.getStack(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT).isEmpty())return new Result("output",-1);
        List<Take> plan=new ArrayList<>();
        if(!collect(table,backpack,0,stack->stack.getItem() instanceof BlankFormationPaperItem,1,plan))return new Result("paper",-1);
        int[] inks=SlottedFormation.ink(world.language(),slots);
        for(int school=0;school<8;school++){
            final int expected=school;
            if(!collect(table,backpack,1,stack->stack.getItem() instanceof FormationInkItem ink&&school(ink)==expected,inks[school],plan))return new Result("ink",school);
        }
        if(!collect(table,backpack,2,stack->stack.isOf(RegCustomItem.UNTREATED_MOONDUST),(int)Arrays.stream(slots).filter(g->g>=0).count(),plan))return new Result("dust",-1);
        ItemStack result=enhanced?SpellFormationItem.createEnhanced(world,level,slots,evaluation):SpellFormationItem.create(world,recipe.spell(),level,slots);
        for(Take take:plan)take.inventory.getStack(take.slot).decrement(take.count);
        table.setStack(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT,result);table.markDirty();backpack.markDirty();
        knowledge.recordSpell(recipe.spell());String key=recipe.spell()+":"+level;
        if(enhanced){progress.runeReferences.put(key,slots.clone());progress.runeCompleted.add(key);}
        else{progress.slotReferences.put(key,slots.clone());progress.slotCompleted.add(key);}progress.completionToken=UUID.randomUUID();
        return new Result("completed",recipe.family());
    }
    public static int school(FormationInkItem ink){return ink.getType().element==null?7:ResearchTarget.FAMILIES.indexOf(ink.getType().element.id);}
    /** Manual foundation validation retains the existing main-scroll and upgrade-material costs. */
    public static Result upgradeEnhanced(Inventory table,Inventory backpack,FormationKnowledgeComponent knowledge,WorldRuneState world,
                                         int level,int[] slots,UUID operation) {
        ResearchProgress progress=knowledge.research();
        if(!world.worldId().equals(progress.world))return new Result("invalid",-1);
        if(!progress.completionToken.equals(operation))return new Result("duplicate",-1);
        var evaluation=RuneBuildEvaluator.evaluate(level,slots,world.language(),SlottedSpellRecipes.all());
        if(!evaluation.valid())return new Result("invalid",-1);
        var recipe=evaluation.recipe();
        if(progress.rank<level)return new Result("rank",-1);
        if(!knowledge.hasSpell(recipe.spell()))return new Result("upgrade_unknown",-1);
        if(RuneLayout.hasEnhancements(level,slots,RuneLayout.VERSION))return new Result("upgrade_foundation",-1);
        if(!table.getStack(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT).isEmpty())return new Result("output",-1);
        ItemStack main=table.getStack(SpellResearchTableBlockEntity.SLOT_UPGRADE_SCROLL);
        Spell spell=ScrollData.getSpell(main);
        if(level<2||spell==null||!spell.getId().getPath().equals(recipe.spell())
                ||ScrollData.getLevel(main)!=level-1||main.getCount()!=1||ArcaneAnalysis.isUnanalyzed(main))
            return new Result("upgrade_input",-1);
        if(main.getNbt()!=null&&main.getNbt().contains(RuneScheme.KEY)&&RuneScheme.authoritativeScroll(world,main)==null)
            return new Result("invalid",-1);
        List<Take> plan=new ArrayList<>();
        if(!collect(table,backpack,SpellResearchTableBlockEntity.SLOT_INK,
                stack->stack.getItem() instanceof FormationInkItem ink&&school(ink)==recipe.family(),
                ScrollWorkshopManager.upgradeInkCost(level),plan))return new Result("ink",recipe.family());
        if(!collect(table,backpack,SpellResearchTableBlockEntity.SLOT_CATALYST,
                stack->stack.isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD),
                ScrollWorkshopManager.upgradeCatalystCost(level),plan))return new Result("upgrade_catalyst",-1);
        ItemStack result=main.copy();
        ScrollData.setLevel(result,level);
        ScrollData.setCastLevel(result,0);
        for(Take take:plan)take.inventory.getStack(take.slot).decrement(take.count);
        table.setStack(SpellResearchTableBlockEntity.SLOT_UPGRADE_SCROLL,ItemStack.EMPTY);
        table.setStack(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT,result);
        progress.runeReferences.put(recipe.spell()+":"+level,slots.clone());
        progress.runeCompleted.add(recipe.spell()+":"+level);
        progress.completionToken=UUID.randomUUID();
        table.markDirty();backpack.markDirty();
        return new Result("completed",recipe.family());
    }
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
