package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.ability.JumpKillManager;
import net.jackcooper.shapeShifterCurseAddon.ability.NineLivesManager;
import net.jackcooper.shapeShifterCurseAddon.ability.NovaSkillManager;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

/**
 * SSCA 纯否决型伤害分支（jackcooper，2026-09-09 由 SscAddonLivingEntityMixin 迁移到
 * 官方 {@link ServerLivingEntityEvents#ALLOW_DAMAGE}，先例：FluorescentDodgeHandler）。
 *
 * <p>只承接「纯否决」（返回 false = 取消本次伤害、不改数值不改伤害源）的分支：</p>
 * <ul>
 *   <li>跳蛛「跳杀」腾空期：免疫<b>已锁定目标</b>对自己的反打（其它来源照常受伤）；</li>
 *   <li>朔望九命：复活后 1s 无敌窗口内否决一切伤害；</li>
 *   <li>朔望「闪避」：概率完全免疫本次伤害（不受伤、不击退）。</li>
 * </ul>
 *
 * <p><b>注意</b>：朔望的「致死伤害触发复活 + 手动补击退」仍留在 mixin 内——复活必须发生在
 * damage 管线中段（血量结算前），ALLOW_DAMAGE 在 HEAD 之前触发时机不同且手动补刀会重入事件链；
 * 「战斗标记 / 挑衅记录 / 金沙岚回血」等非否决逻辑同样留在原 mixin（它们与伤害否决语义无关）。</p>
 */
public final class SscaDamageVetoHandler {

	private SscaDamageVetoHandler() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// 跳蛛跳杀腾空期免疫（仅服务端玩家、仅锁定目标来源）
			if (entity instanceof ServerPlayerEntity sp
					&& JumpKillManager.isLeapingAgainst(sp, source.getAttacker())) {
				return false;
			}
			// 朔望九命：复活无敌窗口 + 概率闪避
			if (entity instanceof ServerPlayerEntity nova && FormUtils.isForm(nova, FormIdentifiers.OCELOT_NOVA)) {
				if (NineLivesManager.isInvulnerable(nova)) {
					return false;
				}
				if (NovaSkillManager.rollDodge(nova)) {
					return false; // 闪避：概率免疫本次伤害（不受伤、不击退）
				}
			}
			return true;
		});
	}
}
