package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.onixary.shapeShifterCurseFabric.mana.FamiliarFoxManaBar;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.jackcooper.shapeShifterCurseAddon.evolution.FamiliarFoxTree;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SSCA 进化使魔 mana 条门控：进化使魔形态下，解锁「mana 系统」节点之前不渲染 mana 条。
 *
 * <p>原因：mana 条由原版 {@link FamiliarFoxManaBar} 渲染，而原版 {@code ManaTypePower.onAdded}
 * 会无条件注册 mana（注释自承「治标不治本」），导致 power 的 {@code has_talent} condition
 * 无法阻止 mana 条显示。这里在客户端渲染入口拦截，<b>仅对进化使魔形态生效</b>，
 * 不影响原版使魔（form_familiar_fox_3）的 mana 条。</p>
 */
@Mixin(FamiliarFoxManaBar.class)
public class FamiliarFoxManaBarGateMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void ssc_addon$gateUpgradeFamiliarFoxMana(DrawContext context, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        IForm form = RegPlayerFormComponent.PLAYER_FORM.get(mc.player).nowForm;
        // 仅门控进化使魔形态；原版使魔等不受影响
        if (form == null || !FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(form.getFormID())) {
            return;
        }
        if (!RegEvolutionComponent.EVOLUTION.get(mc.player).isUnlocked(FamiliarFoxTree.NODE_MANA)) {
            ci.cancel();
        }
    }
}
