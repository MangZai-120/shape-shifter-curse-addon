package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import java.util.HashSet;
import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.compat.DualAccessoryEquipment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStack.class, priority = 500)
abstract class AccessorySlotTooltipMixin {
    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void ssca$mergeSlotTooltips(CallbackInfoReturnable<List<Text>> cir) {
        var stack = (ItemStack) (Object) this;
        if (!DualAccessoryEquipment.manages(stack)
                || !"ssc_addon".equals(Registries.ITEM.getId(stack.getItem()).getNamespace())) return;

        var lines = cir.getReturnValue();
        var accessories = new HashSet<String>();
        for (Text line : lines) {
            String slot = ssca$singleSlot(line, "accessories.slot.tooltip.singular", "accessories.slot.");
            if (slot != null) accessories.add(slot);
        }
        if (!accessories.isEmpty()) {
            lines.removeIf(line -> accessories.contains(ssca$singleSlot(line, "curios.tooltip.slot", "curios.identifier.")));
        }
    }

    /** Both backends append the translated slot label to a translated header. */
    @Unique
    private static @Nullable String ssca$singleSlot(Text line, String headerKey, String labelPrefix) {
        Text header = ssca$unwrap(line);
        if (!(header.getContent() instanceof TranslatableTextContent title) || !headerKey.equals(title.getKey())) {
            return null;
        }
        String slot = null;
        for (Text child : header.getSiblings()) {
            Text label = ssca$unwrap(child);
            if (label.getContent() instanceof TranslatableTextContent key && key.getKey().startsWith(labelPrefix)) {
                if (slot != null) return null;
                slot = key.getKey().substring(labelPrefix.length());
                // Accessories prefixes our custom Trinkets spellbook group; Curios calls the same slot "spellbook".
                if ("trinket_group_moonlit-spellbook".equals(slot)) slot = "spellbook";
            } else if (!label.getString().isBlank()) {
                return null;
            }
        }
        return slot;
    }

    @Unique
    private static Text ssca$unwrap(Text text) {
        while (text.getSiblings().size() == 1 && (text.getContent() == TextContent.EMPTY
                || text.getContent() instanceof LiteralTextContent literal && literal.string().isEmpty())) {
            text = text.getSiblings().get(0);
        }
        return text;
    }
}
