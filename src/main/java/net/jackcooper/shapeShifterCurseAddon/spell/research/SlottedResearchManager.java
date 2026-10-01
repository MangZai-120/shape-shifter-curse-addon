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
        int action=request.getInt("Action"),level=request.getInt("Level");
        if(level<1||level>5)return;
        if(action!=OPEN&&(!request.containsUuid("World")||!world.worldId().equals(request.getUuid("World"))||request.getInt("Version")!=SlottedFormation.VERSION))return;
        int[] slots=request.getIntArray("Slots");
        String key="ready";Object[] arguments={};
        if(action==OPEN){slots=progress.slotDrafts.getOrDefault(level,SlottedFormation.empty(level));}
        else if(!SlottedFormation.validDraft(level,slots)){key="invalid";}
        else{
            progress.slotDrafts.put(level,slots.clone());
            var recipe=SlottedSpellRecipes.identify(level,slots,world.language());
            switch(action){
                case SAVE -> key="saved";
                case INSPECT -> {
                    int glyph=request.getInt("Glyph");
                    if(glyph<0||glyph>16)return;progress.reveal(glyph);
                    if(progress.knows(world.language().glyph(RuneRole.SOURCE))&&progress.knows(world.language().glyph(RuneRole.GAIN))&&progress.knows(world.language().glyph(RuneRole.STABLE)))progress.rank=Math.max(1,progress.rank);
                    key="identified";arguments=new Object[]{Text.translatable("research.ssc_addon.slotted.role."+world.language().meaning(glyph).ordinal())};
                }
                case TEST -> {key=recipe==null?"invalid":"valid";if(recipe!=null)arguments=new Object[]{Text.translatable(SpellRegistry.get(recipe.spell()).getNameKey()),level};}
                case TRAIN -> {
                    if(recipe==null||level!=progress.rank+1||progress.rank<1||progress.slotCompleted.isEmpty()){key="training";}
                    else{progress.rank=level;key="promoted";}
                }
                case COMPLETE -> {
                    if(!request.containsUuid("Operation"))return;
                    var result=SlottedFormationTransaction.complete(table,player.getInventory(),knowledge,world,level,slots,request.getUuid("Operation"));
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
        state.putInt("Revision",request.getInt("Revision"));state.putUuid("World",world.worldId());state.putInt("Version",SlottedFormation.VERSION);
        state.putUuid("Operation",progress.completionToken);state.put("Progress",progress.write());state.putIntArray("Slots",slots);
        int[] meanings=new int[17],schools=new int[17];Arrays.fill(meanings,-1);
        for(int glyph=0;glyph<17;glyph++){if(progress.knows(glyph))meanings[glyph]=world.language().meaning(glyph).ordinal();schools[glyph]=SlottedFormation.school(world.language(),glyph);}
        state.putIntArray("Meanings",meanings);state.putIntArray("Schools",schools);
        var packet=PacketByteBufs.create();packet.writeVarInt(syncId);packet.writeNbt(state);packet.writeText(text(key,arguments));
        packet.writeBoolean(key.equals("valid")||key.equals("completed"));packet.writeVarInt(-1);packet.writeVarInt(-1);packet.writeVarInt(0);
        ServerPlayNetworking.send(player,FormationResearchNetworking.STATE,packet);
    }
}