package net.jackcooper.shapeShifterCurseAddon.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.screen.EnergyBottlerScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.screen.PotionStorageBoxScreenHandler;

/**
 * SSCA 附属方块实体 + 容器类型注册（jackcooper）。
 * 由 {@link RegAddonBlocks#init()} 在方块注册之后调用 {@link #register()}。
 * <p>能量系统四方块：汲取器（无 GUI，右键动作栏显示能量）、储罐（无 GUI）、装瓶器（3 进 3 出 GUI）、
 * 药品存储箱（8 槽 GUI）。
 */
public final class RegAddonBlockEntities {

	private RegAddonBlockEntities() {}

	private static final String NAMESPACE = "ssc_addon";

	public static BlockEntityType<EnergyExtractorBlockEntity> ENERGY_EXTRACTOR_BE;
	public static BlockEntityType<EnergyStorageTankBlockEntity> ENERGY_STORAGE_TANK_BE;
	public static BlockEntityType<EnergyBottlerBlockEntity> ENERGY_BOTTLER_BE;
	public static BlockEntityType<PotionStorageBoxBlockEntity> POTION_STORAGE_BOX_BE;
	public static BlockEntityType<EnergyConverterBlockEntity> ENERGY_CONVERTER_BE;
	public static BlockEntityType<CreativeEnergyTankBlockEntity> CREATIVE_ENERGY_TANK_BE;

	public static ScreenHandlerType<EnergyBottlerScreenHandler> ENERGY_BOTTLER_SH;
	public static ScreenHandlerType<PotionStorageBoxScreenHandler> POTION_STORAGE_BOX_SH;

	public static void register() {
		ENERGY_EXTRACTOR_BE = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				new Identifier(NAMESPACE, "energy_extractor"),
				FabricBlockEntityTypeBuilder.create(EnergyExtractorBlockEntity::new, RegAddonBlocks.ENERGY_EXTRACTOR).build());
		ENERGY_STORAGE_TANK_BE = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				new Identifier(NAMESPACE, "energy_storage_tank"),
				FabricBlockEntityTypeBuilder.create(EnergyStorageTankBlockEntity::new, RegAddonBlocks.ENERGY_STORAGE_TANK).build());
		ENERGY_BOTTLER_BE = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				new Identifier(NAMESPACE, "energy_bottler"),
				FabricBlockEntityTypeBuilder.create(EnergyBottlerBlockEntity::new, RegAddonBlocks.ENERGY_BOTTLER).build());
		POTION_STORAGE_BOX_BE = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				new Identifier(NAMESPACE, "potion_storage_box"),
				FabricBlockEntityTypeBuilder.create(PotionStorageBoxBlockEntity::new, RegAddonBlocks.POTION_STORAGE_BOX).build());
		ENERGY_CONVERTER_BE = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				new Identifier(NAMESPACE, "energy_converter"),
				FabricBlockEntityTypeBuilder.create(EnergyConverterBlockEntity::new, RegAddonBlocks.ENERGY_CONVERTER).build());
		// 注册 TB Energy 对外储能：TR 等模组的电缆/机器可经 EnergyStorage.SIDED 拉取电（官方推荐时机：BE 类型创建后）
		team.reborn.energy.api.EnergyStorage.SIDED.registerForBlockEntity(
				(EnergyConverterBlockEntity be, net.minecraft.util.math.Direction dir) -> be.getExposedStorage(),
				ENERGY_CONVERTER_BE);
		CREATIVE_ENERGY_TANK_BE = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				new Identifier(NAMESPACE, "creative_energy_tank"),
				FabricBlockEntityTypeBuilder.create(CreativeEnergyTankBlockEntity::new, RegAddonBlocks.CREATIVE_ENERGY_TANK).build());

		ENERGY_BOTTLER_SH = Registry.register(
				Registries.SCREEN_HANDLER,
				new Identifier(NAMESPACE, "energy_bottler"),
				new ScreenHandlerType<>(EnergyBottlerScreenHandler::new, FeatureSet.empty()));
		POTION_STORAGE_BOX_SH = Registry.register(
				Registries.SCREEN_HANDLER,
				new Identifier(NAMESPACE, "potion_storage_box"),
				new ScreenHandlerType<>(PotionStorageBoxScreenHandler::new, FeatureSet.empty()));
	}
}
