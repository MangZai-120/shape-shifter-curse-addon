package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.jackcooper.shapeShifterCurseAddon.item.MoonDustReliefItem;
import net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.Crafting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.potion.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.resource.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.ssca.neomagic.NeoState;

/** 分别启动真实JEI/REI，使用其配方检索索引并截图实际界面；不将查看器打入正式包。 */
public final class PotionTubeViewerClientProbe {
    private static boolean starting, finished;
    private static Throwable failure;
    private static int phase, ticks, checks;
    private static final long START=System.nanoTime();
    private static String viewer;
    private static ItemStack sidebarMoon;
    private static int sidebarMoonEntries;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    private static ItemStack tube(Potion potion){return PotionTestTubes.divide(PotionUtil.setPotion(new ItemStack(Items.POTION),potion));}
    public static void install(){
        viewer=FabricLoader.getInstance().isModLoaded("jei")?"jei":"rei";
        ClientLifecycleEvents.CLIENT_STARTED.register(mc->org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(finished)return;
            if(System.nanoTime()-START>300_000_000_000L)throw new AssertionError(viewer+" viewer timeout, phase "+phase);
            if(mc.getOverlay()!=null)return;
            if(!starting&&mc.currentScreen instanceof TitleScreen){
                starting=true;mc.options.pauseOnLostFocus=false;mc.options.getViewDistance().setValue(4);
                mc.options.getMaxFps().setValue(60);mc.options.getGuiScale().setValue(3);mc.onResolutionChanged();
                var data=new DataConfiguration(new DataPackSettings(List.of("vanilla","fabric"),List.of()),DataConfiguration.SAFE_MODE.enabledFeatures());
                mc.createIntegratedServerLoader().createAndStart("TubeViewer-"+System.currentTimeMillis(),
                    new LevelInfo("Tube viewer regression",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),data),
                    new GeneratorOptions(20261031L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
            if(mc.player==null||mc.world==null)return;
            if(phase==0){
                if(!(viewer.equals("jei")?Jei.ready():Rei.ready()))return;
                mc.setScreen(new InventoryScreen(mc.player));
                check(tube(Potions.WATER).getName().getString().equals("装水试管"),"中文应为装水试管");
                var normalDefault=PotionTestTubes.FILLED.getDefaultStack();
                check(normalDefault.isOf(PotionTestTubes.FILLED)&&PotionUtil.getPotion(normalDefault)==Potions.HEALING,"普通试管默认条目应为有效治疗药水");
                if(viewer.equals("jei"))Jei.verify();else Rei.verify();
                // 从实际侧栏取得物品，再打开R对应的配方界面；不能重新构造标准月尘堆栈掩盖旧条目。
                open(sidebarMoon);phase=1;ticks=0;
            }else if(phase==1&&++ticks>25){
                save(mc,viewer+"-moon-dust-recipes.png");open(tube(Potions.WATER));phase=2;ticks=0;
            }else if(phase==2&&++ticks>25){
                save(mc,viewer+"-water-recipes.png");
                String result=NeoState.JSON.toJson(Map.of("viewer",viewer,"checks",checks,"real_client",true,"actual_recipe_index",true,
                    "sidebar_moon_entries",sidebarMoonEntries,"actual_sidebar_stack",Registries.ITEM.getId(sidebarMoon.getItem()).toString()));
                Files.writeString(Path.of(viewer+"-tube-viewer-result.json"),result);
                Files.writeString(Path.of("tube-viewer-result.json"),result);
                finished=true;System.out.println("TUBE_VIEWER_CLIENT_PASS viewer="+viewer+" checks="+checks);mc.scheduleStop();
            }
        }catch(Throwable e){failure=e;finished=true;e.printStackTrace();mc.scheduleStop();}});
    }
    private static void verifyQueries(java.util.function.Function<ItemStack,List<Crafting>> search){
        for(var potion:List.of(Potions.WATER,Potions.HEALING,Potions.POISON)){
            var stack=tube(potion);
            var recipes=search.apply(stack);
            check(recipes.size()==1,"应只找到一种对应的分装配方: "+stack.getName().getString()+" actual="+recipes.size());
            check(recipes.get(0).output().isOf(PotionTestTubes.FILLED)&&PotionUtil.getPotion(recipes.get(0).output())==potion,"不能串到月尘专属物品或其它药水的配方");
            check(recipes.get(0).output().getCount()==3,"每瓶分装三管");
        }
        var custom=sidebarMoon.copy();custom.setCustomName(net.minecraft.text.Text.literal("Renamed moon dust"));
        custom.getOrCreateNbt().putInt("CustomPotionColor",0xAA00FF);
        check(search.apply(custom).size()==1,"改名染色后仍找到月尘配方");
    }
    private static boolean ourPotionItem(ItemStack stack){
        return stack.isOf(MoonDustReliefItem.VIAL)||stack.isOf(MoonDustReliefItem.BOTTLE)||stack.isOf(MoonDustReliefItem.SPLASH)
            ||stack.isOf(PotionTestTubes.EMPTY)||stack.isOf(PotionTestTubes.FILLED);
    }
    private static boolean internalCarrierTube(ItemStack stack){
        return stack.isOf(PotionTestTubes.FILLED)&&Registries.POTION.getId(PotionUtil.getPotion(stack)).toString().equals("ssc_addon:universal_energy");
    }
    private static boolean internalVanillaCarrier(ItemStack stack){
        if(!(stack.isOf(Items.POTION)||stack.isOf(Items.SPLASH_POTION)||stack.isOf(Items.LINGERING_POTION)||stack.isOf(Items.TIPPED_ARROW))
                ||!stack.hasNbt())return false;
        String potion=stack.getNbt().getString("Potion");
        return potion.equals("ssc_addon:universal_energy")||potion.equals("ssc_addon:moon_dust_tube")
            ||potion.equals("ssca_neomagic:moon_dust_tube");
    }
    private static void verifySidebar(List<ItemStack> entries,java.util.function.Function<ItemStack,List<Crafting>> search){
        var named=entries.stream().filter(stack->stack.getName().getString().equals("月尘试管")).toList();
        sidebarMoonEntries=named.size();
        check(!named.isEmpty(),viewer+"实际侧栏必须包含月尘试管");
        // 全量检查所有同名条目：旧独立VIAL和通用NBT双份曾导致一个能查、另一个不能查。
        for(var stack:named){
            String id=Registries.ITEM.getId(stack.getItem()).toString();
            var recipes=search.apply(stack);
            check(recipes.size()==1,viewer+"侧栏月尘条目必须能通过R检索唯一配方: "+id+" actual="+recipes.size());
            var recipe=recipes.get(0);
            check(id.equals("ssc_addon:moon_dust_vial")&&stack.isOf(MoonDustReliefItem.VIAL),"月尘只允许使用ssc_addon专属物品");
            check(recipe.output().isOf(MoonDustReliefItem.VIAL)&&recipe.output().getCount()==3,"月尘配方必须真实产出三支专属月尘试管");
            check(recipe.grid().stream().flatMap(Collection::stream).filter(s->s.isOf(MoonDustReliefItem.BOTTLE)).count()==1
                &&recipe.grid().stream().flatMap(Collection::stream).filter(s->s.isOf(PotionTestTubes.EMPTY)).count()==3,"月尘配方必须使用一瓶月尘药水和三个空管");
        }
        check(named.size()==1,viewer+"侧栏不能存在同名而不同ID的月尘试管: "+named.size());
        sidebarMoon=named.get(0).copy();
        var ours=entries.stream().filter(PotionTubeViewerClientProbe::ourPotionItem).toList();
        check(ours.stream().allMatch(s->Registries.ITEM.getId(s.getItem()).getNamespace().equals("ssc_addon")),"药水和试管全部使用ssc_addon物品ID");
        check(ours.stream().noneMatch(s->s.getName().getString().contains("item.")),"侧栏药水中文名称不能泄露item.*翻译键");
        check(entries.stream().noneMatch(PotionTubeViewerClientProbe::internalCarrierTube),"内部通用能量AOE载体不能出现在试管侧栏");
        check(entries.stream().noneMatch(PotionTubeViewerClientProbe::internalVanillaCarrier),"真实侧栏所有条目均不能包含内部原版药水瓶、喷溅瓶、滞留瓶或药箭载体");
        var normal=ours.stream().filter(s->s.isOf(PotionTestTubes.FILLED)).toList();
        check(normal.stream().anyMatch(s->PotionUtil.getPotion(s)==Potions.POISON),"侧栏应包含真实剧毒试管");
        check(normal.stream().noneMatch(s->PotionUtil.getPotion(s)==Potions.WATER),"无药效水管不占默认侧栏");
    }
    private static void verifyCatalogue(List<Crafting> recipes){
        var filling=recipes.stream().filter(recipe->recipe.note().equals("test_tube_filling")).toList();
        check(!filling.isEmpty(),"真实配方索引中必须包含试管分装");
        check(filling.stream().noneMatch(recipe->internalCarrierTube(recipe.output())),"内部AOE载体不能伪装成普通试管分装配方");
        check(filling.stream().allMatch(recipe->recipe.id().getNamespace().equals("ssc_addon")
            &&!recipe.output().getName().getString().contains("item.")),"试管配方ID及中文名称必须正确");
    }
    private static void open(ItemStack stack){if(viewer.equals("jei"))Jei.open(stack);else Rei.open(stack);}
    private static void save(MinecraftClient mc,String name)throws Exception{try(var screenshot=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){screenshot.writeTo(Path.of(name));}}
    public static void verifyResult(){if(failure!=null)throw new AssertionError("Viewer regression failed",failure);if(!finished)throw new AssertionError("Client stopped before result");}

