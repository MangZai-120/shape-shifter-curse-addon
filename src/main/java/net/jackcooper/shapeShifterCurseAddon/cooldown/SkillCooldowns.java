package net.jackcooper.shapeShifterCurseAddon.cooldown;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能冷却统一入口（服务端）。数值一律来自玩家身上该技能 power 的 JSON 字段（{@link SkillCooldownSpec}）。
 *
 * <p>生命周期：begin（真正开始时登记）→ released（已放出、效果持续）→ ended（持续效果结束）；
 * 瞬发成功用 completed；未成功放出用 failed；死亡/变形用 cancelled 按阶段结算。
 * Instant entry points use instant(); callbacks without an active cast are no-ops.</p>
 */
public final class SkillCooldowns {

	private static final Logger LOGGER = LoggerFactory.getLogger("SSCA/SkillCooldown");
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private SkillCooldowns() {
	}

	/** 玩家身上该技能的冷却配置；缺失时该技能不可用（只记一次日志）。 */
	public static SkillCooldownSpec spec(ServerPlayerEntity player, String skillId) {
		Power power = power(player, skillId);
		if (power instanceof SkillCooldownHolder holder) return holder.cooldownSpec();
		if (WARNED.add(skillId)) {
			LOGGER.warn("Skill {} has no cooldown power on player {} (expected data/<ns>/powers/<path>.json attached to the form); skill disabled",
					skillId, player.getName().getString());
		}
		return null;
	}

	public static boolean ready(ServerPlayerEntity player, String skillId) {
		return spec(player, skillId) != null && manager(player).canBegin(player, skillId);
	}

	public static boolean isCasting(ServerPlayerEntity player, String skillId) {
		return cast(player, skillId) != null;
	}

	/** 真正开始施放时登记；冷却中/已在施放中/缺配置返回 false。 */
	public static boolean begin(ServerPlayerEntity player, String skillId) {
		SkillCooldownSpec spec = spec(player, skillId);
		return spec != null && manager(player).begin(player, skillId, spec.resolve()) >= 0;
	}

    /** Capture once when accepting a cast; delayed callbacks must retain this value. */
    public static long currentCastId(ServerPlayerEntity player, String skillId) {
        var cast = cast(player, skillId);
        return cast == null ? -1 : cast.castId;
    }

    private static boolean matches(ServerPlayerEntity player, String skillId, long castId) {
        return castId >= 0 && currentCastId(player, skillId) == castId;
    }

    public static void released(ServerPlayerEntity player, String skillId, long castId) {
        if (matches(player, skillId, castId)) manager(player).released(player, castId);
    }
    public static void completed(ServerPlayerEntity player, String skillId, long castId) {
        if (matches(player, skillId, castId)) manager(player).complete(castId, SkillCastManager.now(player));
    }
    public static void ended(ServerPlayerEntity player, String skillId, long castId) {
        if (matches(player, skillId, castId)) manager(player).finish(player, castId);
    }
    public static void failed(ServerPlayerEntity player, String skillId, long castId) {
        if (matches(player, skillId, castId)) manager(player).fail(player, castId);
    }
    public static void cancelled(ServerPlayerEntity player, String skillId, long castId) {
        if (matches(player, skillId, castId)) manager(player).interrupt(castId, SkillCastManager.now(player));
    }
    public static void retune(ServerPlayerEntity player, String skillId, long castId, int ticks) {
        if (matches(player, skillId, castId)) manager(player).retune(castId, ticks);
    }

    /** Only for a newly accepted instant action, never a delayed completion callback. */
    public static void instant(ServerPlayerEntity player, String skillId) {
        if (begin(player, skillId)) completed(player, skillId, currentCastId(player, skillId));
    }

    /** Explicit rejected-attempt penalty; never use for delayed failure notifications. */
    public static void penalizeAttempt(ServerPlayerEntity player, String skillId) {
        if (!ready(player, skillId)) return;
        var spec = spec(player, skillId);
        if (spec != null && spec.failCooldown() > 0) force(player, skillId, spec.failCooldown());
    }

