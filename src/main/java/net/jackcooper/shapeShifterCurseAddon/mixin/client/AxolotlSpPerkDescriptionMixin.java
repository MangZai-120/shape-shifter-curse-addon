package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.compat.AxolotlSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.FluorescentAxolotlPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.FamiliarFoxSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.SnowFoxSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.WindSpiritPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.NovaPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.AnubisWolfSpPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.GoldenSandstormPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.BatDesmodusPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.BatParasiticFruitPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.SpiderMoonWeaverPerks;
import net.jackcooper.shapeShifterCurseAddon.compat.SpiderSalticidaePerks;
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
        if (player != null && SpiderSalticidaePerks.isSupported(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_Spider3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.spider_salticidae." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && SpiderMoonWeaverPerks.isSupported(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_Spider3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.spider_moon_weaver." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && BatParasiticFruitPerks.isSupported(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_Bat3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.bat_parasitic_fruit." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && BatDesmodusPerks.isSupported(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_Bat3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.bat_desmodus." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && GoldenSandstormPerks.isSupported(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_AnubisWolf3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.golden_sandstorm_sp." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && AnubisWolfSpPerks.isSupported(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_AnubisWolf3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.anubis_wolf_sp." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && NovaPerks.isNova(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_Ocelot3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.ocelot_nova." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && WindSpiritPerks.isWindSpirit(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_Ocelot3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.wind_spirit." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && SnowFoxSpPerks.isSupported(FormUtils.getPlayerForm(player))
                && RegPerks.P_SnowFoxFrostDive.equals(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.snow_fox_sp.snow_fox_frost_dive.description"));
            return;
        }
        if (player != null && FamiliarFoxSpPerks.isSupported(FormUtils.getPlayerForm(player))
                && "shape-shifter-curse".equals(perk.getNamespace())
                && RegPerks.getPerkTree(RegPerks.T_FamiliarFox3PerkTree).getAllPerks().contains(perk)) {
            String formKey = FormUtils.getPlayerForm(player).getFormID().getPath();
            cir.setReturnValue(Text.translatable("perk.ssc_addon." + formKey + "." + perk.getPath() + ".description"));
            return;
        }
        if (player != null && (AxolotlSpPerks.isSp(FormUtils.getPlayerForm(player))
                || FluorescentAxolotlPerks.isFluorescent(FormUtils.getPlayerForm(player)))
                && "shape-shifter-curse".equals(perk.getNamespace())
                && RegPerks.getPerkTree(RegPerks.T_Axolotl3PerkTree).getAllPerks().contains(perk)) {
            String formKey = AxolotlSpPerks.isSp(FormUtils.getPlayerForm(player)) ? "axolotl_sp" : "axolotl_fluorescent";
            cir.setReturnValue(Text.translatable("perk.ssc_addon." + formKey + "." + perk.getPath() + ".description"));
        }
    }

    @Inject(method = "getPerkName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ssca$nameGoldenPerk(Identifier perk, CallbackInfoReturnable<Text> cir) {
        var player = MinecraftClient.getInstance().player;
        if (player != null && GoldenSandstormPerks.isSupported(FormUtils.getPlayerForm(player))
                && RegPerks.getPerkTree(RegPerks.T_AnubisWolf3PerkTree).getAllPerks().contains(perk)) {
            cir.setReturnValue(Text.translatable("perk.ssc_addon.golden_sandstorm_sp." + perk.getPath() + ".name"));
        }
    }
}
