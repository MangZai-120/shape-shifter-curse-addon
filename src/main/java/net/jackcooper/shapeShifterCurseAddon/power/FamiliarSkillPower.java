package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerCast;
import net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerManager;
import net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public final class FamiliarSkillPower extends ActiveCooldownPower {
	private final boolean primary;

	public FamiliarSkillPower(PowerType<?> type, LivingEntity entity, int cooldown, HudRender hudRender,
	                          Consumer<Entity> action, Active.Key key, boolean primary) {
		super(type, entity, cooldown, hudRender, action);
		setKey(key);
		this.primary = primary;
	}

	@Override
	public void onUse() {
		if (!(entity instanceof ServerPlayerEntity player) || !player.isAlive()
				|| !KillEmpowerManager.isEmpowerForm(player)) return;
		KillEmpowerState state = KillEmpowerManager.readState(player);
		if (state.usesEmpoweredSkill(primary)) {
			if (primary) KillEmpowerCast.tryCastRing(player);
			else KillEmpowerCast.tryCastBreath(player);
			return;
		}
		super.onUse();
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("ssc_addon", "familiar_skill"),
				new SerializableData()
						.add("entity_action", ApoliDataTypes.ENTITY_ACTION)
						.add("cooldown", SerializableDataTypes.INT, 1)
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key())
						.add("primary", SerializableDataTypes.BOOLEAN, false),
				data -> (type, entity) -> new FamiliarSkillPower(type, entity, data.getInt("cooldown"),
						data.get("hud_render"), data.get("entity_action"), data.get("key"), data.getBoolean("primary")))
				.allowCondition();
	}
}