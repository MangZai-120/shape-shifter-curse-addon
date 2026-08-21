package net.jackcooper.shapeShifterCurseAddon.action;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Apoli 实体动作 {@code ssc_addon:item_cooldown}：给指定物品设置原版物品冷却（tick）。
 *
 * <p>配套 condition {@code ssc_addon:item_on_cooldown}（SscAddonConditions 已注册）读取同一冷却，
 * 用于饰品（幻铃 / 救命猫尾等）跨 power 的冷却门控——饰品不在物品栏也能正确判定与防止重复触发。</p>
 *
 * <p>此前 power JSON 引用了本动作但 Java 侧从未注册，Apoli 加载时整个 power 文件被跳过
 * （日志 "is not defined (skipping)"），幻铃逃脱与救命猫尾两个饰品技能完全失效——本类补上注册。</p>
 */
public class ItemCooldownAction {

	private ItemCooldownAction() {
	}

	public static ActionFactory<Entity> getFactory() {
		return new ActionFactory<>(
				new Identifier("ssc_addon", "item_cooldown"),
				new SerializableData()
						.add("item", SerializableDataTypes.ITEM)
						.add("duration", SerializableDataTypes.INT),
				(data, entity) -> {
					if (entity instanceof PlayerEntity player) {
						// 原版物品冷却管理器：set(物品, tick)；同物品重复设置会重置覆盖
						player.getItemCooldownManager().set(data.get("item"), data.getInt("duration"));
					}
				});
	}
}
