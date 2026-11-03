package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ModelPart.class)
public abstract class FrozenModelPartMixin {
    @WrapMethod(method = "rotate")
    private void ssca$holdAttachment(MatrixStack matrices, Operation<Void> original) {
        ModelPart part = (ModelPart)(Object)this;
        FrozenAnimationState.part(part, () -> FrozenAnimationState.transform(part, matrices, () -> original.call(matrices)));
    }
    @WrapMethod(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V")
    private void ssca$holdPart(MatrixStack matrices,VertexConsumer vertices,int light,int overlay,
                               float r,float g,float b,float a,Operation<Void> original) {
        FrozenAnimationState.part((ModelPart)(Object)this,() -> original.call(matrices,vertices,light,overlay,r,g,b,a));
    }
}
