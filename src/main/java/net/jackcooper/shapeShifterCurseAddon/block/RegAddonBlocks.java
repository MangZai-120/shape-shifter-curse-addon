package net.jackcooper.shapeShifterCurseAddon.block;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * SSCA 附属方块注册（jackcooper 署名）。
 * 主类 onInitialize 调用 {@link #init()}；客户端 onInitializeClient 调用 {@link #clientInit()}。
 */
public final class RegAddonBlocks {

	private RegAddonBlocks() {}

	private static final String NAMESPACE = "ssc_addon";

	// 蛛网膜：多面薄层蛛网，减速陷阱，可燃蔓延、遇水冲毁
	public static final Block WEB_MEMBRANE = new WebMembraneBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.WHITE_GRAY)
					.strength(0.2f)
					.sounds(BlockSoundGroup.WOOL)
					.noCollision()
					.nonOpaque()
					.burnable()
					.dropsNothing()
					.pistonBehavior(PistonBehavior.DESTROY));

	public static void init() {
		register("web_membrane", WEB_MEMBRANE);
		// 燃烧快、蔓延强（蛛丝易燃；数值对齐草 / 树叶级别）
		FlammableBlockRegistry.getDefaultInstance().add(WEB_MEMBRANE, 60, 100);
	}

	@Environment(EnvType.CLIENT)
	public static void clientInit() {
		// 贴图含大量真半透明像素（约 40%），用 translucent 层才能正确渲染 alpha 渐变；cutout 会把半透明二值化导致大片「不显示」
		BlockRenderLayerMap.INSTANCE.putBlock(WEB_MEMBRANE, RenderLayer.getTranslucent());
	}

	private static void register(String path, Block block) {
		Identifier id = new Identifier(NAMESPACE, path);
		Registry.register(Registries.BLOCK, id, block);
		Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
	}
}
