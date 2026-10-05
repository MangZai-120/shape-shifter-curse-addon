package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.fabricmc.fabric.api.networking.v1.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.sound.*;
import java.util.*;

public final class SlottedResearchManager {
    public static final int OPEN=20,SAVE=21,TEST=22,COMPLETE=23,TRAIN=24,INSPECT=25;
    private SlottedResearchManager(){}
    public static Text text(String key,Object...args){return Text.translatable("research.ssc_addon.slotted."+key,args);}
    public static net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity context(ServerPlayerEntity player,int syncId){
        return player.currentScreenHandler instanceof net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler handler
                &&handler.syncId==syncId&&handler.canUse(player)&&handler.getInventory() instanceof net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity table?table:null;
    }
    public static void handle(ServerPlayerEntity player,int syncId,NbtCompound request){
        var table=context(player,syncId);if(table==null)return;
        var world=WorldRuneState.get(player.getServer());var knowledge=FormationKnowledgeComponent.get(player);var progress=knowledge.research();progress.bind(world.worldId());
        migrateLegacyData(player,table,progress,world.language());
        int action=request.getInt("Action"),level=request.getInt("Level");
        if(level<1||level>5)return;
        if(action!=OPEN&&(!request.containsUuid("World")||!world.worldId().equals(request.getUuid("World"))||request.getInt("Version")!=RuneLayout.VERSION))return;
        int[] slots=request.getIntArray("Slots");
        String key="ready";Object[] arguments={};int[] diagnosis=new int[0];
        if(action==OPEN){slots=progress.runeDrafts.getOrDefault(level,RuneLayout.empty(level));}
        else if(!RuneLayout.validDraft(level,slots)){key="invalid";}
        else{
            progress.runeDrafts.put(level,slots.clone());
            var evaluation=RuneBuildEvaluator.evaluate(level,slots,world.language(),SlottedSpellRecipes.all());
            var recipe=evaluation.valid()?evaluation.recipe():null;
            switch(action){
                case SAVE -> key="saved";
                case INSPECT -> {
                    int glyph=request.getInt("Glyph");
                    if(glyph<0||glyph>17)return;progress.reveal(glyph);
                    if(progress.knows(world.language().glyph(RuneRole.SOURCE))&&progress.knows(world.language().glyph(RuneRole.GAIN))&&progress.knows(world.language().glyph(RuneRole.STABLE)))progress.rank=Math.max(1,progress.rank);
                    key="identified";arguments=new Object[]{Text.translatable("research.ssc_addon.slotted.role."+world.language().meaning(glyph).ordinal())};
                }
                case TEST -> {key=recipe==null?"invalid":"valid";if(recipe!=null)arguments=new Object[]{Text.translatable(SpellRegistry.get(recipe.spell()).getNameKey()),level};
                    // 阶段B：失败时附带分层诊断（服务端计算，只报区域+问题类别，不泄露具体符文）
                    if(!evaluation.problems().isEmpty()){var p=evaluation.problems().get(0);key="rune_"+p.rule();arguments=p.rule().equals("stability")?new Object[]{p.amount()}:new Object[]{p.first()+1,p.second()+1,p.amount()};}}
                case TRAIN -> {
                    if(recipe==null||level!=progress.rank+1||progress.rank<1||(progress.slotCompleted.isEmpty()&&progress.runeCompleted.isEmpty())){key="training";}
                    else{progress.rank=level;key="promoted";}
                }
                case COMPLETE -> {
                    if(!request.containsUuid("Operation"))return;
                    var result=SlottedFormationTransaction.completeEnhanced(table,player.getInventory(),knowledge,world,level,slots,request.getUuid("Operation"));
                    key=result.key();
                    if(result.success()){
                        player.currentScreenHandler.sendContentUpdates();
                        player.getWorld().playSound(null,table.getPos(),SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,SoundCategory.BLOCKS,.7f,.9f);
                        player.getWorld().playSound(null,table.getPos(),SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.BLOCKS,.8f,1.15f);
                    }
                }
                default -> {return;}
            }
        }
        FormationKnowledgeComponent.sync(player);
        NbtCompound state=new NbtCompound();state.putBoolean("Slotted",true);state.putInt("Action",action);state.putInt("Level",level);
        if(request.containsUuid("Request"))state.putUuid("Request",request.getUuid("Request"));
        state.putInt("Revision",request.getInt("Revision"));state.putUuid("World",world.worldId());state.putInt("Version",RuneLayout.VERSION);
        state.putUuid("Operation",progress.completionToken);state.put("Progress",progress.write());state.putIntArray("Slots",slots);
        if(diagnosis.length>0)state.putIntArray("Diagnosis",diagnosis);
        int[] meanings=new int[18],schools=new int[18];Arrays.fill(meanings,-1);
        for(int glyph=0;glyph<18;glyph++){if(progress.knows(glyph))meanings[glyph]=world.language().meaning(glyph).ordinal();schools[glyph]=SlottedFormation.school(world.language(),glyph);}
        if(RuneLayout.validDraft(level,slots)){var evaluated=RuneBuildEvaluator.evaluate(level,slots,world.language(),SlottedSpellRecipes.all());
            state.putInt("Stability",evaluated.stability());state.putBoolean("RuneValid",evaluated.valid());state.put("Modifiers",evaluated.modifiers().write());
            state.put("RuneTooltipState",RuneSlotTooltips.write(evaluated));
            state.putIntArray("Inactive",evaluated.inactive().stream().toArray());state.putIntArray("Synergy",evaluated.synergy().stream().toArray());state.putIntArray("Suppressed",evaluated.suppressed().stream().toArray());
            state.putIntArray("Problems",evaluated.problems().stream().flatMapToInt(p->java.util.stream.IntStream.of(p.first(),p.second())).filter(v->v>=0).distinct().toArray());}
        state.putIntArray("Meanings",meanings);state.putIntArray("Schools",schools);
        var packet=PacketByteBufs.create();packet.writeVarInt(syncId);packet.writeNbt(state);packet.writeText(text(key,arguments));
        packet.writeBoolean(key.equals("valid")||key.equals("completed"));packet.writeVarInt(-1);packet.writeVarInt(-1);packet.writeVarInt(0);
        ServerPlayNetworking.send(player,FormationResearchNetworking.STATE,packet);
    }
    /**
     * 阶段E惰性迁移（2026-10-01 获批·方案甲）：handle 入口统一执行——
     * ① 背包+研究台内的旧版解析图纸（NBT 存阶段A重排前的序列）重写为新序列；
     * ② 玩家存档 slotReferences（已完成方案）中的旧序列重算为新序列。
     * 幂等：已是新序列的项不匹配旧序列，直接跳过。slotDrafts 是玩家自由摆放的草稿，
     * 无法归属法术，不做强制迁移（玩家可自行调整或清空）。
     */
    private static void migrateLegacyData(ServerPlayerEntity player,net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity table,ResearchProgress progress,RuneLanguage language){
        for(int slot=0;slot<player.getInventory().size();slot++)migrateDiagramStack(player.getInventory().getStack(slot),language);
        for(int slot=0;slot<table.size();slot++)migrateDiagramStack(table.getStack(slot),language);
        for(var entry:progress.slotReferences.entrySet()){
            String[] parts=entry.getKey().split(":");
            if(parts.length!=2)continue;
            int[] migrated=SlottedSpellRecipes.migrateIfLegacy(parts[0],parseIntSafe(parts[1]),entry.getValue(),language);
            if(migrated!=null)entry.setValue(migrated);
        }
    }
    private static void migrateDiagramStack(net.minecraft.item.ItemStack stack,RuneLanguage language){
        var nbt=stack.getNbt();
        if(nbt==null||nbt.getInt("Version")!=1||!stack.isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.ANALYZED_SPELL_DIAGRAM))return;
        int[] migrated=SlottedSpellRecipes.migrateIfLegacy(nbt.getString("Spell"),nbt.getInt("Level"),nbt.getIntArray("Slots"),language);
        if(migrated!=null)nbt.putIntArray("Slots",migrated);
    }
    private static int parseIntSafe(String value){try{return Integer.parseInt(value);}catch(NumberFormatException e){return 0;}}
}
