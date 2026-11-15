package net.jackcooper.shapeShifterCurseAddon.client.camera.mixin;

import net.jackcooper.shapeShifterCurseAddon.client.camera.FormCameraClientProbe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Test-only capture of vertices after the actual animation and render transforms. */
@Mixin(value = GeoRenderer.class, remap = false)
public interface CameraGeometryProbeMixin {
    @WrapMethod(method = "renderCubesOfBone")
    private void capture(MatrixStack matrices, GeoBone bone, VertexConsumer buffer, int light,
                         int overlay, float r, float g, float b, float a, Operation<Void> original) {
        original.call(matrices, bone, FormCameraClientProbe.captureEye(bone.getName(), buffer),
                light, overlay, r, g, b, a);
    }
}
