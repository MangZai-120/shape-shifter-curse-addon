package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

public final class PocketPortalGate {
	private boolean armed;
	private int ticks;

	public boolean tick(boolean insidePortal, boolean standingOnPortal) {
		if (!insidePortal) {
			armed = true;
			ticks = 0;
			return false;
		}
		if (!standingOnPortal) {
			ticks = 0;
			return false;
		}
		return armed && ++ticks >= 60;
	}

	public int remainingSeconds() {
		return (60 - ticks + 19) / 20;
	}

	public boolean counting() {
		return armed && ticks > 0;
	}
}