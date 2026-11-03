package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import org.spongepowered.asm.mixin.Mixin;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

@Mixin(value = GeoReplacedEntityRenderer.class, remap = false)
public abstract class FrozenGeoReplacedMixin {
    @WrapMethod(method = "renderRecursively")
    private void ssca$holdBone(MatrixStack matrices,GeoAnimatable animatable,GeoBone bone,RenderLayer layer,
                               VertexConsumerProvider consumers,VertexConsumer buffer,boolean rerender,float delta,
                               int light,int overlay,float r,float g,float b,float a,Operation<Void> original) {
        FrozenAnimationState.bone(bone,() -> FrozenAnimationState.matrix(bone,matrices,() -> original.call(matrices,animatable,bone,layer,consumers,buffer,
                rerender,delta,light,overlay,r,g,b,a)));
    }
}
