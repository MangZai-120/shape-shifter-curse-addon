package net.jackcooper.shapeShifterCurseAddon.mixin.client;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.hud.HudScaling;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionTalents;
import net.jackcooper.shapeShifterCurseAddon.evolution.FamiliarFoxTree;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.mana.*;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin({FamiliarFoxManaBar.class,InstinctBarLikeManaBar.class,WebResourceBar.class,InstinctBarRenderer.class})
public abstract class SscHudScaleMixin {
    @WrapMethod(method="render",remap=false)
    private void ssca$scale(DrawContext context,float delta,Operation<Void> original) {
        if ((Object)this instanceof FamiliarFoxManaBar) {
            var player = MinecraftClient.getInstance().player;
            var form = player == null ? null : FormUtils.getPlayerForm(player);
            if (form != null && FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(form.getFormID())
                    && !EvolutionTalents.isActive(player, FamiliarFoxTree.NODE_MANA)) return;
        }
        // HudScaling owns both the visibility switch and scale; no second HEAD gate is needed.
        int section=((Object)this) instanceof InstinctBarRenderer || ((Object)this) instanceof InstinctBarLikeManaBar?0:1;
        HudScaling.render(context,section,()->original.call(context,delta));
    }
}
