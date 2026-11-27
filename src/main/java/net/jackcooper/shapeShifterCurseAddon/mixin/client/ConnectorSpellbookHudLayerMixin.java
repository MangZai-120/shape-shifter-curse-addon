package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.NeoClient;
import net.jackcooper.shapeShifterCurseAddon.client.hud.SpellbookHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge draws its chat overlay separately and applies the chat-specific translation inside renderChat. */
@Pseudo
@Mixin(targets = "net.minecraftforge.client.gui.overlay.ForgeGui", remap = false)
public abstract class ConnectorSpellbookHudLayerMixin {
    @Inject(method = "renderChat", at = @At("HEAD"), remap = false)
    private void ssca$renderBeforeForgeChat(int width, int height, DrawContext context, CallbackInfo ci) {
        float tickDelta = MinecraftClient.getInstance().getTickDelta();
        SpellbookHudRenderer.renderBeforeChat(context, tickDelta);
        NeoClient.renderPixelBarBeforeChat(context, tickDelta);
    }
}
