package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** Real transformed Minecraft NBT/items/transaction checks; does not create a window or game world. */
public final class FormationResearchIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("research.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "research-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(FormationResearchIntegrationTest.class.getName()+"$Probe", true, loader).getMethod("run").invoke(null);
    }

    public static final class Probe {
        private static int checks;
        public static void run() throws Exception {
            register("formation",SscAddon.FORMATION); register("paper",SscAddon.BLANK_FORMATION_PAPER);
            register("manuscript",SscAddon.FORMATION_MANUSCRIPT); register("dust",RegCustomItem.UNTREATED_MOONDUST);
            List<Item> inks=new ArrayList<>();
            for(var type:FormationInkItem.Type.values()) inks.add(register("ink_"+type.id,new FormationInkItem(new Item.Settings(),type)));
            var fresh=WorldRuneState.class.getDeclaredConstructor();fresh.setAccessible(true);
            var load=WorldRuneState.class.getDeclaredConstructor(NbtCompound.class);load.setAccessible(true);
            var world=fresh.newInstance();var other=fresh.newInstance();
            SpellRegistry.init();
            register("spell_formation",SscAddon.SPELL_FORMATION);
            checkEnhancements(inks);
            checkSlotted(world,inks);
            check(!world.worldId().equals(other.worldId()),"independent save identities");
            NbtCompound worldNbt=world.writeNbt(new NbtCompound());
            var restored=load.newInstance(worldNbt);
            check(world.worldId().equals(restored.worldId())&&world.language().equals(restored.language()),"saved language stable");
            NbtCompound legacy=new NbtCompound();NbtList recorded=new NbtList();recorded.add(NbtString.of("ice:3"));recorded.add(NbtString.of("universal:2"));legacy.put("recorded",recorded);
            legacy.putInt("learned_fire",2);legacy.putInt("learned_universal",3);legacy.putInt("learned_universal/recovery",4);
            NbtList atlas=new NbtList();atlas.add(NbtString.of("fire_bolt"));legacy.put("spell_atlas",atlas);
            var oldPlayer=new FormationKnowledgeComponent();oldPlayer.readFromNbt(legacy);
            check(oldPlayer.research().rank==4&&oldPlayer.research().legacy.containsAll(List.of("ice:3","universal/regen:2")),"legacy research migration");
            check(oldPlayer.hasLearned(FormationElement.UNIVERSAL,"regen",3)&&oldPlayer.hasLearned(FormationElement.UNIVERSAL,"recovery",4)&&oldPlayer.hasSpell("fire_bolt"),"legacy variants and spell atlas retained");
            var roundTrip=new FormationKnowledgeComponent();roundTrip.readFromNbt(knowledgeNbt(oldPlayer));
            check(knowledgeNbt(oldPlayer).equals(knowledgeNbt(roundTrip)),"player component NBT round trip");
            var progress=oldPlayer.research();progress.bind(world.worldId());progress.reveal(3);progress.knownRules=1;progress.draft.putString("Legacy","preserved");
            progress.bind(other.worldId());
            check(progress.knownGlyphs==0&&progress.knownRules==0&&progress.references.isEmpty()&&progress.draft.isEmpty(),"foreign world clears language knowledge");
            check(progress.rank==4&&progress.legacy.contains("ice:3"),"foreign world retains old capability");
            var bad=worldNbt.copy();bad.remove("World");
            try {load.newInstance(bad);throw new AssertionError("missing identity accepted");}catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"broken definition refused");}
            checkAnalysis(world);
            checkUnanalyzedItems();
            checkIdentification(world);
            checkIdentificationGates();
            checkResearchFeedback();
            var contents=net.minecraft.util.collection.DefaultedList.ofSize(15,ItemStack.EMPTY);
            var bundle=new net.minecraft.client.gui.tooltip.BundleTooltipComponent(new net.minecraft.client.item.BundleTooltipData(contents,0));
            check(bundle.getWidth(null)==74&&bundle.getHeight()==86,"empty reference bundle geometry");
            contents.set(0,new ItemStack(SscAddon.ANALYZED_SPELL_DIAGRAM));
            check(bundle.getWidth(null)==74&&bundle.getHeight()==86,"occupied reference bundle geometry unchanged");
            System.out.println("Formation research integration passed: "+checks+" checks; 55 enhancements, 115 slotted recipes, timed analysis, exact costs, replay, rejection, old-data migration and reference bundle.");
        }
        private static void checkResearchFeedback(){
            var feedback=new net.jackcooper.shapeShifterCurseAddon.client.screen.SlottedFormationScreen.FeedbackState();
            UUID first=feedback.begin(SlottedResearchManager.TEST);
            check(!feedback.finish(first,SlottedResearchManager.SAVE),"save cannot consume test feedback");
            check(!feedback.finish(UUID.randomUUID(),SlottedResearchManager.TEST),"unrelated reply cannot play result");
            check(feedback.finish(first,SlottedResearchManager.TEST),"current test reply accepted");
            check(!feedback.finish(first,SlottedResearchManager.TEST),"duplicate reply is silent");
            UUID old=feedback.begin(SlottedResearchManager.TEST),latest=feedback.begin(SlottedResearchManager.TEST);
            check(!feedback.finish(old,SlottedResearchManager.TEST),"older same-action reply rejected");
            check(feedback.finish(latest,SlottedResearchManager.TEST),"latest reply remains pending after old reply");
            UUID cancelled=feedback.begin(SlottedResearchManager.COMPLETE);feedback.cancel();
            check(!feedback.finish(cancelled,SlottedResearchManager.COMPLETE),"late completion after timeout is silent");
            check(feedback.allowEdit(0),"first edit audible");
            check(!feedback.allowEdit(30_000_000L)&&!feedback.allowEdit(69_999_999L),"drag edit audio bounded");
            check(feedback.allowEdit(70_000_000L),"next edit audible at interval");
            check(!feedback.allowEdit(70_000_000L),"same-instant edit silent");
            var chime=net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            var sound=net.minecraft.client.sound.PositionedSoundInstance.master(chime,1.45f,.22f);
            check(sound.getId().equals(chime.getId())&&sound.getCategory()==net.minecraft.sound.SoundCategory.MASTER,"local cue identity and channel");
            for(var event:List.of(chime,net.minecraft.sound.SoundEvents.ITEM_BOOK_PAGE_TURN,
                    net.minecraft.sound.SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH)){
                check(Registries.SOUND_EVENT.containsId(event.getId()),"research cue registered: "+event.getId());
            }
        }
        private static void checkEnhancements(List<Item> inks){
            for(int family=0;family<11;family++)for(int level=1;level<=5;level++){
                var target=new ResearchTarget(family,level);var element=FormationElement.byId(target.element());
                var knowledge=new FormationKnowledgeComponent();var table=materials(target,inks);var original=inventoryNbt(table);
                check(ResearchTableManager.transact(table,knowledge,target.element(),target.variant(),level,true,0).equals("not_learned"),"unlearned cannot scribe");
                check(ResearchTableManager.transact(table,knowledge,target.element(),target.variant(),level,false,0).equals("not_recorded")&&original.equals(inventoryNbt(table)),"unrecorded costs nothing");
                knowledge.record(element,target.variant(),level);table.setStack(2,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,level*2-1));original=inventoryNbt(table);
                check(ResearchTableManager.transact(table,knowledge,target.element(),target.variant(),level,false,0).equals("no_dust")&&original.equals(inventoryNbt(table)),"insufficient learning dust atomic");
                table.setStack(2,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,12));
                check(ResearchTableManager.transact(table,knowledge,target.element(),target.variant(),level,false,0).equals("ok")&&table.getStack(2).getCount()==12-level*2,"original learning price");
                check(knowledge.hasLearned(element,target.variant(),1),"higher levels include lower");original=inventoryNbt(table);
                check(ResearchTableManager.transact(table,knowledge,target.element(),target.variant(),level,false,0).equals("already_learned")&&original.equals(inventoryNbt(table)),"duplicate learning costs nothing");
                table.setStack(3,new ItemStack(Items.STONE));original=inventoryNbt(table);
                check(ResearchTableManager.transact(table,knowledge,target.element(),target.variant(),level,true,0).equals("output_full")&&original.equals(inventoryNbt(table)),"full output atomic");
                table.setStack(3,ItemStack.EMPTY);ItemStack correctInk=table.getStack(1).copy();table.setStack(1,new ItemStack(Items.INK_SAC,64));original=inventoryNbt(table);
                check(ResearchTableManager.transact(table,knowledge,target.element(),target.variant(),level,true,0).equals("no_ink")&&original.equals(inventoryNbt(table)),"wrong ink atomic");
                table.setStack(1,correctInk);
                check(ResearchTableManager.transact(table,knowledge,target.element(),target.variant(),level,true,0).equals("ok"),"scribe enhancement");
                check(table.getStack(0).getCount()==1&&table.getStack(1).getCount()==8-level&&table.getStack(2).getCount()==12-level*2,"exact scribing cost");
                ItemStack product=table.getStack(3);check(FormationData.getElement(product)==element&&FormationData.getLevel(product)==level,"correct enhancement product");
                if(family>=7)check(FormationData.getVariant(product).equals(target.variant()),"universal variant retained");
                var restored=new FormationKnowledgeComponent();restored.readFromNbt(knowledgeNbt(knowledge));check(restored.hasLearned(element,target.variant(),level),"restored enhancement knowledge");
            }
            check(SscAddon.FORMATION.getMaxUseTime(new ItemStack(SscAddon.FORMATION))==32,"enhancement recording charge restored");
        }
        private static void checkUnanalyzedItems(){
            for(ItemStack stack:List.of(ScrollData.create("fire_bolt",3),FormationData.create(FormationElement.ICE,3))){
                check(!ArcaneAnalysis.isUnanalyzed(stack),"existing and crafted items stay identified");
                NbtCompound original=stack.getOrCreateNbt().copy();
                ArcaneAnalysis.markUnanalyzed(stack);
                check(ArcaneAnalysis.isUnanalyzed(stack.copy()),"unknown state follows copied stack");
                check(ArcaneAnalysis.isUnanalyzed(ItemStack.fromNbt(stack.writeNbt(new NbtCompound()))),"unknown state persists");
                check(stack.getName().getContent() instanceof net.minecraft.text.TranslatableTextContent content&&content.getKey().equals("item.ssc_addon.unfamiliar_spell"),"unknown name hides identity");
                List<net.minecraft.text.Text> tooltip=new ArrayList<>();stack.getItem().appendTooltip(stack,null,tooltip,net.minecraft.client.item.TooltipContext.BASIC);
                check(tooltip.size()==1&&stack.getTooltipData().isEmpty(),"unknown description and spell icon hidden");
                ArcaneAnalysis.identify(stack);check(original.equals(stack.getNbt()),"identification retains all original data");
            }
        }
        private static void checkIdentification(WorldRuneState world){
            var table=new net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.block.Blocks.STONE.getDefaultState());
            for(Spell spell:SpellRegistry.all()){
                ItemStack scroll=net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll(spell.getId().getPath(),1);
                check(ArcaneAnalysis.isUnanalyzed(scroll)&&ScrollData.getSpell(scroll)==spell,"natural scroll retains identity and unknown state");
                ItemStack expected=scroll.copy();ArcaneAnalysis.identify(expected);
                table.clear();table.setStack(5,scroll);table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,2));
                for(int tick=0;tick<199;tick++)table.advanceAnalysis(world);
                check(ArcaneAnalysis.isUnanalyzed(table.getStack(5))&&table.getStack(0).getCount()==2,"not identified before full duration");
                table.advanceAnalysis(world);
                if(SlottedSpellRecipes.get(spell.getId().getPath())!=null){
                    check(ItemStack.areEqual(expected,table.getStack(5))&&table.getStack(3).isOf(SscAddon.ANALYZED_SPELL_DIAGRAM),"normal scroll unlock and diagram");
                }else check(table.getStack(5).isEmpty()&&ItemStack.areEqual(expected,table.getStack(3)),"special scroll unlock without inventing a recipe");
                check(table.getStack(0).getCount()==1,"one paper per identification");
                table.removeStack(3);for(int tick=0;tick<440;tick++)table.advanceAnalysis(world);
                check(table.getStack(3).isEmpty()&&table.getStack(0).getCount()==1,"identified natural scroll does not auto-repeat: "+spell.getId());
            }
            for(int family=0;family<11;family++)for(int level=1;level<=5;level++){
                var target=new ResearchTarget(family,level);
                ItemStack formation=net.jackcooper.shapeShifterCurseAddon.loot.FormationLoot.createNaturalFormation(FormationElement.byId(target.element()),level,target.variant());
                check(ArcaneAnalysis.isUnanalyzed(formation)&&FormationData.getElement(formation)==FormationElement.byId(target.element()),"natural formation remains colored by its true element");
                ItemStack expected=formation.copy();ArcaneAnalysis.identify(expected);
                table.clear();table.setStack(5,formation);table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,2));
                table.setStack(3,new ItemStack(Items.STONE));
                for(int tick=0;tick<210;tick++)table.advanceAnalysis(world);
                check(ArcaneAnalysis.isUnanalyzed(formation)&&table.getStack(0).getCount()==2,"full output does not identify or charge");
                table.removeStack(3);
                for(int tick=0;tick<70;tick++)table.advanceAnalysis(world);
                ItemStack removed=table.removeStack(5);table.advanceAnalysis(world);
                check(ArcaneAnalysis.isUnanalyzed(removed)&&table.getAnalysisTicks()==0,"cancel leaves formation unknown");
                table.setStack(5,removed);
                for(int tick=0;tick<200;tick++)table.advanceAnalysis(world);
                check(table.getStack(5).isEmpty()&&ItemStack.areEqual(expected,table.getStack(3)),"identified formation returned with variant intact");
                check(table.getStack(0).getCount()==1,"formation identification exact cost");
            }
        }
        private static void checkIdentificationGates(){
            register("spellbook",SscAddon.MOON_DUST_SPELLBOOK);
            var inventory=new net.minecraft.entity.player.PlayerInventory(null);
            var book=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);
            var bookHandler=new net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler(2,inventory,book);
            ItemStack scroll=net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll("fire_bolt",2);
            check(!bookHandler.getSlot(0).canInsert(scroll),"unknown scroll cannot enter book");
            ArcaneAnalysis.identify(scroll);check(bookHandler.getSlot(0).canInsert(scroll),"identified scroll can enter book");
            var altarInventory=new SimpleInventory(net.jackcooper.shapeShifterCurseAddon.screen.InfusionAltarScreenHandler.ALTAR_SLOT_COUNT);
            altarInventory.setStack(0,book);
            var altar=new net.jackcooper.shapeShifterCurseAddon.screen.InfusionAltarScreenHandler(3,inventory,altarInventory);
            ItemStack formation=net.jackcooper.shapeShifterCurseAddon.loot.FormationLoot.createNaturalFormation(FormationElement.ICE,2,null);
            check(!altar.getSlot(3).canInsert(formation),"unknown formation cannot enter altar");
            ArcaneAnalysis.identify(formation);check(altar.getSlot(3).canInsert(formation),"identified formation can enter altar");
            check(net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll("does_not_exist",1).isEmpty(),"invalid command spell rejected");
            check(net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll("fire_bolt",6).isEmpty(),"invalid command level rejected");
            check(net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll("domain",2).isEmpty(),"single-level command spell cannot exceed max");
            var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.server.command.ServerCommandSource>();
            net.jackcooper.shapeShifterCurseAddon.command.SscAddonCommands.register(dispatcher);
            var command=dispatcher.getRoot().getChild("ssc_addon").getChild("give_unanalyzed_scroll");
            check(command!=null&&command.getChildren().size()==1&&command.getChild("spell")!=null,"natural scroll command registered with no target selector");
            check(command.getChild("spell").getCommand()!=null&&command.getChild("spell").getChild("level").getCommand()!=null,"default and explicit level supported");
            for(int permission:List.of(0,1,2)){
                var source=new net.minecraft.server.command.ServerCommandSource(net.minecraft.server.command.CommandOutput.DUMMY,net.minecraft.util.math.Vec3d.ZERO,net.minecraft.util.math.Vec2f.ZERO,null,permission,"test",net.minecraft.text.Text.empty(),null,null);
                check(command.getRequirement().test(source)==(permission>=2),"command OP permission "+permission);
            }
        }
        private static Item register(String name,Item item){return Registries.ITEM.getId(item).equals(Registries.ITEM.getDefaultId())?Registry.register(Registries.ITEM,new Identifier("research_test",name),item):item;}
        private static void checkSlotted(WorldRuneState world,List<Item> inks){
            Set<String> supported=new HashSet<>();
            for(var recipe:SlottedSpellRecipes.all()){
                check(SpellRegistry.get(recipe.spell())!=null,"registered recipe "+recipe.spell());supported.add(recipe.spell());
                for(int level=1;level<=5;level++){
                    check(SlottedSpellRecipes.available(recipe,level),"ordinary spell supports level "+recipe.spell());
                    int[] slots=SlottedSpellRecipes.glyphs(recipe,level,world.language());
                    check(SlottedSpellRecipes.identify(level,slots,world.language()).equals(recipe),"actual recipe identifies uniquely");
                    var knowledge=ready(world);var table=new SimpleInventory(5);var bag=slotMaterials(inks);
                    table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER));
                    int[] costs=SlottedFormation.ink(world.language(),slots);UUID operation=knowledge.research().completionToken;
                    var result=SlottedFormationTransaction.complete(table,bag,knowledge,world,level,slots,operation);
                    check(result.success(),"slotted completion: "+recipe.spell()+" / "+result.key());
                    check(table.getStack(0).isEmpty()&&bag.getStack(9).getCount()==2,"table paper used first");
                    check(bag.getStack(8).getCount()==64-slots.length,"one dust per final rune");
                    for(int index=0;index<8;index++){
                        int school=SlottedFormationTransaction.school((FormationInkItem)inks.get(index));
                        check(bag.getStack(index).getCount()==16-costs[school],"exact per-school rounding");
                    }
                    check(table.getStack(3).isOf(SscAddon.SPELL_FORMATION)&&knowledge.hasSpell(recipe.spell()),"product AND atlas");
                    check(Arrays.equals(table.getStack(3).getNbt().getIntArray("Slots"),slots),"product slot data");
                    table.setStack(3,ItemStack.EMPTY);var before=inventoryNbt(bag);
                    check(SlottedFormationTransaction.complete(table,bag,knowledge,world,level,slots,operation).key().equals("duplicate"),"old token rejected");
                    check(before.equals(inventoryNbt(bag))&&table.getStack(3).isEmpty(),"replay changes nothing");
                    knowledge.research().slotDrafts.put(level,slots.clone());var restored=new FormationKnowledgeComponent();restored.readFromNbt(knowledgeNbt(knowledge));
                    check(Arrays.equals(restored.research().slotDrafts.get(level),slots)&&restored.research().slotCompleted.contains(recipe.spell()+":"+level),"slot knowledge persistence");
                    check(restored.research().completionToken.equals(knowledge.research().completionToken),"idempotency token persistence");
                }
            }
            for(Spell spell:SpellRegistry.all())if(spell.getRarity()!=SpellRarity.RED)check(supported.contains(spell.getId().getPath()),"all ordinary spells have recipes");
            var recipe=SlottedSpellRecipes.get("fire_bolt");int[] slots=SlottedSpellRecipes.glyphs(recipe,5,world.language());
            for(String failure:List.of("rank","paper","ink","dust","output","invalid")){
                var knowledge=ready(world);var table=new SimpleInventory(5);var bag=slotMaterials(inks);int[] attempted=slots.clone();
                switch(failure){
                    case "rank"->knowledge.research().rank=4;
                    case "paper"->bag.setStack(9,ItemStack.EMPTY);
                    case "ink"->bag.setStack(0,ItemStack.EMPTY);
                    case "dust"->bag.setStack(8,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,30));
                    case "output"->table.setStack(3,new ItemStack(Items.STONE));
                    case "invalid"->attempted[0]=-1;
                }
                var beforeBag=inventoryNbt(bag);var beforeTable=inventoryNbt(table);var beforeKnowledge=knowledgeNbt(knowledge);
                var result=SlottedFormationTransaction.complete(table,bag,knowledge,world,5,attempted,knowledge.research().completionToken);
                check(result.key().equals(failure),"slotted rejection: "+failure+" got "+result.key());
                check(beforeBag.equals(inventoryNbt(bag))&&beforeTable.equals(inventoryNbt(table))&&beforeKnowledge.equals(knowledgeNbt(knowledge)),"atomic failure: "+failure);
            }
        }
        private static SimpleInventory slotMaterials(List<Item> inks){
            var inventory=new SimpleInventory(36);for(int index=0;index<8;index++)inventory.setStack(index,new ItemStack(inks.get(index),16));
            inventory.setStack(8,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,64));inventory.setStack(9,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,2));return inventory;
        }
        private static void checkAnalysis(WorldRuneState world){
            register("scroll",SscAddon.MAGIC_SCROLL);register("diagram",SscAddon.ANALYZED_SPELL_DIAGRAM);
            var table=new net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.block.Blocks.STONE.getDefaultState());
            check(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.analysisStatus(table).name().equals("EMPTY"),"empty analysis status");
            ItemStack scroll=ScrollData.create("fire_bolt",3);scroll.getOrCreateNbt().putString("BindingCheck","keep");
            table.setStack(5,scroll.copy());
            check(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.analysisStatus(table).name().equals("NO_PAPER"),"missing paper status");
            for(int tick=0;tick<220;tick++)table.advanceAnalysis(world);
            check(table.getAnalysisTicks()==0&&table.getStack(3).isEmpty()&&ItemStack.areEqual(table.getStack(5),scroll),"missing paper does not analyze or consume scroll");
            table.setStack(5,scroll.copy());table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,2));
            check(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.analysisStatus(table).name().equals("READY"),"ready analysis status");
            for(int tick=0;tick<199;tick++)table.advanceAnalysis(world);
            check(table.getStack(3).isEmpty()&&table.getStack(0).getCount()==2,"analysis not early");
            table.advanceAnalysis(world);var diagram=table.getStack(3);
            check(diagram.isOf(SscAddon.ANALYZED_SPELL_DIAGRAM)&&table.getStack(0).getCount()==1,"analysis diagram and one paper");
            check(ItemStack.areEqual(table.getStack(5),scroll),"analysis retains scroll and NBT");
            check(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.analysisStatus(table).name().equals("OUTPUT_FULL"),"collect output status");
            check(net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.valid(diagram,world.worldId()),"diagram world binding");
            check(!net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.valid(diagram,UUID.randomUUID()),"foreign diagram rejected");
            List<net.minecraft.text.Text> tooltip=new ArrayList<>();diagram.getItem().appendTooltip(diagram,null,tooltip,net.minecraft.client.item.TooltipContext.BASIC);
            check(tooltip.get(0).getContent() instanceof net.minecraft.text.TranslatableTextContent content&&content.getKey().equals("item.ssc_addon.magic_scroll.rarity_tier"),"diagram uses scroll rarity-tier format");
            check(tooltip.size()>=3&&diagram.getTooltipData().isPresent(),"diagram effect, purpose and icon present");
            var roundtrip=ItemStack.fromNbt(diagram.writeNbt(new NbtCompound()));
            check(ItemStack.areEqual(diagram,roundtrip),"diagram NBT round trip");
            for(int tick=0;tick<220;tick++)table.advanceAnalysis(world);
            check(table.getStack(0).getCount()==1&&ItemStack.areEqual(table.getStack(3),diagram),"full output no duplication");
            table.removeStack(3);for(int tick=0;tick<440;tick++)table.advanceAnalysis(world);
            check(table.getStack(3).isEmpty()&&table.getStack(0).getCount()==1&&ItemStack.areEqual(table.getStack(5),scroll),"collecting output must not analyze the retained scroll again");
            check(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.analysisStatus(table).name().equals("COMPLETE"),"completed analysis status");
            var clientInventory=new SimpleInventory(6);clientInventory.setStack(5,scroll.copy());
            check(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.analysisStatus(clientInventory,table.getAnalysisTicks()).name().equals("COMPLETE"),"client uses synced completion progress even without paper");
            NbtCompound completedState=new NbtCompound();table.writeNbt(completedState);
            var completedTable=new net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.block.Blocks.STONE.getDefaultState());
            completedTable.readNbt(completedState);
            completedTable.removeStack(0);for(int tick=0;tick<220;tick++)completedTable.advanceAnalysis(world);
            completedTable.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,2));
            for(int tick=0;tick<440;tick++)completedTable.advanceAnalysis(world);
            check(completedTable.getStack(3).isEmpty()&&completedTable.getStack(0).getCount()==2&&completedTable.getAnalysisTicks()==200,"completion persists across reload and paper removal/refill");
            completedTable.setStack(5,ScrollData.create("frost_spike",1));
            for(int tick=0;tick<199;tick++)completedTable.advanceAnalysis(world);
            check(completedTable.getStack(3).isEmpty()&&completedTable.getStack(0).getCount()==2,"replacement scroll waits full duration");
            completedTable.advanceAnalysis(world);
            check(completedTable.getStack(3).isOf(SscAddon.ANALYZED_SPELL_DIAGRAM)&&completedTable.getStack(0).getCount()==1,"replacement scroll can analyze normally");
            table.removeStack(5);table.advanceAnalysis(world);
            check(table.getAnalysisTicks()==0&&table.getStack(0).getCount()==1,"remove scroll cancels without cost");
            table.setStack(5,scroll.copy());for(int tick=0;tick<80;tick++)table.advanceAnalysis(world);
            table.removeStack(5);table.advanceAnalysis(world);
            check(table.getAnalysisTicks()==0&&table.getStack(0).getCount()==1,"in-progress analysis can still be cancelled");
            table.setStack(5,scroll.copy());for(int tick=0;tick<199;tick++)table.advanceAnalysis(world);
            check(table.getStack(3).isEmpty(),"cancelled analysis restarts full timer");
            NbtCompound saved=new NbtCompound();table.writeNbt(saved);
            var resumed=new net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.block.Blocks.STONE.getDefaultState());
            resumed.readNbt(saved);check(resumed.getAnalysisTicks()==199,"analysis progress persisted");resumed.advanceAnalysis(world);
            check(resumed.getStack(3).isOf(SscAddon.ANALYZED_SPELL_DIAGRAM)&&resumed.getStack(0).isEmpty()&&ItemStack.areEqual(resumed.getStack(5),scroll),"resumed analysis completes exactly once");
            var slotInventory=new SimpleInventory(6);
            var handler=new net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler(1,new net.minecraft.entity.player.PlayerInventory(null),slotInventory);
            check(handler.getSlot(0).isEnabled()&&handler.getSlot(3).isEnabled()&&handler.getSlot(5).isEnabled(),"analysis required slots visible");
            check(!handler.getSlot(1).isEnabled()&&!handler.getSlot(2).isEnabled()&&!handler.getSlot(4).isEnabled(),"analysis irrelevant slots hidden");
            handler.getSlot(6).setStack(scroll.copy());handler.quickMove(null,6);
            check(ItemStack.areEqual(slotInventory.getStack(5),scroll)&&slotInventory.getStack(3).isEmpty(),"shift scroll enters analysis only");
            handler.getSlot(6).setStack(scroll.copy());
            check(handler.quickMove(null,6).isEmpty()&&slotInventory.getStack(3).isEmpty()&&handler.getSlot(6).hasStack(),"full analysis does not shift scroll into output");
            handler.getSlot(7).setStack(new ItemStack(SscAddon.BLANK_FORMATION_PAPER,3));handler.quickMove(null,7);
            check(slotInventory.getStack(0).getCount()==3,"shift paper enters paper slot");
            handler.getSlot(8).setStack(new ItemStack(SscAddon.FORMATION_INK_NORMAL));
            check(handler.quickMove(null,8).isEmpty()&&slotInventory.getStack(1).isEmpty(),"analysis cannot shift into hidden ink slot");
            handler.setActivePage(2);
            check(handler.getSlot(1).isEnabled()&&handler.getSlot(2).isEnabled()&&handler.getSlot(4).isEnabled()&&!handler.getSlot(5).isEnabled(),"workshop restores material slots");
            handler.quickMove(null,8);
            check(slotInventory.getStack(1).isOf(SscAddon.FORMATION_INK_NORMAL),"workshop shift ink unchanged");
            handler.setActivePage(0);
            check(handler.quickMove(null,1).isEmpty()&&slotInventory.getStack(1).isOf(SscAddon.FORMATION_INK_NORMAL),"hidden materials preserved");
        }
        private static FormationKnowledgeComponent ready(WorldRuneState world){var k=new FormationKnowledgeComponent();k.research().bind(world.worldId());k.research().rank=5;k.research().attuned=0x7ff;return k;}
        private static SimpleInventory materials(ResearchTarget t,List<Item> inks){var i=new SimpleInventory(5);i.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,2));i.setStack(1,new ItemStack(inks.get(t.family()>=7?0:FormationInkItem.Type.valueOf(t.element().toUpperCase(Locale.ROOT)).ordinal()),8));i.setStack(2,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,12));return i;}
        private static NbtCompound inventoryNbt(SimpleInventory i){var n=new NbtCompound();for(int s=0;s<i.size();s++)n.put("Slot"+s,i.getStack(s).writeNbt(new NbtCompound()));return n;}
        private static NbtCompound knowledgeNbt(FormationKnowledgeComponent k){var n=new NbtCompound();k.writeToNbt(n);return n;}
        private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    }
}