    private static final class Jei {
        private static mezz.jei.api.runtime.IJeiRuntime runtime(){try{
            return (mezz.jei.api.runtime.IJeiRuntime)Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
        }catch(Exception e){throw new IllegalStateException(e);}}
        private static boolean ready(){try{return runtime()!=null&&runtime().getRecipeManager().createRecipeLookup(
            net.jackcooper.shapeShifterCurseAddon.compat.jei.SSCA_JEIPlugin.SPECIAL_CRAFTING).get().findAny().isPresent();}catch(Exception e){return false;}}
        private static mezz.jei.api.recipe.IFocus<ItemStack> focus(ItemStack stack){return runtime().getJeiHelpers().getFocusFactory().createFocus(
            mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,mezz.jei.api.constants.VanillaTypes.ITEM_STACK,stack);}
        private static void verify(){
            java.util.function.Function<ItemStack,List<Crafting>> search=stack->runtime().getRecipeManager().createRecipeLookup(
                net.jackcooper.shapeShifterCurseAddon.compat.jei.SSCA_JEIPlugin.SPECIAL_CRAFTING).limitFocus(List.of(focus(stack))).get().toList();
            runtime().getIngredientFilter().setFilterText("");
            verifySidebar(runtime().getIngredientFilter().getFilteredItemStacks(),search);
            verifyQueries(search);
            verifyCatalogue(runtime().getRecipeManager().createRecipeLookup(net.jackcooper.shapeShifterCurseAddon.compat.jei.SSCA_JEIPlugin.SPECIAL_CRAFTING).get().toList());
        }
        private static void open(ItemStack stack){runtime().getRecipesGui().show(focus(stack));}
    }
    private static final class Rei {
        private static boolean ready(){
            if(me.shedaniel.rei.api.common.plugins.PluginManager.getClientInstance().isReloading())return false;
            try{return me.shedaniel.rei.api.client.registry.display.DisplayRegistry.getInstance().getAll().values().stream().flatMap(Collection::stream)
                .anyMatch(d->d instanceof net.jackcooper.shapeShifterCurseAddon.compat.rei.SscSpecialCraftingDisplay);
            }catch(ConcurrentModificationException reloadStarted){return false;}
        }
        private static me.shedaniel.rei.api.client.view.ViewSearchBuilder view(ItemStack stack){return me.shedaniel.rei.api.client.view.ViewSearchBuilder.builder()
            .addRecipesFor(me.shedaniel.rei.api.common.util.EntryStacks.of(stack));}
        private static void verify(){
            java.util.function.Function<ItemStack,List<Crafting>> search=stack->view(stack).streamDisplays().map(spec->spec.provideInternalDisplay())
                .filter(d->d instanceof net.jackcooper.shapeShifterCurseAddon.compat.rei.SscSpecialCraftingDisplay)
                .map(d->((net.jackcooper.shapeShifterCurseAddon.compat.rei.SscSpecialCraftingDisplay)d).recipe()).toList();
            var entries=me.shedaniel.rei.api.client.registry.entry.EntryRegistry.getInstance().getPreFilteredList().stream()
                .map(entry->entry.getValue()).filter(value->value instanceof ItemStack).map(ItemStack.class::cast).toList();
            verifySidebar(entries,search);
            verifyQueries(search);
            verifyCatalogue(me.shedaniel.rei.api.client.registry.display.DisplayRegistry.getInstance().getAll().values().stream().flatMap(Collection::stream)
                .filter(d->d instanceof net.jackcooper.shapeShifterCurseAddon.compat.rei.SscSpecialCraftingDisplay)
                .map(d->((net.jackcooper.shapeShifterCurseAddon.compat.rei.SscSpecialCraftingDisplay)d).recipe()).toList());
        }
        private static void open(ItemStack stack){check(view(stack).open(),"REI能够打开实际配方界面");}
    }
}
