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
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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

	// 能量汲取器：单格空心舱，使魔系走进转化 mana 为能量注入网络（jackcooper）
	public static final Block ENERGY_EXTRACTOR = new EnergyExtractorBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.PURPLE)
					.strength(3.0f)
					.sounds(BlockSoundGroup.AMETHYST_BLOCK)
					.requiresTool()
					.nonOpaque());

	// 能量储罐：正方体被动存储节点，相邻可叠加总上限（jackcooper）
	public static final Block ENERGY_STORAGE_TANK = new EnergyStorageTankBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.ORANGE)
					.strength(3.0f)
					.sounds(BlockSoundGroup.COPPER)
					.requiresTool());

	// 能量装瓶器：类炼药台，从相邻网络抽能量 + 空瓶合成能量瓶（jackcooper）
	// nonOpaque：前部凹陷空腔开放，空腔内 BER 瓶子可见且有正确光照
	public static final Block ENERGY_BOTTLER = new EnergyBottlerBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.IRON_GRAY)
					.strength(2.5f)
					.sounds(BlockSoundGroup.METAL)
					.requiresTool()
					.nonOpaque());

	// 能量转变器：SSCA 能量 → Team Reborn Energy(E) 单向转换，右键激活，1:4 最高 128 E/t（jackcooper）
	// luminance：激活态微发光（1.20.1 无 getLuminance 覆写，须走 Settings.luminance(ToIntFunction)）
	public static final Block ENERGY_CONVERTER = new EnergyConverterBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.IRON_GRAY)
					.strength(3.0f)
					.sounds(BlockSoundGroup.COPPER)
					.requiresTool()
					.nonOpaque()
					.luminance(EnergyConverterBlock::luminanceOf));

	// 创造能量储罐：仅创造可拿，接入网络后周期补满所有储罐（jackcooper）
	public static final Block CREATIVE_ENERGY_TANK = new CreativeEnergyTankBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.ORANGE)
					.strength(3.0f)
					.sounds(BlockSoundGroup.COPPER)
					.requiresTool());

	// 药品存储箱：专存能量瓶，支持漏斗互通（jackcooper）
	public static final Block POTION_STORAGE_BOX = new PotionStorageBoxBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.SPRUCE_BROWN)
					.strength(2.0f)
					.sounds(BlockSoundGroup.WOOD));

	// 注魔台：给月尘魔法书充法力 + 升级（jackcooper）
	public static final Block INFUSION_ALTAR = new InfusionAltarBlock(
			AbstractBlock.Settings.create()
					.mapColor(MapColor.PURPLE)
					.strength(2.5f)
					.sounds(BlockSoundGroup.AMETHYST_BLOCK)
					.requiresTool()
					.nonOpaque());

	public static void init() {
		register("web_membrane", WEB_MEMBRANE);
		// 燃烧快、蔓延强（蛛丝易燃；数值对齐草 / 树叶级别）
		FlammableBlockRegistry.getDefaultInstance().add(WEB_MEMBRANE, 60, 100);

		register("energy_extractor", ENERGY_EXTRACTOR);
		register("energy_storage_tank", ENERGY_STORAGE_TANK);
		register("energy_bottler", ENERGY_BOTTLER);
		register("energy_converter", ENERGY_CONVERTER);
		register("potion_storage_box", POTION_STORAGE_BOX);
		register("infusion_altar", INFUSION_ALTAR);
		// 创造储罐：带附魔光效的 BlockItem（无合成表无其它获取途径，仅创造物品栏可拿）
		{
			Identifier glintId = new Identifier(NAMESPACE, "creative_energy_tank");
			Registry.register(Registries.BLOCK, glintId, CREATIVE_ENERGY_TANK);
			Registry.register(Registries.ITEM, glintId, new BlockItem(CREATIVE_ENERGY_TANK, new Item.Settings()) {
				@Override
				public boolean hasGlint(net.minecraft.item.ItemStack stack) {
					return true; // 附魔光效：创造物品标识
				}
			});
		}
		// 方块实体 + 容器类型注册（能量系统四方块）
		RegAddonBlockEntities.register();
		// 加入 SSCA 创造物品栏
		ItemGroupEvents.modifyEntriesEvent(
				RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier("ssc_addon", "group")))
				.register(entries -> {
					entries.add(ENERGY_EXTRACTOR);
					entries.add(ENERGY_STORAGE_TANK);
					entries.add(ENERGY_BOTTLER);				entries.add(ENERGY_CONVERTER);					entries.add(POTION_STORAGE_BOX);
					entries.add(INFUSION_ALTAR);
					entries.add(CREATIVE_ENERGY_TANK);
				});
	}

	@Environment(EnvType.CLIENT)
	public static void clientInit() {
		// 贴图含大量真半透明像素（约 40%），用 translucent 层才能正确渲染 alpha 渐变；cutout 会把半透明二值化导致大片「不显示」
		BlockRenderLayerMap.INSTANCE.putBlock(WEB_MEMBRANE, RenderLayer.getTranslucent());
		// 能量储罐无级液面 BER（玻璃内随网络能量比例升降的半透明液体盒）
		net.jackcooper.shapeShifterCurseAddon.client.renderer.EnergyStorageTankRenderer.register();
		// 能量装瓶器动态瓶子 BER（类炼药台：按槽位动态显示空瓶/能量瓶）
		net.jackcooper.shapeShifterCurseAddon.client.renderer.EnergyBottlerRenderer.register();
		// 储药柜动态药水瓶 BER（8 槽装填后在柜内对应位置显示 3D 能量瓶）
		net.jackcooper.shapeShifterCurseAddon.client.renderer.PotionStorageBoxRenderer.register();
		// 能量系统容器界面注册（汲取器/储罐无 GUI，右键走动作栏）
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				RegAddonBlockEntities.ENERGY_BOTTLER_SH,
				net.jackcooper.shapeShifterCurseAddon.client.screen.EnergyBottlerScreen::new);
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				RegAddonBlockEntities.POTION_STORAGE_BOX_SH,
				net.jackcooper.shapeShifterCurseAddon.client.screen.PotionStorageBoxScreen::new);
		net.minecraft.client.gui.screen.ingame.HandledScreens.register(
				RegAddonBlockEntities.INFUSION_ALTAR_SH,
				net.jackcooper.shapeShifterCurseAddon.client.screen.InfusionAltarScreen::new);
	}

	private static void register(String path, Block block) {
		Identifier id = new Identifier(NAMESPACE, path);
		Registry.register(Registries.BLOCK, id, block);
		Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
	}
}
