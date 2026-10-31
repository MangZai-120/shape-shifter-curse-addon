package net.jackcooper.shapeShifterCurseAddon.compat.jei;

import mezz.jei.api.*;
import mezz.jei.api.constants.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.*;
import net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer.SscViewerRecipes.Process;
import net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;

@JeiPlugin
public class SSCA_JEIPlugin implements IModPlugin {
    public static final RecipeType<Crafting> SPECIAL_CRAFTING = RecipeType.create("ssc_addon", "special_crafting", Crafting.class);
    public static final RecipeType<Process> PROCESSING = RecipeType.create("ssc_addon", "processing", Process.class);
    @Override public Identifier getPluginUid() { return new Identifier("ssc_addon", "jei_plugin"); }
    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(PotionTestTubes.FILLED, new mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter<ItemStack>() {
            @Override public Object getSubtypeData(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext context) {
                return PotionTestTubes.subtypeData(stack);
            }
            @Override public String getLegacyStringSubtypeInfo(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext context) {
                return PotionTestTubes.subtypeData(stack).toString();
            }
        });
    }
    @Override public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(PotionTestTubes.viewerStacks());
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CraftingCategory(gui), new ProcessingCategory(gui));
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new SscClickTransferHandler<>(CraftingScreenHandler.class, registration.getTransferHelper()), SPECIAL_CRAFTING);
        registration.addRecipeTransferHandler(new SscClickTransferHandler<>(PlayerScreenHandler.class, registration.getTransferHelper()), SPECIAL_CRAFTING);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(Items.CRAFTING_TABLE, SPECIAL_CRAFTING);
        registration.addRecipeCatalyst(RegAddonBlocks.ENERGY_BOTTLER, PROCESSING);
    }
	@Override
	public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
		// 绘制法阵界面（FormationResearchScreen）是全屏画布，JEI 物品侧栏会盖住画布。
		// JEI 机制（反编译核实）：ScreenHelper.getGuiProperties 按「类距离升序 + isInstance」取第一个
		// 非 null 的 IScreenHandler；JEI 内部给 HandledScreen 注册了兜底（返回 GUI 框矩形），
		// 所以这里不能返回 null（会落到兜底）——改为返回「GUI 覆盖整个屏幕」的属性，
		// JEI 侧栏被挤到屏幕外、无空间显示，等效隐藏。数值全部合法（>0 且 <1e9），
		// 不会触发 validateGuiProperties 的 error 日志。返回研究台界面后自动恢复。
		registration.addGuiScreenHandler(
				net.jackcooper.shapeShifterCurseAddon.client.screen.SlottedFormationScreen.class,
				screen -> new mezz.jei.api.gui.handlers.IGuiProperties() {
					@Override
					public Class<? extends net.minecraft.client.gui.screen.Screen> getScreenClass() {
						return net.jackcooper.shapeShifterCurseAddon.client.screen.SlottedFormationScreen.class;
					}

					@Override
					public int getGuiLeft() {
						return 0;
					}

					@Override
					public int getGuiTop() {
						return 0;
					}

					@Override
					public int getGuiXSize() {
						return screen.width;
					}

					@Override
					public int getGuiYSize() {
						return screen.height;
					}

					@Override
					public int getScreenWidth() {
						return screen.width;
					}

					@Override
					public int getScreenHeight() {
						return screen.height;
					}
				});
	}

    @Override public void registerRecipes(IRecipeRegistration registration) {
        var world = MinecraftClient.getInstance().world;
        registration.addRecipes(SPECIAL_CRAFTING, world == null ? List.of() : SscViewerRecipes.crafting(world.getRecipeManager()));
        registration.addRecipes(PROCESSING, SscViewerRecipes.processes());
        var factory = registration.getJeiHelpers().getVanillaRecipeFactory();
        registration.addRecipes(RecipeTypes.BREWING, SscViewerRecipes.brewing().stream().map(r ->
            factory.createBrewingRecipe(List.of(r.input()), r.ingredient(), r.output(), r.id())).toList());
        for (var info : SscViewerRecipes.information()) registration.addItemStackInfo(info.stacks(), info.description());
    }
    private static void description(DrawContext context, Text text, int y, int width) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        for (var line : renderer.wrapLines(text, width)) {
            context.drawText(renderer, line, 1, y, 0xFF555555, false); y += 10;
        }
    }
    public static final class CraftingCategory implements IRecipeCategory<Crafting> {
        private final IDrawable background, icon, slotBackground;
        public CraftingCategory(IGuiHelper gui) {
            background = gui.createBlankDrawable(144, 166);
            icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.CRAFTING_TABLE));
            slotBackground = gui.getSlotDrawable();
        }
        @Override public RecipeType<Crafting> getRecipeType() { return SPECIAL_CRAFTING; }
        @Override public Text getTitle() { return Text.translatable("gui.ssc_addon.category.special_crafting"); }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public Identifier getRegistryName(Crafting recipe) { return recipe.id(); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, Crafting recipe, IFocusGroup focuses) {
            for (int i = 0; i < recipe.grid().size(); i++) {
                var slot = builder.addSlot(RecipeIngredientRole.INPUT, 1 + i % 3 * 18, 1 + i / 3 * 18);
                slot.setBackground(slotBackground, -1, -1);
                slot.addItemStacks(recipe.grid().get(i));
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 19).setBackground(slotBackground, -1, -1).addItemStack(recipe.output());
        }
        @Override public void draw(Crafting recipe, IRecipeSlotsView slots, DrawContext context, double x, double y) {
            context.drawTexture(new Identifier("minecraft", "textures/gui/container/crafting_table.png"), 60, 24, 90, 40, 22, 15, 256, 256);
            description(context, recipe.description(), 60, 140);
        }
    }
    public static final class ProcessingCategory implements IRecipeCategory<Process> {
        private final IDrawable background, icon, slotBackground;
        public ProcessingCategory(IGuiHelper gui) {
            background = gui.createBlankDrawable(160, 140);
            icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(RegAddonBlocks.ENERGY_BOTTLER));
            slotBackground = gui.getSlotDrawable();
        }
        @Override public RecipeType<Process> getRecipeType() { return PROCESSING; }
        @Override public Text getTitle() { return Text.translatable("gui.ssc_addon.category.processing"); }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public Identifier getRegistryName(Process recipe) { return recipe.id(); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, Process recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 1, 1).setBackground(slotBackground, -1, -1).addItemStack(recipe.workstation());
            builder.addSlot(RecipeIngredientRole.INPUT, 37, 1).setBackground(slotBackground, -1, -1).addItemStacks(recipe.inputs());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 111, 1).setBackground(slotBackground, -1, -1).addItemStack(recipe.output());
        }
        @Override public void draw(Process recipe, IRecipeSlotsView slots, DrawContext context, double x, double y) {
            context.drawTexture(new Identifier("minecraft", "textures/gui/container/crafting_table.png"), 72, 2, 90, 40, 22, 15, 256, 256);
            description(context, recipe.description(), 28, 156);
        }
    }
}
