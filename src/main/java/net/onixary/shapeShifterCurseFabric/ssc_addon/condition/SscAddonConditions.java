package net.onixary.shapeShifterCurseFabric.ssc_addon.condition;

import dev.emi.trinkets.api.TrinketsApi;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.apoli.util.Comparison;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpDeathDomain;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ErosionBrandClientState;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormErosionBrand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkClientState;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SkillBlocker;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

public class SscAddonConditions {

	private SscAddonConditions() {
		// This utility class should not be instantiated
	}

	public static void register() {
		register(new ConditionFactory<>(new Identifier("ssc_addon", "has_reverse_thermometer"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof PlayerEntity player) {
						return TrinketsApi.getTrinketComponent(player).map(component ->
								component.isEquipped(Registries.ITEM.get(new Identifier("shape-shifter-curse", "charm_of_reverse_thermometer")))
						).orElse(false);
					}
					return false;
				}));

		register(new ConditionFactory<>(new Identifier("ssc_addon", "has_trinket"),
				new SerializableData()
						.add("item", SerializableDataTypes.ITEM),
				(data, entity) -> {
					if (entity instanceof PlayerEntity player) {
						return TrinketsApi.getTrinketComponent(player).map(component ->
								component.isEquipped((net.minecraft.item.Item) data.get("item"))
						).orElse(false);
					}
					return false;
				}));

		register(new ConditionFactory<>(new Identifier("ssc_addon", "item_on_cooldown"),
				new SerializableData()
						.add("item", SerializableDataTypes.ITEM),
				(data, entity) -> {
					if (entity instanceof PlayerEntity player) {
						return player.getItemCooldownManager().isCoolingDown(data.get("item"));
					}
					return false;
				}));

		register(new ConditionFactory<>(new Identifier("ssc_addon", "has_blue_fire_amulet"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof PlayerEntity player) {
						return TrinketsApi.getTrinketComponent(player).map(component ->
								component.isEquipped(SscAddon.BLUE_FIRE_AMULET)
						).orElse(false);
					}
					return false;
				}));

		register(new ConditionFactory<>(new Identifier("ssc_addon", "has_mana_percent_safe"),
				new SerializableData()
						.add("mana_percent", SerializableDataTypes.DOUBLE)
						.add("comparison", ApoliDataTypes.COMPARISON),
				(data, entity) -> {
					if (!(entity instanceof PlayerEntity player)) return false;
					double requiredPercent = data.getDouble("mana_percent");
					Comparison comparison = data.get("comparison");

					double current = ManaUtils.getPlayerMana(player);
					double max = ManaUtils.getPlayerMaxMana(player);

					if (max <= 0) return false;

					return comparison.compare(current / max, requiredPercent);
				}));

		registerBiEntity(new ConditionFactory<>(new Identifier("my_addon", "not_actor_whitelisted"),
				new SerializableData(),
				(data, pair) -> {
					Entity actor = pair.getLeft();
					Entity target = pair.getRight();
					if (!(actor instanceof ServerPlayerEntity player)) return true;
					if (!(target instanceof LivingEntity living)) return true;
					return !WhitelistUtils.isProtected(player, living);
				}));

		// 侵蚀烙印颜色状态条件 - 用于entity_glow
		// 参数 "color"：yellow / orange / red / green
		// 服务端使用服务器HashMap，客户端使用S2C同步的缓存数据
		registerBiEntity(new ConditionFactory<>(new Identifier("ssc_addon", "erosion_brand_state"),
				new SerializableData()
						.add("color", SerializableDataTypes.STRING),
				(data, pair) -> {
					Entity actor = pair.getLeft();
					Entity target = pair.getRight();
					String color = data.getString("color");
					// 服务端检查（actor是ServerPlayerEntity）
					if (actor instanceof ServerPlayerEntity) {
						return GoldenSandstormErosionBrand.hasColor(actor.getUuid(), target.getUuid(), color);
					}
					// 客户端检查（使用网络同步的本地缓存）
					if (actor.getWorld().isClient()) {
						return ErosionBrandClientState.hasColor(target.getUuid(), color);
					}
					return false;
				}));

		// 契灵标记颜色条件 - 用于 entity_glow（黄/橙/红三档）
		// 服务端：从 MancianimaMarkManager 查询；客户端：从 MancianimaMarkClientState 查询
		registerBiEntity(new ConditionFactory<>(new Identifier("my_addon", "mancianima_mark_color"),
				new SerializableData()
						.add("color", SerializableDataTypes.STRING),
				(data, pair) -> {
					Entity actor = pair.getLeft();
					Entity target = pair.getRight();
					String color = data.getString("color");
					if (actor instanceof ServerPlayerEntity) {
						String c = MancianimaMarkManager.getColorString(actor.getUuid(), target.getUuid());
						return c != null && c.equals(color);
					}
					if (actor.getWorld().isClient()) {
						return MancianimaMarkClientState.hasColor(target.getUuid(), color);
					}
					return false;
				}));

		// 契灵准星目标条件（仅客户端有效）：用于绿色高亮覆盖
		registerBiEntity(new ConditionFactory<>(new Identifier("my_addon", "mancianima_crosshair_target"),
				new SerializableData(),
				(data, pair) -> {
					Entity actor = pair.getLeft();
					Entity target = pair.getRight();
					if (actor == null || target == null) return false;
					// 仅在客户端、且 actor 是本地玩家时才查询
					if (!actor.getWorld().isClient()) return false;
					try {
						return net.onixary.shapeShifterCurseFabric.ssc_addon.client.MancianimaCrosshairTracker.isCurrent(target.getUuid());
					} catch (Throwable t) { return false; }
				}));

		// Skill blocking condition - returns true when skill is NOT blocked (normal behavior)
		// Add this condition to action_over_time powers so they don't execute when disabled
		register(new ConditionFactory<>(new Identifier("ssc_addon", "skill_disabled"),
				new SerializableData()
						.add("form", SerializableDataTypes.STRING)
						.add("skill", SerializableDataTypes.STRING),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						String form = data.getString("form");
						String skill = data.getString("skill");
						return !SkillBlocker.isSkillBlocked(player, form, skill);
					}
					return true; // Non-player entities not blocked
				}));

		// SP阿努比斯之狼 - 是否处于自己的死亡领域范围内（用于领域内免疫自身受击凋零）
		register(new ConditionFactory<>(new Identifier("ssc_addon", "in_own_death_domain"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayerEntity player) {
						return AnubisWolfSpDeathDomain.isInActiveDomain(player.getUuid(), player.getBlockPos());
					}
					return false;
				}));
	}

	private static void register(ConditionFactory<Entity> factory) {
		Registry.register(ApoliRegistries.ENTITY_CONDITION, factory.getSerializerId(), factory);
	}

	private static void registerBiEntity(ConditionFactory<Pair<Entity, Entity>> factory) {
		Registry.register(ApoliRegistries.BIENTITY_CONDITION, factory.getSerializerId(), factory);
	}
}
