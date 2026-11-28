package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.SpUpgradeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.data.CodexData;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionNode;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 在幻形者之书的本能栏补充永久形态的进化线索，在外观栏记录已解锁的进化节点。 */
@Mixin(CodexData.class)
public class SscAddonCodexStatusMixin {

    @Inject(method = "getContentText", at = @At("RETURN"), cancellable = true, require = 0)
    private static void appendEvolutionText(CodexData.ContentType type, PlayerEntity player,
                                            CallbackInfoReturnable<Text> cir) {
        if (type != CodexData.ContentType.APPEARANCE && type != CodexData.ContentType.INSTINCTS) {
            return;
        }
        PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (component == null) {
            return;
        }
        IForm currentForm = component.nowForm;
        if (currentForm == null || currentForm.getFormID() == null) {
            return;
        }
        if (type == CodexData.ContentType.INSTINCTS) {
            if (currentForm.getFormTier() == 4 && SpUpgradeItem.getEvolutionTarget(currentForm.getFormID()) != null) {
                Text hint = Text.translatable("text.ssc_addon.evolution.book.instinct_hint");
                Text original = cir.getReturnValue();
                cir.setReturnValue(original == null || original.getString().isBlank()
                        ? hint : original.copy().append("\n\n").append(hint));
            }
            return;
        }

        // 根据当前形态选择对应的加点树；非进化形态不处理
        var route = net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry.forWorld(player.getWorld())
                .getRouteByStartForm(currentForm.getFormID());
        if (route == null) return;
        List<EvolutionNode> nodes = route.nodes;
        String baseNodeId = route.getBaseNodeId();
        if (nodes.isEmpty()) {
            return;  // route 未加载，跳过
        }

        EvolutionComponent comp = RegEvolutionComponent.EVOLUTION.get(player);
        if (comp == null) {
            return;
        }
        Text base = cir.getReturnValue();
        if (base == null) {
            return;
        }

        // 统计已解锁的可加点节点数
        int unlockableCount = route.branchChoiceLevel > 0 ? 1 : 0;
        int unlockedCount = 0;
        for (EvolutionNode node : nodes) {
            if (baseNodeId.equals(node.id) || (FormIdentifiers.UPGRADE_SNOW_FOX.equals(currentForm.getFormID()) && node.autoUnlock)) continue;
            if (!route.isBranchChoice(node)) unlockableCount++;
            if (route.isUnlocked(comp, node)) {
                unlockedCount++;
            }
        }

        MutableText dynamic = Text.empty();
        dynamic.append(base);
        dynamic.append(Text.literal("\n\n"));
        dynamic.append(Text.translatable("text.ssc_addon.evolution.book.summary_title"));
        dynamic.append(Text.literal("\n"));
        dynamic.append(Text.translatable("text.ssc_addon.evolution.book.summary_stats",
                unlockedCount, unlockableCount, comp.getPoints(), comp.getEvoLevel()));
        // 逐个已解锁节点追加书页化叙述
        for (EvolutionNode node : nodes) {
            if (baseNodeId.equals(node.id)) {
                continue;
            }
            if (!route.isUnlocked(comp, node)) {
                continue;
            }
            dynamic.append(Text.literal("\n\n\u2022 "));
            dynamic.append(Text.translatable(node.nameKey));
            dynamic.append(Text.literal("\n"));
            String bookKey = node.descKey.substring(0, node.descKey.length() - 5) + ".book";
            dynamic.append(Text.translatableWithFallback(bookKey, Text.translatable(node.descKey).getString()));
        }
        cir.setReturnValue(dynamic);
    }
}
