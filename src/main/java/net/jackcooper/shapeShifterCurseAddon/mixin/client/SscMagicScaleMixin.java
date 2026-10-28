package net.jackcooper.shapeShifterCurseAddon.mixin.client;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.hud.HudScaling;
import net.minecraft.client.gui.DrawContext;
import net.onixary.shapeShifterCurseFabric.magic.MagicGUIRender;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(MagicGUIRender.class)
public abstract class SscMagicScaleMixin {
    @WrapMethod(method="render",remap=false)
    private static void ssca$scale(DrawContext context,float delta,Operation<Void> original) {
        HudScaling.render(context,5,()->original.call(context,delta));
    }
}
