package net.jackcooper.shapeShifterCurseAddon.ability;

import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 变羊形态切换管理器（羊了个羊配套，服务端权威，jackcooper，2026-09-29 用户三轮定稿）。
 *
 * <p><b>架构换代</b>：变羊从「效果压制」改为「切换空壳羊形态」——羊形态（my_addon:sheep_spell_form，
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

	private record SheepSnapshot(String originalFormId, Map<String, Integer> cooldowns, long sheepStartTime) {}

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

	/** 兜底扫描（挂 END_SERVER_TICK）：效果没了但还卡在羊形态 → 切回。
	 *  覆盖：牛奶外的一切非正常路径（死亡重生清效果/指令清效果/reload 清效果等）。 */
	public static void tickSweep(net.minecraft.server.MinecraftServer server) {
		for (var world : server.getWorlds()) {
			for (ServerPlayerEntity sp : world.getPlayers()) {
				IForm form = FormUtils.getPlayerForm(sp);
				if (isSheepForm(form) && !sp.hasStatusEffect(SscAddon.SHEEP_FORM)) {
					onSheepEnd(sp); // 效果已无但形态还是羊：恢复
				}
			}
		}
	}

	/** 玩家数据持久化（PersistentState 语义）：写 NBT（断线/停服不丢原形态记录）。 */
	public static void writePlayerNbt(ServerPlayerEntity player, NbtCompound nbt) {
		SheepSnapshot snapshot = SNAPSHOTS.get(player.getUuid());
		if (snapshot == null) return;
		NbtCompound tag = new NbtCompound();
		tag.putString("OriginalForm", snapshot.originalFormId());
		tag.putLong("SheepStart", snapshot.sheepStartTime());
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
		for (String key : cdTag.getKeys()) cds.put(key, cdTag.getInt(key));
		SNAPSHOTS.put(player.getUuid(), new SheepSnapshot(
				tag.getString("OriginalForm"), cds, tag.getLong("SheepStart")));
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
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> {
					ServerPlayerEntity player = handler.player;
					IForm form = FormUtils.getPlayerForm(player);
					if (isSheepForm(form)) {
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
							onSheepEnd(player); // 无快照时内部兜底切人形
						}
					}
				}));
		// 注意：**不能**注册 DISCONNECT 清空快照（2026-09-29 实测问题2真因）：Fabric DISCONNECT
		// 事件在 onDisconnected HEAD 触发，早于 PlayerManager.remove → savePlayerData →
		// writeCustomDataToNbt 存盘——先清内存表会让 SSCA.SheepForm 从未写进 NBT，重进后
		// readPlayerNbt 无数据 → 到期 onSheepEnd 走人形兜底（「变羊退出回来直接变人」）。
		// 内存表无泄漏风险：重进时 readPlayerNbt 按 UUID 覆盖、到期 onSheepEnd 已 remove、
		// 停服进程结束自然清空。
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
