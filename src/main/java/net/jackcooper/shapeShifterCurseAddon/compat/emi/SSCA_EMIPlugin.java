package net.jackcooper.shapeShifterCurseAddon.compat.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.recipe.handler.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.widget.*;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.recipe.EmiBrewingRecipe;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.jackcooper.shapeShifterCurseAddon.client.screen.SlottedFormationScreen;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscGridClickTransfer;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.Crafting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.*;
import net.minecraft.util.Identifier;
import java.util.List;

public final class SSCA_EMIPlugin implements EmiPlugin {
    public static final EmiRecipeCategory SPECIAL_CRAFTING = new EmiRecipeCategory(new Identifier("ssc_addon", "special_crafting"), EmiStack.of(net.minecraft.item.Items.CRAFTING_TABLE));
    public static final EmiRecipeCategory PROCESSING = new EmiRecipeCategory(new Identifier("ssc_addon", "processing"), EmiStack.of(RegAddonBlocks.ENERGY_BOTTLER));
    @Override public void register(EmiRegistry registry) {
        registry.setDefaultComparison(net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes.FILLED,
            Comparison.compareData(s -> net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes.subtypeData(s.getItemStack())));
        for (var stack : net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes.viewerStacks()) registry.addEmiStack(EmiStack.of(stack));
        registry.addCategory(SPECIAL_CRAFTING);
        registry.addWorkstation(SPECIAL_CRAFTING, EmiStack.of(net.minecraft.item.Items.CRAFTING_TABLE));
        registry.addCategory(PROCESSING);
        registry.addWorkstation(PROCESSING, EmiStack.of(RegAddonBlocks.ENERGY_BOTTLER));
        for (var recipe : SscViewerRecipes.crafting(registry.getRecipeManager())) registry.addRecipe(new CraftingRecipe(recipe));
        for (var recipe : SscViewerRecipes.brewing()) registry.addRecipe(new EmiBrewingRecipe(
            EmiStack.of(recipe.input()), EmiStack.of(recipe.ingredient()), EmiStack.of(recipe.output()), recipe.id()));
        for (var recipe : SscViewerRecipes.processes()) registry.addRecipe(new ProcessingRecipe(recipe));
        int index = 0;
        for (var info : SscViewerRecipes.information()) registry.addRecipe(new EmiInfoRecipe(
            info.stacks().stream().map(stack -> (EmiIngredient) EmiStack.of(stack)).toList(), List.of(info.description()), new Identifier("ssc_addon", "info/" + index++)));
        registry.addRecipeHandler(ScreenHandlerType.CRAFTING, new CraftingHandler<>(false));
        // EMI 1.1 uses the null type key for vanilla's player menu (verified against the bundled API).
        registry.addRecipeHandler(null, new CraftingHandler<PlayerScreenHandler>(true));
        registry.setDefaultComparison(net.jackcooper.shapeShifterCurseAddon.SscAddon.PORTABLE_MOISTURIZER,
            Comparison.compareData(s -> net.jackcooper.shapeShifterCurseAddon.item.PortableMoisturizerItem.getLevel(s.getItemStack())));
        registry.addExclusionArea(SlottedFormationScreen.class, (screen, consumer) -> consumer.accept(new Bounds(0, 0, screen.width, screen.height)));
    }

    public static final class CraftingRecipe extends EmiCraftingRecipe {
        private final Crafting recipe;
        public CraftingRecipe(Crafting recipe) {
            super(recipe.grid().stream().map(alts -> EmiIngredient.of(alts.stream().map(EmiStack::of).toList())).toList(),
                EmiStack.of(recipe.output()), recipe.id(), recipe.shapeless());
            this.recipe = recipe;
            // Compare only the level for upgrades, retaining arbitrary charge/name/custom NBT.
            for (var ingredient : input) for (var stack : ingredient.getEmiStacks()) {
                var item = stack.getItemStack();
                if (item.isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.PORTABLE_MOISTURIZER))
                    stack.comparison(Comparison.compareData(s -> net.jackcooper.shapeShifterCurseAddon.item.PortableMoisturizerItem.getLevel(s.getItemStack())));
            }
        }
        public Crafting recipe() { return recipe; }
        @Override public EmiRecipeCategory getCategory() { return SPECIAL_CRAFTING; }
        @Override public boolean supportsRecipeTree() { return !recipe.dynamic(); }
        @Override public int getDisplayWidth() { return 144; }
        @Override public int getDisplayHeight() { return 166; }
        @Override public void addWidgets(WidgetHolder widgets) {
            super.addWidgets(widgets);
            widgets.addDrawable(0, 64, 144, 102, (context, mouseX, mouseY, delta) -> {
                var font = MinecraftClient.getInstance().textRenderer;
                int y = 0;
                for (var line : font.wrapLines(recipe.description(), 140)) {
                    context.drawText(font, line, 1, y, 0xFF555555, false); y += 10;
                }
            });
        }
    }

    private static final class CraftingHandler<T extends ScreenHandler> implements EmiRecipeHandler<T> {
        private final boolean playerMenu;
        CraftingHandler(boolean playerMenu) { this.playerMenu = playerMenu; }
        @Override public EmiPlayerInventory getInventory(HandledScreen<T> screen) {
            // Include the existing grid just as the shared planner does.
            int size = playerMenu ? 4 : 9;
            var stacks = new java.util.ArrayList<EmiStack>();
            for (int i = 1; i <= size; i++) stacks.add(EmiStack.of(screen.getScreenHandler().getSlot(i).getStack()));
            var player = MinecraftClient.getInstance().player;
            if (player != null) for (int i = 0; i < 36; i++) stacks.add(EmiStack.of(player.getInventory().getStack(i)));
            return new EmiPlayerInventory(stacks);
        }
        @Override public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe instanceof CraftingRecipe crafting && (!playerMenu || crafting.recipe().shapeless());
        }
        @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
            return recipe instanceof CraftingRecipe crafting && SscGridClickTransfer.transfer(MinecraftClient.getInstance(), crafting.recipe(), true)
                == net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan.Outcome.SUCCESS;
        }
        @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
            return recipe instanceof CraftingRecipe crafting && SscGridClickTransfer.transfer(MinecraftClient.getInstance(), crafting.recipe(), false)
                == net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscTransferPlan.Outcome.SUCCESS;
        }
    }

    private static final class ProcessingRecipe extends BasicEmiRecipe {
        private final SscViewerRecipes.Process recipe;
        ProcessingRecipe(SscViewerRecipes.Process recipe) {
            super(PROCESSING, recipe.id(), 160, 140);
            this.recipe = recipe;
            inputs = recipe.inputs().stream().map(stack -> (EmiIngredient) EmiStack.of(stack)).toList();
            catalysts = List.of(EmiStack.of(recipe.workstation()));
            outputs = List.of(EmiStack.of(recipe.output()));
        }
        @Override public boolean supportsRecipeTree() { return false; } // network energy is an additional cost
        @Override public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(catalysts.get(0), 0, 0);
            widgets.addSlot(inputs.get(0), 36, 0);
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 72, 1);
            widgets.addSlot(outputs.get(0), 110, 0).recipeContext(this);
            widgets.addDrawable(0, 28, 160, 112, (context, mouseX, mouseY, delta) -> {
                var font = MinecraftClient.getInstance().textRenderer;
                int y = 0;
                for (var line : font.wrapLines(recipe.description(), 156)) {
                    context.drawText(font, line, 1, y, 0xFF555555, false); y += 10;
                }
            });
        }
    }
}
