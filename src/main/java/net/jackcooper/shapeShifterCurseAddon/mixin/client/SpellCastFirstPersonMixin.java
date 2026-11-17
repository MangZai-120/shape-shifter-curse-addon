package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState;
import net.jackcooper.shapeShifterCurseAddon.client.UpgradeAxolotlSpearRenderState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** One balanced scope for SSCA's first-person casting and spear poses. */
@Mixin(HeldItemRenderer.class)
public abstract class SpellCastFirstPersonMixin {
    @WrapMethod(method = "renderFirstPersonItem")
    private void ssca$firstPersonPose(AbstractClientPlayerEntity player, float tickDelta, float pitch,
            Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, Operation<Void> original) {
        boolean spear = player != null && hand == Hand.MAIN_HAND
                && UpgradeAxolotlSpearRenderState.isCharging(player.getUuid());
        float progress = ssca$castingProgress(player);
        if (!spear && progress <= 0.0F) {
            original.call(player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, consumers, light);
            return;
        }
        matrices.push();
        try {
            // Keep the existing spear-then-casting transform order, including simultaneous states.
            if (spear) {
                item = new ItemStack(SscAddon.WATER_SPEAR);
                item.getOrCreateNbt().putInt("CustomModelData", 1);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0F));
            }
            if (progress > 0.0F) {
                matrices.translate(0.0F, 0.45F * progress, -1.1F * progress);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25.0F * progress));
                boolean right = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
                float sway = CastingVisualState.armSway(player.age + tickDelta, right);
                matrices.translate(0, 0.018F * sway * progress, 0.10F * sway * progress);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sway * progress * 4.0F));
            }
            original.call(player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, consumers, light);
        } finally {
            matrices.pop();
        }
    }

    @Unique
    private static float ssca$castingProgress(AbstractClientPlayerEntity player) {
        if (player == null || !CastingVisualState.localPlayerCasting()) return 0.0F;
        try {
            var component = RegPlayerFormComponent.PLAYER_FORM.get(player);
            if (component == null || component.nowForm == null
                    || component.nowForm.getBodyType() != PlayerFormBodyType.NORMAL) return 0.0F;
            return CastingVisualState.raiseProgress(player.getUuid());
        } catch (Exception ignored) {
            return 0.0F;
        }
    }
}
