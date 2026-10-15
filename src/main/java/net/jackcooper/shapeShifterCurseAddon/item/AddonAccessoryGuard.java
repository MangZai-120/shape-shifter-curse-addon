package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * SSCA 形态专属饰品「登录装载守卫」（jackcooper）。
 *
 * <p><b>背景</b>：全部 19 个 SSCA 饰品的 {@code canEquip} 都带形态专属校验
 * （如雪狐专属霜护符、阿努比斯之狼专属水晶）。Curios/Trinkets 在玩家登录装载
 * 已装备饰品时会重新校验 {@code canEquip}——此刻 SSC 形态数据（CCA 组件）可能
 * 尚未从存档恢复，校验误判 false → 饰品被当成「非法物品」吐出背包（重开世界
 * 饰品掉出的根因；1.20.1 Curios 链路：{@code readTag → isItemValid →
 * loseInvalidStack → handleInvalidStacks 塞回背包}）。</p>
 *
 * <p><b>方案</b>：所有专属饰品统一走 {@link #canEquip(LivingEntity, Predicate)}
 * ——实体刚生成（{@code age == 0}，登录/换维度装载瞬间）宽容放行，其余时刻执行
 * 真实形态校验。宽容放行可能放进「形态不符」的饰品，由服务端统一 tick 兜底
 * （{@link #tick}）：形态恢复后仍不符的自动卸下归还 + 红字提示（下一 tick 执行，
 * 避免在饰品框架遍历时修改饰品栏）。</p>
 *
 * <p>挂载：{@code canEquip} 由各饰品调用；{@link #tick} 由
 * {@code SscAddonServerEvents} 的玩家 tick 循环调用。Trinkets / Curios 双后端一致。</p>
 */
public final class AddonAccessoryGuard {

	private AddonAccessoryGuard() {}

	/**
	 * 专属饰品统一 canEquip 入口。
	 *
	 * @param entity  佩戴者
	 * @param formCheck 真实形态校验（形态数据就绪时的判定）
	 * @return 登录装载瞬间（age==0）宽容放行；否则按 formCheck
	 */
	public static boolean canEquip(LivingEntity entity, Predicate<LivingEntity> formCheck) {
		if (entity.age == 0) return true; // 登录/换维度装载瞬间：形态未恢复，宽容放行
		return formCheck.test(entity);
	}

	/** 主包增强版通过同一份映射执行禁戴检查，并共享登录装载宽限。 */
	public static boolean canEquipMappedAccessory(LivingEntity entity, AccessoryItem item) {
		return canEquip(entity, wearer -> !Boolean.FALSE.equals(AddonExclusiveFormCheck.check(wearer, item)));
	}

	/**
	 * 服务端统一兜底（每 tick 对每个在线玩家调用）：
	 * 扫描全部饰品槽，发现形态不符的 SSCA 专属饰品或被限制的 SSC 增强饰品 → 自动卸下归还。
	 * 扫描周期 32t（1.6s）；卸下经 server.execute 排队延迟执行（服务端线程内调用时为同 tick 尾部、
	 * 跨线程时为下一 tick，不严格等于「下一 tick」，但足以避开饰品框架遍历中的直接修改）。
	 */
	public static void tick(ServerPlayerEntity player) {
		if (player.age <= 20) return; // 登录初期形态可能仍在恢复，先观望 1 秒
		if ((player.age & 31) != 0) return; // 每 32t（1.6s）扫一次，控开销
		if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld)) return;
		Map<Pair<String, String>, List<ItemStack>> slots = AccessoryUtils.getEntitySlots(player, "auto");
		if (slots == null || slots.isEmpty()) return;
		slots.forEach((slotKey, stacks) -> {
			if (stacks == null) return;
			for (int i = 0; i < stacks.size(); i++) {
				ItemStack current = stacks.get(i);
				if (current == null || current.isEmpty()) continue;
				if (!(current.getItem() instanceof AccessoryItem acc)) continue;
				// 与 canEquip 共用映射，也覆盖先佩戴增强饰品再变为受限形态的情况。
				// 注意：return 只退出当前槽组的 forEach 回调，多个槽组可在同轮各卸一件；
				// 逐件延迟核对+归还，满背包时逐件掉落，实测物品守恒无异常
				Boolean allowed = AddonExclusiveFormCheck.check(player, acc);
				if (allowed == null || allowed) continue; // 非专属 / 形态相符 → 不管
				unequipAndReturn(player, current, slotKey, i);
				return; // 本槽组扫描结束
			}
		});
	}

	/**
	 * 卸下一件饰品并归还背包（放不下则掉落脚下），附红字提示。经 server.execute 排队执行
	 *（服务端线程内调用时为同 tick 尾部，非严格「下一 tick」，见 {@link #tick} 注释）。
	 *
	 * <p>执行时按「组名/槽名/下标」单点读回，不能用两次 {@code getEntitySlots()} 之间的
	 * Minecraft {@code Pair} 对象当键——SSC 的实现每次构造新 Pair 且该类不覆写
	 * equals/hashCode，Map 按对象身份比较，二次查找恒为 null（2026-10-15 实测修复）。</p>
	 */
	private static void unequipAndReturn(ServerPlayerEntity player, ItemStack stack,
										 Pair<String, String> slotKey, int index) {
		MinecraftServer server = player.getServer();
		if (server == null) return;
		server.execute(() -> {
			// 执行时再核对一次（期间可能已变身/已手动卸下）；按坐标单点读回，跨后端按槽名匹配
			ItemStack current = AccessoryUtils.getEntitySlot(player, "auto", slotKey.getLeft(), slotKey.getRight(), index);
			if (current == null || current.isEmpty() || current.getItem() != stack.getItem()) return;
			Boolean allowed = AddonExclusiveFormCheck.check(player, (AccessoryItem) current.getItem());
			if (allowed == null || allowed) return;
			AccessoryUtils.setEntitySlot(player, "auto", slotKey.getLeft(), slotKey.getRight(), index, ItemStack.EMPTY);
			// 写后读回确认：后端写入失败（如槽位快照失效）时不归还，防复制；下一轮扫描会重试
			ItemStack after = AccessoryUtils.getEntitySlot(player, "auto", slotKey.getLeft(), slotKey.getRight(), index);
			if (after == null || !after.isEmpty()) return;
			if (!player.getInventory().insertStack(current)) {
				player.dropItem(current, false);
			}
			player.sendMessage(
					Text.translatable("msg.my_addon.accessory_form_mismatch_removed")
							.formatted(Formatting.RED),
					true
			);
		});
	}
}
