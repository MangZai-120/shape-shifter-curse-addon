package net.jackcooper.shapeShifterCurseAddon.client.particle;

/** Only explicitly identified, nonessential effects opt into first-person avoidance. */
public interface OwnedDecoration extends ParticleOwnership {
    double ssca$cameraDistance(net.minecraft.client.render.Camera camera, float tickDelta);

    float ssca$cameraVisibility(net.minecraft.client.render.Camera camera, float tickDelta);

    float ssca$getRenderAlpha();

    void ssca$setRenderAlpha(float alpha);
}
