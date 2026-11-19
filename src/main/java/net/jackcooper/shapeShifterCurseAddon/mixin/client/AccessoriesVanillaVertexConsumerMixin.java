package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Anchors the optional Accessories vertex-writer fix in SscAddonMixinConfigPlugin. */
@Pseudo
@Mixin(targets = "io.wispforest.accessories.client.MPOATVConstructingVertexConsumer", remap = false)
public abstract class AccessoriesVanillaVertexConsumerMixin {
}
