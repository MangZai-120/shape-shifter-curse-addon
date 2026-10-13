package net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyBottlerBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.jackcooper.shapeShifterCurseAddon.item.PortableFridgeItem;
import net.jackcooper.shapeShifterCurseAddon.item.PortableMoisturizerItem;
import net.jackcooper.shapeShifterCurseAddon.item.SnowballLauncherItem;
import net.jackcooper.shapeShifterCurseAddon.recipe.*;
import net.minecraft.item.*;
import net.minecraft.potion.*;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;

import java.util.*;

/** Viewer-neutral examples. Only recipes present in the current recipe manager are advertised. */
public final class SscViewerRecipes {
    private SscViewerRecipes() {}

    public record Crafting(Identifier id, List<List<ItemStack>> grid, ItemStack output,
                           boolean shapeless, boolean dynamic, String note) {
        public int width() { return shapeless ? 2 : 3; }
        public int height() { return shapeless ? 1 : 3; }
        public List<List<ItemStack>> gridFor(int width) {
            if (!shapeless) return grid;
            var result = emptyGrid(width * width);
            int index = 0;
            for (var alternatives : grid) if (!alternatives.isEmpty()) result.set(index++, alternatives);
            return result;
        }
        public Text description() { return Text.translatable("recipe.ssc_addon." + note); }
    }

    public record Brewing(Identifier id, ItemStack input, ItemStack ingredient, ItemStack output) {}
    public record Process(Identifier id, ItemStack workstation, List<ItemStack> inputs,
                          ItemStack output, Text description) {}
    public record Info(List<ItemStack> stacks, String key) {
        public Text description() { return Text.translatable("recipe.ssc_addon." + key); }
    }

    public static List<Crafting> crafting(RecipeManager manager) {
        return manager.values().stream().flatMap(recipe -> examples(recipe).stream()).toList();
    }

