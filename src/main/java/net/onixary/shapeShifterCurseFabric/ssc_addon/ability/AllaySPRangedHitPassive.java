package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AllaySPRangedHitPassive {
	private static final int COOLDOWN_TICKS = 70;
	private static final float HEAL_AMOUNT = 1.0F;
	private static final double MANA_RESTORE_RATIO = 0.08D;
	private static final Map<UUID, Long> LAST_TRIGGER_TICK = new ConcurrentHashMap<>();

	private AllaySPRangedHitPassive() {
		throw new UnsupportedOperationException("This class cannot be instantiated.");
	}

	public static void onDamageApplied(LivingEntity target, DamageSource source) {
		if (target.getWorld().isClient()) return;
		if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return;
		if (!(source.getSource() instanceof ProjectileEntity)) return;
		if (!FormUtils.isAllaySP(player)) return;

		long currentTick = player.getServer().getOverworld().getTime();
		UUID playerUuid = player.getUuid();
		Long lastTick = LAST_TRIGGER_TICK.get(playerUuid);
		if (lastTick != null && currentTick - lastTick < COOLDOWN_TICKS) return;

		LAST_TRIGGER_TICK.put(playerUuid, currentTick);
		int maxMana = PowerUtils.getResourceMax(player, FormIdentifiers.ALLAY_MANA_RESOURCE);
		if (maxMana > 0) {
			int manaRestore = Math.max(1, (int) Math.round(maxMana * MANA_RESTORE_RATIO));
			PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.ALLAY_MANA_RESOURCE, manaRestore);
		}
		player.heal(HEAL_AMOUNT);
	}
}