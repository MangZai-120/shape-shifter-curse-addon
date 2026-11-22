package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneCastContext;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers;
import net.jackcooper.shapeShifterCurseAddon.util.SscIgnitedEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

public final class TaotieSovereignSpell extends LockedTargetSpell {
    public TaotieSovereignSpell() { super("taotie_sovereign"); }

    @Override protected boolean eligible(ServerPlayerEntity caster, LivingEntity target) {
        return super.eligible(caster, target) && !target.isFireImmune()
                && (!(target instanceof ServerPlayerEntity player) || !player.isCreative()
                    && caster.getServer().isPvpEnabled() && caster.shouldDamagePlayer(player));
    }

    private static int duration(int tier) {
        return RuneCastContext.current().duration((5 + tier) * 20, RuneModifiers.Stat.BURN);
    }

    @Override public SpellBacklash.Effects backlashEffects(ServerPlayerEntity caster, float power, boolean solo, int level) {
        return SpellBacklash.Effects.burning(0, duration(level));
    }

    @Override public void cast(ServerPlayerEntity caster, float power, boolean solo) { cast(caster, power, solo, 1); }
    @Override public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
        LivingEntity target = takeTarget(caster);
        if (target == null) return;
        int tier = MathHelper.clamp(level, 1, getMaxLevel());
        int ticks = duration(tier);
        boolean wasBurning = target.isOnFire();
        ignite(caster, ticks);
        ignite(target, ticks);
        var data = NeoRuntime.data(caster);
        data.taotieTier = tier;
        data.taotieUntil = caster.getServer().getOverworld().getTime() + ticks;
        NeoRuntime.dirty(caster);
        SpellAnomalyHits.hit(caster, target);
        FormCastingStyle.onSpellHit(caster, target, FormationElement.FIRE, solo ? null : ssc_addon$getRefundCastId(), wasBurning);
        var world = caster.getServerWorld();
        for (LivingEntity entity : new LivingEntity[]{caster, target}) {
            world.spawnParticles(ParticleTypes.FLAME, entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                    12, entity.getWidth() * 0.4, entity.getHeight() * 0.25, entity.getWidth() * 0.4, 0.02);
            world.playSound(null, entity.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.75f, 0.85f);
        }
    }

    private static void ignite(LivingEntity entity, int duration) {
        // A fresh vanilla burn has no attacker, including on entities previously ignited by a fox-fire ability.
        if (entity.getFireTicks() <= 0) ((SscIgnitedEntityAccessor) entity).sscAddon$setIgniterUuid(null);
        entity.setFireTicks(Math.max(entity.getFireTicks(), duration));
    }

    private static int burningTier(Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player) || !player.isAlive() || !player.isOnFire()) return 0;
        var data = NeoRuntime.data(player);
        return data.taotieUntil > player.getServer().getOverworld().getTime() ? MathHelper.clamp(data.taotieTier, 0, 5) : 0;
    }

    /** Explicit fire-school hits keep their original spell damage type and resistance rules. */
    public static float outgoingFire(Entity attacker, LivingEntity target, float amount) {
        int tier = attacker == target ? 0 : burningTier(attacker);
        return tier == 0 ? amount : amount * (1f + (tier + 1) * 0.1f);
    }

    public static float modifyFireDamage(LivingEntity target, DamageSource source, float amount) {
        if (!source.isIn(DamageTypeTags.IS_FIRE)) return amount;
        if (source.isOf(DamageTypes.ON_FIRE)) amount *= 1f - burningTier(target) * 0.1f;
        return outgoingFire(source.getAttacker(), target, amount);
    }
}
