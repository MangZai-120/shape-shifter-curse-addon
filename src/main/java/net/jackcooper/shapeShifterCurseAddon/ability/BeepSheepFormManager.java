package net.jackcooper.shapeShifterCurseAddon.ability;

import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.effect.SheepFormRestoreHandler;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.NamespaceMigration;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.event.SSCEvent;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 变羊形态切换管理器（羊了个羊配套，服务端权威，jackcooper，2026-09-29 用户三轮定稿）。
 *
 * <p><b>架构换代</b>：变羊从「效果压制」改为「切换空壳羊形态」——羊形态（ssc_addon:sheep_spell_form，
 * 空壳无 power）不挂任何技能/被动/魔法，天然全失效；渲染继续由 SheepFormRenderMixin 画原版羊模型，
 * SHEEP_FORM 效果继续承载时长/AI 停机/换血（这套已验证）。无黑屏动画（immediatelyTransform 瞬变，
 * 用户定稿）；到期切回原形态；牛奶不可净化。</p>
 *
 * <p><b>原形态保存</b>：变羊瞬间记录原 FormID 到本管理器内存表（按玩家 UUID）；<b>Apoli 原生 CD 快照</b>：
 * 切形态会重建 power 实例导致 CooldownPower.lastUseTime 清零——变羊前快照所有 CooldownPower 的
 * 剩余 tick，切回后按「快照剩余 + 变羊经过时长」回写（CD 正常走表，不重算；用户定稿）。
 * SSCA 统一冷却（SkillCooldowns，PersistentState 记账）不受切形态影响，无需处理。</p>
 *
 * <p><b>持久化</b>：原形态记录写 NBT 到本类的玩家数据（登录时若玩家处于羊形态且无记录 → 自动切回
 * 基础人形 original_before_enable，防卡死羊形态）；变羊期间掉线：效果与形态都在玩家数据里，
 * 重进后由 tickSweep 续走（效果在→继续羊；效果没了→切回）。</p>
 */
public final class BeepSheepFormManager {

	private BeepSheepFormManager() {}

	/** 玩家 UUID → 变羊快照（原 FormID + Apoli CD 快照 + 变羊时刻）。 */
	private static final Map<UUID, SheepSnapshot> SNAPSHOTS = new HashMap<>();
	// 与持久快照分开：旧存档可能没有快照，离线玩家也可能还有待写盘的快照。
	private static final Set<UUID> ONLINE_SHEEP = new HashSet<>();

	private record SheepSnapshot(String originalFormId, Map<String, Integer> cooldowns, long sheepStartTime) {}

	/** Existing persisted transformation snapshot; callers must not treat it as an active form/power. */
	public static String originalFormId(ServerPlayerEntity player) {
		SheepSnapshot snapshot = SNAPSHOTS.get(player.getUuid());
		return snapshot == null ? null : snapshot.originalFormId();
	}

	/** 当前是否处于羊形态（按 FormID 判定，双端可用）。 */
	public static boolean isSheepForm(IForm form) {
		return form != null && FormIdentifiers.SHEEP_FORM.equals(form.getFormID());
	}

	/** 变羊开始：快照原形态 + Apoli CD → 瞬切羊形态（无黑屏，用户定稿）。 */
	public static void onSheepStart(ServerPlayerEntity player) {
		// 装死打断（2026-09-29 用户实测问题3）：变羊瞬间中止装死——清 PLAYING_DEAD 及伴生效果、恢复站姿。
		// 若不清：效果继续 tick（锁 SWIMMING 姿态/持续回血），且羊期间 PlayDeadEndClient 用裸 GLFW
		// 绕过按键屏蔽仍会发提前结束包 → 羊身上无美西螈 power → extra() 返回 0 → force(0) 清空装死 CD
		//（即问题2「变回后 CD 被重置」的真因，日志 20:12:54 skill disabled 警告与此吻合）。
		if (player.hasStatusEffect(SscAddon.PLAYING_DEAD)) {
			player.removeStatusEffect(SscAddon.PLAYING_DEAD);
			player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
			player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
			player.setPose(net.minecraft.entity.EntityPose.STANDING);
		}
		if (SNAPSHOTS.containsKey(player.getUuid())) return; // 重复施法只刷新效果时长
		IForm original = FormUtils.getPlayerForm(player);
		String originalId = original != null && original.getFormID() != null
				? original.getFormID().toString() : null;
		if (originalId == null || FormIdentifiers.SHEEP_FORM.toString().equals(originalId)) {
			return; // 已是羊/无形态（理论不可达）：不快照不切换
		}
		// Apoli 原生 CD 快照（CooldownPower.lastUseTime 随切形态重建清零；切回后回写续走）
		Map<String, Integer> cds = new HashMap<>();
		for (Power power : io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(player).getPowers()) {
			if (power instanceof CooldownPower cooldown && cooldown.getRemainingTicks() > 0) {
				cds.put(power.getType().getIdentifier().toString(), cooldown.getRemainingTicks());
			}
		}
		long now = player.getWorld().getTime();
		SNAPSHOTS.put(player.getUuid(), new SheepSnapshot(originalId, cds, now));

		IForm sheep = RegPlayerForms.getPlayerForm(FormIdentifiers.SHEEP_FORM);
		if (sheep != null) {
			TransformManager.immediatelyTransform(player, sheep); // 瞬变（无黑屏动画，用户定稿）
			// fallback 修复（2026-09-29 实测问题2真因）：immediatelyTransform 尾部 _setForm →
			// NormalForm.onTransform_Finish → setFallbackForm(null)——null 查不到 form 会把
			// fallbackFormID 污染成 InitialForm（=开书前人形）。羊期间一旦任何路径走
			// FormUtils.applyFallback（如重进登录链异常兜底），玩家就会被错误变回人类。
			// 变羊后立刻把 fallback 修回「变羊前的原形态」，保证任何 fallback 都回到原形态。
			IForm originalForm = RegPlayerForms.getPlayerForm(Identifier.tryParse(originalId));
			if (originalForm != null) {
				net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent.COMPONENT
						.get(player).setFallbackForm(originalForm.getFormID());
			}
		}
	}

