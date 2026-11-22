package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import java.util.Optional;
import net.jackcooper.shapeShifterCurseAddon.event.AxolotlShifterSpawnHandler;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnEggItem.class)
abstract class AxolotlBabyEggMixin {
    // Using an egg on a parent calls createChild directly, bypassing spawnFromItemStack.
    @Inject(method = "spawnBaby", at = @At("RETURN"))
    private void ssca$skipEggCompanion(CallbackInfoReturnable<Optional<MobEntity>> result) {
        result.getReturnValue().ifPresent(AxolotlShifterSpawnHandler::skipEggCompanion);
    }
}