    // ID-only overloads are for synchronous actions controlling the current cast.

	public static void released(ServerPlayerEntity player, String skillId) {
		SkillCastManager.Cast cast = cast(player, skillId);
		if (cast != null) manager(player).released(player, cast.castId);
	}

	public static void completed(ServerPlayerEntity player, String skillId) {
		SkillCastManager.Cast cast = cast(player, skillId);
		if (cast != null) {
			manager(player).complete(cast.castId, SkillCastManager.now(player));
		}
	}

	public static void ended(ServerPlayerEntity player, String skillId) {
		SkillCastManager.Cast cast = cast(player, skillId);
		if (cast != null) manager(player).finish(player, cast.castId);
	}

	public static void failed(ServerPlayerEntity player, String skillId) {
		SkillCastManager.Cast cast = cast(player, skillId);
		if (cast != null) {
			manager(player).fail(player, cast.castId);
		}
	}

	public static void cancelled(ServerPlayerEntity player, String skillId) {
		SkillCastManager.Cast cast = cast(player, skillId);
		if (cast != null) manager(player).interrupt(cast.castId, SkillCastManager.now(player));
	}

	/** 改写本次施放的正常 CD（档位/诅咒之月/单段超时等）；已起算的冷却同步改长短。 */
	public static void retune(ServerPlayerEntity player, String skillId, int ticks) {
		SkillCastManager.Cast cast = cast(player, skillId);
		if (cast != null) manager(player).retune(cast.castId, ticks);
	}

	/** 不经施放流程直接给技能上 CD（外部联动：增强领域、提前结束等）。 */
	public static void force(ServerPlayerEntity player, String skillId, int ticks) {
		manager(player).force(player.getUuid(), skillId, ticks, SkillCastManager.now(player));
	}

	public static void reset(ServerPlayerEntity player, String skillId) {
		manager(player).reset(player.getUuid(), skillId);
	}

	public static int cooldown(ServerPlayerEntity player, String skillId) {
		SkillCooldownSpec spec = spec(player, skillId);
		return spec == null ? 0 : spec.cooldown();
	}

	public static int failCooldown(ServerPlayerEntity player, String skillId) {
		SkillCooldownSpec spec = spec(player, skillId);
		return spec == null ? 0 : spec.failCooldown();
	}

	public static int extra(ServerPlayerEntity player, String skillId, String key) {
		SkillCooldownSpec spec = spec(player, skillId);
		return spec == null ? 0 : spec.extra(key);
	}

	public static int remaining(ServerPlayerEntity player, String skillId) {
		SkillCastManager.CooldownState state = manager(player).cooldown(player.getUuid(), skillId);
		return state == null ? 0 : state.remaining(SkillCastManager.now(player));
	}

	/** 丢失 power 时使用施放快照结算：已释放走正常结束，未释放走失败。 */
	public static void sweep(MinecraftServer server) {
		if (server.getOverworld() == null) return;
		SkillCastManager manager = SkillCastManager.get(server.getOverworld());
		for (SkillCastManager.Cast cast : manager.activeCasts()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(cast.playerId);
			if (player != null && power(player, cast.skillId) == null) manager.interrupt(cast.castId, SkillCastManager.now(player));
		}
	}

	private static Power power(ServerPlayerEntity player, String skillId) {
		Identifier id = Identifier.tryParse(skillId);
		if (id == null || !PowerTypeRegistry.contains(id)) return null;
		// PowerTypeRegistry.get 返回 raw PowerType，用 PowerType<?> 局部变量接住避免 unchecked 警告（同 SkillCooldownBarRenderer 模式）
		PowerType<?> type = PowerTypeRegistry.get(id);
		return PowerHolderComponent.KEY.get(player).getPower(type);
	}

	private static SkillCastManager.Cast cast(ServerPlayerEntity player, String skillId) {
		return manager(player).control(player.getUuid(), skillId);
	}

	private static SkillCastManager manager(ServerPlayerEntity player) {
		return SkillCastManager.get(player.getServerWorld());
	}
}
