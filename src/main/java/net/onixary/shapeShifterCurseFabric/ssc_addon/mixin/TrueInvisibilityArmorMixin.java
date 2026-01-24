package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class TrueInvisibilityArmorMixin {
    
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hideArmorWhenTrueInvisible(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, LivingEntity livingEntity, float f, float g, float h, float j, float k, float l, CallbackInfo ci) {
        if (livingEntity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
            ci.cancel();
        }
    }
}
