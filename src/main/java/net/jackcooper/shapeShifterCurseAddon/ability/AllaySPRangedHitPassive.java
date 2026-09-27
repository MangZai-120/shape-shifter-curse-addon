package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AllaySPRangedHitPassive {
	// 远程命中被动参数：默认与原值一致；运行时从 balance 快照读取（abilities.allay_sp_ranged_hit）
	private static final int COOLDOWN_TICKS = 70;
	private static final float HEAL_AMOUNT = 1.0F;
	private static final double MANA_RESTORE_RATIO = 0.08D;
	private static final Map<UUID, Long> LAST_TRIGGER_TICK = new ConcurrentHashMap<>();

	// 阶段 5：服务端权威快照读取（快照未初始化回退默认常量）
	private static final net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader BAL =
			new net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader("abilities.allay_sp_ranged_hit");

	private AllaySPRangedHitPassive() {
		throw new UnsupportedOperationException("This class cannot be instantiated.");
	}

	public static void onDamageApplied(LivingEntity target, DamageSource source) {
		if (target.getWorld().isClient()) return;
		if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;
		if (!(source.getSource() instanceof ProjectileEntity)) return;
		if (!FormUtils.isAllaySP(player)) return;

		// 集成服关服瞬间 getServer() 可能为 null —— 防御性判空，避免 NPE
		var srv = player.getServer();
		if (srv == null) return;
		long currentTick = srv.getOverworld().getTime();
		UUID playerUuid = player.getUuid();
		Long lastTick = LAST_TRIGGER_TICK.get(playerUuid);
		if (lastTick != null && currentTick - lastTick < BAL.i("cooldown_ticks", COOLDOWN_TICKS)) return;

		LAST_TRIGGER_TICK.put(playerUuid, currentTick);
		int maxMana = net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars.maxOf(player,
				net.jackcooper.shapeShifterCurseAddon.resource.BarKeys.ALLAY_MANA);
		if (maxMana > 0) {
			int manaRestore = Math.max(1, (int) Math.round(maxMana * BAL.d("mana_restore_ratio", MANA_RESTORE_RATIO)));
			net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars.gain(player,
					net.jackcooper.shapeShifterCurseAddon.resource.BarKeys.ALLAY_MANA, manaRestore);
		}
		player.heal((float) BAL.d("heal_amount", HEAL_AMOUNT));
	}
}