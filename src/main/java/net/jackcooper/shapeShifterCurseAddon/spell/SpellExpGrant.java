package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 法术命中经验发放工具（jackcooper，2026-09-15）。
 *
 * <p>exp_mode 1/2 的挂起经验由施法管理器经 {@link Spell#ssc_addon$setPendingExp} 装入桥、
 * 法术实体（弹射物/延迟落地体）在 cast 内取走存 NBT（跨 tick 存活），命中结算时调用本类发放；
 * AOE 法术在 damage 成功后直接从桥取走（首目标取全额、后续取 0，天然幂等）。</p>
 *
 * <p>未满级书继续获得升级经验；满级书及卸书后的延迟命中经验归玩家精通，离线则不补发。</p>
 */
public final class SpellExpGrant {
	private SpellExpGrant() {
	}

	/**
	 * 命中发放入口（弹射物 / AOE 通用）。
	 *
	 * @param caster  施法者（owner；离线/非玩家则丢弃）
	 * @param expTen  待发放经验（×10 整数；≤0 直接跳过）
	 */
	public static void grant(ServerPlayerEntity caster, int expTen) {
		if (caster == null || expTen <= 0) {
			return;
		}
		ItemStack book = SpellCastManager.getEquippedBook(caster);
		PlayerSpellMastery.grant(caster, book, expTen);
	}
}
