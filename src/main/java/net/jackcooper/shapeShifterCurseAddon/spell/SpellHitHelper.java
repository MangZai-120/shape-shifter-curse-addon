package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * 法术命中结算辅助（jackcooper，2026-09-23 抽取）：
 * 原先多处（弹射物 onEntityHit + AOE 循环体）各自复制同一段
 * 「白名单豁免 → 法术伤害 → 经验补发」20 行，任何一处漏改即行为分叉
 * （SpellCurseMarkEntity 就曾因不带这段而与其它实体不一致）。统一收敛到本类。
 *
 * <p><b>钩子调用的两种形态</b>（{@link FormCastingStyle#onSpellHit}）：</p>
 * <ul>
 * <li><b>逐目标</b>（ICE/VOID/LUNAR 等无击杀分支的系别）：命中即调 → 用 {@link #projectileHit}；
 *     防重窗 20t 自动挡掉 AOE 内的重复返还，与原循环内逐目标调用等价。</li>
 * <li><b>聚合</b>（FIRE 系：燎原需要「击杀目标优先 + 燃烧目标聚合」判定）：循环外只调一次 →
 *     循环内用 {@link #hitRaw}（不带钩子），聚合钩子保留在调用方。</li>
 * </ul>
 *
 * <p>各法术的差异化逻辑（点燃/缓速/击退/穿刺等）仍留在调用方——本类只管
 * 「白名单 + 伤害 + 经验」公共骨架。</p>
 */
public final class SpellHitHelper {

	private SpellHitHelper() {}

	/** 结算结果：{@code HIT} 已造成伤害 / {@code PROTECTED} 白名单豁免 / {@code IMMUNE} 伤害未生效（无敌帧等）。 */
	public enum HitResult { HIT, PROTECTED, IMMUNE }

	/**
	 * 弹射物 / 逐目标 AOE 命中结算（白名单豁免 → 法术伤害 → 经验补发 → 流派钩子）。
	 *
	 * @param owner        攻击来源（弹射物主人或 AOE 施法者；非玩家时经验/钩子自动跳过）
	 * @param target       被命中目标
	 * @param damage       伤害值
	 * @param element      法术系别（流派钩子用）
	 * @param refundCastId 书内施法编号（solo 为 null）
	 * @param expBounty    待补发经验（×10）；AOE 循环内传 {@code ssc_addon$takePendingExp()}（幂等清零），
	 *                     弹射物传字段值并在返回 HIT 后清零字段防重复
	 */
	public static HitResult projectileHit(Entity owner, LivingEntity target, float damage,
	                                      FormationElement element, UUID refundCastId, int expBounty) {
		HitResult result = hitRaw(owner, target, damage, expBounty);
		if (result != HitResult.HIT) return result;
		// 流派命中钩子（2026-09-17）：燎原/噬咒/噬梦按本次耗蓝返还；固定值类（审魂等）不依赖耗蓝
		if (owner instanceof ServerPlayerEntity styleOwner) {
			FormCastingStyle.onSpellHit(styleOwner, target, element, refundCastId);
		}
		return result;
	}

	/**
	 * 基础命中结算（白名单豁免 → 法术伤害 → 经验补发，<b>不调流派钩子</b>）。
	 * 供需要聚合钩子的场合（FIRE 系击杀/燃烧判定）在循环内使用，钩子由调用方循环外聚合调用。
	 */
	public static HitResult hitRaw(Entity owner, LivingEntity target, float damage, int expBounty) {
		// 默认白名单：主人在线且目标受保护 → 不造成伤害、不产经验/返还
		if (owner instanceof ServerPlayerEntity ownerPlayer && WhitelistUtils.isProtected(ownerPlayer, target)) {
			return HitResult.PROTECTED;
		}
		boolean damaged;
		if (owner instanceof LivingEntity livingOwner) {
			// 法术伤害专用类型（ssc_addon:spell_damage）：供法术抗性附魔精确识别（jackcooper）
			damaged = target.damage(SpellDamageSource.of(target.getWorld().getDamageSources(), livingOwner), damage);
		} else {
			damaged = target.damage(SpellDamageSource.of(target.getWorld().getDamageSources()), damage);
		}
		// exp_mode 1/2 命中补发：damage 成功才发放（幂等：AOE 循环内 takePendingExp 后续为 0）
		if (damaged && owner instanceof ServerPlayerEntity ownerPlayer) {
			SpellExpGrant.grant(ownerPlayer, expBounty);
		}
		return damaged ? HitResult.HIT : HitResult.IMMUNE;
	}
}
