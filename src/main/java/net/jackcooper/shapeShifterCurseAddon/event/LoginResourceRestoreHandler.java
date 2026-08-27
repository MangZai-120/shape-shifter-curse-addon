package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录资源条恢复：修复「形态资源（雪狐寒霜/悦灵 mana/蝙蝠血等）重进游戏被 init power 重置回初始值」。
 *
 * <p><b>根因</b>：SSC 登录时 {@code PlayerEventHandler.JOIN → FormUtils._loadForm → applyLayer →
 * OriginComponent.setOrigin}（先切 technical_form 再切回），Origins 的 setOrigin 每次都移除并重新赋予
 * powers——各形态的 init power（{@code apoli:action_on_callback} 的 {@code entity_action_chosen}，
 * 如 form_snow_fox_sp_init_mana 的 set 100）在重新赋予时再次触发，把 Apoli 本已持久化的 resource 值
 * 强制写回初始值。表现为「所有形态的能量重进游戏重置」。
 *
 * <p><b>修复</b>（与 {@link LoginHealthRestoreHandler} 同款快照药方）：mixin 在
 * {@code readCustomDataFromNbt} 末尾从玩家 NBT 的 Apoli {@code Powers} 列表读出各 resource power 的
 * 存档值（快照，早于任何重赋）；随后每 tick 观察，等 init 重置发生完（判定：资源值 ≠ 快照 且玩家
 * 已进入世界若干 tick，或直接定时）后把值恢复为快照。为简化时序，本实现采用「登录后第 N tick
 * 校验恢复」：在 INIT_SETTLE_TICKS（覆盖 SSC 双重 setOrigin + init power 执行）后，若资源当前值
 * 与快照不符则以快照为准恢复（此后玩家游戏内变化不被覆盖——只恢复一次）。
 *
 * <p>保护：快照仅恢复一次；恢复前玩家断线则作废；仅服务端判定，主客机一致。
 */
public final class LoginResourceRestoreHandler {

	private LoginResourceRestoreHandler() {}

	/** 等 SSC 形态重挂 + init power 重置完成的观察期（tick）。SSC JOIN 里 server.execute 立即执行，
	 * 双重 setOrigin 同 tick 内完成，宽松取 20t（1 秒）覆盖世界加入与同步链。 */
	private static final int SETTLE_TICKS = 20;

	private static final class Pending {
		final Map<Identifier, Integer> snapshot = new HashMap<>();
		// 原版 SSC ManaComponent 的存档值（红堕落等走原版 mana 体系的形态）
		double savedMana = -1.0d;
		String savedManaTypeId = null;
		int waited = 0;
		boolean restored = false;
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	/** 由 mixin 在读取存档 NBT 时调用：从 Apoli Powers NBT 抽出全部数值型 resource 的存档值。
	 * <p>真实存储路径（已对照 CCA AbstractComponentContainer / Apoli PowerHolderComponentImpl 源码验证）：
	 * 玩家 NBT 根 → {@code cardinal_components}（CCA 根键）→ {@code apoli:powers}（组件 id）→
	 * {@code Powers} 列表 → 每项 {@code Type}（power id）+ {@code Data}（VariableIntPower 的 NbtInt 存档值）。 */
	public static void recordSnapshot(UUID uuid, NbtCompound playerNbt) {
		NbtCompound ccaRoot = playerNbt.getCompound("cardinal_components");
		if (ccaRoot.isEmpty()) {
			return;
		}
		NbtCompound apoliPowers = ccaRoot.getCompound("apoli:powers");
		if (apoliPowers.isEmpty()) {
			return;
		}
		Pending p = new Pending();
		// 原版 SSC ManaComponent 快照（存在 cardinal_components→shape-shifter-curse:mana）
		NbtCompound sscMana = ccaRoot.getCompound("shape-shifter-curse:mana");
		if (!sscMana.isEmpty() && sscMana.contains("Mana")) {
			p.savedMana = sscMana.getDouble("Mana");
			if (sscMana.contains("ManaTypeID")) {
				p.savedManaTypeId = sscMana.getString("ManaTypeID");
			}
		}
		NbtList powerList = apoliPowers.getList("Powers", NbtElement.COMPOUND_TYPE);
		if (powerList == null || powerList.isEmpty()) {
			if (p.savedMana >= 0.0d) {
				PENDING.put(uuid, p); // 无 apoli 资源但原版 mana 需要恢复
			}
			return;
		}
		for (int i = 0; i < powerList.size(); i++) {
			NbtCompound powerTag = powerList.getCompound(i);
			Identifier typeId = Identifier.tryParse(powerTag.getString("Type"));
			if (typeId == null) {
				continue;
			}
			// VariableIntPower 的 toTag 是 NbtInt（Data 字段），resource power 均属此类
			NbtElement valueTag = powerTag.get("Data");
			if (valueTag != null && valueTag.getType() == NbtElement.INT_TYPE) {
				p.snapshot.put(new Identifier(typeId.getNamespace(), typeId.getPath()),
						((net.minecraft.nbt.NbtInt) valueTag).intValue());
			}
		}
		if (!p.snapshot.isEmpty()) {
			PENDING.put(uuid, p);
		}
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}
			Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Pending> entry = it.next();
				Pending p = entry.getValue();
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
				if (player == null) {
					// 尚未加入 / 已断线：宽限计时后清理
					if (++p.waited > SETTLE_TICKS * 4) {
						it.remove();
					}
					continue;
				}
				if (++p.waited < SETTLE_TICKS) {
					continue; // 观察期内：等 SSC 形态重挂 + init 重置完成
				}
				if (!p.restored) {
					p.restored = true;
					// 恢复 Apoli resource：仅当当前值 ≠ 快照（init 重置过）才写回
					for (Map.Entry<Identifier, Integer> e : p.snapshot.entrySet()) {
						try {
						int current = PowerUtils.getResourceValue(player, e.getKey());
						if (current != e.getValue()) {
							PowerUtils.setResourceValueAndSync(player, e.getKey(), e.getValue());
						}
					} catch (Exception ignored) {
						// 玩家已不持有该 power（形态已变）：跳过即可
					}
				}
					// 恢复原版 ManaComponent：ManaTypePower.onGained 登录重挂时会把 mana 顶满（Double.MAX_VALUE/8），
					// 仅当类型一致且当前值高于快照时压回存档值（保留较低值不受影响）
					if (p.savedMana >= 0.0d) {
						try {
						String curType = String.valueOf(ManaUtils.getPlayerManaTypeID(player));
						if (p.savedManaTypeId == null || curType.equals(p.savedManaTypeId)) {
							double cur = ManaUtils.getPlayerMana(player);
							if (Math.abs(cur - p.savedMana) > 0.01d) {
								ManaUtils.setPlayerMana(player, p.savedMana);
							}
						}
					} catch (Exception ignored) {
						// 无 mana 组件等异常：跳过
						}
					}
				}
				it.remove(); // 一次性恢复后清理
			}
		});

		// 断线清理
		ServerPlayConnectionEvents.DISCONNECT.register((netHandler, server) ->
				PENDING.remove(netHandler.player.getUuid()));
	}
}
