package net.jackcooper.shapeShifterCurseAddon.compat.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.jackcooper.shapeShifterCurseAddon.compat.jei.SscGridClickTransfer.GridSpec;
import net.jackcooper.shapeShifterCurseAddon.compat.jei.SscGridClickTransfer.Outcome;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * SSCA 特殊配方的 JEI 转移处理器（客户端点击模拟，支持 NBT 药水材料）。
 * <p>
 * JEI 原生转移（RecipeTransferServerUtil）按 Ingredient 重建材料、无法携带药水 NBT，
 * 压缩能量药水/剧毒药水永远搬不进合成格；这里接管我们的两个配方展示，
 * 改走原版 ClickSlot 模拟点击（详见 {@link SscGridClickTransfer}）。
 *
 * @param <R> 展示用静态配方 record（InfiniteEnergyPotionDisplay / VenomGlandRecipe）
 */
public class SscClickTransferHandler<R> implements IRecipeTransferHandler<CraftingScreenHandler, R> {

	private final RecipeType<R> recipeType;
	private final GridSpec gridSpec;
	private final IRecipeTransferHandlerHelper helper;

	public SscClickTransferHandler(RecipeType<R> recipeType, GridSpec gridSpec, IRecipeTransferHandlerHelper helper) {
		this.recipeType = recipeType;
		this.gridSpec = gridSpec;
		this.helper = helper;
	}

	@Override
	public Class<? extends CraftingScreenHandler> getContainerClass() {
		return CraftingScreenHandler.class;
	}

	@Override
	public Optional<ScreenHandlerType<CraftingScreenHandler>> getMenuType() {
		// JEI 按 getContainerClass 匹配即可，menu type 留空
		return Optional.empty();
	}

	@Override
	public RecipeType<R> getRecipeType() {
		return recipeType;
	}

	@Override
	public IRecipeTransferError transferRecipe(CraftingScreenHandler container, R recipe, IRecipeSlotsView recipeSlotsView,
											   PlayerEntity player, boolean maxTransfer, boolean doTransfer) {
		MinecraftClient client = MinecraftClient.getInstance();
		// maxTransfer（shift 批量）不支持：点击模拟一次只摆一份，忽略该标记按单份处理
		Outcome outcome = SscGridClickTransfer.transfer(client, gridSpec, !doTransfer);
		return switch (outcome) {
			case SUCCESS -> null;
			case NOT_ENOUGH -> helper.createUserErrorWithTooltip(Text.translatable("error.rei.not.enough.materials"));
			case NO_ROOM -> helper.createUserErrorWithTooltip(Text.translatable("error.ssc_addon.transfer.no_room"));
			case CURSOR_NOT_EMPTY, FAILED -> helper.createUserErrorWithTooltip(Text.translatable("error.ssc_addon.transfer.failed"));
		};
	}
}
