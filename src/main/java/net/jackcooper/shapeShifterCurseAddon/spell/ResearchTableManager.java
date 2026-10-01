package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

public final class ResearchTableManager {
    private ResearchTableManager() {}

    public static void scribe(ServerPlayerEntity player,String elementId,String variant,int level){
        execute(player,elementId,variant,level,true,0);
    }

    public static void learn(ServerPlayerEntity player,String elementId,String variant,int level,int discount){
        execute(player,elementId,variant,level,false,discount);
    }

    private static void execute(ServerPlayerEntity player,String elementId,String variant,int level,boolean scribing,int discount){
        if(!(player.currentScreenHandler instanceof SpellResearchTableScreenHandler handler)||!handler.canUse(player)
                ||!(handler.getInventory() instanceof SpellResearchTableBlockEntity table))return;
        FormationElement element=FormationElement.byId(elementId);
        if(element==null||level<1||level>FormationData.MAX_FORMATION_LEVEL)return;
        String result=transact(table,FormationKnowledgeComponent.get(player),elementId,variant,level,scribing,discount);
        if(!result.equals("ok")){
            if(result.equals("no_ink"))error(player,result,Text.translatable(element.getNameKey()),level);
            else if(result.equals("no_dust"))error(player,result,learningCost(level,discount));
            else error(player,result);
            return;
        }
        if(!scribing)FormationKnowledgeComponent.sync(player);
        handler.sendContentUpdates();
        player.getWorld().playSound(null,table.getPos(),scribing?SoundEvents.ITEM_BOOK_PAGE_TURN:SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,SoundCategory.BLOCKS,.5f,1.2f);
        player.sendMessage(Text.translatable("message.ssc_addon.research."+(scribing?"scribed":"learned"),Text.translatable(element.getNameKey()),level).formatted(Formatting.GREEN),true);
    }

    public static String transact(Inventory table,FormationKnowledgeComponent knowledge,String elementId,String variant,int level,boolean scribing,int discount){
        FormationElement element=FormationElement.byId(elementId);
        if(element==null||level<1||level>FormationData.MAX_FORMATION_LEVEL)return "not_recorded";
        String normalized=FormationData.normalizeVariant(variant);
        if(element==FormationElement.UNIVERSAL&&normalized==null)normalized=FormationData.VARIANT_REGEN;
        if(!scribing){
            if(!knowledge.hasRecorded(element,normalized,level))return "not_recorded";
            if(knowledge.hasLearned(element,normalized,level))return "already_learned";
            int cost=learningCost(level,discount);ItemStack dust=table.getStack(SpellResearchTableBlockEntity.SLOT_MOONDUST);
            if(!dust.isOf(RegCustomItem.UNTREATED_MOONDUST)||dust.getCount()<cost)return "no_dust";
            dust.decrement(cost);knowledge.learn(element,normalized,level);table.markDirty();return "ok";
        }
        if(!knowledge.hasLearned(element,normalized,level))return "not_learned";
        if(!(table.getStack(SpellResearchTableBlockEntity.SLOT_PAPER).getItem() instanceof BlankFormationPaperItem))return "no_paper";
        ItemStack ink=table.getStack(SpellResearchTableBlockEntity.SLOT_INK);
        if(!(ink.getItem() instanceof FormationInkItem inkItem)||ink.getCount()<level
                ||(element==FormationElement.UNIVERSAL?inkItem.getType()!=FormationInkItem.Type.NORMAL:inkItem.getType().element!=element)){
            return "no_ink";
        }
        if(!table.getStack(SpellResearchTableBlockEntity.SLOT_OUTPUT).isEmpty())return "output_full";
        table.getStack(SpellResearchTableBlockEntity.SLOT_PAPER).decrement(1);ink.decrement(level);
        table.setStack(SpellResearchTableBlockEntity.SLOT_OUTPUT,FormationData.create(element,level,element==FormationElement.UNIVERSAL?normalized:null));
        table.markDirty();return "ok";
    }

    private static int learningCost(int level,int discount){return Math.max(1,level*2-level*2*Math.max(0,Math.min(40,discount))/100);}

    private static void error(ServerPlayerEntity player,String key,Object...args){
        player.sendMessage(Text.translatable("message.ssc_addon.research."+key,args).formatted(Formatting.RED),true);
    }
}