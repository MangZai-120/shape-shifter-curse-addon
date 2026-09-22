package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainRules;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class DomainSpell extends Spell {
	public DomainSpell() { super(new Identifier("ssc_addon", "domain"), SpellRarity.RED); }
	@Override
	protected SpellCastingRules.Profile getCustomCastingProfile(ServerPlayerEntity caster, int level, boolean solo) {
		return new SpellCastingRules.Profile(DomainRules.CHARGE_TICKS, 0, true);
	}
	@Override
	public boolean canCast(ServerPlayerEntity caster) { return DomainManager.canStart(caster); }
	@Override
	public void onChannelStarted(ServerPlayerEntity caster) { DomainManager.begin(caster); }
	@Override
	public boolean canContinueCasting(ServerPlayerEntity caster, ItemStack scroll) { return DomainManager.canContinue(caster); }
	/** 锁定点（2026-09-23 用户定稿）：黑色壳开始扩张（蓄力 200t / 第 10 秒）后不可打断，
	 * 必须释放——伤害/主动取消/长按取消/位移走超 3 格均不再断。 */
	@Override
	public boolean isLockedIn(ServerPlayerEntity caster) {
		return DomainManager.isExpanding(caster);
	}
	@Override
	public void onChannelEnded(ServerPlayerEntity caster, boolean interrupted) {
		if (interrupted) DomainManager.remove(caster);
	}
	// 2026-09-22 反馈修正：打断返还 20% CD（走统一规则，与其它法术一致），不再是 0 CD。
	@Override
	public int getInterruptedCooldown(int cooldown) {
		return net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules.interruptedCooldown(cooldown);
	}
	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) { DomainManager.activate(caster); }
	@Override
	public String getInBookTooltipKey() { return "item.ssc_addon.magic_scroll.tip_in_book_domain"; }
}