    public static List<Crafting> examples(Recipe<?> recipe) {
        List<Crafting> result = new ArrayList<>();
        if (recipe instanceof VenomGlandRecipe) {
            var grid = emptyGrid(9);
            for (int i = 0; i < 9; i++) grid.set(i, List.of(new ItemStack(Items.SPIDER_EYE)));
            grid.set(4, potions(Potions.POISON, Potions.LONG_POISON, Potions.STRONG_POISON));
            result.add(example(recipe, "", grid, new ItemStack(SscAddon.VENOM_GLAND), false, false, "venom"));
        } else if (recipe instanceof InfiniteEnergyPotionRecipe) {
            var grid = emptyGrid(9);
            grid.set(1, List.of(new ItemStack(SscAddon.SP_UPGRADE_THING)));
            grid.set(3, List.of(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
            grid.set(4, potions(RegCustomPotions.FEED_POTION));
            grid.set(5, List.of(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
            result.add(example(recipe, "", grid, new ItemStack(SscAddon.INFINITE_ENERGY_POTION), false, false, "infinite"));
        } else if (recipe instanceof SpUpgradeRecipe) {
            var grid = emptyGrid(9);
            for (int i : new int[]{0, 2, 6, 8}) grid.set(i, List.of(new ItemStack(Items.GOLD_INGOT)));
            grid.set(4, List.of(new ItemStack(RegCustomItem.MORPHSCALE_CORE)));
            int[] sides = {1, 3, 5, 7};
            Item[] materials = {Items.EMERALD, Items.REDSTONE, RegCustomItem.MOONDUST_CRYSTAL_SHARD, Items.NETHERITE_INGOT};
            // A complete valid arrangement; independent cycling on four slots could show duplicates.
            for (int i = 0; i < 4; i++) grid.set(sides[i], List.of(new ItemStack(materials[i])));
            result.add(example(recipe, "", grid, new ItemStack(SscAddon.SP_UPGRADE_THING), false, false, "moon_ring"));
        } else if (recipe instanceof RefillMoisturizerRecipe) {
            for (int level = 1; level <= 3; level++) {
                ItemStack input = moisturizer(level);
                ItemStack output = input.copy();
                PortableMoisturizerItem.setFullCharge(output);
                result.add(example(recipe, "level_" + level, pair(input, new ItemStack(Items.AXOLOTL_BUCKET)), output, true, true, "refill"));
            }
        } else if (recipe instanceof UpgradeMoisturizerRecipe) {
            for (int level = 1; level < 3; level++) {
                var grid = emptyGrid(9);
                for (int i : new int[]{0, 2, 6, 8}) grid.set(i, List.of(new ItemStack(level == 1 ? Items.WET_SPONGE : Items.HEART_OF_THE_SEA)));
                for (int i : new int[]{1, 3, 5, 7}) grid.set(i, List.of(new ItemStack(level == 1 ? Items.GOLD_INGOT : Items.DIAMOND)));
                grid.set(4, List.of(moisturizer(level)));
                result.add(example(recipe, "level_" + level, grid, moisturizer(level + 1), false, true, "upgrade_moisturizer"));
            }
        } else if (recipe instanceof ReloadSnowballLauncherRecipe) {
            for (Item ammo : List.of(Items.SNOWBALL, Items.SNOW, Items.SNOW_BLOCK)) {
                ItemStack input = new ItemStack(SscAddon.SNOWBALL_LAUNCHER);
                SnowballLauncherItem.setAmmo(input, 0);
                ItemStack output = input.copy();
                SnowballLauncherItem.setAmmo(output, ammo == Items.SNOW_BLOCK ? 8 : 1);
                result.add(example(recipe, net.minecraft.registry.Registries.ITEM.getId(ammo).getPath(), pair(input, new ItemStack(ammo)), output, true, true, "reload"));
            }
        } else if (recipe instanceof BlizzardTankRechargeRecipe) {
            ItemStack input = new ItemStack(SscAddon.PORTABLE_FRIDGE);
            PortableFridgeItem.setCharge(input, 0);
            ItemStack output = input.copy();
            PortableFridgeItem.setCharge(output, 8);
            result.add(example(recipe, "", pair(input, new ItemStack(Items.SNOW_BLOCK)), output, true, true, "recharge"));
        }
        return result;
    }

    private static Crafting example(Recipe<?> recipe, String variant, List<List<ItemStack>> grid,
                                    ItemStack output, boolean shapeless, boolean dynamic, String note) {
        Identifier source = recipe.getId();
        Identifier id = variant.isEmpty() ? source : new Identifier(source.getNamespace(), source.getPath() + "/" + variant);
        return new Crafting(id, grid, output, shapeless, dynamic, note);
    }

    public static ItemStack moisturizer(int level) {
        ItemStack stack = new ItemStack(SscAddon.PORTABLE_MOISTURIZER);
        PortableMoisturizerItem.setLevel(stack, level);
        PortableMoisturizerItem.setCharge(stack, 0);
        return stack;
    }

    public static List<ItemStack> potions(Potion... potions) {
        List<ItemStack> result = new ArrayList<>();
        for (Potion potion : potions) for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION))
            result.add(PotionUtil.setPotion(new ItemStack(bottle), potion));
        return result;
    }

    private static List<List<ItemStack>> pair(ItemStack first, ItemStack second) {
        var grid = emptyGrid(9);
        grid.set(0, List.of(first)); grid.set(1, List.of(second));
        return grid;
    }

    public static List<List<ItemStack>> emptyGrid(int size) {
        var grid = new ArrayList<List<ItemStack>>(size);
        for (int i = 0; i < size; i++) grid.add(List.of());
        return grid;
    }

    /** Same semantic requirements as matches(): names, charges and unrelated custom NBT are retained. */
    public static boolean matches(ItemStack have, ItemStack want) {
        if (have.isEmpty() || want.isEmpty() || !have.isOf(want.getItem())) return false;
        if (have.isOf(Items.POTION) || have.isOf(Items.SPLASH_POTION) || have.isOf(Items.LINGERING_POTION))
            return PotionUtil.getPotion(have) == PotionUtil.getPotion(want);
        if (have.isOf(SscAddon.PORTABLE_MOISTURIZER))
            return PortableMoisturizerItem.getLevel(have) == PortableMoisturizerItem.getLevel(want);
        if (have.isOf(SscAddon.PORTABLE_FRIDGE)) return PortableFridgeItem.getCharge(have) < PortableFridgeItem.MAX_CHARGE;
        return true;
    }

    public static List<Brewing> brewing() {
        return List.of(
            brew("infinite_splash", SscAddon.INFINITE_ENERGY_POTION, Items.GUNPOWDER, SscAddon.INFINITE_ENERGY_POTION_SPLASH),
            brew("infinite_lingering", SscAddon.INFINITE_ENERGY_POTION_SPLASH, Items.DRAGON_BREATH, SscAddon.INFINITE_ENERGY_POTION_LINGERING),
            brew("universal_splash", SscAddon.UNIVERSAL_ENERGY_POTION, Items.GUNPOWDER, SscAddon.UNIVERSAL_ENERGY_POTION_SPLASH),
            brew("universal_lingering", SscAddon.UNIVERSAL_ENERGY_POTION_SPLASH, Items.DRAGON_BREATH, SscAddon.UNIVERSAL_ENERGY_POTION_LINGERING));
    }

    private static Brewing brew(String path, Item input, Item ingredient, Item output) {
        return new Brewing(new Identifier("ssc_addon", path), new ItemStack(input), new ItemStack(ingredient), new ItemStack(output));
    }

    public static List<Process> processes() {
        var result = new ArrayList<Process>();
        result.add(new Process(new Identifier("ssc_addon", "energy_bottling"), new ItemStack(RegAddonBlocks.ENERGY_BOTTLER),
            List.of(new ItemStack(Items.GLASS_BOTTLE)), EnergyBottlerBlockEntity.makeEnergyBottle(),
            Text.translatable("recipe.ssc_addon.bottling", EnergyBottlerBlockEntity.ENERGY_PER_BOTTLE, EnergyBottlerBlockEntity.CRAFT_TIME / 20)));
        return result;
    }

    /** Research descriptions intentionally contain no solved spell-to-rune layouts or world language. */
    public static List<Info> information() {
        return List.of(
            new Info(List.of(new ItemStack(SscAddon.FROST_SPINE_COLLAR)), "loot_frost_spine_collar"),
            new Info(List.of(new ItemStack(SscAddon.ANKH_STONE)), "loot_ankh_stone"),
            new Info(List.of(new ItemStack(SscAddon.MOON_DUST_SPELLBOOK), new ItemStack(RegAddonBlocks.INFUSION_ALTAR)), "infusion"),
            new Info(List.of(new ItemStack(SscAddon.FORMATION), new ItemStack(SscAddon.BLANK_FORMATION_PAPER), new ItemStack(RegAddonBlocks.SPELL_RESEARCH_TABLE)), "formation"),
            new Info(List.of(new ItemStack(SscAddon.MAGIC_SCROLL), new ItemStack(RegAddonBlocks.SPELL_RESEARCH_TABLE)), "scroll_workshop"),
            new Info(List.of(new ItemStack(SscAddon.SPELL_FORMATION)), "rune_research"),
            new Info(List.of(new ItemStack(SscAddon.ANALYZED_SPELL_DIAGRAM)), "analysis"));
    }
}
