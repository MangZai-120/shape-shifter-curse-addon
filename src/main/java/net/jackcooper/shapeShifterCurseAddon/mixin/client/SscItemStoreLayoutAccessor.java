package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.onixary.shapeShifterCurseFabric.render.tech.ItemStorePowerRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStorePowerRender.class)
public interface SscItemStoreLayoutAccessor {
    @Accessor(value="NowCol",remap=false)
    static int ssca$columns(){throw new AssertionError();}
    @Accessor(value="NowRow",remap=false)
    static int ssca$rows(){throw new AssertionError();}
}
