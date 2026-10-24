package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.compat.AxolotlSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.FluorescentAxolotlPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.FamiliarFoxSpPerks;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NormalPerk.class)
public abstract class AxolotlSpPerkMixin {
    @Inject(method = "onLoad", at = @At("HEAD"), cancellable = true, remap = false)
    private void ssca$mapAxolotlSpPerk(PlayerEntity player, IForm form, CallbackInfo ci) {
        if (AxolotlSpPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || FluorescentAxolotlPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || FamiliarFoxSpPerks.loadMapped((NormalPerk) (Object) this, player, form)) ci.cancel();
    }
}
