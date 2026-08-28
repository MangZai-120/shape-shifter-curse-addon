package net.jackcooper.shapeShifterCurseAddon.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;
import org.jetbrains.annotations.NotNull;

/**
 * SSCA 的 JEI 插件：注册「SSCA 特殊合成」分类展示毒液腺体配方
 * （8 蜘蛛眼环绕 + 中心剧毒药水×3 瓶型任一）。仅装 JEI 时加载（jei_plugins 入口 / @JeiPlugin）。
 */
@mezz.jei.api.JeiPlugin
public class SSCA_JEIPlugin implements IModPlugin {

	/** 展示用静态配方记录（3×3 布局的毒液腺体合成）。 */
	public record VenomGlandRecipe() {}

	public static final RecipeType<VenomGlandRecipe> VENOM_GLAND =
			RecipeType.create("ssc_addon", "venom_gland", VenomGlandRecipe.class);

	/** 展示用静态配方记录（无限压缩能量药水合成）。 */
	public record InfiniteEnergyPotionDisplay() {}

	public static final RecipeType<InfiniteEnergyPotionDisplay> INFINITE_ENERGY_POTION =
			RecipeType.create("ssc_addon", "infinite_energy_potion", InfiniteEnergyPotionDisplay.class);

	@Override
	public @NotNull Identifier getPluginUid() {
		return new Identifier("ssc_addon", "jei_plugin");
	}

