package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.function.Consumer;

/** Runs the configured craft action when vanilla reaches offhand item use. */
public final class WaterSpearCraftPower extends CooldownPower {
	public static final Identifier ID = new Identifier("my_addon", "form_axolotl_sp_water_spear_craft_spear");
	private final Consumer<Entity> craft;

	private WaterSpearCraftPower(PowerType<?> type, LivingEntity entity, int cooldown, HudRender hud, Consumer<Entity> craft) {
		super(type, entity, cooldown, hud);
		this.craft = craft;
	}

	public static TypedActionResult<ItemStack> useArrow(PlayerEntity player, World world, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (hand != Hand.OFF_HAND || !stack.isOf(Items.ARROW) || !player.getMainHandStack().isEmpty()
				|| player.isSpectator() || player.currentScreenHandler != player.playerScreenHandler
				|| player.getItemCooldownManager().isCoolingDown(Items.ARROW) || AbilityUseGuard.blocked(player)
				|| !PowerTypeRegistry.contains(ID) || !(PowerTypeRegistry.get(ID).get(player) instanceof WaterSpearCraftPower power)
				|| !power.canUse()) {
			return TypedActionResult.pass(stack);
		}
		if (!world.isClient) {
			power.craft.accept(player);
			power.use();
		}
		return TypedActionResult.success(player.getStackInHand(hand));
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("my_addon", "water_spear_craft"),
				new SerializableData().add("entity_action", ApoliDataTypes.ENTITY_ACTION)
						.add("cooldown", SerializableDataTypes.INT)
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER),
				data -> (type, entity) -> new WaterSpearCraftPower(type, entity, data.getInt("cooldown"),
						data.get("hud_render"), data.get("entity_action"))).allowCondition();
	}
}
