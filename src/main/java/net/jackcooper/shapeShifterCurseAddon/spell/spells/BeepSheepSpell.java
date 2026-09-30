package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.entity.BeepSheepEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * 羊了个羊（召唤系，白色基底，jackcooper，2026-09-29 用户定稿）：
 * 丢出一颗<b>鸡蛋式抛物线</b>的「咩弹」，命中活体后将其<b>变成一只羊</b>——
 * 外观替换为羊模型，期间无法攻击 / 无法破坏 / 无法放置方块，时长结束自动恢复。
 *
 * <p><b>目标限制</b>：Boss 类（凋灵/监守者）与大型生物（碰撞箱宽>1.2 或高>2.0，如铁傀儡）
 * 免疫，命中只弹开不生效；默认白名单——白名单为空时玩家及玩家宠物/召唤物不受影响，
 * 白名单非空时只保护名单内个体。羊还能走动（不清移动输入，只压攻击与操作）。</p>
 *
 * <p>数值外置 {@code data/ssc_addon/spells/beep_sheep.json}：
 * 0 直伤 / 20 蓝 / 35s CD（700t，L5≈20s，用户定稿「0级cd 35秒，五级20」）/ basic_1 档 0.4s 读条 /
 * interrupt 3。变羊时长 L1=6s、每级 +1.5s（L5=12s）。</p>
 */
public class BeepSheepSpell extends Spell {

	/** 基础变羊时长（tick，L1=6 秒）默认；运行时从 balance 快照读取。 */
	public static final int BASE_DURATION_TICKS = 120;
	/** 每级增加的时长（tick，+1.5 秒/级，L5=12 秒）默认；运行时从 balance 快照读取。 */
	public static final int DURATION_PER_LEVEL = 30;

	// 阶段 5：服务端权威快照读取（spells.beep_sheep；快照未初始化回退默认常量）
	private static final BalanceReader BAL = new BalanceReader("spells.beep_sheep");

	public BeepSheepSpell() {
		super(new Identifier("ssc_addon", "beep_sheep"), SpellRarity.WHITE); // NORMAL 五级品质走 JSON levels[].rarity（白→橙），基底回退白色
	}

	@Override
	public String getInBookTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_in_book_beep_sheep";
	}

	@Override
	public String getSoloTooltipKey() {
		return "item.ssc_addon.magic_scroll.tip_solo_beep_sheep";
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		// 变羊时长：6s + 1.5s/级（L1=6s、L3=9s、L5=12s），balance 快照可覆盖；召唤系亲和
		// （效果等级+1）由 FormAffinity.bonusSpellLevel 在 SpellCastManager 侧折算进 level。
		int durationTicks = BAL.i("base_duration_ticks", BASE_DURATION_TICKS)
				+ (level - 1) * BAL.i("duration_per_level", DURATION_PER_LEVEL);
		BeepSheepEntity bullet = new BeepSheepEntity(caster.getWorld(), caster);
		bullet.setSheepDuration(durationTicks);
		bullet.setSpellLevel(level);
		bullet.setRefundCastId(solo ? null : ssc_addon$getRefundCastId());
		// 朝准星方向以固定初速射出（重力由 ThrownItemEntity 自带 → 鸡蛋式抛物线；
		// 仰角补偿：在原视线仰角上再抬 12°，水平射击也有合理射程，同投掷物手感）
		var dir = caster.getRotationVec(1.0F).normalize();
		double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		double elev = (horiz == 0 ? Math.signum(dir.y) * (Math.PI / 2) : Math.atan2(dir.y, horiz)) + Math.toRadians(12.0);
		double cosE = Math.cos(elev);
		var velocity = new net.minecraft.util.math.Vec3d(
				horiz == 0 ? 0 : cosE * (dir.x / horiz),
				Math.sin(elev),
				horiz == 0 ? 0 : cosE * (dir.z / horiz)).multiply(0.9);
		bullet.setVelocity(velocity.x, velocity.y, velocity.z);
		caster.getWorld().spawnEntity(bullet);
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 0.9f, 1.4f);
	}
}
