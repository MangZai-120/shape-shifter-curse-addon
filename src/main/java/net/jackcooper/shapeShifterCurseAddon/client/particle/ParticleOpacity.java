package net.jackcooper.shapeShifterCurseAddon.client.particle;

/** Applies opacity at the draw-call boundary, including renderers that pack vertex colours directly. */
public final class ParticleOpacity {
    private ParticleOpacity() {}

    public static void draw(OwnedDecoration particle, float visibility, Runnable geometry) {
        if (visibility <= 0) return;
        if (visibility >= 1) {
            geometry.run();
            return;
        }
        float original = particle.ssca$getRenderAlpha();
        particle.ssca$setRenderAlpha(original * visibility);
        try {
            geometry.run();
        } finally {
            // Simulation, later render passes and perspective changes always see the original alpha.
            particle.ssca$setRenderAlpha(original);
        }
    }
}
