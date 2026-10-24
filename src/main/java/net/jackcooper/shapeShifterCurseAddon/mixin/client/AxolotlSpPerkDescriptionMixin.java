package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.compat.AxolotlSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.FluorescentAxolotlPerks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RegPerks.class)
public abstract class AxolotlSpPerkDescriptionMixin {
    @Inject(method = "getPerkDescription", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ssca$describeSpPerk(Identifier perk, CallbackInfoReturnable<Text> cir) {
        var player = MinecraftClient.getInstance().player;
        if (player != null && (AxolotlSpPerks.isSp(FormUtils.getPlayerForm(player))
                || FluorescentAxolotlPerks.isFluorescent(FormUtils.getPlayerForm(player)))
                && "shape-shifter-curse".equals(perk.getNamespace())
                && RegPerks.getPerkTree(RegPerks.T_Axolotl3PerkTree).getAllPerks().contains(perk)) {
            String formKey = AxolotlSpPerks.isSp(FormUtils.getPlayerForm(player)) ? "axolotl_sp" : "axolotl_fluorescent";
            cir.setReturnValue(Text.translatable("perk.ssc_addon." + formKey + "." + perk.getPath() + ".description"));
        }
    }
}
