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
    // Set the final buffer color after Apoli's glow powers have chosen their colors.
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void ssca$lunarColor(Entity entity, double x, double y, double z, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider consumers, CallbackInfo ci) {
        if (SpellcastClient.isLunarTarget(entity) && consumers instanceof OutlineVertexConsumerProvider outline) {
            int color = LunarPhaseSpell.HIGHLIGHT_COLOR;
            outline.setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, 255);
        }
    }
}
