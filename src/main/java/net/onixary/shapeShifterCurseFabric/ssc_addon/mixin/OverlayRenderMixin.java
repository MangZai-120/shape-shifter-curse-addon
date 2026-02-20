package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form_render.IPlayerEntityMixins;
import net.onixary.shapeShifterCurseFabric.player_form_render.ModelRootAccessor;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginalFurClient;
import net.onixary.shapeShifterCurseFabric.player_form_render.OriginFurModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 LivingEntityRenderer.render() 中，EntityModel.render() 之后注入，
 * 为原版玩家模型渲染 overlay 材质（皮肤叠加层）。
 * 使用比原版 SSC (99999) 更高的优先级，确保在原版之后运行。
 */
@Mixin(value = LivingEntityRenderer.class, priority = 100000)
public abstract class OverlayRenderMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected M model;

    @Shadow
    public abstract M getModel();

    @Shadow
    protected abstract boolean isVisible(T entity);

    @Shadow
    protected abstract float getAnimationCounter(T entity, float tickDelta);

    @Unique
    private int ssc_addon$getOverlay(LivingEntity entity, float whiteOverlayProgress) {
        return OverlayTexture.packUv(
                OverlayTexture.getU(whiteOverlayProgress),
                OverlayTexture.getV(entity.hurtTime > 0 || entity.deathTime > 0)
        );
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V",
                    shift = At.Shift.AFTER),
            require = 0) // require=0: 不强制要求匹配，防止与原版冲突时崩溃
    private void ssc_addon$renderOverlayTexture(T livingEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        if (!(livingEntity instanceof AbstractClientPlayerEntity aCPE)) return;
        if (aCPE.isInvisible() || aCPE.isSpectator()) return;

        // 检查原版的 isInvisible 标志（通过 IPlayerEntityMixins 接口）
        LivingEntityRenderer<?, ?> self = (LivingEntityRenderer<?, ?>) (Object) this;
        if (self instanceof IPlayerEntityMixins iPEM && iPEM.originalFur$isPlayerInvisible()) {
            return;
        }

        IPlayerEntityMixins playerMixins = (IPlayerEntityMixins) aCPE;
        int overlay = ssc_addon$getOverlay(livingEntity, this.getAnimationCounter(livingEntity, g));

        for (var fur : playerMixins.originalFur$getCurrentFurs()) {
            if (fur == null) continue;

            OriginFurModel m_Model = (OriginFurModel) fur.getGeoModel();
            var modelAccessor = (ModelRootAccessor) (PlayerEntityModel<?>) this.getModel();

            Identifier overlayTexture = m_Model.getOverlayTexture(modelAccessor.originalFur$isSlim());
            Identifier emissiveTexture = m_Model.getEmissiveTexture(modelAccessor.originalFur$isSlim());

            if (overlayTexture == null && emissiveTexture == null) continue;

            boolean bl = this.isVisible(livingEntity);
            boolean bl2 = !bl && !livingEntity.isInvisibleTo(MinecraftClient.getInstance().player);

            // 渲染 overlay 材质
            if (overlayTexture != null) {
                RenderLayer renderLayer;
                if (OriginalFurClient.isRenderingInWorld && FabricLoader.getInstance().isModLoaded("iris")) {
                    renderLayer = RenderLayer.getEntityCutoutNoCullZOffset(overlayTexture);
                } else {
                    renderLayer = RenderLayer.getEntityCutout(overlayTexture);
                }
                this.model.render(matrixStack, vertexConsumerProvider.getBuffer(renderLayer),
                        light, overlay, 1, 1, 1, bl2 ? 0.15F : 1.0F);
            }

            // 渲染 emissive (发光) overlay 材质
            if (emissiveTexture != null) {
                RenderLayer renderLayer = RenderLayer.getEntityTranslucentEmissive(emissiveTexture);
                this.model.render(matrixStack, vertexConsumerProvider.getBuffer(renderLayer),
                        light, overlay, 1, 1, 1, bl2 ? 0.15F : 1.0F);
            }

            // 重置 hidden 状态，防止影响后续的 FeatureRenderer
            var m = (PlayerEntityModel<?>) this.getModel();
            m.hat.hidden = false;
            m.head.hidden = false;
            m.body.hidden = false;
            m.jacket.hidden = false;
            m.leftArm.hidden = false;
            m.leftSleeve.hidden = false;
            m.rightArm.hidden = false;
            m.rightSleeve.hidden = false;
            m.leftLeg.hidden = false;
            m.leftPants.hidden = false;
            m.rightLeg.hidden = false;
            m.rightPants.hidden = false;
        }
    }
}
