package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.jackcooper.shapeShifterCurseAddon.client.WrathMarkClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
abstract class WrathFogMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void ssca$wrathFog(Camera camera, BackgroundRenderer.FogType type, float distance,
                                       boolean thick, float tickDelta, CallbackInfo ci) {
        float strength = WrathMarkClient.blindnessStrength(camera.getFocusedEntity());
        if (strength <= 0 || camera.getSubmersionType() == CameraSubmersionType.LAVA
                || camera.getSubmersionType() == CameraSubmersionType.POWDER_SNOW) return;
        float range = MathHelper.lerp(strength, distance, 5f);
        boolean sky = type == BackgroundRenderer.FogType.FOG_SKY;
        RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), sky ? 0 : range * 0.25f));
        RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), sky ? range * 0.8f : range));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private static void ssca$wrathFogColor(Camera camera, float tickDelta, ClientWorld world,
                                            int viewDistance, float skyDarkness, CallbackInfo ci) {
        float strength = WrathMarkClient.blindnessStrength(camera.getFocusedEntity());
        if (strength <= 0 || camera.getSubmersionType() == CameraSubmersionType.LAVA
                || camera.getSubmersionType() == CameraSubmersionType.POWDER_SNOW) return;
        var ordinary = ((LivingEntity) camera.getFocusedEntity()).getStatusEffect(StatusEffects.BLINDNESS);
        float existing = ordinary == null ? 0 : ordinary.isInfinite() ? 1 : Math.min(1f, ordinary.getDuration() / 20f);
        if (strength <= existing) return;
        float light = (1 - strength) / (1 - existing);
        light *= light;
        red *= light;
        green *= light;
        blue *= light;
        RenderSystem.clearColor(red, green, blue, 0);
    }
}

