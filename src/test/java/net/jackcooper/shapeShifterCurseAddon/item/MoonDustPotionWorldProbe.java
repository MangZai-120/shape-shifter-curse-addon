package net.jackcooper.shapeShifterCurseAddon.item;

import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.*;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.potion.*;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.ssca.neomagic.*;

/** 在真实 ServerWorld 中验证物品入口、原版药水碰撞、衰减及实体存盘恢复。 */
public final class MoonDustPotionWorldProbe {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void near(double actual, double expected, String why) { check(Math.abs(actual-expected)<1e-6,why+": "+actual); }
    private static ServerPlayerEntity player(TestContext t, Vec3d pos) {
        var p=t.createMockCreativeServerPlayerInWorld();
        p.changeGameMode(GameMode.SURVIVAL); p.setNoGravity(true);
        p.refreshPositionAndAngles(pos.x,pos.y,pos.z,0,0);
        NeoRuntime.refreshForm(p); reset(p,100); return p;
    }
    private static void reset(ServerPlayerEntity p,double value) {
        var data=NeoRuntime.data(p); data.burden=value; data.recovery=10000;
    }
    public static void start(TestContext t) {
        t.waitAndRun(70,()->{try {
            var world=t.getWorld();
            checks+=PotionItemMigrationChecks.verify();
            checks+=PotionTubeDoseWorldProbe.verify(t);
            checks+=PotionTubeStackWorldProbe.verify(t);
            checks+=PotionTubeInventoryWorldProbe.verify(t);
            checks+=PotionTubeBagWorldProbe.verify(t);
            var center=Vec3d.ofCenter(t.getAbsolutePos(new BlockPos(2,3,2)));
            var p=player(t,center); var near=player(t,center.add(2,0,0));
            var edge=player(t,center.add(4,0,0)); var outside=player(t,center.add(5,0,0));
            tubesAndBrewing(t,p);
            for(var item:List.of(MoonDustReliefItem.VIAL,MoonDustReliefItem.BOTTLE,MoonDustReliefItem.SPLASH)) {
                check(Registries.ITEM.getId(item).getNamespace().equals("ssc_addon"),"药水使用附属命名空间");
                check(world.getServer().getRecipeManager().get(Registries.ITEM.getId(item)).isEmpty(),"旧工作台药水配方已移除");
                check(item.getMaxCount()==(item==MoonDustReliefItem.VIAL?3:16),"试管默认三管，其余月尘药水维持十六个堆叠");
            }
            check(MoonDustReliefItem.VIAL.getMaxUseTime(new ItemStack(MoonDustReliefItem.VIAL))==16,"试管饮用十六tick");
            check(MoonDustReliefItem.BOTTLE.getMaxUseTime(new ItemStack(MoonDustReliefItem.BOTTLE))==40,"瓶装饮用四十tick");
            double safety=NeoRuntime.profile(p).safety(); int exp=NeoRuntime.data(p).spellbookMasteryExpTen;
            var vial=new ItemStack(MoonDustReliefItem.VIAL);
            p.setStackInHand(Hand.MAIN_HAND,vial);
            check(MoonDustReliefItem.VIAL.use(world,p,Hand.MAIN_HAND).getResult().isAccepted()&&p.isUsingItem(),"试管走真实饮用入口");
            p.stopUsingItem();
            var empty=MoonDustReliefItem.VIAL.finishUsing(vial,world,p);
            near(NeoRuntime.data(p).burden,80,"试管只降低二十崩解度");
            check(empty.isOf(PotionTestTubes.EMPTY)&&empty.getCount()==1&&vial.isEmpty(),"旧试管仅消耗一次并归还空试管");
            reset(p,100);
            var bottle=new ItemStack(MoonDustReliefItem.BOTTLE,2);
            p.getInventory().clear(); p.setStackInHand(Hand.OFF_HAND,bottle);
            check(MoonDustReliefItem.BOTTLE.use(world,p,Hand.OFF_HAND).getResult().isAccepted(),"副手也能饮用");
            p.stopUsingItem(); MoonDustReliefItem.BOTTLE.finishUsing(bottle,world,p);
            near(NeoRuntime.data(p).burden,40,"瓶装降低六十崩解度");
            check(bottle.getCount()==1&&p.getInventory().count(Items.GLASS_BOTTLE)==1,"堆叠药水归还空瓶且不吞其它剂量");
            reset(p,5); MoonDustReliefItem.VIAL.finishUsing(new ItemStack(MoonDustReliefItem.VIAL),world,p);
            near(NeoRuntime.data(p).burden,0,"恢复不降到零以下");
            p.changeGameMode(GameMode.CREATIVE); reset(p,100);
            var creative=new ItemStack(MoonDustReliefItem.BOTTLE);
            MoonDustReliefItem.BOTTLE.finishUsing(creative,world,p);
            check(creative.getCount()==1,"创造模式饮用不消耗");
            near(NeoRuntime.data(p).burden,40,"创造模式也正确恢复");
            p.changeGameMode(GameMode.SURVIVAL); reset(p,100);
            check(NeoRuntime.profile(p).safety()==safety&&NeoRuntime.data(p).spellbookMasteryExpTen==exp,"药水不增加阈值或经验");
            var support=new ItemStack(MoonDustReliefItem.SPLASH,2);p.setStackInHand(Hand.MAIN_HAND,support);
            var before=world.getEntitiesByClass(PotionEntity.class,p.getBoundingBox().expand(12),e->true).size();
            check(MoonDustReliefItem.SPLASH.use(world,p,Hand.MAIN_HAND).getResult().isAccepted(),"支援药水走真实投掷入口");
            var projectiles=world.getEntitiesByClass(PotionEntity.class,p.getBoundingBox().expand(12),e->true);
            check(projectiles.size()==before+1&&support.getCount()==1,"仅生成一枚原版弹体且扣除一瓶");
            check(p.getItemCooldownManager().isCoolingDown(MoonDustReliefItem.SPLASH),"投掷有十tick冷却");
            var thrown=projectiles.get(projectiles.size()-1);
            check(thrown.getStack().isOf(MoonDustReliefItem.SPLASH),"投掷同步保持支援药水外观");
            var saved=thrown.writeNbt(new NbtCompound());thrown.discard();
            var restored=new PotionEntity(EntityType.POTION,world);restored.readNbt(saved);restored.setPosition(center);
            check(restored.getStack().hasNbt()&&restored.getStack().getNbt().getDouble("MoonDustReliefAmount")==35,"实体存盘保留本次恢复剂量");
            var collision=PotionEntity.class.getDeclaredMethod("onCollision",HitResult.class);collision.setAccessible(true);
            collision.invoke(restored,new BlockHitResult(center,Direction.UP,BlockPos.ofFloored(center),false));
            near(NeoRuntime.data(p).burden,65,"原版碰撞中心恢复三十五");
            near(NeoRuntime.data(near).burden,82.5,"两格距离按线性衰减恢复十七点五");
            near(NeoRuntime.data(edge).burden,100,"四格边界不恢复");
            near(NeoRuntime.data(outside).burden,100,"范围外不恢复");
            check(restored.isRemoved(),"碰撞后药水销毁，不重复恢复");
            reset(p,100);p.changeGameMode(GameMode.CREATIVE);
            var free=new ItemStack(MoonDustReliefItem.SPLASH);p.setStackInHand(Hand.MAIN_HAND,free);
            MoonDustReliefItem.SPLASH.use(world,p,Hand.MAIN_HAND);
            check(free.getCount()==1,"创造模式投掷不消耗");
            MoonDustReliefItem.RELIEF.applyInstantEffect(null,null,p,0,1);
            near(NeoRuntime.data(p).burden,65,"瞬时效果本身也只恢复一次");
            MoonDustReliefItem.RELIEF.applyInstantEffect(null,null,p,0,Double.NaN);
            near(NeoRuntime.data(p).burden,65,"非法衰减参数不污染玩家数据");
            Files.writeString(Path.of("moon-dust-world-result.json"),NeoState.JSON.toJson(Map.of("checks",checks,"real_server_world",true,"saved_potion_collision",true)));
            System.out.println("MOON_DUST_POTION_WORLD_PASS checks="+checks); t.complete();
        }catch(Throwable e){e.printStackTrace();t.throwGameTestException(e.toString());}});
    }

