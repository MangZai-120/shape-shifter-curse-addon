package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.hud.HudScaling;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.minecraft.client.gui.DrawContext;
import net.onixary.shapeShifterCurseFabric.render.tech.ItemStorePowerRender;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStorePowerRender.class)
public abstract class SscItemStoreScaleMixin {
    @WrapMethod(method="renderSlot",remap=false)
    private static void ssca$scaleSlot(DrawContext context,float delta,ItemStorePowerRender.itemStorePowerRenderInterface power,Operation<Void> original){
        if(SSCAddonConfig.client().showSscItemStoreHud)
            HudScaling.render(context,6,()->original.call(context,delta,power));
    }
}
