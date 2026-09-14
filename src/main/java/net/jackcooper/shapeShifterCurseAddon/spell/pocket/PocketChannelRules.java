package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

public final class PocketChannelRules {
	public static final double SPEED_MULTIPLIER = 0.2;

	private PocketChannelRules() {}

	public static long refundCooldownEnd(long now, long end, int originalCooldown) {
		return Math.max(now, end - Math.round(Math.max(0, originalCooldown) * 0.2));
	}
}