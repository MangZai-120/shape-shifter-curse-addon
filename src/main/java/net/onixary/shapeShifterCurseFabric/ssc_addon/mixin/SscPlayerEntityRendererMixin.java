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
                
                PlayerEntityRenderer renderer = (PlayerEntityRenderer)(Object)this;
                PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();

                // 优先判断特定形态的渲染需求

                // 1. 悦灵 (Allay) - 包括原版和SP: 保留头部和手臂，隐藏身体和腿
                // 由于Mixin是底层修改，必须在此处显式隐藏原版模型的身体/腿部，否则会造成重叠
                if (path.contains("allay")) {
                     model.body.visible = false;
                     model.jacket.visible = false;
                     model.leftLeg.visible = false;
                     model.rightLeg.visible = false;
                     model.leftPants.visible = false;
                     model.rightPants.visible = false;
                     
                     model.head.visible = true;
                     model.hat.visible = true;
                     model.rightArm.visible = true;
                     model.leftArm.visible = true;
                     model.rightSleeve.visible = true;
                     model.leftSleeve.visible = true;
                } 
                // 2. 其他完全变身 (Phase 3 或 Phase SP) - 排除 Allay
                else if ((phase == PlayerFormPhase.PHASE_3 || phase == PlayerFormPhase.PHASE_SP) && !path.contains("allay")) {
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
                // 3. 过渡形态 - 狐狸 (Fox) Phase 1 & 2
                else if (path.contains("fox") && (phase == PlayerFormPhase.PHASE_1 || phase == PlayerFormPhase.PHASE_2)) {
                     // 隐藏四肢
                     model.leftLeg.visible = false;
                     model.rightLeg.visible = false;
                     model.leftPants.visible = false;
                     model.rightPants.visible = false;
                     model.leftArm.visible = false;
                     model.rightArm.visible = false;
                     model.leftSleeve.visible = false;
                     model.rightSleeve.visible = false;
                }
                // 4. 过渡形态 - 狼 (Wolf)
                else if (path.contains("wolf")) {
                     if (phase == PlayerFormPhase.PHASE_1) {
                         // 隐藏腿部和袖子
                         model.leftLeg.visible = false;
                         model.rightLeg.visible = false;
                         model.leftPants.visible = false;
                         model.rightPants.visible = false;
                         model.leftSleeve.visible = false;
                         model.rightSleeve.visible = false;
                     } else if (phase == PlayerFormPhase.PHASE_2) {
                         // 隐藏四肢和帽子
                         model.leftLeg.visible = false;
                         model.rightLeg.visible = false;
                         model.leftPants.visible = false;
                         model.rightPants.visible = false;
                         model.leftArm.visible = false;
                         model.rightArm.visible = false;
                         model.leftSleeve.visible = false;
                         model.rightSleeve.visible = false;
                         model.hat.visible = false;
                     }
                }
                // 5. 过渡形态 - 蝙蝠 (Bat) Phase 2
                else if (path.contains("bat") && phase == PlayerFormPhase.PHASE_2) {
                     // 隐藏四肢
                     model.leftLeg.visible = false;
                     model.rightLeg.visible = false;
                     model.leftPants.visible = false;
                     model.rightPants.visible = false;
                     model.leftArm.visible = false;
                     model.rightArm.visible = false;
                     model.leftSleeve.visible = false;
                     model.rightSleeve.visible = false;
                }
                // 6. 过渡形态 - 野猫 (Ocelot) Phase 2
                else if (path.contains("ocelot") && phase == PlayerFormPhase.PHASE_2) {
                     // 隐藏腿部和袖子 (同狼 Phase 1)
                     model.leftLeg.visible = false;
                     model.rightLeg.visible = false;
                     model.leftPants.visible = false;
                     model.rightPants.visible = false;
                     model.leftSleeve.visible = false;
                     model.rightSleeve.visible = false;
                }
            }
        }
    }
}