    private static void tubesAndBrewing(TestContext t,ServerPlayerEntity p) {
        var world=t.getWorld();
        var recipe=(net.jackcooper.shapeShifterCurseAddon.recipe.TestTubeFillingRecipe)world.getRecipeManager().get(new net.minecraft.util.Identifier("ssc_addon","test_tube_filling")).orElseThrow();
        var grid=new CraftingInventory(p.playerScreenHandler,3,3);
        var bottle=PotionUtil.setPotion(new ItemStack(Items.POTION),Potions.LONG_SWIFTNESS);
        bottle.setCustomName(net.minecraft.text.Text.literal("NBT dose probe"));
        bottle.getOrCreateNbt().putInt("CustomPotionColor",0xC040FF);
        PotionUtil.setCustomPotionEffects(bottle,List.of(new StatusEffectInstance(StatusEffects.REGENERATION,903,1,true,false,true)));
        grid.setStack(5,bottle);
        for(int i:new int[]{0,4,8})grid.setStack(i,new ItemStack(PotionTestTubes.EMPTY,2));
        check(recipe.matches(grid,world),"分装配方从数据包加载且接受任意四格");
        var tubes=recipe.craft(grid,world.getRegistryManager());
        check(tubes.isOf(PotionTestTubes.FILLED)&&tubes.getCount()==3,"每瓶只分装三管");
        check(PotionUtil.getPotion(tubes)==Potions.LONG_SWIFTNESS&&PotionUtil.getCustomPotionEffects(tubes).get(0).getDuration()==903,"原瓶药水与自定义效果NBT完整保留");
        check(tubes.getName().getString().equals("NBT dose probe")&&PotionUtil.getColor(tubes)==0xC040FF,"自定义名称和液体颜色保留");
        check(PotionTestTubes.FILLED.getMaxUseTime(tubes)==13,"原版32tick瓶的40%饮用时间为13tick");
        var effects=PotionTestTubes.doseEffects(tubes);
        var regen=effects.stream().filter(e->e.getEffectType()==StatusEffects.REGENERATION).findFirst().orElseThrow();
        check(regen.getDuration()==301&&regen.getAmplifier()==1&&regen.isAmbient()&&!regen.shouldShowParticles()&&regen.shouldShowIcon(),"持续时间三分之一，等级与显示标记保留");
        var restored=ItemStack.fromNbt(tubes.writeNbt(new NbtCompound()));
        check(ItemStack.areEqual(restored,tubes),"试管全部药水信息存盘恢复一致");
        var outputInventory=new SimpleInventory(1);outputInventory.setStack(0,tubes.copy());
        new CraftingResultSlot(p,grid,outputInventory,0,0,0).onTakeItem(p,tubes.copy());
        check(grid.getStack(5).isOf(Items.GLASS_BOTTLE)&&grid.getStack(5).getCount()==1,"真实原版合成取出流程将空瓶返还原格");
        check(grid.getStack(0).getCount()==1&&grid.getStack(4).getCount()==1&&grid.getStack(8).getCount()==1,"真实合成每格仅消耗一个空管");
        grid.setStack(5,new ItemStack(MoonDustReliefItem.BOTTLE,2));
        check(!recipe.matches(grid,world),"拒绝堆叠药水瓶，保证原格返还而不挤入背包");
        grid.setStack(5,tubes.copy());check(!recipe.matches(grid,world),"已装药试管不能再次分装复制药效");
        grid.setStack(5,PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION),Potions.HEALING));
        check(!recipe.matches(grid,world),"喷溅与滞留药水不能冒充饮用药水分装");
        p.clearStatusEffects();p.setHealth(10);
        var healing=PotionTestTubes.divide(PotionUtil.setPotion(new ItemStack(Items.POTION),Potions.HEALING));healing.setCount(1);
        var returned=PotionTestTubes.FILLED.finishUsing(healing,world,p);
        near(p.getHealth(),11,"真实饮用治疗试管只按原版四点治疗的三分之一剂量取整");
        check(returned.isOf(PotionTestTubes.EMPTY),"最后一管药水归还可复用空管");
        PotionTestTubes.FILLED.finishUsing(tubes.copy(),world,p);
        check(p.getStatusEffect(StatusEffects.SPEED).getDuration()==3200&&p.getStatusEffect(StatusEffects.REGENERATION).getDuration()==301,"真实饮用持续效果按三分之一生效");
        p.clearStatusEffects();
        var moon=PotionTestTubes.divide(new ItemStack(MoonDustReliefItem.BOTTLE));
        check(moon.getMaxUseTime()==16,"月尘试管按40tick瓶装的40%饮用");
        reset(p,100);MoonDustReliefItem.VIAL.finishUsing(moon,world,p);
        near(NeoRuntime.data(p).burden,80,"专属月尘试管降低20点且不会再次除三");
        check(moon.getCount()==2&&p.getInventory().count(PotionTestTubes.EMPTY)>=1,"堆叠试管消耗一管并归还空管");
        check(!BrewingRecipeRegistry.hasRecipe(moon,new ItemStack(Items.GUNPOWDER)),"试管不能进行喷溅酿造");
        var brewPos=t.getAbsolutePos(new BlockPos(8,2,8));world.setBlockState(brewPos,Blocks.BREWING_STAND.getDefaultState());
        var stand=(BrewingStandBlockEntity)world.getBlockEntity(brewPos);
        var screen=new BrewingStandScreenHandler(1,p.getInventory());
        for(int i=0;i<3;i++){
            check(stand.isValid(i,new ItemStack(MoonDustReliefItem.BOTTLE))&&screen.getSlot(i).canInsert(new ItemStack(MoonDustReliefItem.BOTTLE)),"瓶装药水同时接受自动化和GUI槽位"+i);
            check(!stand.isValid(i,moon)&&!stand.isValid(i,new ItemStack(PotionTestTubes.EMPTY))&&!screen.getSlot(i).canInsert(moon),"空管和药水管均不能进酿造槽"+i);
            stand.setStack(i,PotionUtil.setPotion(new ItemStack(Items.POTION),Potions.AWKWARD));
        }
        var dust=new ItemStack(net.onixary.shapeShifterCurseFabric.items.RegCustomItem.UNTREATED_MOONDUST);
        check(stand.isValid(3,dust)&&screen.getSlot(3).canInsert(dust),"月尘可通过GUI及自动化放入材料槽");
        check(!BrewingRecipeRegistry.hasRecipe(PotionUtil.setPotion(new ItemStack(Items.POTION),Potions.WATER),dust),"水瓶不能跳过粗制药水步骤");
        stand.setStack(3,dust);stand.setStack(4,new ItemStack(Items.BLAZE_POWDER));
        for(int i=0;i<401;i++)BrewingStandBlockEntity.tick(world,brewPos,world.getBlockState(brewPos),stand);
        for(int i=0;i<3;i++)check(stand.getStack(i).isOf(MoonDustReliefItem.BOTTLE),"原版酿造台完整400tick流程产出瓶装月尘药水"+i);
        check(stand.getStack(3).isEmpty()&&stand.getStack(4).isEmpty(),"酿造实际消耗月尘与燃料");
        stand.setStack(3,new ItemStack(Items.GUNPOWDER));
        for(int i=0;i<401;i++)BrewingStandBlockEntity.tick(world,brewPos,world.getBlockState(brewPos),stand);
        for(int i=0;i<3;i++)check(stand.getStack(i).isOf(MoonDustReliefItem.SPLASH),"原版酿造台火药转支援药水"+i);
        var customPotion=PotionUtil.setPotion(new ItemStack(Items.POTION),net.onixary.shapeShifterCurseFabric.items.RegCustomPotions.CUSTOM_STATUE_FORM_POTION);
        var targetForm=new net.minecraft.util.Identifier("shape-shifter-curse","familiar_fox_1");
        net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils.setCTPFormIDToNBT(customPotion.getOrCreateNbt(),targetForm);
        check(!PotionTestTubes.isBottle(customPotion)&&PotionTestTubes.divide(customPotion).isEmpty(),"一次性特殊变形药水禁止分装，不能触发多次完整变形");
        p.clearStatusEffects();net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils.resetTransformativePotionForm(p);
        var examples=net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.examples(recipe);
        check(PotionTestTubes.moonDustStack().isOf(MoonDustReliefItem.VIAL),"月尘试管的展示与实际配方输出使用唯一专属物品");
        check(!PotionTestTubes.isBottle(PotionUtil.setPotion(new ItemStack(Items.POTION),net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects.UNIVERSAL_ENERGY_POTION)),"内部通用能量AOE载体不能伪装成可分装的普通瓶");
        check(PotionTestTubes.viewerStacks().stream().noneMatch(s->PotionUtil.getPotion(s)==net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects.UNIVERSAL_ENERGY_POTION),"内部通用能量载体不得出现在试管侧栏");
        check(examples.size()==PotionTestTubes.fillingBottles().size()&&examples.stream().allMatch(e->e.width()==2&&e.height()==2&&e.output().getCount()==3),"配方查看器展示所有药水的四格分装和三管产出");
        var water=PotionTestTubes.divide(PotionUtil.setPotion(new ItemStack(Items.POTION),Potions.WATER));
        var moonKey=PotionTestTubes.subtypeData(PotionTestTubes.moonDustStack());
        check(!moonKey.equals(PotionTestTubes.subtypeData(water)),"月尘与装水试管的子类型不同");
        var named=PotionTestTubes.moonDustStack();named.setCustomName(net.minecraft.text.Text.literal("custom"));named.getOrCreateNbt().putInt("CustomPotionColor",0xFF00AA);
        check(moonKey.equals(PotionTestTubes.subtypeData(named)),"改名和药液染色不改变配方所属药水");
        check(PotionUtil.getPotion(PotionTestTubes.FILLED.getDefaultStack())==Potions.HEALING,"通用试管默认展示有效治疗药水");
        check(PotionTestTubes.viewerStacks().stream().noneMatch(s->PotionUtil.getPotion(s)==Potions.WATER),"无药效水管不占用查看器默认侧栏");
        check(net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.brewing().stream().anyMatch(e->e.output().isOf(MoonDustReliefItem.BOTTLE)),"配方查看器包含瓶装月尘酿造路线");
        reset(p,100);p.getInventory().clear();
    }
}
