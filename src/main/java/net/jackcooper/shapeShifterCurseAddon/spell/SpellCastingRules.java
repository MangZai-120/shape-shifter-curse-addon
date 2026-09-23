package net.jackcooper.shapeShifterCurseAddon.spell;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpellCastingRules {
	private SpellCastingRules() {}

	public enum Mode { AUTOMATIC, RELEASE, CONTINUOUS }

	public static boolean canAfford(int cost, long available) {
		return cost >= 0 && available >= cost;
	}

	public static int highestAffordableLevel(int maxLevel, long available, java.util.function.IntUnaryOperator costAtLevel) {
		for (int level = maxLevel; level >= 1; level--) {
			if (canAfford(costAtLevel.applyAsInt(level), available)) return level;
		}
		return 0;
	}

	/** 同一书/槽/档位在首按起 1 秒内三次按下沿；返回剩余次数，0 表示本次触发。 */
	public static final class TriplePress {
		private Object context;
		private long startedAt;
		private int presses;

		public int press(Object context, long now) {
			if (presses == 0 || !java.util.Objects.equals(this.context, context)
					|| now < startedAt || now - startedAt > 1000) {
				this.context = context;
				startedAt = now;
				presses = 0;
			}
			if (++presses == 3) {
				reset();
				return 0;
			}
			return 3 - presses;
		}

		public void reset() { context = null; presses = 0; }
	}

	public static final class RefundBudget {
		private final int manaCost;
		private int remaining;

		private RefundBudget(int manaCost) {
			this.manaCost = Math.max(0, manaCost);
			this.remaining = Math.round(this.manaCost * 0.5f);
		}

		public int grant(float fraction) {
			int granted = Math.min(remaining, Math.max(0, Math.round(manaCost * fraction)));
			remaining -= granted;
			return granted;
		}
	}

	public static final class RefundLedger {
		public static final long LIFETIME_TICKS = 6000;
		private record Entry(UUID owner, RefundBudget budget, long expiresAt) {}
		private final Map<UUID, Entry> entries = new HashMap<>();

		public UUID open(UUID owner, int manaCost, long now) {
			UUID castId = UUID.randomUUID();
			entries.put(castId, new Entry(owner, new RefundBudget(manaCost), now + LIFETIME_TICKS));
			return castId;
		}

		public RefundBudget find(UUID owner, UUID castId, long now) {
			Entry entry = entries.get(castId);
			return entry != null && entry.owner.equals(owner) && now < entry.expiresAt ? entry.budget : null;
		}

		public void expire(long now) { entries.values().removeIf(entry -> now >= entry.expiresAt); }
		public void clearPlayer(UUID owner) { entries.values().removeIf(entry -> entry.owner.equals(owner)); }
		public void clear() { entries.clear(); }
	}

	public static final class Progress<T> {
		private final Mode mode;
		private final int duration;
		private final int token;
		private int elapsed;
		private boolean released;
		private boolean started;
		private T target;

		public Progress(Mode mode, int duration, int token) {
			this.mode = mode;
			this.duration = Math.max(0, duration);
			this.token = token;
		}

		public void tick() { elapsed = Math.min(duration, elapsed + 1); }
		public int elapsed() { return elapsed; }
		public boolean released() { return released; }
		public boolean started() { return started; }
		public T target() { return target; }

		public boolean release(int token, T target) {
			if (this.token != token || mode != Mode.RELEASE || released || started) return false;
			this.target = target;
			released = true;
			return true;
		}

		public boolean beginEffect() {
			if (started || elapsed < duration || mode == Mode.RELEASE && !released) return false;
			started = true;
			return true;
		}
	}

	public static final class InputGuard {
		private boolean blocked;

		public void block() { blocked = true; }
		public void reset() { blocked = false; }
		public boolean consume(boolean anyPressed) {
			if (!blocked) return false;
			if (!anyPressed) blocked = false;
			return true;
		}
	}

	public record Profile(int ticks, double speedMultiplier, boolean immobilized) {
		public Profile {
			ticks = Math.max(0, ticks);
			speedMultiplier = Double.isFinite(speedMultiplier) ? Math.max(0, Math.min(1, speedMultiplier)) : 1;
		}
	}

	public enum Tier {
		INSTANT(0, 1, false),
		BASIC_1(8, 0.9, false),
		BASIC_2(16, 0.8, false),
		INTERMEDIATE_1(30, 0.65, false),
		INTERMEDIATE_2(50, 0.5, false),
		ADVANCED_1(80, 0.35, false),
		ADVANCED_2(120, 0.2, false),
		SPECIAL_1(160, 0, true),
		SPECIAL_2(240, 0, true),
		CUSTOM(20, 0.8, false);

		public final Profile profile;

		Tier(int ticks, double speedMultiplier, boolean immobilized) {
			this.profile = new Profile(ticks, speedMultiplier, immobilized);
		}

		public int defaultInterruptMode() {
			return this == INSTANT || this == BASIC_1 || this == BASIC_2 ? 0 : 3;
		}

		public static Tier byId(String id) {
			try {
				return valueOf(id.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException | NullPointerException exception) {
				return BASIC_1;
			}
		}
	}

	public static boolean allowsExternal(int mode) {
		return (mode & 1) != 0;
	}

	public static boolean allowsSelf(int mode) {
		return (mode & 2) != 0;
	}

	public static int cumulativeMana(int total, int elapsed, int duration) {
		if (duration <= 0) return Math.max(0, total);
		return (int) ((long) Math.max(0, total) * Math.max(0, Math.min(elapsed, duration)) / duration);
	}

	public static int interruptedCooldown(int fullCooldown) {
		return Math.max(0, fullCooldown) - Math.round(Math.max(0, fullCooldown) * 0.2f);
	}

	public static int summonManaLevel(int selectedLevel, boolean affinity) {
		return Math.max(1, Math.min(affinity ? 4 : 5, selectedLevel));
	}

	public static boolean naturalRegenAllowed(boolean casting, long elapsed, int delay) {
		return !casting && elapsed >= delay;
	}

	public static int curseDurationTicks(int ticks, boolean fallenAffinity, boolean spiderAffinity) {
		return Math.round(ticks * (fallenAffinity ? 1.15f : spiderAffinity ? 1.1f : 1f));
	}
}
