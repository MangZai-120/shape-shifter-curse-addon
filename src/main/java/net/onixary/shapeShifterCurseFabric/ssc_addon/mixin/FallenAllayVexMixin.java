package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mixin for VexEntity: gives custom "ssc_fallen_allay_vex" tagged vexes
 * custom targeting AI, follow owner when idle, and a 35-second lifetime.
 * Attack priority: marked (glowing) > non-whitelisted players > hostile mobs.
 * Vanilla vexes (without the tag) are completely unaffected.
 */
@Mixin(VexEntity.class)
public abstract class FallenAllayVexMixin extends MobEntity {

    // Access the vanilla "alive" field to disable vanilla starve countdown
    @Shadow private boolean alive;

    // Per-instance state for custom vexes (unused by vanilla vexes)
    @Unique private boolean ssc_initialized = false;
    @Unique private int ssc_remainingLife = 700; // 35 seconds
    @Unique private int ssc_targetSearchCD = 0;

    protected FallenAllayVexMixin(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    /**
     * Inject at TAIL of tick() so we run AFTER vanilla AI goal processing.
     * This lets us override the target the vanilla targetSelector may have set.
     * Vanilla ChargeTargetGoal will then use our target on the next tick.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void ssc_addon$onCustomVexTick(CallbackInfo ci) {
        if (this.getWorld().isClient()) return;

        Set<String> tags = this.getCommandTags();
        if (!tags.contains("ssc_fallen_allay_vex")) return;

        // Find owner UUID from tags
        String ownerUuidStr = null;
        for (String tag : tags) {
            if (tag.startsWith("owner:")) {
                ownerUuidStr = tag.substring(6);
                break;
            }
        }
        if (ownerUuidStr == null) return;

        ServerWorld serverWorld = (ServerWorld) this.getWorld();

        // === First-time initialization ===
        if (!ssc_initialized) {
            ssc_initialized = true;
            // Clear vanilla targeting AI (RevengeGoal, TrackOwnerTargetGoal, ActiveTargetGoal)
            // Without this, vanilla AI would target all players including the owner
            Set<net.minecraft.entity.ai.goal.PrioritizedGoal> targetGoals =
                    new HashSet<>(this.targetSelector.getGoals());
            for (net.minecraft.entity.ai.goal.PrioritizedGoal g : targetGoals) {
                this.targetSelector.remove(g.getGoal());
            }
            // Disable vanilla starve countdown (prevents unintended HP drain)
            this.alive = false;
            ssc_remainingLife = 700; // 35 seconds
        }

        // === Lifetime countdown (25s = 500 ticks) ===
        ssc_remainingLife--;
        if (ssc_remainingLife <= 0) {
            serverWorld.spawnParticles(ParticleTypes.SOUL,
                    this.getX(), this.getY() + 0.5, this.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
            this.discard();
            return;
        }

        if (!this.isAlive()) return;

        PlayerEntity owner = serverWorld.getServer().getPlayerManager()
                .getPlayer(java.util.UUID.fromString(ownerUuidStr));
        if (owner == null) return;

        // === Custom target search every 10 ticks ===
        ssc_targetSearchCD--;
        if (ssc_targetSearchCD <= 0) {
            ssc_targetSearchCD = 10;
            ssc_findAndSetBestTarget(owner, ownerUuidStr, serverWorld);
        }

        // === Follow owner when no valid target and too far ===
        LivingEntity currentTarget = this.getTarget();
        if ((currentTarget == null || !currentTarget.isAlive())
                && this.squaredDistanceTo(owner) > 25.0) {
            // Use VexMoveControl to fly toward owner
            this.getMoveControl().moveTo(owner.getX(), owner.getEyeY(), owner.getZ(), 1.0);
        }
    }

    /**
     * Find the best target for this custom vex and set it.
     * Priority (high to low): marked (glowing) targets > non-whitelisted players > non-friendly mobs.
     * Vanilla ChargeTargetGoal will then charge at the selected target.
     */
    @Unique
    private void ssc_findAndSetBestTarget(PlayerEntity owner, String ownerUuidStr, ServerWorld world) {
        Box searchBox = this.getBoundingBox().expand(16.0);
        List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, searchBox,
                e -> e != owner && e != (Object) this && e.isAlive());
        boolean whitelistEmpty = owner.getCommandTags().stream().noneMatch(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX));

        // Priority buckets: marked > non-whitelisted players > hostile mobs
        LivingEntity bestMarked = null;
        double bestMarkedDistSq = Double.MAX_VALUE;
        LivingEntity bestPlayer = null;
        double bestPlayerDistSq = Double.MAX_VALUE;
        LivingEntity bestHostile = null;
        double bestHostileDistSq = Double.MAX_VALUE;

        for (LivingEntity e : nearby) {
            // Skip owner's own vexes
            if (e instanceof VexEntity vex
                    && vex.getCommandTags().contains("owner:" + ownerUuidStr)) {
                continue;
            }
            // Skip owner's tamed entities
            if (e instanceof net.minecraft.entity.passive.TameableEntity tameable
                    && owner.getUuid().equals(tameable.getOwnerUuid())) {
                continue;
            }
            // Skip raid faction entities (pillagers, illagers, vex, etc.)
            if (e instanceof RaiderEntity || e instanceof VexEntity) {
                continue;
            }

            double distSq = this.squaredDistanceTo(e);

            // Highest priority: marked (glowing) targets
            if (e.hasStatusEffect(StatusEffects.GLOWING)) {
                if (distSq < bestMarkedDistSq) {
                    bestMarked = e;
                    bestMarkedDistSq = distSq;
                }
                continue;
            }

            // Second priority: non-whitelisted players
            if (e instanceof PlayerEntity) {
                if (whitelistEmpty) {
                    continue;
                }
                boolean isWhitelisted = owner.getCommandTags().contains(
                        AllaySPGroupHeal.WHITELIST_TAG_PREFIX + e.getUuidAsString());
                if (!isWhitelisted) {
                    if (distSq < bestPlayerDistSq) {
                        bestPlayer = e;
                        bestPlayerDistSq = distSq;
                    }
                }
                continue;
            }

            // Third priority: non-friendly mobs (hostile entities, not passive)
            if (e instanceof net.minecraft.entity.mob.HostileEntity
                    || e instanceof net.minecraft.entity.mob.SlimeEntity) {
                if (distSq < bestHostileDistSq) {
                    bestHostile = e;
                    bestHostileDistSq = distSq;
                }
            }
        }

        // Select best target by priority: marked > player > hostile
        LivingEntity best = bestMarked;
        if (best == null) best = bestPlayer;
        if (best == null) best = bestHostile;
        this.setTarget(best);
    }
}
