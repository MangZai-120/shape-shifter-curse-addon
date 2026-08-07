package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;

/**
 * 女巫使魔伴生逻辑 + 野外自然生成注册（从 SscAddon.registerEntitySpawnHandlers 拆分而来）。
 * - 袭击中生成的女巫会在附近生成1-3只女巫使魔
 * - 野外极低概率自然生成无主使魔（会自动寻找附近女巫认主）
 * 使用命令标签确保每只女巫只检查一次（重新加载区块不会重复触发）
 */
public final class WitchFamiliarSpawnHandler {

	private WitchFamiliarSpawnHandler() {}

	public static void register() {
		// 野外自然生成（末影人权重10的一半=5）
		SpawnRestriction.register(SscAddon.WITCH_FAMILIAR_ENTITY, SpawnRestriction.Location.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		// 仅在原版女巫会自然生成的主世界生物群系注册：foundInOverworld 已排除下界/末地，
		// 再手动排除蘑菇岛与深暗之域（原版女巫/陆地怪物在主世界唯二不自然生成的群系）。
		BiomeModifications.addSpawn(
				BiomeSelectors.foundInOverworld().and(context ->
						!context.getBiomeKey().equals(BiomeKeys.MUSHROOM_FIELDS)
								&& !context.getBiomeKey().equals(BiomeKeys.DEEP_DARK)),
				SpawnGroup.MONSTER,
				SscAddon.WITCH_FAMILIAR_ENTITY,
				5,    // 末影人权重10的一半
				1, 1  // 最小/最大成组数量
		);

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof WitchEntity witch)) return;
			// 仅主世界伴生：女巫使魔不在下界/末地等非主世界生成（跟随原版女巫生成逻辑）
			if (!world.getRegistryKey().equals(World.OVERWORLD)) return;
			// 每只女巫只检查一次
			if (witch.getCommandTags().contains("ssc_familiar_checked")) return;
			witch.addCommandTag("ssc_familiar_checked");

			// 仅袭击中生成的女巫才会伴生使魔
			if (!witch.hasActiveRaid()) return;

			// 随机生成1-3只使魔
			int count = 1 + witch.getRandom().nextInt(3);
			for (int i = 0; i < count; i++) {
				WitchFamiliarEntity familiar = SscAddon.WITCH_FAMILIAR_ENTITY.create(world);
				if (familiar == null) continue;

				// 设置主人为该女巫
				familiar.setOwnerUuid(witch.getUuid());

				double offsetX = (witch.getRandom().nextDouble() - 0.5) * 3.0;
				double offsetZ = (witch.getRandom().nextDouble() - 0.5) * 3.0;
				familiar.refreshPositionAndAngles(
						witch.getX() + offsetX,
						witch.getY(),
						witch.getZ() + offsetZ,
						witch.getRandom().nextFloat() * 360f,
						0f
				);
				world.spawnEntity(familiar);
			}
		});
	}
}
