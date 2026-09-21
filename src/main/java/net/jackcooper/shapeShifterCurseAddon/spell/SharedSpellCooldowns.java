package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家级法术共享冷却（jackcooper，阶段 B / 计划书 §15.2）。
 *
 * <p><b>规则</b>：同一玩家、同一法术 ID 的所有书与卷轴共享「最短再次使用时刻」——
 * 换槽位、换书、用第二张同法术卷轴单独使用，都不能重置或绕过冷却。卷轴自身 NBT 的
 * {@code Cd} 字段保留（用于交易继承与单独使用界面），但施法判定一律以本表为权威。</p>
 *
 * <p><b>持久化</b>：PersistentState 挂主世界（随存档保存），世界时间基准与卷轴 NBT 一致；
 * 冷却随服务器运行时间自然推进，停服不额外推进（计划书 §15.2 默认语义）。</p>
 *
 * <p><b>写入点</b>：服务端施法（{@code SpellCastManager}）与单独使用（{@code MagicScrollItem}
 * 服务端分支）成功后写表；两处读取判 CD 时均取「表 vs 卷轴 NBT」的较大者（兼容旧档：
 * 旧卷轴自带较长 CD 时不被清空表清零）。</p>
 */
public final class SharedSpellCooldowns extends PersistentState {

	private static final String KEY = "ssc_addon_shared_spell_cooldowns";

	/** 玩家 UUID → (法术 id path → 冷却结束的世界时间)。 */
	private final Map<UUID, Map<String, Long>> data = new HashMap<>();

	/** 取某玩家某法术的共享冷却结束时刻（无记录 = 0）。 */
	public long getCooldownEnd(UUID player, String spellPath) {
		Map<String, Long> inner = data.get(player);
		return inner == null ? 0L : inner.getOrDefault(spellPath, 0L);
	}

	/** 写入共享冷却结束时刻（只取更大值——保留更长的既有 CD，防止旧卷轴清表）。 */
	public void setCooldownEnd(UUID player, String spellPath, long endTick) {
		if (endTick > getCooldownEnd(player, spellPath)) {
			data.computeIfAbsent(player, k -> new HashMap<>()).put(spellPath, endTick);
			markDirty();
		}
	}

	/**
	 * 缩短共享冷却结束时刻（强制写入，可低于既有值）。
	 * 仅限中断退款类场景（如空间归途中断 CD 减半、口袋空间中断退 20%）：正常施法一律走
	 * {@link #setCooldownEnd}（只取更大值）；退款只应缩短不应清零，下限为当前时刻。
	 */
	public void shortenCooldownEnd(UUID player, String spellPath, long endTick, long now) {
		long clamped = Math.max(now, endTick);
		data.computeIfAbsent(player, k -> new HashMap<>()).put(spellPath, clamped);
		markDirty();
	}

	// ---- 持久化 ----

	/** 冷却结束后懒清理（读取时清掉已过期条目，防 map 无限膨胀）。 */
	private void prune(UUID player, long now) {
		Map<String, Long> inner = data.get(player);
		if (inner == null) {
			return;
		}
		Iterator<Map.Entry<String, Long>> it = inner.entrySet().iterator();
		boolean changed = false;
		while (it.hasNext()) {
			if (it.next().getValue() <= now) {
				it.remove();
				changed = true;
			}
		}
		if (changed) {
			markDirty();
		}
	}

	public static SharedSpellCooldowns get(MinecraftServer server) {
		if (server == null) {
			return null;
		}
		PersistentStateManager m = server.getOverworld().getPersistentStateManager();
		return m.getOrCreate(SharedSpellCooldowns::readNbt, SharedSpellCooldowns::new, KEY);
	}

	private static SharedSpellCooldowns readNbt(NbtCompound nbt) {
		SharedSpellCooldowns state = new SharedSpellCooldowns();
		for (String uuidStr : nbt.getKeys()) {
			try {
				UUID uuid = UUID.fromString(uuidStr);
				NbtCompound inner = nbt.getCompound(uuidStr);
				Map<String, Long> m = new HashMap<>();
				for (String spellPath : inner.getKeys()) {
					m.put(spellPath, inner.getLong(spellPath));
				}
				state.data.put(uuid, m);
			} catch (IllegalArgumentException ignored) {
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		for (Map.Entry<UUID, Map<String, Long>> e : data.entrySet()) {
			NbtCompound inner = new NbtCompound();
			for (Map.Entry<String, Long> ent : e.getValue().entrySet()) {
				inner.putLong(ent.getKey(), ent.getValue());
			}
			nbt.put(e.getKey().toString(), inner);
		}
		return nbt;
	}

	// ---- 便捷静态门面（按需调用；server 可空时安全降级为不启用共享 CD） ----

	/** 判定玩家某法术是否处于共享冷却（server 可空/无表 = 不拦截）。 */
	public static boolean isOnSharedCooldown(ServerPlayerEntity player, Spell spell) {
		if (player == null || spell == null) {
			return false;
		}
		SharedSpellCooldowns state = get(player.getServer());
		if (state == null) {
			return false;
		}
		long now = player.getWorld().getTime();
		state.prune(player.getUuid(), now);
		return now < state.getCooldownEnd(player.getUuid(), spell.getId().getPath());
	}

	/** 写共享冷却（只取更大值）。 */
	public static void record(ServerPlayerEntity player, Spell spell, long cooldownEnd) {
		if (player == null || spell == null) {
			return;
		}
		SharedSpellCooldowns state = get(player.getServer());
		if (state != null) {
			state.setCooldownEnd(player.getUuid(), spell.getId().getPath(), cooldownEnd);
		}
	}

	/**
	 * 清空指定玩家的全部共享冷却（/ssc_addon reset_spell_cd 指令专用）。
	 * 书内卷轴 NBT Cd 由指令层一并清理；施法中 GCD 由指令层调用清门方法处理。
	 */
	public static void clearAllFor(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		SharedSpellCooldowns state = get(player.getServer());
		if (state != null) {
			state.data.remove(player.getUuid());
			state.markDirty();
		}
	}

	/** 取共享冷却结束时刻（供被拦截时回写卷轴 NBT 同步 HUD；无表 = 0）。 */
	public static long getCooldownEndOf(ServerPlayerEntity player, Spell spell) {
		if (player == null || spell == null) {
			return 0L;
		}
		SharedSpellCooldowns state = get(player.getServer());
		return state == null ? 0L : state.getCooldownEnd(player.getUuid(), spell.getId().getPath());
	}

	/**
	 * 缩短共享冷却（退款专用，强制写入；卷轴 NBT 同步尽力而为——卷轴可能已不在书内，
	 * 找不到时只缩共享表，共享表是施法判定的权威）。
	 */
	public static void shorten(ServerPlayerEntity player, Spell spell, long newEndTick) {
		if (player == null || spell == null) {
			return;
		}
		SharedSpellCooldowns state = get(player.getServer());
		if (state != null) {
			state.shortenCooldownEnd(player.getUuid(), spell.getId().getPath(), newEndTick,
					player.getWorld().getTime());
		}
	}
}
