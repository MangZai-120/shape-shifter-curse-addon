package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownHolder;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;

/**
 * 统一技能 power（所有形态主/副键技能共用这一种写法）。
 *
 * <pre>
 * {
 *   "type": "my_addon:fail_aware_active_self",
 *   "cooldown": 120,               // 成功 CD（tick）
 *   "fail_cooldown": 72,           // 失败 CD（0 = 失败不进 CD）
 *   "cooldown_start": "on_cast",   // on_cast / on_release / on_end
 *   "extra_cooldowns": { },        // 该技能特有的附加 CD
 *   "entity_action": { ... },      // 不写 = 按键由模组发包处理，本 power 只提供冷却配置
 *   "key": { "key": "key.ssc_addon.sp_primary" }
 * }
 * </pre>
 *
 * <p>defer_release=false（瞬发）：按键即登记施放，action 内调用 {@link #markFail()} 算失败，否则算成功。
 * defer_release=true：action（Java 或 JSON 通用 action）在真正开始时自己 begin，并负责后续结算。</p>
 */
public class FailAwareActiveSelfPower extends ActiveCooldownPower implements SkillCooldownHolder {

	private static final ThreadLocal<Boolean> PENDING_FAIL = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private final SkillCooldownSpec spec;
	private final boolean deferRelease;
	private final boolean repressWhileActive;
	private final int repressDelay;
	private final ActionFactory<Entity>.Instance entityActionRef;

	public FailAwareActiveSelfPower(PowerType<?> type, LivingEntity entity, SkillCooldownSpec spec, HudRender hudRender,
	                                Active.Key key, ActionFactory<Entity>.Instance entityAction,
	                                boolean deferRelease, boolean repressWhileActive, int repressDelay) {
		super(type, entity, Math.max(1, spec.cooldown()), hudRender, e -> { });
		this.spec = spec;
		this.deferRelease = deferRelease;
		this.repressWhileActive = repressWhileActive;
		this.repressDelay = repressDelay;
		this.entityActionRef = entityAction;
		this.setKey(key);
	}

	/** action 失败分支调用：本次施放按 fail_cooldown 进入冷却。 */
	public static void markFail() {
		PENDING_FAIL.set(Boolean.TRUE);
	}

	@Override
	public SkillCooldownSpec cooldownSpec() {
		return spec;
	}

	@Override
	public boolean canUse() {
		if (entity instanceof ServerPlayerEntity player) {
			return isActive() && SkillCooldowns.ready(player, powerIdentifier());
		}
		return isActive();
	}

	@Override
	public void onUse() {
		if (!(entity instanceof ServerPlayerEntity player) || !isActive() || entityActionRef == null) return;
		String id = powerIdentifier();
		SkillCastManager.Cast active = SkillCastManager.get(player.getServerWorld()).control(player.getUuid(), id);
		if (active != null) {
			if (repressWhileActive && SkillCastManager.now(player) - active.beginTick >= repressDelay) runAction(player, id);
			return;
		}
		if (!SkillCooldowns.ready(player, id)) return;
		if (!deferRelease && !SkillCooldowns.begin(player, id)) return;
		long castId = SkillCooldowns.currentCastId(player, id);
		if (runAction(player, id) || deferRelease) return;
		SkillCooldowns.completed(player, id, castId);
	}

	/** 执行 action；action 内 markFail 时按失败结算并返回 true。 */
	private boolean runAction(ServerPlayerEntity player, String id) {
		long castId = SkillCooldowns.currentCastId(player, id);
		PENDING_FAIL.set(Boolean.FALSE);
		try {
			entityActionRef.accept(player);
			if (!Boolean.TRUE.equals(PENDING_FAIL.get())) return false;
			if (castId >= 0) SkillCooldowns.failed(player, id, castId);
            else if (SkillCooldowns.isCasting(player, id)) SkillCooldowns.failed(player, id, SkillCooldowns.currentCastId(player, id));
            else SkillCooldowns.penalizeAttempt(player, id);
			return true;
		} finally {
			PENDING_FAIL.remove();
		}
	}

	/** 本 power 的稳定技能 ID（= power 注册 ID，SkillCastManager 存储键）。 */
	public String powerIdentifier() {
		return type != null && type.getIdentifier() != null
				? type.getIdentifier().toString() : "my_addon:unknown";
	}

	/** 本类与子类共用的 JSON 字段。 */
	public static SerializableData fields() {
		return SkillCooldownSpec.addFields(new SerializableData()
						.add("entity_action", ApoliDataTypes.ENTITY_ACTION, null)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key())
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
						// true = action 在真正开始时自己 begin 并负责结算（蓄力/持续型）
						.add("defer_release", SerializableDataTypes.BOOLEAN, false)
						// true = 施放进行中再按键仍执行 action（取消/关环/二段）
						.add("repress_while_active", SerializableDataTypes.BOOLEAN, false)
						// 开始后至少经过多少 tick 才响应再按（防连按误取消）
						.add("repress_delay", SerializableDataTypes.INT, 0),
				1, SkillCastManager.START_ON_CAST);
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("my_addon", "fail_aware_active_self"), fields(),
				data -> {
					SkillCooldownSpec spec = SkillCooldownSpec.read(data);
					return (type, player) -> new FailAwareActiveSelfPower(type, player, spec,
							data.get("hud_render"), data.get("key"), data.get("entity_action"),
							data.getBoolean("defer_release"), data.getBoolean("repress_while_active"),
							data.getInt("repress_delay"));
				}
		).allowCondition();
	}
}