	/** 变羊结束：切回原形态 + 回写 Apoli CD（快照剩余 + 变羊经过时长）。 */
	public static void onSheepEnd(ServerPlayerEntity player) {
		SheepSnapshot snapshot = SNAPSHOTS.remove(player.getUuid());
		IForm original = snapshot != null
				? RegPlayerForms.getPlayerForm(Identifier.tryParse(snapshot.originalFormId())) : null;
		if (original == null) {
			original = RegPlayerForms.ORIGINAL_BEFORE_ENABLE; // 记录缺失兜底：人形
		}
		TransformManager.immediatelyTransform(player, original);
		// fallback 修复（同 onSheepStart）：切回的 _setForm 尾部 setFallbackForm(null) 又会把
		// fallbackFormID 污染成 InitialForm——切回后立刻修回原形态，防后续任何 applyFallback
		// 把玩家错误变成开书前人形（2026-09-29 实测「退出羊形态后变人类」真因）。
		net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent.COMPONENT
				.get(player).setFallbackForm(original.getFormID());
		// CD 回写：切形态已重建 power 实例（CD 清零），按「快照剩余 - 变羊经过」续写，
		// 使 CD 表现与「从未变羊」完全一致（正常计算，用户定稿「别全重新计算」）。
		// 遍历玩家当前 power 按 type id 匹配快照（PowerTypeRegistry 无 get(Identifier) API，不依赖它）
		if (snapshot != null && !snapshot.cooldowns().isEmpty()) {
			long elapsed = Math.max(0, player.getWorld().getTime() - snapshot.sheepStartTime());
			var holder = io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(player);
			for (Power power : holder.getPowers()) {
				if (!(power instanceof CooldownPower cooldown)) continue;
				Integer snap = snapshot.cooldowns().get(power.getType().getIdentifier().toString());
				if (snap == null) continue;
				int remaining = (int) Math.max(0, snap - elapsed);
				if (remaining > 0) {
					cooldown.setCooldown(remaining);
					io.github.apace100.apoli.component.PowerHolderComponent.syncPower(player, power.getType());
				}
			}
		}
	}

