package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.jackcooper.shapeShifterCurseAddon.event.AxolotlShifterSpawnHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
abstract class AxolotlEggSpawnMixin {
    @Inject(method = "spawnFromItemStack", at = @At("RETURN"))
    private void ssca$skipEggCompanion(ServerWorld world, ItemStack stack, PlayerEntity player,
            BlockPos pos, SpawnReason reason, boolean align, boolean invert,
            CallbackInfoReturnable<Entity> result) {
        if (reason == SpawnReason.SPAWN_EGG || (reason == SpawnReason.DISPENSER
                && stack != null && stack.getItem() instanceof SpawnEggItem)) {
            AxolotlShifterSpawnHandler.skipEggCompanion(result.getReturnValue());
        }
    }
}
