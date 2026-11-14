package net.jackcooper.shapeShifterCurseAddon.spell;

import java.util.List;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;

/** Spell-owned harmful payload only: never releases a spell, projectile, hit reward or propagation. */
public final class SpellBacklash {
    private SpellBacklash() {}

    public record Effects(float normalHit, int fireTicks, List<StatusEffectInstance> debuffs) {
        public static final Effects NONE = hit(0);
        public Effects { debuffs = debuffs.stream().map(StatusEffectInstance::new).toList(); }
        public static Effects hit(float damage) { return new Effects(damage, 0, List.of()); }
        public static Effects burning(float damage, int ticks) { return new Effects(damage, ticks, List.of()); }
        public static Effects debuffs(float damage, StatusEffectInstance... effects) {
            return new Effects(damage, 0, List.of(effects));
        }
    }

    /** Captured in the same rune context as the accepted cast, before cooldown/cleanup can change state. */
    public static final class Pending {
        private final Effects effects;
        private final int rarity;
        private boolean applied;

        public Pending(Effects effects, SpellRarity rarity) {
            this.effects = effects;
            this.rarity = rarity.ordinal() + 1;
        }

        public void apply(ServerPlayerEntity player, double excess) {
            if (applied) return;
            applied = true;
            if (!(excess > 0) || !Double.isFinite(excess) || !player.isAlive() || player.isSpectator()) return;
            float damage = SpellBacklashRules.damage(effects.normalHit(), excess, rarity);
            // Environmental spell damage retains defenses without being blocked by PvP/team rules or credited as a hit.
            if (damage > 0) player.damage(SpellDamageSource.of(player.getDamageSources()), damage);
            if (!player.isAlive()) return;
            for (var original : effects.debuffs()) {
                int duration = SpellBacklashRules.duration(original.getDuration(), excess, rarity);
                if (duration > 0) player.addStatusEffect(new StatusEffectInstance(original.getEffectType(), duration,
                        original.getAmplifier(), original.isAmbient(), original.shouldShowParticles(), original.shouldShowIcon()), player);
            }
            int fire = SpellBacklashRules.duration(effects.fireTicks(), excess, rarity);
            if (fire > 0) player.setFireTicks(Math.max(player.getFireTicks(), fire));
        }
    }
}
