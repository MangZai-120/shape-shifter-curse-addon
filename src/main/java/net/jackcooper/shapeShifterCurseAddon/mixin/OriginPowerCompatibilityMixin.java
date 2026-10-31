package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.compat.OriginPowerCompatibility;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.PerkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 先修复基础能力，再让SSC和现有SSCA映射恢复购买的Perk，避免覆盖专属增强。 */
@Mixin(value = PerkUtils.class, remap = false)
public abstract class OriginPowerCompatibilityMixin {
    @Inject(method = "loadAllPerk", at = @At("HEAD"))
    private static void ssca$repairMissingPowers(PlayerEntity player, Identifier tree, CallbackInfo ci) {
        OriginPowerCompatibility.beforePerks(player, tree);
    }
}
