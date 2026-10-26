package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.compat.AxolotlSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.UpgradeAxolotlPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.FluorescentAxolotlPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.FamiliarFoxSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.UpgradeFamiliarFoxPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.SnowFoxSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.WindSpiritPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.NovaPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.AnubisWolfSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.GoldenSandstormPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.BatDesmodusPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.BatParasiticFruitPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.SpiderMoonWeaverPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.SpiderSalticidaePerks;
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
                || UpgradeAxolotlPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || FluorescentAxolotlPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || FamiliarFoxSpPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || UpgradeFamiliarFoxPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || SnowFoxSpPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || WindSpiritPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || NovaPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || AnubisWolfSpPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || GoldenSandstormPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || BatDesmodusPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || BatParasiticFruitPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || SpiderMoonWeaverPerks.loadMapped((NormalPerk) (Object) this, player, form)
                || SpiderSalticidaePerks.loadMapped((NormalPerk) (Object) this, player, form)) ci.cancel();
    }
}
