package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Spell-hit transport, independent of resource refunds and their per-player cooldown. */
public final class SpellAnomalyHits {
    @FunctionalInterface
    public interface AmountResolver {
        double resolve(Spell spell, int level);
    }

    public interface ProjectileData {
        SpellAnomalyContext ssca$anomaly();
        void ssca$anomaly(SpellAnomalyContext context);
    }

    /** Summoned creatures retain the creating cast without reading their owner's current book. */
    public interface SummonedData {
        SpellAnomalyContext ssca$summonAnomaly();
        void ssca$summonAnomaly(SpellAnomalyContext context);
    }

    public static boolean acceptedStatus(LivingEntity target, net.minecraft.entity.effect.StatusEffect effect,
                                         int duration, int amplifier, boolean applied) {
        if (applied) return true;
        if (!target.isAlive() || target.isRemoved() || target.isSpectator()
                || !target.canHaveStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(effect, duration, amplifier))) return false;
        // addStatusEffect may return false solely because an equal/stronger active effect already wins.
        var existing = target.getStatusEffect(effect);
        return existing != null && (existing.getAmplifier() > amplifier
                || existing.getAmplifier() == amplifier && existing.getDuration() >= duration);
    }

    private static final ThreadLocal<SpellAnomalyContext> CURRENT = new ThreadLocal<>();
    private static final CopyOnWriteArrayList<BiConsumer<LivingEntity, SpellAnomalyContext>> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static volatile AmountResolver resolver = (spell, level) -> 0;

    private SpellAnomalyHits() {}

    public static void amountResolver(AmountResolver next) {
        resolver = Objects.requireNonNull(next);
    }

    public static AmountResolver amountResolver() { return resolver; }

    public static double resolve(Spell spell, int level) {
        double amount = resolver.resolve(spell, level);
        return Double.isFinite(amount) && amount > 0 ? amount : 0;
    }

    /** Zero-amount elements still notify listeners so they can refresh shared expiry. */
    public static AutoCloseable register(BiConsumer<LivingEntity, SpellAnomalyContext> listener) {
        LISTENERS.add(Objects.requireNonNull(listener));
        return () -> LISTENERS.remove(listener);
    }

    public static SpellAnomalyContext create(ServerPlayerEntity caster, Spell spell, int level) {
        var element = spell.getElement();
        if (element == null) return null;
        return new SpellAnomalyContext(spell.getId(), element.id, Math.max(1, level),
                resolve(spell, level), caster.getUuid(), UUID.randomUUID());
    }

    public static SpellAnomalyContext current() { return CURRENT.get(); }

    public static SpellAnomalyContext context(Entity source) {
        return source instanceof ProjectileData projectile ? projectile.ssca$anomaly() : current();
    }

    public static <T> T with(SpellAnomalyContext context, Supplier<T> action) {
        var previous = CURRENT.get();
        if (context == null) CURRENT.remove(); else CURRENT.set(context);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static void with(SpellAnomalyContext context, Runnable action) {
        with(context, () -> { action.run(); return null; });
    }

    /** Call once after the original offensive damage/status attempt has actually succeeded. */
    public static void hit(Entity source, LivingEntity target) { hit(context(source), target); }

    public static void hit(SpellAnomalyContext context, LivingEntity target) {
        if (context == null || target.getWorld().isClient) return;
        for (var listener : LISTENERS) listener.accept(target, context);
    }
}
