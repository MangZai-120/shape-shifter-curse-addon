package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.entity.AxolotlShifterEntity;

/**
 * 美西螈幻形者生成注册（jackcooper 署名）。
 * ① 自然生成：繁茂洞穴（LUSH_CAVES）水体，AXOLOTLS 生成组（对齐原版美西螈）。
 * ② 伴生兜底：每只原版美西螈附近强制确保至少一只野生美西螈幻形者（自然生成受配额限制不稳定）。
 */
public final class AxolotlShifterSpawnHandler {

	private AxolotlShifterSpawnHandler() {}

	public static void register() {
		SpawnRestriction.register(SscAddon.AXOLOTL_SHIFTER_ENTITY, SpawnRestriction.Location.IN_WATER,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AxolotlShifterEntity::canSpawnInWater);

		// 仅繁茂洞穴（与原版美西螈自然生成群系一致）
		BiomeModifications.addSpawn(
				BiomeSelectors.includeByKey(BiomeKeys.LUSH_CAVES),
				SpawnGroup.AXOLOTLS,
				SscAddon.AXOLOTL_SHIFTER_ENTITY,
				10,  // 权重（对齐原版美西螈 lush_caves）
				2, 4 // 最小/最大成组数量
		);

		// 伴生兜底：每只原版美西螈附近确保至少一只野生美西螈幻形者（自然生成受生成组配额限制不稳定，伴生兜底）。
		// command tag 保证每只原版美西螈只检查一次（区块重载不重复，且对已存在的原版美西螈重进世界时也生效）；附近已有幻形者则跳过，避免成群。
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof AxolotlEntity axolotl)) return;
			if (axolotl.getCommandTags().contains("ssc_axolotl_shifter_checked")) return;
			axolotl.addCommandTag("ssc_axolotl_shifter_checked");

			// 附近 16 格已有野生美西螈幻形者则不再伴生（每个区域至少一只，不成群）
			boolean nearbyExists = !world.getEntitiesByClass(AxolotlShifterEntity.class,
					axolotl.getBoundingBox().expand(16.0), e -> true).isEmpty();
			if (nearbyExists) return;

			AxolotlShifterEntity shifter = SscAddon.AXOLOTL_SHIFTER_ENTITY.create(world);
			if (shifter == null) return;
			double offsetX = (axolotl.getRandom().nextDouble() - 0.5) * 3.0;
			double offsetZ = (axolotl.getRandom().nextDouble() - 0.5) * 3.0;
			shifter.refreshPositionAndAngles(
					axolotl.getX() + offsetX,
					axolotl.getY(),
					axolotl.getZ() + offsetZ,
					axolotl.getRandom().nextFloat() * 360f,
					0f
			);
			world.spawnEntity(shifter);
		});
	}
}
