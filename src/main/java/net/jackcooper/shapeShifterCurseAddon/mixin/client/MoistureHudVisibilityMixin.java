package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.onixary.shapeShifterCurseFabric.additional_power.CustomWaterBreathingPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Moisture is a land resource too; reaching full moisture must not hide its HUD. */
@Mixin(InGameHud.class)
public abstract class MoistureHudVisibilityMixin {
    @WrapOperation(method = "renderStatusBars", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;isSubmergedIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean ssca$showFullMoisture(PlayerEntity player, TagKey<Fluid> fluid,
                                         Operation<Boolean> original) {
        return original.call(player, fluid) || fluid == FluidTags.WATER
                && PowerHolderComponent.getPowers(player, CustomWaterBreathingPower.class)
                .stream().anyMatch(Power::isActive);
    }
}
