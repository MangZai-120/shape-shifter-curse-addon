package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.SpellcastClient;
import net.jackcooper.shapeShifterCurseAddon.spell.spells.LunarPhaseSpell;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class LunarTargetColorMixin {
    @Inject(method = "hasBlindnessOrDarkness", at = @At("RETURN"), cancellable = true)
    private void ssca$wrathSky(net.minecraft.client.render.Camera camera,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (net.jackcooper.shapeShifterCurseAddon.client.WrathMarkClient.addonBlindnessStrength(camera.getFocusedEntity()) > 0)
            cir.setReturnValue(true);
    }

    // Set the final buffer color after Apoli's glow powers have chosen their colors.
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void ssca$lunarColor(Entity entity, double x, double y, double z, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider consumers, CallbackInfo ci) {
        if (!(consumers instanceof OutlineVertexConsumerProvider outline)) return;
        boolean wrath = net.jackcooper.shapeShifterCurseAddon.client.WrathMarkClient.hasOutline(entity) || net.jackcooper.shapeShifterCurseAddon.client.WrathAimPreview.isTarget(entity);
        if (wrath || SpellcastClient.isLunarTarget(entity)) {
            int color = wrath ? 0xFFFFFF : LunarPhaseSpell.HIGHLIGHT_COLOR;
            outline.setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, 255);
        }
    }
}