	@Override
	public void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(
				new VenomGlandCategory(registration.getJeiHelpers().getGuiHelper()),
				new InfinitePotionCategory(registration.getJeiHelpers().getGuiHelper()));
	}

	@Override
	public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration registration) {
		registration.addRecipes(VENOM_GLAND, java.util.List.of(new VenomGlandRecipe()));
		registration.addRecipes(INFINITE_ENERGY_POTION, java.util.List.of(new InfiniteEnergyPotionDisplay()));
		// 酿造转换（mixin 实现，JEI 无法自动发现）：饮用型+火药→喷溅型；喷溅型+龙息→滞留型
		mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory factory =
				registration.getJeiHelpers().getVanillaRecipeFactory();
		registration.addRecipes(mezz.jei.api.constants.RecipeTypes.BREWING, java.util.List.of(
				factory.createBrewingRecipe(
						java.util.List.of(new ItemStack(SscAddon.INFINITE_ENERGY_POTION)),
						new ItemStack(Items.GUNPOWDER),
						new ItemStack(SscAddon.INFINITE_ENERGY_POTION_SPLASH),
						new Identifier("ssc_addon", "infinite_energy_potion_brewing_splash")),
				factory.createBrewingRecipe(
						java.util.List.of(new ItemStack(SscAddon.INFINITE_ENERGY_POTION_SPLASH)),
						new ItemStack(Items.DRAGON_BREATH),
						new ItemStack(SscAddon.INFINITE_ENERGY_POTION_LINGERING),
						new Identifier("ssc_addon", "infinite_energy_potion_brewing_lingering"))));
	}

	/** 分类：3×3 槽位布局 + 箭头 + 输出。 */
	public static class VenomGlandCategory implements IRecipeCategory<VenomGlandRecipe> {
		private final IDrawable background;
		private final IDrawable icon;

		public VenomGlandCategory(IGuiHelper guiHelper) {
			this.background = guiHelper.createBlankDrawable(120, 66);
			this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(SscAddon.VENOM_GLAND));
		}

		@Override
		public @NotNull RecipeType<VenomGlandRecipe> getRecipeType() {
			return VENOM_GLAND;
		}

		@Override
		public @NotNull Text getTitle() {
			return Text.translatable("gui.ssc_addon.category.special_crafting");
		}

		@Override
		public @NotNull IDrawable getBackground() {
			return background;
		}

		@Override
		public IDrawable getIcon() {
			return icon;
		}

		@Override
		public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull VenomGlandRecipe recipe, @NotNull IFocusGroup focuses) {
			// 3×3：8 蜘蛛眼 + 中心三种剧毒瓶型（作为可循环选项）
			ItemStack eye = new ItemStack(Items.SPIDER_EYE);
			ItemStack poisonDrink = PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.POISON);
			ItemStack poisonSplash = PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.POISON);
			ItemStack poisonLingering = PotionUtil.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.POISON);
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					int idx = row * 3 + col;
					if (idx == 4) {
						builder.addSlot(RecipeIngredientRole.INPUT, 1 + col * 18, 1 + row * 18)
								.addItemStack(poisonDrink).addItemStack(poisonSplash).addItemStack(poisonLingering);
					} else {
						builder.addSlot(RecipeIngredientRole.INPUT, 1 + col * 18, 1 + row * 18)
								.addItemStack(eye);
					}
				}
			}
			builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 19)
					.addItemStack(new ItemStack(SscAddon.VENOM_GLAND));
		}

		@Override
		public void draw(@NotNull VenomGlandRecipe recipe, @NotNull IRecipeSlotsView recipeSlotsView,
						 net.minecraft.client.gui.DrawContext context, double mouseX, double mouseY) {
			// 画箭头（vanilla 图标）
			context.drawTexture(net.minecraft.util.Identifier.of("minecraft", "textures/gui/container/crafting_table.png"),
					60, 24, 90, 40, 22, 15, 256, 256);
		}
	}

	/** 分类：无限压缩能量药水合成（月髓环 + 2 附魔金苹果 + 压缩能量药水，上对齐摆放）。 */
	public static class InfinitePotionCategory implements IRecipeCategory<InfiniteEnergyPotionDisplay> {
		private final IDrawable background;
		private final IDrawable icon;

		public InfinitePotionCategory(IGuiHelper guiHelper) {
			this.background = guiHelper.createBlankDrawable(120, 66);
			this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(SscAddon.INFINITE_ENERGY_POTION));
		}

		@Override
		public @NotNull RecipeType<InfiniteEnergyPotionDisplay> getRecipeType() {
			return INFINITE_ENERGY_POTION;
		}

		@Override
		public @NotNull Text getTitle() {
			return Text.translatable("gui.ssc_addon.category.special_crafting");
		}

		@Override
		public @NotNull IDrawable getBackground() {
			return background;
		}

		@Override
		public @NotNull IDrawable getIcon() {
			return icon;
		}

		@Override
		public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull InfiniteEnergyPotionDisplay recipe, @NotNull IFocusGroup focuses) {
			// 3×3：上中=月髓环，中间行=附魔金苹果 / 压缩能量药水 / 附魔金苹果，其余为空槽
			ItemStack moonRing = new ItemStack(SscAddon.SP_UPGRADE_THING);
			ItemStack apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
			ItemStack feedPotion = PotionUtil.setPotion(new ItemStack(Items.POTION), RegCustomPotions.FEED_POTION);
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					int idx = row * 3 + col;
					ItemStack stack = null;
					if (idx == 1) {
						stack = moonRing;
					} else if (idx == 3 || idx == 5) {
						stack = apple;
					} else if (idx == 4) {
						stack = feedPotion;
					}
					mezz.jei.api.gui.builder.IRecipeSlotBuilder slot =
							builder.addSlot(RecipeIngredientRole.INPUT, 1 + col * 18, 1 + row * 18);
					if (stack != null) {
						slot.addItemStack(stack);
					}
				}
			}
			builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 19)
					.addItemStack(new ItemStack(SscAddon.INFINITE_ENERGY_POTION));
		}

		@Override
		public void draw(@NotNull InfiniteEnergyPotionDisplay recipe, @NotNull IRecipeSlotsView recipeSlotsView,
						 net.minecraft.client.gui.DrawContext context, double mouseX, double mouseY) {
			// 画箭头（vanilla 图标，与毒液腺体分类一致）
			context.drawTexture(net.minecraft.util.Identifier.of("minecraft", "textures/gui/container/crafting_table.png"),
					60, 24, 90, 40, 22, 15, 256, 256);
		}
	}
}
