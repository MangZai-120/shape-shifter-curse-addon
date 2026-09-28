package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerCast;
import net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerManager;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** 使魔主/副键技能：击杀赋能就绪时走赋能释放，否则按统一技能 power 处理（冷却字段同 fail_aware_active_self）。 */
public final class FamiliarSkillPower extends FailAwareActiveSelfPower {
	private final boolean primary;

	public FamiliarSkillPower(PowerType<?> type, LivingEntity entity, SkillCooldownSpec spec, HudRender hudRender,
	                          Active.Key key, ActionFactory<Entity>.Instance action, boolean deferRelease,
	                          boolean repressWhileActive, int repressDelay, boolean primary) {
		super(type, entity, spec, hudRender, key, action, deferRelease, repressWhileActive, repressDelay);
		this.primary = primary;
	}

	@Override
	public void onUse() {
		if (!(entity instanceof ServerPlayerEntity player) || !player.isAlive()
				|| !BalanceIntegration.isPlayerReady(player) || !KillEmpowerManager.isEmpowerForm(player)) return;
		if (KillEmpowerManager.readState(player).usesEmpoweredSkill(primary)) {
			if (primary) KillEmpowerCast.tryCastRing(player);
			else KillEmpowerCast.tryCastBreath(player);
			return;
		}
		super.onUse();
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("ssc_addon", "familiar_skill"),
				FailAwareActiveSelfPower.fields().add("primary", SerializableDataTypes.BOOLEAN, false),
				data -> {
					SkillCooldownSpec spec = SkillCooldownSpec.read(data);
					return (type, entity) -> new FamiliarSkillPower(type, entity, spec, data.get("hud_render"),
							data.get("key"), data.get("entity_action"), data.getBoolean("defer_release"),
							data.getBoolean("repress_while_active"), data.getInt("repress_delay"), data.getBoolean("primary"));
				})
				.allowCondition();
	}
}