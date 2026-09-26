package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.particle.AsyncParticleCompatibility;
import net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleOwnership;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(Particle.class)
public abstract class ParticleOwnershipMixin implements ParticleOwnership {
    @Unique private UUID ssca$decorationOwner;
    @Unique private boolean ssca$projectileDecoration;

    public UUID ssca$getDecorationOwner() { return ssca$decorationOwner; }

    public void ssca$setDecorationOwner(UUID owner) {
        ssca$decorationOwner = owner;
        if (owner != null) AsyncParticleCompatibility.prepare((Particle) (Object) this);
    }

    /** 弹道粒子豁免锥压制（拖尾/爆炸/火环沿视线飞行，再叠锥会把整条弹道压到 10%）。 */
    public boolean ssca$isProjectileDecoration() { return ssca$projectileDecoration; }

    public void ssca$markProjectileDecoration() { ssca$projectileDecoration = true; }
}
