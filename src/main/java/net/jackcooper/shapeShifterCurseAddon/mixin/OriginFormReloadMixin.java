package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.compat.OriginPowerReloadGuard;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FormUtils.class, remap = false)
public abstract class OriginFormReloadMixin {
    @WrapMethod(method = "_loadForm")
    private static void ssca$preserveDeclaredPowerInstances(PlayerEntity player, IForm form, boolean forced,
                                                           Operation<Void> original) {
        OriginPowerReloadGuard.duringReload(player, form, forced, () -> original.call(player, form, forced));
    }
}
