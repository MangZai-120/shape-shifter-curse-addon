package net.jackcooper.shapeShifterCurseAddon.ability.fruit;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;

import java.util.*;

/** Reversible fruit contributions over an independently aged vanilla-effect baseline. */
public final class FruitStatusOverlay {
    private record Stamp(StatusEffectInstance effect, long at, int layers, boolean scales) { }
    private static final class Slot {
        Stamp external;
        final Map<String, Stamp> fruit = new LinkedHashMap<>();
    }
    private static final class State {
        final Map<StatusEffect, Slot> slots = new LinkedHashMap<>();
        boolean dirty;
    }
    private record Capture(LivingEntity target, String group, int layers, Set<StatusEffect> seen) { }
    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> MUTATING = ThreadLocal.withInitial(() -> false);
    private FruitStatusOverlay() { }

    public static void pulse(LivingEntity target, String group, int layers, Runnable action) {
        Capture previous = CAPTURE.get();
        var capture = new Capture(target, group, layers, new HashSet<>());
        CAPTURE.set(capture);
        try { action.run(); }
        finally {
            if (previous == null) CAPTURE.remove(); else CAPTURE.set(previous);
            State state = STATES.get(target);
            if (state != null) {
                state.slots.forEach((effect, slot) -> {
                    if (!capture.seen().contains(effect)) slot.fruit.remove(group);
                });
                reconcile(target, state);
            }
        }
    }

    /** Called AFTER cancellable HEAD gates and argument modifiers, before vanilla merges effects. */
    public static void attempted(LivingEntity target, StatusEffectInstance effect, boolean forced) {
        if (target.getWorld().isClient || MUTATING.get() || effect == null
                || effect.getEffectType() == SscAddon.BAT_ABSORPTION) return;
        Capture capture = CAPTURE.get();
        State tracked = STATES.get(target);
        // Ordinary effects never touch fruit clocks or bookkeeping unless this type is overlaid.
        if ((capture == null || capture.target() != target)
                && (tracked == null || !tracked.slots.containsKey(effect.getEffectType()))) return;
        if (!forced && !target.canHaveStatusEffect(effect)) return;
        long now = target.getWorld().getTime();
        if (capture != null && capture.target() == target) {
            State state = STATES.computeIfAbsent(target, ignored -> new State());
            Slot slot = state.slots.computeIfAbsent(effect.getEffectType(), ignored -> {
                var value = new Slot();
                StatusEffectInstance existing = target.getStatusEffect(effect.getEffectType());
                if (existing != null) value.external = new Stamp(copy(existing), now, 0, false);
                return value;
            });
            boolean scales = !capture.group().equals("support") && !capture.group().equals("outline")
                    && (effect.getEffectType() == StatusEffects.SLOWNESS
                    || effect.getEffectType() == StatusEffects.WEAKNESS
                    || effect.getEffectType() == StatusEffects.POISON);
            slot.fruit.put(capture.group(), new Stamp(copy(effect), now, capture.layers(), scales));
            capture.seen().add(effect.getEffectType()); state.dirty = true;
        } else {
            State state = tracked;
            Slot slot = state == null ? null : state.slots.get(effect.getEffectType());
            if (slot == null) return;
            StatusEffectInstance baseline = forced ? null : aged(slot.external, now);
            if (baseline == null) baseline = copy(effect); else baseline.upgrade(copy(effect));
            slot.external = new Stamp(baseline, now, 0, false); state.dirty = true;
        }
    }

    /** Milk/purification and other explicit removals must not resurrect an external baseline. */
    public static void removed(LivingEntity target, StatusEffect effect) {
        if (target.getWorld().isClient || MUTATING.get()) return;
        State state = STATES.get(target);
        if (state == null) return;
        state.slots.remove(effect);
        if (state.slots.isEmpty()) STATES.remove(target);
    }

    public static void update(LivingEntity target, Map<String, Integer> activeGroups) {
        State state = STATES.get(target);
        if (state == null) return;
        long now = target.getWorld().getTime();
        for (Slot slot : state.slots.values()) {
            var iterator = slot.fruit.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next(); Stamp value = entry.getValue();
                int count = activeGroups.getOrDefault(entry.getKey(), 0);
                StatusEffectInstance remaining = aged(value, now);
                if (count <= 0 || remaining == null) { iterator.remove(); state.dirty = true; }
                else if (count != value.layers()) {
                    if (value.scales()) remaining = new StatusEffectInstance(remaining.getEffectType(),
                            remaining.getDuration(), Math.max(0, remaining.getAmplifier() + count - value.layers()),
                            remaining.isAmbient(), remaining.shouldShowParticles(), remaining.shouldShowIcon());
                    entry.setValue(new Stamp(remaining, now, count, value.scales())); state.dirty = true;
                }
            }
        }
        if (state.dirty) reconcile(target, state);
    }

    public static void clear(LivingEntity target) {
        State state = STATES.get(target);
        if (state == null) return;
        state.slots.values().forEach(slot -> slot.fruit.clear());
        reconcile(target, state);
    }

    private static void reconcile(LivingEntity target, State state) {
        long now = target.getWorld().getTime();
        var iterator = state.slots.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); Slot slot = entry.getValue();
            StatusEffectInstance combined = aged(slot.external, now);
            for (Stamp stamp : slot.fruit.values()) {
                StatusEffectInstance effect = aged(stamp, now);
                if (effect != null) {
                    if (combined == null) combined = effect; else combined.upgrade(effect);
                }
            }
            StatusEffectInstance current = target.getStatusEffect(entry.getKey());
            if (!same(current, combined)) {
                boolean previous = MUTATING.get(); MUTATING.set(true);
                try {
                    target.removeStatusEffect(entry.getKey());
                    if (combined != null) target.setStatusEffect(combined, null);
                } finally { MUTATING.set(previous); }
            }
            if (slot.fruit.isEmpty()) iterator.remove();
        }
        state.dirty = false;
        if (state.slots.isEmpty()) STATES.remove(target);
    }

    private static boolean same(StatusEffectInstance a, StatusEffectInstance b) {
        return a == null ? b == null : b != null && a.writeNbt(new NbtCompound()).equals(b.writeNbt(new NbtCompound()));
    }
    private static StatusEffectInstance copy(StatusEffectInstance effect) {
        return Objects.requireNonNull(StatusEffectInstance.fromNbt(effect.writeNbt(new NbtCompound())));
    }
    private static StatusEffectInstance aged(Stamp value, long now) {
        if (value == null) return null;
        NbtCompound nbt = age(value.effect().writeNbt(new NbtCompound()), Math.max(0, now - value.at()));
        return nbt == null ? null : StatusEffectInstance.fromNbt(nbt);
    }
    private static NbtCompound age(NbtCompound value, long ticks) {
        NbtCompound hidden = value.contains("HiddenEffect") ? age(value.getCompound("HiddenEffect"), ticks) : null;
        if (hidden == null) value.remove("HiddenEffect"); else value.put("HiddenEffect", hidden);
        int duration = value.getInt("Duration");
        if (duration >= 0) {
            long left = duration - ticks;
            if (left <= 0) return hidden;
            value.putInt("Duration", (int) Math.min(Integer.MAX_VALUE, left));
        }
        return value;
    }
}
