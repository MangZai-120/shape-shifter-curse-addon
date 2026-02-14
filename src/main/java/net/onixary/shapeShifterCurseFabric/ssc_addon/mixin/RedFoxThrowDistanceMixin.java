package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ThrowablePotionItem.class)
public class RedFoxThrowDistanceMixin {

    @Unique
    private static final Identifier RED_FOX_MANA_POWER = new Identifier("my_addon", "form_familiar_fox_sp_init_mana");

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/thrown/PotionEntity;setVelocity(Lnet/minecraft/entity/Entity;FFFFF)V"))
    private void modifyThrowVelocity(PotionEntity instance, Entity entity, float pitch, float yaw, float roll, float speed, float divergence) {
        if (entity instanceof PlayerEntity player) {
            // Check if player has the specific power indicating Red Fox form
            boolean isRedFox = PowerHolderComponent.KEY.get(player).getPowers().stream()
                    .anyMatch(power -> power.getType().getIdentifier().equals(RED_FOX_MANA_POWER));
            
            if (isRedFox) {
                 speed *= 1.5F; // Increase by 50%
            }
        }
        instance.setVelocity(entity, pitch, yaw, roll, speed, divergence);
    }
}
