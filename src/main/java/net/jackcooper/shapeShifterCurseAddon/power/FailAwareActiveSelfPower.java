package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
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
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

/**
 * 可区分成功/失败冷却的主动技能 power（JSON 驱动 CD 的单一真相源）。
 *
 * <p>power JSON 用法：
 * <pre>
 * {
 *   "type": "my_addon:fail_aware_active_self",
 *   "entity_action": { ... },
 *   "key": { "key": "key.ssc_addon.sp_primary" },
 *   "cooldown": 120,      // 正常（成功）CD：写多少游戏内就锁多少 tick
 *   "fail_cooldown": 72,  // 失败 CD（绝对值）：蓝不够/无目标等失败时改锁这个值
 *   "hud_render": { "should_render": false }
 * }
 * </pre></p>
 *
 * <p>机制：Apoli 原生 {@code ActiveCooldownPower.onUse()} 的执行序为
 * {@code canUse() → action → use()}（已反编译 2.11.4 验证），action 内部无法改写冷却。
 * 本类在 action 里通过 {@link #markFail()} 打线程局部标记，{@link #onUse()} 结束时
 * 若发现标记则直接改写 {@code lastUseTime = now - cooldownDuration + failCooldown}，
 * 绕开父类 {@code setCooldown()} 的 {@code Math.min(ticks, cooldownDuration)} 钳制
 * （该钳制导致父类 API 无法表达「失败 CD 长/短于成功 CD」）。</p>
 *
 * <p>线程安全：服务端 tick 单线程逐玩家顺序执行，action 与 onUse 同栈同步完成，
 * ThreadLocal 无竞态；finally 中 remove 防泄漏。</p>
 */
public class FailAwareActiveSelfPower extends ActiveCooldownPower {

	/** action 执行期间标记“本次施放失败”：失败分支调用 {@link #markFail()}。 */
	private static final ThreadLocal<Boolean> PENDING_FAIL = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/** 失败 CD（tick 绝对值）：power JSON fail_cooldown 字段。 */
	private final int failCooldown;

	public FailAwareActiveSelfPower(PowerType<?> type, LivingEntity entity, int cooldownDuration,
	                                 int failCooldown, HudRender hudRender, Active.Key key,
	                                 ActionFactory<Entity>.Instance entityAction) {
		super(type, entity, cooldownDuration, hudRender, entityAction);
		this.failCooldown = failCooldown;
		this.setKey(key);
	}

	/** action 失败分支调用：本次施放按 fail_cooldown 进入冷却。 */
	public static void markFail() {
		PENDING_FAIL.set(Boolean.TRUE);
	}

	@Override
	public void onUse() {
		PENDING_FAIL.set(Boolean.FALSE);
		try {
			// 原生序：canUse() → action（失败时 markFail()）→ use()（lastUseTime=now + 同步）
			super.onUse();
			if (Boolean.TRUE.equals(PENDING_FAIL.get())) {
				// 手写 lastUseTime 绕开 setCooldown 的 min 钳制：
				// canUse() 门禁 = now' >= lastUseTime + cooldownDuration = now + failCooldown，
				// 即失败后实际锁 failCooldown tick；HUD 剩余 = failCooldown - 流逝，分母仍为 JSON cooldown。
				long now = entity.getWorld().getTime();
				this.lastUseTime = now - this.cooldownDuration + this.failCooldown;
				PowerHolderComponent.syncPower(entity, this.type);
			}
		} finally {
			PENDING_FAIL.remove();
		}
	}

	public int getFailCooldown() {
		return failCooldown;
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("my_addon", "fail_aware_active_self"),
				new SerializableData()
						.add("entity_action", ApoliDataTypes.ENTITY_ACTION)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key())
						.add("cooldown", SerializableDataTypes.INT, 1)
						// 失败 CD（tick 绝对值）：写多少失败后就锁多少 tick（例：cooldown=120 + fail_cooldown=72）
						.add("fail_cooldown", SerializableDataTypes.INT, 1)
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER),
				data -> (type, player) -> new FailAwareActiveSelfPower(
						type,
						player,
						data.getInt("cooldown"),
						data.getInt("fail_cooldown"),
						data.get("hud_render"),
						data.get("key"),
						data.get("entity_action")
				)
		).allowCondition();
	}
}
