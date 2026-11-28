package net.jackcooper.shapeShifterCurseAddon.client;

import net.jackcooper.shapeShifterCurseAddon.ability.AxolotlTailFlint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtils;

public final class AxolotlTailFlintRenderer {
    private static final String[] TAIL_TIPS = {"tail_3", "tail_2", "tail_1", "tail_0"};

    private AxolotlTailFlintRenderer() {}

    public static void pose(PlayerEntityModel<?> model, PlayerEntity player, float delta) {
        float remaining = AxolotlTailFlint.STATE.get(player).panicRemaining(player, delta);
        if (remaining <= 0) return;
        float age = AxolotlTailFlint.PANIC_TICKS - remaining;
        float weight = MathHelper.clamp(Math.min(age / 2, remaining / 4), 0, 1);
        float swing = 12 * MathHelper.sin(age * 0.78F);
        raise(model.rightArm, swing, weight);
        raise(model.leftArm, -swing, weight);
        model.head.pitch -= 9 * MathHelper.RADIANS_PER_DEGREE * weight;
        model.head.yaw += 7 * MathHelper.RADIANS_PER_DEGREE * MathHelper.sin(age * 0.65F) * weight;
        model.hat.copyTransform(model.head);
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
    }

    private static void raise(ModelPart arm, float swing, float weight) {
        // Vanilla measures pitch from the resting arm. 170 degrees puts it 80 above horizontal.
        arm.pitch = MathHelper.lerp(weight, arm.pitch, (-170 + swing) * MathHelper.RADIANS_PER_DEGREE);
        arm.yaw = MathHelper.lerp(weight, arm.yaw, 0);
        arm.roll = MathHelper.lerp(weight, arm.roll, 0);
    }

    public static void render(FormModel model, PlayerEntity player, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light) {
        if (!AxolotlTailFlint.onTail(player)) return;
        for (String name : TAIL_TIPS) {
            GeoBone tip = model.getCachedGeoBone(name);
            if (tip == null) continue;
            matrices.push();
            try {
                // SSC supplies the pre-render origin here. Match GeoObjectRenderer.preRender.
                matrices.translate(0.5F, 0.51F, 0.5F);
                followBone(matrices, tip);
                RenderUtils.translateToPivotPoint(matrices, tip);
                // The two early forms have shorter fins. Keep the lower edge touching the tip.
                double alongFin = name.equals("tail_0") || name.equals("tail_1") ? -0.27 : -0.43;
                matrices.translate(0.13, 0.19, alongFin);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(78));
                matrices.scale(0.54F, 0.54F, 0.54F);
                MinecraftClient.getInstance().getItemRenderer().renderItem(player.getOffHandStack(),
                        ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, matrices,
                        consumers, player.getWorld(), player.getId());
            } finally {
                matrices.pop();
            }
            return;
        }
    }

    private static void followBone(MatrixStack matrices, GeoBone bone) {
        if (bone.getParent() != null) followBone(matrices, bone.getParent());
        RenderUtils.prepMatrixForBone(matrices, bone);
    }
}
