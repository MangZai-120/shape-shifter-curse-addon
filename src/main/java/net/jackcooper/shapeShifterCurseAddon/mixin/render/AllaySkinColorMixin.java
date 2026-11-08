package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.AllaySkinColor;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AllaySkinColorMixin {
    @WrapMethod(method = "getSkinTexture")
    private Identifier ssca$colorExposedSkin(Operation<Identifier> original) {
        return AllaySkinColor.texture((AbstractClientPlayerEntity)(Object)this, original.call());
    }
}
