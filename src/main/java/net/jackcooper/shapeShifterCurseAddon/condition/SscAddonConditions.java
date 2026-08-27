package net.jackcooper.shapeShifterCurseAddon.condition;

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
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.AnubisWolfSpDeathDomain;
import net.jackcooper.shapeShifterCurseAddon.ability.ErosionBrandClientState;
import net.jackcooper.shapeShifterCurseAddon.ability.GoldenSandstormErosionBrand;
import net.jackcooper.shapeShifterCurseAddon.ability.MancianimaMarkClientState;
import net.jackcooper.shapeShifterCurseAddon.ability.MancianimaMarkManager;
import net.jackcooper.shapeShifterCurseAddon.util.SkillBlocker;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;

public class SscAddonConditions {

	private SscAddonConditions() {
		// This utility class should not be instantiated
	}

	public static void register() {
		register(new ConditionFactory<>(new Identifier("ssc_addon", "has_reverse_thermometer"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof PlayerEntity player) {
						return TrinketUtils.isWearing(player,
								Registries.ITEM.get(new Identifier("shape-shifter-curse", "charm_of_reverse_thermometer")));
					}
					return false;
				}));

		register(new ConditionFactory<>(new Identifier("ssc_addon", "has_trinket"),
				new SerializableData()
						.add("item", SerializableDataTypes.ITEM),
				(data, entity) -> {
					if (entity instanceof PlayerEntity player) {
						return TrinketUtils.isWearing(player, (net.minecraft.item.Item) data.get("item"));
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
						return TrinketUtils.isWearing(player, SscAddon.BLUE_FIRE_AMULET);
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

		// 食梦魔「入梦」门控（Apoli JSON 侧用）：actor（施加者）被 target（食梦魔）入梦 → false（效果不施加）。
		// 用于 apply_effect 等无法带 source 的 JSON 动作，与服务端 addStatusEffect 拦截器同语义。
		registerBiEntity(new ConditionFactory<>(new Identifier("my_addon", "not_dream_blocked"),
				new SerializableData(),
				(data, pair) -> {
					Entity actor = pair.getLeft();
					Entity target = pair.getRight();
					if (!(actor instanceof LivingEntity livingActor)) return true;
					if (!(target instanceof LivingEntity livingTarget)) return true;
					if (actor.getWorld().isClient()) return true;
					return !net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager
							.isBlocked(livingActor, livingTarget);
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
						return net.jackcooper.shapeShifterCurseAddon.client.MancianimaCrosshairTracker.isCurrent(target.getUuid());
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

		// SSCA 进化加点系统 - 天赋节点解锁条件
		// 给「可解锁能力」的 power 挂在此条件后：未解锁则该 power 不生效（解锁后自动生效）
		register(new ConditionFactory<>(new Identifier("ssc_addon", "has_talent"),
				new SerializableData()
						.add("talent_id", SerializableDataTypes.STRING),
				(data, entity) -> {
					if (entity instanceof PlayerEntity player) {
						return RegEvolutionComponent.EVOLUTION.get(player).isUnlocked(data.getString("talent_id"));
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
