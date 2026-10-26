package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.jackcooper.shapeShifterCurseAddon.compat.FamiliarFoxSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.UpgradeFamiliarFoxPerks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.PotionCharmItem;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PotionCharmItem.class)
public abstract class FamiliarFoxSpPotionCharmMixin {
    @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Identifier;equals(Ljava/lang/Object;)Z"))
    private boolean ssca$allowSpFamiliar(boolean original, World world, PlayerEntity player, Hand hand) {
        return original || FamiliarFoxSpPerks.canUsePotionCharm(FormUtils.getPlayerForm(player).getFormID())
                || UpgradeFamiliarFoxPerks.canUsePotionCharm(player);
    }
}
