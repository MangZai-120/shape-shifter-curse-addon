package net.jackcooper.shapeShifterCurseAddon.ability;

public record KillEmpowerState(int readyTicks, int ringTicks) {
	public static final int READY = 1;
	public static final int RING = 2;
	public static final int WINDOW_TICKS = 200;

	public static boolean allowsNormalSkill(int flags, boolean primary) {
		return primary ? flags == 0 : (flags & READY) == 0;
	}

	public boolean hasReady() {
		return readyTicks > 0;
	}

	public boolean hasRing() {
		return ringTicks > 0;
	}

	public boolean usesEmpoweredSkill(boolean primary) {
		return hasReady() || primary && hasRing();
	}

	public int flags() {
		return (hasReady() ? READY : 0) | (hasRing() ? RING : 0);
	}

	public KillEmpowerState grant() {
		return hasReady() ? this : new KillEmpowerState(WINDOW_TICKS, ringTicks);
	}

	public KillEmpowerState consume() {
		return new KillEmpowerState(0, ringTicks);
	}

	public KillEmpowerState startRing(int duration) {
		return hasReady() && !hasRing() ? new KillEmpowerState(0, duration) : this;
	}

	public KillEmpowerState stopRing() {
		return new KillEmpowerState(readyTicks, 0);
	}

	public KillEmpowerState tickFromRingEffect(int remainingTicks) {
		return new KillEmpowerState(Math.max(0, readyTicks - 1), hasRing() ? Math.max(0, remainingTicks) : 0);
	}

	public static double countdownFraction(int remainingTicks, int durationTicks) {
		return Math.max(0, Math.min(1, remainingTicks / (double) Math.max(1, durationTicks)));
	}

	public static int countdownHeight(double fraction, int height) {
		return (int) Math.ceil(Math.max(0, Math.min(1, fraction)) * height);
	}
}