	/** 效果未走 onRemoved 的兜底。恢复会同步触发形态事件，故在遍历后切换。 */
	public static void tickSweep(MinecraftServer server) {
		if (ONLINE_SHEEP.isEmpty()) return;
		ArrayList<ServerPlayerEntity> toRestore = null;
		for (var it = ONLINE_SHEEP.iterator(); it.hasNext();) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(it.next());
			if (player == null || !isSheepForm(FormUtils.getPlayerForm(player))) {
				it.remove();
			} else if (!player.hasStatusEffect(SscAddon.SHEEP_FORM)) {
				if (toRestore == null) toRestore = new ArrayList<>();
				toRestore.add(player);
			}
		}
		if (toRestore != null) toRestore.forEach(SheepFormRestoreHandler::onSheepEnd);
	}

	/** 玩家数据持久化（PersistentState 语义）：写 NBT（断线/停服不丢原形态记录）。 */
	public static void writePlayerNbt(ServerPlayerEntity player, NbtCompound nbt) {
		SheepSnapshot snapshot = SNAPSHOTS.get(player.getUuid());
		if (snapshot == null) return;
		NbtCompound tag = new NbtCompound();
		tag.putString("OriginalForm", snapshot.originalFormId());
		tag.putLong("SheepStart", snapshot.sheepStartTime());
		SheepFormRestoreHandler.writePlayerModifiers(player, tag);
		NbtCompound cds = new NbtCompound();
		snapshot.cooldowns().forEach(cds::putInt);
		tag.put("Cooldowns", cds);
		nbt.put("SSCA.SheepForm", tag);
	}

	/** 登录恢复：读 NBT 重建快照（变羊中掉线重连续走）。 */
	public static void readPlayerNbt(ServerPlayerEntity player, NbtCompound nbt) {
		if (!nbt.contains("SSCA.SheepForm")) return;
		NbtCompound tag = nbt.getCompound("SSCA.SheepForm");
		Map<String, Integer> cds = new HashMap<>();
		NbtCompound cdTag = tag.getCompound("Cooldowns");
		for (String key : cdTag.getKeys()) cds.merge(NamespaceMigration.powerId(key), cdTag.getInt(key), Math::max);
		SNAPSHOTS.put(player.getUuid(), new SheepSnapshot(
				net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration.canonical(tag.getString("OriginalForm")),
				cds, tag.getLong("SheepStart")));
		float savedHealth = nbt.contains("Health", NbtElement.NUMBER_TYPE) ? nbt.getFloat("Health") : player.getHealth();
		SheepFormRestoreHandler.resumePlayerModifiers(player, tag, savedHealth);
	}

	/** 玩家彻底退出（不留尸体数据）：清内存记录。 */
	public static void onPlayerRemoved(ServerPlayerEntity player) {
		SNAPSHOTS.remove(player.getUuid());
	}

	// ==================== 事件挂载（SscAddon.registerApoliSystems 调用） ====================

	/** 事件注册：玩家 NBT 读写（持久化）+ 登录矫正 + 断线清理。 */
	public static void init() {
		// JOIN：变羊中重进（效果与形态都在玩家数据里，天然续走）；此处兜底两种异常：
		// ① 卡羊形态但效果已无（停服期间到期未走 onRemoved）→ 切回原形态；
		// ② 快照丢失但处于羊形态（旧存档）→ 切回人形防卡死。
		SSCEvent.FORM_CHANGE_END.register((player, previous, current) -> {
			if (player instanceof ServerPlayerEntity sp) {
				if (isSheepForm(current)) ONLINE_SHEEP.add(sp.getUuid());
				else if (isSheepForm(previous)) ONLINE_SHEEP.remove(sp.getUuid());
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((previous, player, alive) -> {
			if (isSheepForm(FormUtils.getPlayerForm(player))) ONLINE_SHEEP.add(player.getUuid());
			else ONLINE_SHEEP.remove(player.getUuid());
		});
		ServerTickEvents.END_SERVER_TICK.register(BeepSheepFormManager::tickSweep);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof SheepFormRestoreHandler.MobSnapshotAccess saved) saved.ssca$resumeSheepSnapshot();
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof SheepFormRestoreHandler.MobSnapshotAccess saved) saved.ssca$suspendSheepSnapshot();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> {
					ServerPlayerEntity player = handler.player;
					IForm form = FormUtils.getPlayerForm(player);
					if (isSheepForm(form)) {
						ONLINE_SHEEP.add(player.getUuid());
						// 重进后立刻修 fallback：存档里的 fallbackFormID 可能是上一会话
						// onTransform_Finish 污染的人形值；用快照原形态覆盖，防登录链任何
						// applyFallback 把变羊中的玩家变人类。
						SheepSnapshot snap = SNAPSHOTS.get(player.getUuid());
						IForm original = snap != null
								? RegPlayerForms.getPlayerForm(Identifier.tryParse(snap.originalFormId())) : null;
						if (original != null) {
							net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent.COMPONENT
									.get(player).setFallbackForm(original.getFormID());
						}
						if (!player.hasStatusEffect(SscAddon.SHEEP_FORM)) {
							SheepFormRestoreHandler.onSheepEnd(player); // 无快照时内部兜底切人形
						}
					}
				}));
		// DISCONNECT 先于玩家 NBT 存盘，只移除在线检查项。停服存盘完成后再释放快照。
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ONLINE_SHEEP.remove(handler.player.getUuid()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ONLINE_SHEEP.clear();
			SNAPSHOTS.clear();
			SheepFormRestoreHandler.onServerStopped();
		});
	}

	/** NBT 写：由 mixin（PlayerEntity.writeCustomDataToNbt 末尾）调用。 */
	public static void onWriteNbt(ServerPlayerEntity player, NbtCompound nbt) {
		writePlayerNbt(player, nbt);
	}

	/** NBT 读：由 mixin（PlayerEntity.readCustomDataFromNbt 末尾）调用。 */
	public static void onReadNbt(ServerPlayerEntity player, NbtCompound nbt) {
		readPlayerNbt(player, nbt);
	}
}
