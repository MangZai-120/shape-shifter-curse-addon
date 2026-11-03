package net.jackcooper.shapeShifterCurseAddon.network.visual;

import net.minecraft.particle.ParticleEffect;

/** Relative particle batches without an allocation for every sample point. */
@FunctionalInterface
public interface SceneEmitter {
    void emit(ParticleEffect particle, double x, double y, double z, int count,
              double dx, double dy, double dz, double speed, boolean decoration, boolean projectile);
}
