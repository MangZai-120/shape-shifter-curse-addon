package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class SscPlayerEntityRendererMixin {

    // Inject before super.render() to ensure setModelPose has run, but modify visibility before Main Model renders.
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", 
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    public void render(AbstractClientPlayerEntity player, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (component != null) {
            PlayerFormBase currentForm = component.getCurrentForm();
            if (currentForm != null && currentForm.FormID != null) {
                PlayerFormPhase phase = currentForm.getPhase();
                String path = currentForm.FormID.getPath();
                // 仅在Phase 3 (永久形态)、Phase SP (特殊形态) 或悦灵/野猫形态时隐藏原版模型
                if (phase == PlayerFormPhase.PHASE_3 || phase == PlayerFormPhase.PHASE_SP || path.contains("allay") || path.contains("ocelot")) {
                     PlayerEntityRenderer renderer = (PlayerEntityRenderer)(Object)this;
                     PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
                     
                     // Hide all parts
                     model.head.visible = false;
                     model.hat.visible = false;
                     model.body.visible = false;
                     model.rightArm.visible = false;
                     model.leftArm.visible = false;
                     model.rightLeg.visible = false;
                     model.leftLeg.visible = false;
                     model.leftSleeve.visible = false;
                     model.rightSleeve.visible = false;
                     model.leftPants.visible = false;
                     model.rightPants.visible = false;
                     model.jacket.visible = false;
                }
            }
        }
    }
}
