package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
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
            checkRandomRuneLoot(world,other,load);
            checkPlainRuneScrolls(world,other,inks,load);
            checkRuneSchemes(world,other,inks,load);
            checkFourthTierLayout(world,inks,load);
            checkResearchOutput(world,inks);
            checkRunePresentation(world);
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
        private static void checkResearchOutput(WorldRuneState world,List<Item> inks){
            var table=new SimpleInventory(SpellResearchTableBlockEntity.SLOT_COUNT);var bag=slotMaterials(inks);var knowledge=ready(world);
            table.setStack(SpellResearchTableBlockEntity.SLOT_OUTPUT,new ItemStack(Items.STONE));
            int[] slots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,world.language());
            fillOuter(slots,2,world.language(),RuneRole.STORE,RuneRole.SOURCE,RuneRole.STABLE,RuneRole.STABLE,RuneRole.SOURCE);
            check(SlottedFormationTransaction.completeEnhanced(table,bag,knowledge,world,2,slots,knowledge.research().completionToken).success(),"occupied workshop output does not block research");
            ItemStack product=table.getStack(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT).copy();
            check(product.isOf(SscAddon.SPELL_FORMATION)&&table.getStack(3).isOf(Items.STONE),"research output is independent from workshop output");
            var before=inventoryNbt(table);var bagBefore=inventoryNbt(bag);
            check(SlottedFormationTransaction.completeEnhanced(table,bag,knowledge,world,2,slots,knowledge.research().completionToken).key().equals("output")
                    &&before.equals(inventoryNbt(table))&&bagBefore.equals(inventoryNbt(bag)),"pending research product blocks repeat completion without charging materials");
            var backpack=new net.minecraft.entity.player.PlayerInventory(null);
            var handler=new SpellResearchTableScreenHandler(1,backpack,table);
            for(int page=0;page<5;page++){
                handler.setActivePage(page);
                check(handler.getSlot(6).isEnabled()==(page==1),"research output is enabled on its own page: "+page);
            }
            handler.setActivePage(1);
            check(!handler.getSlot(6).canInsert(ScrollData.create("fire_bolt",2)),"research output cannot accept scrolls or overwrite a product");
            for(int slot=0;slot<36;slot++)backpack.setStack(slot,new ItemStack(Items.STONE,64));
            check(handler.quickMove(null,6).isEmpty()&&ItemStack.areEqual(product,table.getStack(6)),"full backpack leaves the product in its research slot");
            backpack.setStack(0,ItemStack.EMPTY);
            check(ItemStack.areEqual(product,handler.quickMove(null,6))&&table.getStack(6).isEmpty()
                    &&ItemStack.areEqual(product,backpack.getStack(0))&&table.getStack(3).isOf(Items.STONE),"click-to-collect transfers exactly one product and leaves workshop output alone");
            var legacy=new SimpleInventory(6);legacy.setStack(3,product.copy());legacy.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,2));
            NbtCompound oldNbt=new NbtCompound();net.minecraft.inventory.Inventories.writeNbt(oldNbt,inventoryList(legacy));
            var block=analysisTable();block.readNbt(oldNbt);
            check(block.getStack(3).isEmpty()&&ItemStack.areEqual(product,block.getStack(6))&&block.getStack(0).getCount()==2,"old pending formation moves into appended slot without losing its data or paper");
            NbtCompound savedOutput=new NbtCompound();block.writeNbt(savedOutput);
            var restored=analysisTable();restored.readNbt(savedOutput);
            check(ItemStack.areEqual(product,restored.getStack(6)),"new research output survives save and reload");
            var scroll=ScrollData.create("fire_bolt",2);legacy.setStack(3,scroll.copy());
            oldNbt=new NbtCompound();net.minecraft.inventory.Inventories.writeNbt(oldNbt,inventoryList(legacy));block.readNbt(oldNbt);
            check(ItemStack.areEqual(scroll,block.getStack(3))&&block.getStack(6).isEmpty(),"legacy scroll output is not mistaken for a research scheme");
            check(block.canExtract(6,product,net.minecraft.util.math.Direction.DOWN)&&!block.canInsert(6,product,net.minecraft.util.math.Direction.UP),"research output supports extraction without insertion");
        }
        private static net.minecraft.util.collection.DefaultedList<ItemStack> inventoryList(net.minecraft.inventory.Inventory inventory){
            var list=net.minecraft.util.collection.DefaultedList.ofSize(inventory.size(),ItemStack.EMPTY);
            for(int slot=0;slot<list.size();slot++)list.set(slot,inventory.getStack(slot));return list;
        }
        private static SpellResearchTableBlockEntity analysisTable(){
            return new SpellResearchTableBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.block.Blocks.STONE.getDefaultState());
        }
        private static void checkRunePresentation(WorldRuneState world)throws Exception{
            var previous=net.minecraft.util.Language.getInstance();Map<String,String> translations=new HashMap<>();
            try(var input=FormationResearchIntegrationTest.class.getResourceAsStream("/assets/my_addon/lang/zh_cn.json")){
                net.minecraft.util.Language.load(input,translations::put);
            }
            net.minecraft.util.Language.setInstance(new net.minecraft.util.Language(){
                public String get(String key,String fallback){return translations.getOrDefault(key,previous.get(key,fallback));}
                public boolean hasTranslation(String key){return translations.containsKey(key)||previous.hasTranslation(key);}
                public boolean isRightToLeft(){return false;}
                public net.minecraft.text.OrderedText reorder(net.minecraft.text.StringVisitable text){return previous.reorder(text);}
            });
            try{
                check(SlottedResearchManager.text("rune_outer_incomplete",4,0,4).getString().equals("外圈还缺4个符文。请填满整圈，或清空全部外圈符文。"),
                        "incomplete outer ring feedback formats the actual missing count");
                checkRuneSlotDescriptions(world);
                checkRuneSummary(world);
                for(int level=1;level<=5;level++){
                    ItemStack product=net.jackcooper.shapeShifterCurseAddon.item.SpellFormationItem.create(world,"fire_bolt",level,RuneLayout.empty(level));
                    var name=product.getName();
                    ItemStack ordinary=ScrollData.create("fire_bolt",level);
                    check(name.equals(ordinary.getName())&&!name.getString().endsWith(" 改")
                            &&name.getStyle().getColor().getRgb()==SpellRegistry.get("fire_bolt").getRarity(level).color.getColorValue(),"foundation-only research title matches an ordinary scroll: "+level);
                    check(product.getTooltip(null,net.minecraft.client.item.TooltipContext.Default.BASIC)
                            .equals(ordinary.getTooltip(null,net.minecraft.client.item.TooltipContext.Default.BASIC)),"foundation-only research tooltip matches an ordinary scroll: "+level);
                    int color=SpellRegistry.get("fire_bolt").getRarity(level).color.getColorValue();
                    product.getTooltip(null,net.minecraft.client.item.TooltipContext.Default.BASIC).get(0).visit((style,part)->{
                        if(part.contains("卷轴")||part.contains("改"))check(style.getColor()!=null&&style.getColor().getRgb()==color,"vanilla tooltip retains research rarity color on the scroll title and suffix");
                        return Optional.empty();
                    },net.minecraft.text.Style.EMPTY);
                }
                var plain=ScrollData.create("fire_bolt",2);check(!plain.getName().getString().endsWith(" 改"),"plain scroll does not acquire modified suffix");
                for(RuneRole role:List.of(RuneRole.STORE,RuneRole.DISABLE)){
                    int[] slots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,world.language());
                    if(role==RuneRole.STORE)fillOuter(slots,2,world.language(),RuneRole.STORE,RuneRole.SOURCE,RuneRole.STABLE,RuneRole.STABLE,RuneRole.SOURCE);
                    else fillOuter(slots,2,world.language(),RuneRole.DISABLE,RuneRole.STABLE,RuneRole.STABLE,RuneRole.SOURCE,RuneRole.FIRE);
                    var result=RuneBuildEvaluator.evaluate(2,slots,world.language(),SlottedSpellRecipes.all());
                    ItemStack product=net.jackcooper.shapeShifterCurseAddon.item.SpellFormationItem.createEnhanced(world,2,slots,result);
                    check(product.getName().getString().endsWith(" 改")&&RuneScheme.isModified(product),"actual outer enhancements retain the modified product name");
                    List<net.minecraft.text.Text> lines=new ArrayList<>();product.getItem().appendTooltip(product,null,lines,net.minecraft.client.item.TooltipContext.Default.BASIC);
                    check(lines.stream().noneMatch(line->line.getString().contains("纸槽")||line.getString().contains("强化方案")),"scheme tooltip contains only its profile, no paper-slot recipe");
                    check(lines.stream().anyMatch(line->line.getString().equals(role==RuneRole.STORE?"伤害：+26%":"威力：-12%")),"actual positive and negative scheme effects display explicit signs");
                    check(lines.stream().anyMatch(line->line.getString().equals(role==RuneRole.STORE?"蓄力：+14 tick":"蓄力：-2 tick")),"charge-time changes use signed ticks and simplified label");
                    plain.getOrCreateNbt().put(RuneScheme.KEY,product.getNbt().getCompound(RuneScheme.KEY).copy());
                    check(plain.getName().getString().endsWith(" 改")&&plain.getName().getStyle().getColor().getRgb()==SpellRegistry.get("fire_bolt").getRarity(2).color.getColorValue(),"imprinted scroll keeps rarity color and modified suffix");
                }
                int[] foundation=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,world.language());
                var neutral=ScrollData.create("fire_bolt",2);
                neutral.getOrCreateNbt().put(RuneScheme.KEY,RuneScheme.create(world,2,foundation,
                        RuneBuildEvaluator.evaluate(2,foundation,world.language(),SlottedSpellRecipes.all())));
                var ordinary=ScrollData.create("fire_bolt",2);
                check(neutral.getName().equals(ordinary.getName())
                        &&neutral.getTooltip(null,net.minecraft.client.item.TooltipContext.Default.BASIC)
                        .equals(ordinary.getTooltip(null,net.minecraft.client.item.TooltipContext.Default.BASIC)),"legacy empty scheme uses the complete ordinary scroll presentation");
                check(net.jackcooper.shapeShifterCurseAddon.spell.SpellCastFeedback.spellName(SpellRegistry.get("fire_bolt"),neutral)
                        .equals(net.jackcooper.shapeShifterCurseAddon.spell.SpellCastFeedback.spellName(SpellRegistry.get("fire_bolt"),ordinary)),"HUD and altar names also ignore legacy empty schemes");
            }finally{net.minecraft.util.Language.setInstance(previous);}
        }
        private static void checkRuneSummary(WorldRuneState world)throws Exception{
            var language=world.language();
            int[] slots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),4,language);
            fillOuter(slots,4,language,RuneRole.GAIN,RuneRole.MERGE,RuneRole.STABLE,RuneRole.GAIN,
                    RuneRole.STORE,RuneRole.SOURCE,RuneRole.STABLE,RuneRole.STABLE);
            var result=RuneBuildEvaluator.evaluate(4,slots,language,SlottedSpellRecipes.all());
            check(result.valid(),"mixed resonance comparison uses a complete legal ring");
            var summary=RuneSummary.write(result);var packet=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            try{packet.writeNbt(summary);summary=packet.readNbt();}finally{packet.release();}
            check(summary!=null,"effect comparison survives real packet serialization");
            var damage=summary.getList("Rows",net.minecraft.nbt.NbtElement.COMPOUND_TYPE).getCompound(0);
            check(damage.getString("Key").equals("damage_total")&&damage.getInt("Base")==52&&damage.getInt("Interaction")==24
                    &&damage.getInt("Total")==76&&damage.getInt("Effective")==60,
                    "comparison groups power and damage before the same cap used by casting");
            var lines=RuneSummary.lines(summary).stream().map(net.minecraft.text.Text::getString).toList();
            check(lines.contains("伤害合计：实际+60%")&&lines.contains("基础+52%，组合+24%，累计+76%")
                    &&lines.contains("累计+76% → 生效+60%，溢出16个百分点；代价照常。"),
                    "Chinese comparison exposes actual output and over-cap contributions");
            check(lines.contains("已激活：蓄能爆发（每圈一次）")&&lines.contains("已激活：重型投射（每圈一次）"),
                    "all active resonance families are visible without revealing a spell foundation");
            var original=summary.copy();
            for(var recipe:SlottedSpellRecipes.all())for(int level=1;level<=5;level++){
                var plain=RuneBuildEvaluator.evaluate(level,RuneLayout.foundation(recipe,level,language),language,SlottedSpellRecipes.all());
                var defaults=RuneSummary.preview(plain,level,null).getCompound("Costs");
                check(!defaults.getBoolean("Equipped")&&defaults.getInt("BaseMana")==defaults.getInt("Mana")
                        &&defaults.getInt("BaseTime")==defaults.getInt("Time")&&defaults.getInt("BaseCd")==defaults.getInt("Cd"),
                        "all supported spells can quote without an equipped book or player context; neutral effects change no costs");
            }
            var priced=RuneSummary.withCosts(summary,true,20,0,100,true,8,200);var costs=priced.getCompound("Costs");
            check(original.equals(summary)&&costs.getInt("Mana")==38&&costs.getInt("Time")==28&&costs.getInt("Cd")==133
                    &&costs.getInt("SoloTime")==36&&costs.getInt("SoloCd")==266,
                    "contextual preview uses fixed plus percentage mana, real instant preparation, book and solo cooldowns");
            check(RuneSummary.lines(priced).stream().map(net.minecraft.text.Text::getString).toList().contains("耗蓝：20 → 38（+18）"),
                    "cost comparison shows final numbers and their difference from an unmodified spell");
            var pending=RuneSummary.withCosts(summary,false,-1,8,100,false,8,100);
            check(pending.getCompound("Costs").getInt("Mana")==-1&&RuneSummary.lines(pending).stream()
                    .anyMatch(line->line.getString().contains("耗蓝配置尚未同步")),"unavailable mana never becomes a zero-cost quote");
            var product=net.jackcooper.shapeShifterCurseAddon.item.SpellFormationItem.createEnhanced(world,4,slots,result);
            var profile=product.getNbt().getCompound(RuneScheme.KEY);
            check(profile.getInt("Rules")==4&&profile.getCompound("Summary").equals(summary)
                    &&RuneScheme.authoritative(world,profile)!=null,"new scheme freezes exactly the server comparison and modifiers");
            var forged=profile.copy();forged.getCompound("Summary").getList("Rows",net.minecraft.nbt.NbtElement.COMPOUND_TYPE)
                    .getCompound(0).putInt("Effective",999);
            check(RuneScheme.authoritative(world,forged)==null,"display summary cannot forge a registered profile");
            var load=WorldRuneState.class.getDeclaredConstructor(NbtCompound.class);load.setAccessible(true);
            var restored=load.newInstance(world.writeNbt(new NbtCompound()));
            check(restored.worldId().equals(world.worldId())&&restored.language().equals(language)
                    &&RuneScheme.authoritative(restored,profile).getCompound("Summary").equals(summary),
                    "new snapshots reload without rerandomizing language or recalculating contributions");
            List<net.minecraft.text.Text> tooltip=new ArrayList<>();RuneTooltips.append(product,tooltip);
            check(tooltip.stream().anyMatch(line->line.getString().equals("实际伤害合计：+60%"))
                    &&tooltip.stream().anyMatch(line->line.getString().contains("溢出16个百分点")),"item tooltip reports the same frozen effective cap");
            var scroll=ScrollData.create("fire_bolt",4);var table=new SimpleInventory(7);table.setStack(0,product);table.setStack(3,scroll);
            check(RuneScheme.imprint(table,world)==0&&RuneScheme.authoritativeScroll(restored,scroll)!=null
                    &&RuneScheme.modifiers(null,scroll,4).power(100,true,false)==160
                    &&RuneScheme.modifiers(null,scroll,4).cooldown(100)==133,"imprint transfers the exact effects and costs into the real scroll path");
            check(RuneScheme.modifiers(null,scroll,3)==RuneModifiers.NONE,"lower selected level suspends new resonances and their costs together");
            int[] oldSlots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,language);
            fillOuter(oldSlots,2,language,RuneRole.STORE,RuneRole.SOURCE,RuneRole.STABLE,RuneRole.STABLE,RuneRole.SOURCE);
            int[] oldValues=new int[RuneModifiers.Stat.values().length];
            oldValues[RuneModifiers.Stat.DAMAGE.ordinal()]=20;oldValues[RuneModifiers.Stat.SPEED.ordinal()]=30;
            oldValues[RuneModifiers.Stat.MANA.ordinal()]=28;oldValues[RuneModifiers.Stat.TIME.ordinal()]=14;oldValues[RuneModifiers.Stat.FLAT_MANA.ordinal()]=5;
            var legacy=new NbtCompound();legacy.putUuid("World",world.worldId());legacy.putInt("Version",3);legacy.putInt("Rules",3);
            legacy.putString("Spell","fire_bolt");legacy.putInt("Level",2);legacy.putIntArray("Slots",oldSlots);legacy.putInt("Stability",86);
            legacy.put("Modifiers",new RuneModifiers(oldValues).write());legacy.putUuid("Id",world.registerScheme(legacy));
            var oldScroll=ScrollData.create("fire_bolt",2);oldScroll.getOrCreateNbt().put(RuneScheme.KEY,legacy);
            var before=oldScroll.getNbt().copy();restored=load.newInstance(world.writeNbt(new NbtCompound()));
            check(RuneScheme.authoritativeScroll(restored,oldScroll)!=null&&before.equals(oldScroll.getNbt())
                    &&RuneScheme.modifiers(null,oldScroll,2).effectivePower(true,false)==20
                    &&RuneScheme.modifiers(null,oldScroll,2).get(RuneModifiers.Stat.SPEED)==30
                    &&RuneScheme.modifiers(null,oldScroll,2).cooldown(100)==100,
                    "rule-three full-ring scrolls keep every original effect and cost despite new adjacency semantics");
            slots=RuneLayout.foundation(SlottedSpellRecipes.get("flame_nova"),2,language);
            slots[3]=language.glyph(RuneRole.SOURCE);slots[4]=language.glyph(RuneRole.STORE);
            result=RuneBuildEvaluator.evaluate(2,slots,language,SlottedSpellRecipes.all());
            var inactive=RuneSummary.lines(RuneSummary.write(result)).stream().map(net.minecraft.text.Text::getString).toList();
            check(inactive.contains("未激活：重型投射（无协同额外费用）"),"unsupported resonance is visible without claiming a bonus or extra payment");
            var view=RuneSlotTooltips.write(result);
            check(RuneSlotTooltips.slot(2,3,slots,language.meanings(),view,true).stream().anyMatch(line->line.getString().contains("不支付协同额外费用")),
                    "adjacent unsupported pair receives an explanatory slot hint");
            slots=RuneLayout.foundation(SlottedSpellRecipes.get("curse_mark"),4,language);
            fillOuter(slots,4,language,RuneRole.CURSE,RuneRole.INSIGHT,RuneRole.CONVERT,RuneRole.CURSE,
                    RuneRole.INSIGHT,RuneRole.CONVERT,RuneRole.STABLE,RuneRole.STABLE);
            result=RuneBuildEvaluator.evaluate(4,slots,language,SlottedSpellRecipes.all());summary=RuneSummary.write(result);
            var mark=summary.getList("Rows",net.minecraft.nbt.NbtElement.COMPOUND_TYPE).getCompound(0);
            check(result.valid()&&mark.getString("Key").equals("mark_total")&&mark.getInt("Total")==100&&mark.getInt("Effective")==60
                    &&result.modifiers().duration(100,RuneModifiers.Stat.MARK,RuneModifiers.Stat.NEGATIVE)==160,
                    "combined mark duration uses the real shared duration cap instead of two misleading separate caps");
        }
        private static void checkRuneSlotDescriptions(WorldRuneState world){
            var language=world.language();int[] meanings=language.meanings();
            int[] slots=RuneLayout.foundation(SlottedSpellRecipes.get("flame_nova"),2,language);
            fillOuter(slots,2,language,RuneRole.GAIN,RuneRole.MERGE,RuneRole.STABLE,RuneRole.FIRE,RuneRole.STABLE);
            var result=RuneBuildEvaluator.evaluate(2,slots,language,SlottedSpellRecipes.all());
            var view=RuneSlotTooltips.write(result);var packet=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            try{packet.writeNbt(view);view=packet.readNbt();}finally{packet.release();}
            check(view!=null,"slot evaluation survives actual packet NBT round trip");
            var gain=RuneSlotTooltips.slot(2,3,slots,meanings,view,true).stream().map(net.minecraft.text.Text::getString).toList();
            check(gain.contains("威力：+13%")&&!gain.contains("威力：+10%")&&gain.contains("耗蓝：+8%，另加1点")
                    &&gain.contains("稳定度：-12")&&gain.contains("蓄力：+2 tick"),"hovered slot shows authoritative synergy and separate costs");
            check(gain.stream().anyMatch(line->line.contains("第5槽")&&line.contains("冷却+8%")),"slot describes the specific adjacent combination and its extra costs");
            var stable=RuneSlotTooltips.slot(2,5,slots,meanings,view,true).stream().map(net.minecraft.text.Text::getString).toList();
            check(stable.contains("稳定度：+10")&&!stable.contains("稳定度：-2"),"stable hover displays net restoration");
            var foundation=RuneSlotTooltips.slot(2,0,slots,meanings,view,true).stream().map(net.minecraft.text.Text::getString).toList();
            check(foundation.contains("用于组成法术基础，不提供外圈加成。")&&foundation.stream().noneMatch(line->line.startsWith("耗蓝：")),
                    "foundation hover never advertises enhancement effects or costs");
            var pending=RuneSlotTooltips.slot(2,3,slots,meanings,view,false).stream().map(net.minecraft.text.Text::getString).toList();
            check(pending.contains("正在更新此符文的效果与搭配。")&&!pending.contains("威力：+13%"),"edited slots cannot show obsolete server contributions");
            int[] unknown=new int[18];Arrays.fill(unknown,-1);
            var unidentified=RuneSlotTooltips.slot(2,3,slots,unknown,view,true).stream().map(net.minecraft.text.Text::getString).toList();
            check(unidentified.stream().noneMatch(line->line.startsWith("威力："))&&unidentified.contains("先在研究笔记中辨识这个符文，查看其效果。"),
                    "unknown glyphs keep identification behavior");
            slots=RuneLayout.foundation(SlottedSpellRecipes.get("flame_nova"),2,language);
            slots[3]=language.glyph(RuneRole.GAIN);slots[4]=language.glyph(RuneRole.DISABLE);
            result=RuneBuildEvaluator.evaluate(2,slots,language,SlottedSpellRecipes.all());view=RuneSlotTooltips.write(result);
            var disabled=RuneSlotTooltips.slot(2,4,slots,meanings,view,true).stream().map(net.minecraft.text.Text::getString).toList();
            check(disabled.contains("威力：-6%")&&disabled.contains("蓄力：-4 tick")&&disabled.contains("耗蓝：+1点"),
                    "disable hover shows reduced actual effects, charge benefit and fixed mana");
            slots=RuneLayout.foundation(SlottedSpellRecipes.get("frost_armor"),2,language);slots[3]=language.glyph(RuneRole.STORE);
            result=RuneBuildEvaluator.evaluate(2,slots,language,SlottedSpellRecipes.all());view=RuneSlotTooltips.write(result);
            var inactive=RuneSlotTooltips.slot(2,3,slots,meanings,view,true).stream().map(net.minecraft.text.Text::getString).toList();
            check(inactive.contains("该法术没有对应的可增强属性；仍支付代价。")&&!inactive.contains("伤害：+20%")
                    &&inactive.contains("蓄力：+8 tick"),"unsupported rune describes why it is inactive without displaying a false bonus");
            slots[3]=language.glyph(RuneRole.FIRE);view=RuneSlotTooltips.write(RuneBuildEvaluator.evaluate(2,slots,language,SlottedSpellRecipes.all()));
            check(RuneSlotTooltips.slot(2,3,slots,meanings,view,true).stream().anyMatch(line->line.getString().contains("元素与法术系别不匹配")),
                    "element mismatch has its own explanation");
            slots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,language);slots[3]=language.glyph(RuneRole.STORE);slots[4]=language.glyph(RuneRole.STABLE);
            view=RuneSlotTooltips.write(RuneBuildEvaluator.evaluate(2,slots,language,SlottedSpellRecipes.all()));
            check(RuneSlotTooltips.slot(2,3,slots,meanings,view,true).stream().anyMatch(line->line.getString().contains("储存和稳定不能相邻")),
                    "hard-conflict hover gives the exact forbidden neighbors");
            slots=RuneLayout.foundation(SlottedSpellRecipes.get("summon_lunar_spirit"),2,language);slots[3]=language.glyph(RuneRole.DISABLE);
            view=RuneSlotTooltips.write(RuneBuildEvaluator.evaluate(2,slots,language,SlottedSpellRecipes.all()));
            var ineffectiveDisable=RuneSlotTooltips.slot(2,3,slots,meanings,view,true).stream().map(net.minecraft.text.Text::getString).toList();
            check(ineffectiveDisable.stream().anyMatch(line->line.startsWith("无法缩短蓄力："))
                    &&ineffectiveDisable.stream().noneMatch(line->line.startsWith("蓄力：")),"inapplicable disable explains why charge time cannot be reduced");
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
        private static void checkFourthTierLayout(WorldRuneState world,List<Item> inks,
                java.lang.reflect.Constructor<WorldRuneState> load) throws Exception {
            var language=world.language();var recipe=SlottedSpellRecipes.get("fire_bolt");
            int[] slots=RuneLayout.foundation(recipe,4,language);
            check(slots.length==19&&RuneLayout.baseSize(4)==11&&RuneLayout.closed(4),"fourth tier has six star vertices, five small sockets and eight closed outer modifiers");
            fillOuter(slots,4,language,RuneRole.GAIN,RuneRole.STABLE,RuneRole.SOURCE,RuneRole.STABLE,RuneRole.SOURCE,RuneRole.STABLE,RuneRole.SOURCE,RuneRole.MERGE);
            var evaluation=RuneBuildEvaluator.evaluate(4,slots,language,SlottedSpellRecipes.all());
            check(evaluation.valid()&&evaluation.modifiers().get(RuneModifiers.Stat.POWER)==28
                    &&evaluation.modifiers().get(RuneModifiers.Stat.CD)==8&&evaluation.stability()==76,"complete fourth tier outer seam applies synergy and local protection without reducing cooldown");
            var knowledge=new FormationKnowledgeComponent();knowledge.research().bind(world.worldId());knowledge.research().rank=5;
            var table=new SimpleInventory(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.SLOT_COUNT);var bag=slotMaterials(inks);table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER));
            var result=SlottedFormationTransaction.completeEnhanced(table,bag,knowledge,world,4,slots,knowledge.research().completionToken);
            check(result.success()&&bag.getStack(8).getCount()==45,"new fourth tier completion charges eleven foundation plus eight modifier runes");
            var scheme=table.getStack(6).copy();var profile=scheme.getNbt().getCompound(RuneScheme.KEY);
            check(profile.getInt("Version")==3&&profile.getIntArray("Slots").length==19
                    &&RuneScheme.authoritative(world,profile)!=null,"new fourth tier registered as current layout");
            table.setStack(0,scheme);table.setStack(3,ScrollData.create("fire_bolt",4));
            check(RuneScheme.imprint(table,world)==0,"new fourth tier can be imprinted");
            var newDiagram=net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.create(world,table.getStack(3));
            check(net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.valid(newDiagram,world.worldId())
                    &&Arrays.equals(slots,net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.referenceSlots(newDiagram.getNbt())),"new fourth tier full diagram matches current nineteen sockets");
            int[] damaged=slots.clone();damaged[5]=language.glyph(RuneRole.DISABLE);
            check(!RuneBuildEvaluator.evaluate(4,damaged,language,SlottedSpellRecipes.all()).valid(),"sixth star vertex is required foundation");
            int[] oldSlots=SlottedSpellRecipes.glyphs(recipe,4,language);Arrays.fill(oldSlots,13,oldSlots.length,-1);
            oldSlots[13]=language.glyph(RuneRole.GAIN);oldSlots[17]=language.glyph(RuneRole.MERGE);
            int[] isolated=RuneLayout.foundation(recipe,2,language);isolated[3]=oldSlots[13];isolated[5]=oldSlots[17];
            var oldEffect=RuneBuildEvaluator.evaluate(2,isolated,language,SlottedSpellRecipes.all());
            var oldDefinition=new NbtCompound();oldDefinition.putUuid("World",world.worldId());oldDefinition.putInt("Version",2);
            oldDefinition.putInt("Rules",1);oldDefinition.putString("Spell","fire_bolt");oldDefinition.putInt("Level",4);
            oldDefinition.putIntArray("Slots",oldSlots);oldDefinition.putInt("Stability",oldEffect.stability());oldDefinition.put("Modifiers",oldEffect.modifiers().write());
            oldDefinition.putUuid("Id",world.registerScheme(oldDefinition));
            var oldScroll=ScrollData.create("fire_bolt",4);oldScroll.getOrCreateNbt().put(RuneScheme.KEY,oldDefinition);
            var before=oldScroll.getNbt().copy();
            var oldDiagram=net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.create(world,oldScroll);
            check(net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.valid(oldDiagram,world.worldId())
                    &&oldDiagram.getNbt().getInt("Version")==2&&oldDiagram.getNbt().getIntArray("Slots").length==18,"existing eighteen-slot diagram remains valid");
            int[] normalized=net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.referenceSlots(oldDiagram.getNbt());
            check(normalized.length==19&&normalized[5]==oldSlots[0]&&normalized[6]==oldSlots[5]
                    &&normalized[11]==-1&&normalized[12]==-1&&normalized[13]==-1
                    &&normalized[14]==oldSlots[13]&&normalized[18]==oldSlots[17],"legacy reference adds sixth vertex and frees only old outer foundation, retains actual modifiers");
            check(!RuneBuildEvaluator.evaluate(4,normalized,language,SlottedSpellRecipes.all()).valid()
                    &&RuneScheme.modifiers(null,oldScroll,4).get(RuneModifiers.Stat.POWER)==22
                    &&RuneScheme.modifiers(null,oldScroll,4).get(RuneModifiers.Stat.CD)==0
                    &&before.equals(oldScroll.getNbt()),"existing partial profile keeps frozen costs; recreation requires filling the new ring");
            check(RuneScheme.authoritativeScroll(load.newInstance(world.writeNbt(new NbtCompound())),oldScroll)!=null,"previous profile authority survives world reload");
            var oldProgress=new ResearchProgress();oldProgress.bind(world.worldId());oldProgress.rank=4;
            oldProgress.runeDrafts.put(4,oldSlots);oldProgress.runeReferences.put("fire_bolt:4",oldSlots);
            oldProgress.runeCompleted.add("fire_bolt:4");oldProgress.slotDrafts.put(4,SlottedFormation.empty(4));
            var oldNbt=oldProgress.write();oldNbt.remove("RuneLayoutVersion");
            var migrated=new ResearchProgress();migrated.read(oldNbt);
            check(Arrays.equals(normalized,migrated.runeDrafts.get(4))&&Arrays.equals(normalized,migrated.runeReferences.get("fire_bolt:4"))
                    &&migrated.runeCompleted.contains("fire_bolt:4")&&migrated.slotDrafts.get(4).length==18,"enhancement progress migrates while legacy grammar data stays intact");
            var again=new ResearchProgress();again.read(migrated.write());
            check(again.write().equals(migrated.write()),"fourth tier progress migration is idempotent");
        }
        private static void checkRandomRuneLoot(WorldRuneState world,WorldRuneState other,
                java.lang.reflect.Constructor<WorldRuneState> load) throws Exception {
            var language=world.language();var worldBefore=world.writeNbt(new NbtCompound());
            for(var recipe:SlottedSpellRecipes.all())for(int level=1;level<=5;level++) {
                for(int seed=0;seed<4;seed++) {
                    var scroll=net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll(world,recipe.spell(),level,seed);
                    check(!scroll.isEmpty()&&ArcaneAnalysis.isUnanalyzed(scroll),"natural plain scroll keeps analysis requirement");
                    check(!scroll.getNbt().contains(RuneScheme.KEY),"natural loot at every tier has no imprint or modified frame");
                    var diagram=net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.create(world,scroll);
                    check(net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.valid(diagram,world.worldId()),"plain natural scroll produces valid matching diagram");
                    int[] slots=diagram.getNbt().getIntArray("Slots");
                    var evaluation=RuneBuildEvaluator.evaluate(level,slots,language,SlottedSpellRecipes.all());
                    check(evaluation.valid()&&evaluation.recipe().equals(recipe)&&evaluation.stability()==100
                            &&Arrays.stream(evaluation.modifiers().values()).allMatch(v->v==0),"natural diagram is neutral and constructible");
                    check(Arrays.equals(slots,RuneLayout.foundation(recipe,level,language))
                            &&Arrays.equals(slots,net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.referenceSlots(diagram.getNbt())),
                            "natural analysis reveals only the foundation with a fully empty outer ring");
                    check(Arrays.equals(slots,RandomRuneFormation.generate(recipe,level,language,seed)),"compatibility generator is also neutral");
                    var before=scroll.getNbt().copy();
                    net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.addRandomRunes(world,scroll,seed+1000);
                    check(before.equals(scroll.getNbt())&&!ItemStack.fromNbt(scroll.writeNbt(new NbtCompound())).getNbt().contains(RuneScheme.KEY),
                            "legacy loot hook and item reload never add enhancements");
                    var foreignDiagram=net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.create(other,scroll);
                    check(Arrays.equals(foreignDiagram.getNbt().getIntArray("Slots"),RuneLayout.foundation(recipe,level,other.language())),
                            "plain loot resolves foundation using its analysis world's language");
                }
            }
            check(worldBefore.equals(world.writeNbt(new NbtCompound()))
                    &&worldBefore.equals(load.newInstance(worldBefore).writeNbt(new NbtCompound())),"natural loot registers no schemes and leaves saved rune language unchanged");
            check(net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll(world,"missing",2,0).isEmpty()
                    &&net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll(world,"fire_bolt",6,0).isEmpty(),
                    "invalid loot produces nothing");
            var scroll=net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.createNaturalScroll(world,"fire_bolt",5,1234);
            var table=new SpellResearchTableBlockEntity(net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.block.Blocks.STONE.getDefaultState());
            table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,3));table.setStack(5,scroll);
            for(int tick=0;tick<199;tick++)table.advanceAnalysis(world);
            check(table.getStack(3).isEmpty()&&ArcaneAnalysis.isUnanalyzed(scroll),"plain scroll analysis respects full duration");
            table.advanceAnalysis(world);
            check(!ArcaneAnalysis.isUnanalyzed(scroll)&&table.getStack(0).getCount()==2
                    &&!scroll.getNbt().contains(RuneScheme.KEY)&&!table.getStack(3).getNbt().contains(RuneScheme.KEY),
                    "timed analysis pays once without enhancing either scroll or diagram");
            int[] reference=table.getStack(3).getNbt().getIntArray("Slots");
            table.removeStack(3);table.removeStack(5);table.setStack(5,scroll);
            for(int tick=0;tick<200;tick++)table.advanceAnalysis(world);
            check(Arrays.equals(reference,table.getStack(3).getNbt().getIntArray("Slots"))&&table.getStack(0).getCount()==1,
                    "repeat plain analysis reproduces the foundation");
            // Existing registered partial imprints remain authoritative; only new assembly uses the full-ring gate.
            var partial=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),2,language);partial[3]=language.glyph(RuneRole.STORE);
            var oldEffect=RuneBuildEvaluator.evaluate(2,partial,language,SlottedSpellRecipes.all());
            var oldProfile=new NbtCompound();oldProfile.putUuid("World",world.worldId());oldProfile.putInt("Version",RuneLayout.VERSION);
            oldProfile.putInt("Rules",2);oldProfile.putString("Spell","fire_bolt");oldProfile.putInt("Level",2);
            oldProfile.putIntArray("Slots",partial);oldProfile.putInt("Stability",oldEffect.stability());oldProfile.put("Modifiers",oldEffect.modifiers().write());
            oldProfile.putUuid("Id",world.registerScheme(oldProfile));
            var legacyScroll=ArcaneAnalysis.markUnanalyzed(ScrollData.create("fire_bolt",2));legacyScroll.getOrCreateNbt().put(RuneScheme.KEY,oldProfile);
            var legacyBefore=legacyScroll.getNbt().copy();
            net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.addRandomRunes(world,legacyScroll,123);
            check(legacyBefore.equals(legacyScroll.getNbt())&&RuneScheme.authoritativeScroll(world,legacyScroll)!=null
                    &&RuneScheme.authoritativeScroll(load.newInstance(world.writeNbt(new NbtCompound())),legacyScroll)!=null,
                    "already generated partial imprint is preserved through loot hook and world reload");
            table.removeStack(3);table.setStack(5,legacyScroll);table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,4));
            for(int tick=0;tick<200;tick++)table.advanceAnalysis(world);
            check(oldProfile.equals(table.getStack(3).getNbt().getCompound(RuneScheme.KEY)),"existing imprinted loot still analyzes its frozen profile");
            for(int failure=0;failure<3;failure++) {
                table.removeStack(3);var invalid=ArcaneAnalysis.markUnanalyzed(legacyScroll.copy());
                if(failure==1)invalid.getNbt().getCompound(RuneScheme.KEY).putInt("Stability",1000);
                if(failure==2)invalid.getOrCreateNbt().putString("Spell","flame_nova");
                table.setStack(5,invalid);var before=inventoryNbt(table);
                for(int tick=0;tick<210;tick++)table.advanceAnalysis(failure==0?other:world);
                check(before.equals(inventoryNbt(table))&&table.getAnalysisTicks()==0,"foreign, forged or mismatched old profile fails without paying");
            }
            var legacy=net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.create(world,"fire_bolt",5);
            legacy.getOrCreateNbt().putInt("Version",1);
            legacy.getOrCreateNbt().putIntArray("Slots",SlottedSpellRecipes.glyphs(SlottedSpellRecipes.get("fire_bolt"),5,language));
            int[] legacyReference=net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.referenceSlots(legacy.getNbt());
            check(Arrays.stream(Arrays.copyOfRange(legacyReference,RuneLayout.baseSize(5),legacyReference.length)).allMatch(v->v==-1),
                    "old grammar runes never become free enhancement references");
            net.jackcooper.shapeShifterCurseAddon.loot.RandomRuneLootFunction.register();
            var entryMethod=net.jackcooper.shapeShifterCurseAddon.loot.MagicScrollLoot.class.getDeclaredMethod("scrollEntry",String.class,int.class,int.class);
            entryMethod.setAccessible(true);
            var entry=(net.minecraft.loot.entry.LootPoolEntry.Builder<?>)entryMethod.invoke(null,"fire_bolt",2,1);
            var lootTable=net.minecraft.loot.LootTable.builder().pool(net.minecraft.loot.LootPool.builder()
                    .rolls(net.minecraft.loot.provider.number.ConstantLootNumberProvider.create(1)).with(entry)).build();
            var gson=net.minecraft.loot.LootGsons.getTableGsonBuilder().create();String json=gson.toJson(lootTable);
            check(!json.contains("ssc_addon:random_rune_formation")&&!json.contains(RuneScheme.KEY),"new chest entries never attach random enhancements");
            check(!gson.toJson(gson.fromJson(json,net.minecraft.loot.LootTable.class)).contains(RuneScheme.KEY),"plain chest entries remain plain after loot-table reload");
            var oldTable=net.minecraft.loot.LootTable.builder().pool(net.minecraft.loot.LootPool.builder()
                    .rolls(net.minecraft.loot.provider.number.ConstantLootNumberProvider.create(1))
                    .with(net.minecraft.loot.entry.ItemEntry.builder(SscAddon.MAGIC_SCROLL)
                            .apply(net.jackcooper.shapeShifterCurseAddon.loot.RandomRuneLootFunction.builder()))).build();
            check(gson.toJson(gson.fromJson(gson.toJson(oldTable),net.minecraft.loot.LootTable.class)).contains("ssc_addon:random_rune_formation"),
                    "legacy random loot function stays loadable for old datapacks");
        }
        private static void fillOuter(int[] slots,int level,RuneLanguage language,RuneRole... roles){
            check(roles.length==slots.length-RuneLayout.baseSize(level),"test fixture fills exactly the enhancement ring");
            for(int i=0;i<roles.length;i++)slots[RuneLayout.baseSize(level)+i]=language.glyph(roles[i]);
        }
        private static void checkPlainRuneScrolls(WorldRuneState world,WorldRuneState other,List<Item> inks,
                                                java.lang.reflect.Constructor<WorldRuneState> load) throws Exception {
            var beforeWorld=world.writeNbt(new NbtCompound());
            for(var recipe:SlottedSpellRecipes.all())for(int level=1;level<=5;level++){
                int[] slots=RuneLayout.foundation(recipe,level,world.language());
                var table=new SimpleInventory(SpellResearchTableBlockEntity.SLOT_COUNT);var bag=slotMaterials(inks);var knowledge=ready(world);
                table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER));
                UUID operation=knowledge.research().completionToken;
                var completed=SlottedFormationTransaction.completeEnhanced(table,bag,knowledge,world,level,slots,operation);
                ItemStack scroll=table.getStack(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT);
                check(completed.success()&&ItemStack.areEqual(scroll,ScrollData.create(recipe.spell(),level)),"empty outer ring directly produces a full-use ordinary scroll: "+recipe.spell()+" / "+level);
                check(!RuneScheme.isModified(scroll)&&RuneScheme.modifiers(null,scroll,level)==RuneModifiers.NONE
                        &&RuneScheme.validScroll(null,scroll,ScrollData.getSpell(scroll)),"ordinary crafted scroll has no modifier profile or world restriction");
                check(bag.getStack(8).getCount()==64-RuneLayout.baseSize(level),"ordinary completion charges only actual foundation runes");
                check(knowledge.hasSpell(recipe.spell())&&knowledge.research().runeCompleted.contains(recipe.spell()+":"+level),"ordinary completion retains atlas and research progression");
                check(ItemStack.areEqual(scroll,ItemStack.fromNbt(scroll.writeNbt(new NbtCompound()))),"ordinary research output survives item serialization");
                var foreignDiagram=net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.create(other,scroll);
                check(net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem.valid(foreignDiagram,other.worldId())
                        &&!foreignDiagram.getNbt().contains(RuneScheme.KEY),"ordinary crafted scroll analyzes normally in another world");
                table.setStack(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT,ItemStack.EMPTY);var beforeBag=inventoryNbt(bag);
                check(SlottedFormationTransaction.completeEnhanced(table,bag,knowledge,world,level,slots,operation).key().equals("duplicate")
                        &&beforeBag.equals(inventoryNbt(bag)),"replay of ordinary completion cannot charge or create another scroll");
            }
            check(beforeWorld.equals(world.writeNbt(new NbtCompound())),"foundation-only crafting does not register empty rune schemes");
            for(int level=1;level<=5;level++){
                int[] slots=RuneLayout.foundation(SlottedSpellRecipes.get("fire_bolt"),level,world.language());
                var mirror=RuneScheme.create(world,level,slots,RuneBuildEvaluator.evaluate(level,slots,world.language(),SlottedSpellRecipes.all()));
                var scroll=ScrollData.create("fire_bolt",level);ScrollData.setUses(scroll,1);ScrollData.setCooldownEnd(scroll,123456);
                scroll.getOrCreateNbt().putString("PocketBinding","retained");var ordinary=scroll.getNbt().copy();
                scroll.getOrCreateNbt().put(RuneScheme.KEY,mirror.copy());var before=scroll.getNbt().copy();
                check(!RuneScheme.isModified(scroll)&&RuneScheme.modifiers(null,scroll,level)==RuneModifiers.NONE,"legacy empty scheme is ordinary at every tier");
                check(!RuneScheme.normalizeUnmodified(other,scroll)&&before.equals(scroll.getNbt()),"foreign empty profile is never silently accepted or rewritten");
                check(RuneScheme.normalizeUnmodified(load.newInstance(world.writeNbt(new NbtCompound())),scroll)
                        &&ordinary.equals(scroll.getNbt()),"authorized saved empty profile becomes ordinary without losing uses, cooldown or bindings");
                var oldScheme=net.jackcooper.shapeShifterCurseAddon.item.SpellFormationItem.create(world,"fire_bolt",level,slots);
                oldScheme.getOrCreateNbt().put(RuneScheme.KEY,mirror.copy());
                var table=new SimpleInventory(SpellResearchTableBlockEntity.SLOT_COUNT);table.setStack(0,oldScheme);table.setStack(3,scroll);
                check(RuneScheme.imprint(table,world)==0&&table.getStack(0).isEmpty()&&ordinary.equals(scroll.getNbt()),"legacy empty scheme imprint preserves an ordinary scroll");
                scroll.getOrCreateNbt().put(RuneScheme.KEY,mirror.copy());scroll.getNbt().getCompound(RuneScheme.KEY).putInt("Stability",1000);before=scroll.getNbt().copy();
                check(!RuneScheme.normalizeUnmodified(world,scroll)&&before.equals(scroll.getNbt()),"forged empty profile cannot bypass authority through normalization");
            }
            var oldSlots=SlottedSpellRecipes.glyphs(SlottedSpellRecipes.get("fire_bolt"),4,world.language());
            Arrays.fill(oldSlots,RuneLayout.baseSize(4,RuneLayout.PREVIOUS_VERSION),oldSlots.length,-1);
            check(!RuneLayout.hasEnhancements(4,oldSlots,RuneLayout.PREVIOUS_VERSION),"previous fourth-tier foundation sockets do not count as enhancement runes");
            oldSlots[oldSlots.length-1]=world.language().glyph(RuneRole.GAIN);
            check(RuneLayout.hasEnhancements(4,oldSlots,RuneLayout.PREVIOUS_VERSION),"previous fourth-tier outer enhancements remain modified");
        }
        private static void checkRuneSchemes(WorldRuneState world,WorldRuneState other,List<Item> inks,
                                             java.lang.reflect.Constructor<WorldRuneState> load) throws Exception {
            RuneEnhancementChecks.run();
            var language=world.language();var recipe=SlottedSpellRecipes.get("fire_bolt");
            int[] slots=RuneLayout.foundation(recipe,2,language);
            fillOuter(slots,2,language,RuneRole.STORE,RuneRole.SOURCE,RuneRole.STABLE,RuneRole.STABLE,RuneRole.SOURCE);
            var table=new SimpleInventory(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.SLOT_COUNT);var bag=slotMaterials(inks);var knowledge=ready(world);
            table.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER));
            UUID token=knowledge.research().completionToken;
            var result=SlottedFormationTransaction.completeEnhanced(table,bag,knowledge,world,2,slots,token);
            check(result.success()&&bag.getStack(8).getCount()==56,"enhanced completion charges three foundation and five enhancement runes");
            ItemStack scheme=table.getStack(6).copy();var mirror=scheme.getNbt().getCompound(RuneScheme.KEY);
            check(mirror.getInt("Rules")==4&&mirror.getInt("Stability")==80&&RuneModifiers.read(mirror.getCompound("Modifiers")).get(RuneModifiers.Stat.DAMAGE)==26
                    &&mirror.contains("Summary"),"registered resonance and comparison snapshot");
            check(RuneScheme.authoritative(world,mirror)!=null&&RuneScheme.authoritative(other,mirror)==null,"world authority rejects foreign scheme");
            var changed=mirror.copy();changed.putInt("Stability",1000);
            check(RuneScheme.authoritative(world,changed)==null,"item mirror cannot forge server snapshot");
            var saved=load.newInstance(world.writeNbt(new NbtCompound()));
            check(RuneScheme.authoritative(saved,mirror)!=null,"scheme registry survives save and load");
            var scroll=ScrollData.create("fire_bolt",2);ScrollData.setUses(scroll,2);ScrollData.setCooldownEnd(scroll,123456);
            scroll.getOrCreateNbt().putString("PocketBinding","retained");var original=scroll.getNbt().copy();
            table.setStack(0,scheme);table.setStack(3,scroll);
            check(RuneScheme.imprint(table,world)==0&&table.getStack(0).isEmpty(),"one-use scheme imprinted");
            var after=scroll.getNbt().copy();after.remove(RuneScheme.KEY);
            check(after.equals(original),"imprint preserves uses, cooldown, selected level and unrelated binding");
            var modifiers=RuneScheme.modifiers(null,scroll,2);
            check(modifiers.effectivePower(true,false)==26&&modifiers.mana(20)==31&&modifiers.time(20)==34&&modifiers.cooldown(100)==110,"effect and costs use same profile");
            check(RuneScheme.modifiers(null,scroll,1)==RuneModifiers.NONE,"lower selected level suspends full profile");
            ScrollData.setLevel(scroll,3);check(RuneScheme.modifiers(null,scroll,3).get(RuneModifiers.Stat.DAMAGE)==26,"upgrade retains original scheme, no new free slots");
            table.setStack(0,scheme.copy()); // consumed copy is empty
            var before=inventoryNbt(table);check(RuneScheme.imprint(table,world)!=0&&before.equals(inventoryNbt(table)),"failed imprint atomic");
            table.setStack(0,net.jackcooper.shapeShifterCurseAddon.item.SpellFormationItem.createEnhanced(world,2,slots,RuneBuildEvaluator.evaluate(2,slots,language,SlottedSpellRecipes.all())));
            before=inventoryNbt(table);check(RuneScheme.imprint(table,world)==3&&before.equals(inventoryNbt(table)),"level mismatch atomic");
            table.setStack(3,ScrollData.create("flame_nova",2));before=inventoryNbt(table);
            check(RuneScheme.imprint(table,world)==3&&before.equals(inventoryNbt(table)),"spell mismatch atomic");
            int[] invalid=slots.clone();invalid[4]=language.glyph(RuneRole.DISABLE);
            var rejectTable=new SimpleInventory(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.SLOT_COUNT);var rejectBag=slotMaterials(inks);rejectTable.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER));
            before=inventoryNbt(rejectBag);var progressBefore=knowledgeNbt(knowledge);
            check(!SlottedFormationTransaction.completeEnhanced(rejectTable,rejectBag,knowledge,world,2,invalid,knowledge.research().completionToken).success()
                    &&before.equals(inventoryNbt(rejectBag))&&progressBefore.equals(knowledgeNbt(knowledge)),"hard conflicts cost nothing and grant no knowledge");
            for(int level=2;level<=5;level++){
                var incomplete=RuneLayout.foundation(recipe,level,language);incomplete[RuneLayout.baseSize(level)]=language.glyph(RuneRole.STABLE);
                var tableBefore=inventoryNbt(rejectTable);var worldBefore=world.writeNbt(new NbtCompound());
                check(RuneLayout.validDraft(level,incomplete)&&!SlottedFormationTransaction.completeEnhanced(rejectTable,rejectBag,knowledge,world,level,incomplete,knowledge.research().completionToken).success()
                        &&before.equals(inventoryNbt(rejectBag))&&tableBefore.equals(inventoryNbt(rejectTable))
                        &&progressBefore.equals(knowledgeNbt(knowledge))&&worldBefore.equals(world.writeNbt(new NbtCompound())),
                        "partial outer rings cannot charge materials, register profiles or grant progress at any tier");
            }
            var restoredKnowledge=new FormationKnowledgeComponent();restoredKnowledge.readFromNbt(knowledgeNbt(knowledge));
            check(restoredKnowledge.research().runeCompleted.contains("fire_bolt:2")&&Arrays.equals(restoredKnowledge.research().runeReferences.get("fire_bolt:2"),slots),"new references persist independently of legacy references");
            knowledge.research().reveal(17);restoredKnowledge.readFromNbt(knowledgeNbt(knowledge));check(restoredKnowledge.research().knows(17),"eighteenth discovery persists");
            int[] oldMeanings=new int[17];for(int i=0;i<17;i++)oldMeanings[i]=i;
            var old=world.writeNbt(new NbtCompound());old.putInt("Version",1);old.putIntArray("Meanings",oldMeanings);old.remove("RuneSchemes");
            var migrated=load.newInstance(old);check(migrated.worldId().equals(world.worldId())&&Arrays.equals(Arrays.copyOf(migrated.language().meanings(),17),oldMeanings)
                    &&migrated.language().meaning(17)==RuneRole.DISABLE&&Arrays.equals(migrated.language().rules(),world.language().rules()),"legacy language appends disable without rerandomizing existing glyphs or rules");
            check(RuneCastContext.current()==RuneModifiers.NONE,"context initially clear");
            try{RuneCastContext.with(modifiers,()->{check(RuneCastContext.current()==modifiers,"context uses immutable cast profile");throw new IllegalStateException("test");});}catch(IllegalStateException expected){}
            check(RuneCastContext.current()==RuneModifiers.NONE,"exception cannot leak modifiers into the next cast");
            var bridgeSlots=RuneLayout.foundation(SlottedSpellRecipes.get("space_blink"),2,language);bridgeSlots[3]=language.glyph(RuneRole.BRIDGE);
            var bridge=RuneBuildEvaluator.evaluate(2,bridgeSlots,language,SlottedSpellRecipes.all()).modifiers();
            var blink=(net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceBlinkSpell)SpellRegistry.get("space_blink");
            double originalRange=blink.getBlinkRange(2);
            check(Math.abs(RuneCastContext.with(bridge,()->blink.getBlinkRange(2))-originalRange*1.2)<.000001,"actual blink range getter consumes frozen modifier");
            var meteor=SpellRegistry.get("meteor");double originalRadius=meteor.getAimRadius(2);
            bridgeSlots=RuneLayout.foundation(SlottedSpellRecipes.get("meteor"),2,language);bridgeSlots[3]=language.glyph(RuneRole.SPLIT);
            var area=RuneBuildEvaluator.evaluate(2,bridgeSlots,language,SlottedSpellRecipes.all()).modifiers();
            check(Math.abs(RuneCastContext.with(area,()->meteor.getAimRadius(2))-originalRadius*1.2)<.000001,"actual meteor preview radius consumes effect modifier");
            var cooldowns=new SharedSpellCooldowns();UUID player=UUID.randomUUID();
            cooldowns.setCooldownEnd(player,"fire_bolt",600,108);
            SharedSpellCooldowns.applyClientSnapshot(cooldowns.snapshot(player,500));
            check(SharedSpellCooldowns.getClientCooldownTotal(SpellRegistry.get("fire_bolt"),600,100)==108,"server duration overrides stale scroll preview");
            check(SharedSpellCooldowns.getClientCooldownTotal(SpellRegistry.get("fire_bolt"),700,200)==200,"longer inherited scroll retains its duration");
            var cooldownSaved=cooldowns.writeNbt(new NbtCompound());var cooldownLoad=SharedSpellCooldowns.class.getDeclaredMethod("readNbt",NbtCompound.class);cooldownLoad.setAccessible(true);
            SharedSpellCooldowns.applyClientSnapshot(((SharedSpellCooldowns)cooldownLoad.invoke(null,cooldownSaved)).snapshot(player,500));
            check(SharedSpellCooldowns.getClientCooldownTotal(SpellRegistry.get("fire_bolt"),0,0)==108,"cooldown duration persists across reload");
            SharedSpellCooldowns.applyClientSnapshot(cooldowns.snapshot(player,600));
            check(SharedSpellCooldowns.getClientCooldownTotal(SpellRegistry.get("fire_bolt"),0,0)==0,"expiry clears duration mirror");
            SharedSpellCooldowns.applyClientSnapshot(null);
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
                    var knowledge=ready(world);var table=new SimpleInventory(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.SLOT_COUNT);var bag=slotMaterials(inks);
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
                    check(table.getStack(6).isOf(SscAddon.SPELL_FORMATION)&&knowledge.hasSpell(recipe.spell()),"product AND atlas");
                    check(Arrays.equals(table.getStack(6).getNbt().getIntArray("Slots"),slots),"product slot data");
                    table.setStack(6,ItemStack.EMPTY);var before=inventoryNbt(bag);
                    check(SlottedFormationTransaction.complete(table,bag,knowledge,world,level,slots,operation).key().equals("duplicate"),"old token rejected");
                    check(before.equals(inventoryNbt(bag))&&table.getStack(6).isEmpty(),"replay changes nothing");
                    knowledge.research().slotDrafts.put(level,slots.clone());var restored=new FormationKnowledgeComponent();restored.readFromNbt(knowledgeNbt(knowledge));
                    check(Arrays.equals(restored.research().slotDrafts.get(level),slots)&&restored.research().slotCompleted.contains(recipe.spell()+":"+level),"slot knowledge persistence");
                    check(restored.research().completionToken.equals(knowledge.research().completionToken),"idempotency token persistence");
                }
            }
            for(Spell spell:SpellRegistry.all())if(spell.getRarity()!=SpellRarity.RED)check(supported.contains(spell.getId().getPath()),"all ordinary spells have recipes");
            var recipe=SlottedSpellRecipes.get("fire_bolt");int[] slots=SlottedSpellRecipes.glyphs(recipe,5,world.language());
            for(String failure:List.of("rank","paper","ink","dust","output","invalid")){
                var knowledge=ready(world);var table=new SimpleInventory(net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity.SLOT_COUNT);var bag=slotMaterials(inks);int[] attempted=slots.clone();
                switch(failure){
                    case "rank"->knowledge.research().rank=4;
                    case "paper"->bag.setStack(9,ItemStack.EMPTY);
                    case "ink"->bag.setStack(0,ItemStack.EMPTY);
                    case "dust"->bag.setStack(8,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,30));
                    case "output"->table.setStack(6,new ItemStack(Items.STONE));
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
            var slotInventory=new SimpleInventory(SpellResearchTableBlockEntity.SLOT_COUNT);
            var handler=new net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler(1,new net.minecraft.entity.player.PlayerInventory(null),slotInventory);
            check(handler.getSlot(0).isEnabled()&&handler.getSlot(3).isEnabled()&&handler.getSlot(5).isEnabled(),"analysis required slots visible");
            check(!handler.getSlot(1).isEnabled()&&!handler.getSlot(2).isEnabled()&&!handler.getSlot(4).isEnabled(),"analysis irrelevant slots hidden");
            handler.getSlot(7).setStack(scroll.copy());handler.quickMove(null,7);
            check(ItemStack.areEqual(slotInventory.getStack(5),scroll)&&slotInventory.getStack(3).isEmpty(),"shift scroll enters analysis only");
            handler.getSlot(7).setStack(scroll.copy());
            check(handler.quickMove(null,7).isEmpty()&&slotInventory.getStack(3).isEmpty()&&handler.getSlot(7).hasStack(),"full analysis does not shift scroll into output");
            handler.getSlot(8).setStack(new ItemStack(SscAddon.BLANK_FORMATION_PAPER,3));handler.quickMove(null,8);
            check(slotInventory.getStack(0).getCount()==3,"shift paper enters paper slot");
            handler.getSlot(9).setStack(new ItemStack(SscAddon.FORMATION_INK_NORMAL));
            check(handler.quickMove(null,9).isEmpty()&&slotInventory.getStack(1).isEmpty(),"analysis cannot shift into hidden ink slot");
            handler.setActivePage(2);
            check(handler.getSlot(1).isEnabled()&&handler.getSlot(2).isEnabled()&&handler.getSlot(4).isEnabled()&&!handler.getSlot(5).isEnabled(),"workshop restores material slots");
            handler.quickMove(null,9);
            check(slotInventory.getStack(1).isOf(SscAddon.FORMATION_INK_NORMAL),"workshop shift ink unchanged");
            handler.setActivePage(0);
            check(handler.quickMove(null,1).isEmpty()&&slotInventory.getStack(1).isOf(SscAddon.FORMATION_INK_NORMAL),"hidden materials preserved");
        }
        private static FormationKnowledgeComponent ready(WorldRuneState world){var k=new FormationKnowledgeComponent();k.research().bind(world.worldId());k.research().rank=5;k.research().attuned=0x7ff;return k;}
        private static SimpleInventory materials(ResearchTarget t,List<Item> inks){var i=new SimpleInventory(5);i.setStack(0,new ItemStack(SscAddon.BLANK_FORMATION_PAPER,2));i.setStack(1,new ItemStack(inks.get(t.family()>=7?0:FormationInkItem.Type.valueOf(t.element().toUpperCase(Locale.ROOT)).ordinal()),8));i.setStack(2,new ItemStack(RegCustomItem.UNTREATED_MOONDUST,12));return i;}
        private static NbtCompound inventoryNbt(net.minecraft.inventory.Inventory i){var n=new NbtCompound();for(int s=0;s<i.size();s++)n.put("Slot"+s,i.getStack(s).writeNbt(new NbtCompound()));return n;}
        private static NbtCompound knowledgeNbt(FormationKnowledgeComponent k){var n=new NbtCompound();k.writeToNbt(n);return n;}
        private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    }
}
