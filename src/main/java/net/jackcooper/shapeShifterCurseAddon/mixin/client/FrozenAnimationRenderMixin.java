package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class FrozenAnimationRenderMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void ssca$entityAnchor(EntityRenderer<Entity> renderer, Entity entity, float yaw, float delta,
                                   MatrixStack matrices, VertexConsumerProvider consumers, int light, Operation<Void> original) {
        FrozenAnimationState.root(matrices, () -> original.call(renderer, entity, yaw, delta, matrices, consumers, light));
    }
    @WrapMethod(method = "render")
    private void ssca$frozenPose(Entity entity,double x,double y,double z,float yaw,float delta,
                                 MatrixStack matrices,VertexConsumerProvider consumers,int light,Operation<Void> original) {
        FrozenAnimationState.render(entity,delta,frozenDelta ->
                original.call(entity,x,y,z,yaw,frozenDelta,matrices,consumers,light));
    }
}
