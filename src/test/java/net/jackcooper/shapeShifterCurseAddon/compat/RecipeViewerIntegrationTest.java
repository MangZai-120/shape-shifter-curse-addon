package net.jackcooper.shapeShifterCurseAddon.compat;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.*;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan.Outcome;
import net.jackcooper.shapeShifterCurseAddon.item.*;
import net.jackcooper.shapeShifterCurseAddon.recipe.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.*;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;
import java.nio.file.*;
import java.util.*;

/** Real Minecraft matching/crafting and NBT; no game window, player connection or viewer UI is created. */
public final class RecipeViewerIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", Files.readString(Path.of(System.getProperty("viewer.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "viewer-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(RecipeViewerIntegrationTest.class.getName() + "$Probe", true, loader).getMethod("run").invoke(null);
    }

    public static final class Probe {
        private static int checks;
        private static final DynamicRegistryManager REGISTRY = DynamicRegistryManager.EMPTY;
        public static void run() throws Exception {
            Item[] items = {SscAddon.VENOM_GLAND, SscAddon.INFINITE_ENERGY_POTION, SscAddon.SP_UPGRADE_THING,
                SscAddon.PORTABLE_MOISTURIZER, SscAddon.SNOWBALL_LAUNCHER, SscAddon.PORTABLE_FRIDGE,
                RegCustomItem.MORPHSCALE_CORE, RegCustomItem.MOONDUST_CRYSTAL_SHARD};
            for (int i = 0; i < items.length; i++) if (Registries.ITEM.getRawId(items[i]) < 0)
                Registry.register(Registries.ITEM, new Identifier("viewer_test", "item_" + i), items[i]);
            if (Registries.POTION.getRawId(RegCustomPotions.FEED_POTION) < 0)
                Registry.register(Registries.POTION, new Identifier("viewer_test", "feed"), RegCustomPotions.FEED_POTION);
            var recipes = List.of(
                new VenomGlandRecipe(id("venom"), CraftingRecipeCategory.MISC),
                new InfiniteEnergyPotionRecipe(id("infinite"), CraftingRecipeCategory.MISC),
                new SpUpgradeRecipe(id("ring"), CraftingRecipeCategory.MISC),
                new RefillMoisturizerRecipe(id("refill"), CraftingRecipeCategory.MISC),
                new UpgradeMoisturizerRecipe(id("upgrade"), CraftingRecipeCategory.MISC),
                new ReloadSnowballLauncherRecipe(id("reload"), CraftingRecipeCategory.MISC),
                new BlizzardTankRechargeRecipe(id("recharge"), CraftingRecipeCategory.MISC));
            var manager = new RecipeManager(); manager.setRecipes(new ArrayList<Recipe<?>>(recipes));
            check(SscViewerRecipes.crafting(manager).size() == 12, "seven actual recipe types produce twelve valid examples");
            Set<Identifier> ids = new HashSet<>();
            for (var recipe : recipes) for (var display : SscViewerRecipes.examples(recipe)) {
                check(ids.add(display.id()), "unique display location");
                Input inventory = input(display.grid(), 3);
                check(recipe.matches(inventory, null), "display is craftable: " + display.id());
                check(ItemStack.areEqual(recipe.craft(inventory, REGISTRY), display.output()), "display output equals actual crafting: " + display.id());
                checkPlan(recipe, display, 3);
                if (display.shapeless()) checkPlan(recipe, display, 2);
            }
            manager.setRecipes(List.of(recipes.get(0)));
            check(SscViewerRecipes.crafting(manager).size() == 1, "removed recipes disappear after reload");
            manager.setRecipes(List.of());
            check(SscViewerRecipes.crafting(manager).isEmpty(), "disabled recipes are never advertised");
            poison((VenomGlandRecipe) recipes.get(0));
            stateful((UpgradeMoisturizerRecipe) recipes.get(4), (RefillMoisturizerRecipe) recipes.get(3),
                (ReloadSnowballLauncherRecipe) recipes.get(5), (BlizzardTankRechargeRecipe) recipes.get(6));
            transferEdges((VenomGlandRecipe) recipes.get(0));
            System.out.println("Recipe viewer integration passed: " + checks + " checks; actual recipe outputs, nine poison variants, NBT, 2x2/3x3 planning, shortages, full inventory and reload.");
        }
        private static void checkPlan(SpecialCraftingRecipe recipe, SscViewerRecipes.Crafting display, int width) {
            var required = display.gridFor(width);
            var inventory = empty(36);
            int index = 0;
            for (var alternatives : required) if (!alternatives.isEmpty()) inventory.set(index++, alternatives.get(0).copy());
            var before = nbt(inventory);
            var plan = SscTransferPlan.create(required, empty(width * width), inventory, ItemStack.EMPTY);
            check(plan.outcome() == Outcome.SUCCESS, "valid transfer " + display.id());
            check(before.equals(nbt(inventory)), "planning cannot mutate player stacks");
            var result = new Input(width); for (int i = 0; i < result.size(); i++) result.setStack(i, plan.result().get(i).copy());
            check(recipe.matches(result, null), "planned grid matches authoritative recipe");
            check(multiset(inventory).equals(multiset(plan.result())), "transfer preserves every item and its NBT");
            var executed = clickOracle(empty(width * width), inventory, plan.clicks());
            check(nbt(executed).equals(nbt(plan.result())), "ordinary pickup clicks reproduce the planned inventory: " + display.id());
        }
        private static void poison(VenomGlandRecipe recipe) {
            var display = SscViewerRecipes.examples(recipe).get(0);
            check(display.grid().get(4).size() == 9, "all poison strengths and bottle types are searchable");
            for (var potion : display.grid().get(4)) {
                var bag = empty(36); bag.set(0, new ItemStack(Items.SPIDER_EYE, 8));
                var renamed = potion.copy(); renamed.setCustomName(Text.literal("Keep my name")); bag.set(1, renamed);
                var plan = SscTransferPlan.create(display.grid(), empty(9), bag, ItemStack.EMPTY);
                check(plan.outcome() == Outcome.SUCCESS, "poison variant with custom NBT accepted");
                var inv = new Input(3); for (int i = 0; i < 9; i++) inv.setStack(i, plan.result().get(i));
                check(recipe.matches(inv, null), "actual recipe accepts poison variant");
                check(ItemStack.areEqual(plan.result().get(4), renamed), "potion NBT retained");
            }
            var wrong = PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
            check(!SscViewerRecipes.matches(wrong, display.grid().get(4).get(0)), "unrelated potion rejected");
        }
        private static void stateful(UpgradeMoisturizerRecipe upgrade, RefillMoisturizerRecipe refill,
                                     ReloadSnowballLauncherRecipe reload, BlizzardTankRechargeRecipe recharge) {
            for (var example : SscViewerRecipes.examples(upgrade)) {
                var inv = input(example.grid(), 3);
                ItemStack center = inv.getStack(4); center.setCustomName(Text.literal("Named device"));
                center.getOrCreateNbt().putString("CustomData", "keep"); PortableMoisturizerItem.setCharge(center, 1234);
                var upgraded = upgrade.craft(inv, REGISTRY);
                check(PortableMoisturizerItem.getCharge(upgraded) == 1234, "upgrade retains existing charge");
                check(upgraded.getNbt().getString("CustomData").equals("keep"), "upgrade retains arbitrary NBT");
                check(SscViewerRecipes.matches(center, example.grid().get(4).get(0)), "transfer ignores irrelevant NBT and charge");
                PortableMoisturizerItem.setLevel(center, 3);
                check(!SscViewerRecipes.matches(center, example.grid().get(4).get(0)), "transfer rejects wrong device level");
            }
            for (var example : SscViewerRecipes.examples(refill)) {
                var inv = input(example.grid(), 3); var device = inv.getStack(0);
                device.getOrCreateNbt().putBoolean("Active", true);
                var result = refill.craft(inv, REGISTRY);
                check(PortableMoisturizerItem.getCharge(result) == PortableMoisturizerItem.getMaxCharge(PortableMoisturizerItem.getLevel(device)), "refill uses current level capacity");
                check(result.getNbt().getBoolean("Active"), "refill retains activation state");
            }
            var inv = input(SscViewerRecipes.examples(reload).get(2).grid(), 3);
            SnowballLauncherItem.setAmmo(inv.getStack(0), 17);
            check(SnowballLauncherItem.getAmmo(reload.craft(inv, REGISTRY)) == 20, "reloading clamps at capacity");
            inv = input(SscViewerRecipes.examples(recharge).get(0).grid(), 3);
            PortableFridgeItem.setCharge(inv.getStack(0), 60);
            check(PortableFridgeItem.getCharge(recharge.craft(inv, REGISTRY)) == 64, "recharge clamps at capacity");
            PortableFridgeItem.setCharge(inv.getStack(0), 64);
            check(!recharge.matches(inv, null) && !SscViewerRecipes.matches(inv.getStack(0), new ItemStack(SscAddon.PORTABLE_FRIDGE)), "full tank cannot be offered for recharge");
        }
        private static void transferEdges(VenomGlandRecipe recipe) {
            var display = SscViewerRecipes.examples(recipe).get(0);
            var bag = empty(36); bag.set(0, new ItemStack(Items.SPIDER_EYE, 7)); bag.set(1, display.grid().get(4).get(0).copy());
            var plan = SscTransferPlan.create(display.grid(), empty(9), bag, ItemStack.EMPTY);
            check(plan.outcome() == Outcome.NOT_ENOUGH && plan.clicks().isEmpty(), "seven eyes never count as eight or return success");
            var grid = empty(9); grid.set(0, new ItemStack(Items.SPIDER_EYE));
            plan = SscTransferPlan.create(display.grid(), grid, bag, ItemStack.EMPTY);
            check(plan.outcome() == Outcome.SUCCESS, "existing grid material is included in preflight");
            check(multiset(join(grid, bag)).equals(multiset(plan.result())), "existing grid materials are not duplicated");
            var full = empty(36); for (int i = 0; i < 36; i++) full.set(i, new ItemStack(Items.STONE, 64));
            var complete = input(display.grid(), 3).getInputStacks();
            plan = SscTransferPlan.create(display.grid(), complete, full, ItemStack.EMPTY);
            check(plan.outcome() == Outcome.SUCCESS && plan.clicks().isEmpty(), "already correct grid needs no inventory room");
            full.set(0, new ItemStack(Items.SPIDER_EYE, 64)); full.set(1, display.grid().get(4).get(0).copy());
            grid = empty(9); grid.set(0, new ItemStack(Items.DIRT));
            var before = nbt(join(grid, full));
            plan = SscTransferPlan.create(display.grid(), grid, full, ItemStack.EMPTY);
            check(plan.outcome() == Outcome.NO_ROOM && plan.clicks().isEmpty(), "full inventory fails before moving anything");
            check(before.equals(nbt(join(grid, full))), "no room preserves every slot");
            plan = SscTransferPlan.create(display.grid(), empty(9), full, new ItemStack(Items.DIRT));
            check(plan.outcome() == Outcome.CURSOR_NOT_EMPTY && plan.clicks().isEmpty(), "occupied cursor fails during preflight");
            var alternatives = List.of(List.of(new ItemStack(Items.SPIDER_EYE), new ItemStack(Items.DIRT)), List.of(new ItemStack(Items.SPIDER_EYE)));
            bag = empty(36); bag.set(0, new ItemStack(Items.SPIDER_EYE)); bag.set(1, new ItemStack(Items.DIRT));
            plan = SscTransferPlan.create(alternatives, empty(2), bag, ItemStack.EMPTY);
            check(plan.outcome() == Outcome.SUCCESS && plan.result().get(0).isOf(Items.DIRT) && plan.result().get(1).isOf(Items.SPIDER_EYE), "overlapping choices reserve materials without starvation");
        }
        // Independent vanilla pickup semantics: left-click picks/merges; right-click deposits exactly one.
        private static List<ItemStack> clickOracle(List<ItemStack> grid, List<ItemStack> bag, List<SscTransferPlan.Click> clicks) {
            var slots = join(grid, bag).stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            ItemStack cursor = ItemStack.EMPTY;
            for (var click : clicks) {
                ItemStack target = slots.get(click.slot());
                if (cursor.isEmpty()) { cursor = target; slots.set(click.slot(), ItemStack.EMPTY); }
                else if (target.isEmpty() || ItemStack.canCombine(cursor, target)) {
                    int count = Math.min(click.button() == 1 ? 1 : cursor.getCount(), cursor.getMaxCount() - target.getCount());
                    if (target.isEmpty()) { target = cursor.copy(); target.setCount(count); slots.set(click.slot(), target); }
                    else target.increment(count);
                    cursor.decrement(count);
                } else { slots.set(click.slot(), cursor); cursor = target; }
            }
            check(cursor.isEmpty(), "planned clicks leave cursor empty");
            return slots;
        }
        private static Input input(List<List<ItemStack>> required, int width) {
            var input = new Input(width);
            for (int i = 0; i < required.size(); i++) if (!required.get(i).isEmpty()) input.setStack(i, required.get(i).get(0).copy());
            return input;
        }
        private static List<ItemStack> empty(int size) { return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY)); }
        private static List<ItemStack> join(List<ItemStack> first, List<ItemStack> second) { var all = new ArrayList<>(first); all.addAll(second); return all; }
        private static List<NbtCompound> nbt(List<ItemStack> stacks) { return stacks.stream().map(s -> (s.isEmpty() ? ItemStack.EMPTY : s).writeNbt(new NbtCompound())).toList(); }
        private static Map<String, Integer> multiset(List<ItemStack> stacks) {
            var result = new TreeMap<String, Integer>();
            for (var stack : stacks) if (!stack.isEmpty()) { var one = stack.copy(); one.setCount(1); result.merge(one.writeNbt(new NbtCompound()).toString(), stack.getCount(), Integer::sum); }
            return result;
        }
        private static Identifier id(String path) { return new Identifier("viewer_test", path); }
        private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    }
    public static final class Input extends SimpleInventory implements RecipeInputInventory {
        private final int width;
        public Input(int width) { super(width * width); this.width = width; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return width; }
        @Override public List<ItemStack> getInputStacks() { var list = new ArrayList<ItemStack>(); for (int i = 0; i < size(); i++) list.add(getStack(i)); return list; }
        @Override public void provideRecipeInputs(RecipeMatcher finder) { for (var stack : getInputStacks()) finder.addInput(stack); }
    }
}
