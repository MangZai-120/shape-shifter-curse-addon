package net.jackcooper.shapeShifterCurseAddon.client.tooltip;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Wrap item descriptions to the available GUI width while preserving translated text and styles. */
public final class AddonTooltipFormatter {
    private AddonTooltipFormatter() {}

    public static void wrap(TextRenderer renderer, List<Text> lines, int screenWidth) {
        int maxWidth = Math.max(1, Math.min(260, screenWidth - 24));
        List<Text> wrapped = new ArrayList<>();
        for (Text source : lines) {
            if (renderer.getWidth(source) <= maxWidth && !source.getString().contains("\n")) {
                wrapped.add(source);
                continue;
            }
            for (var ordered : renderer.wrapLines(source, maxWidth)) {
                var line = Text.empty();
                ordered.accept((index, style, codePoint) -> {
                    line.append(Text.literal(Character.toString(codePoint)).setStyle(style));
                    return true;
                });
                wrapped.add(line);
            }
        }
        lines.clear();
        lines.addAll(wrapped);
    }
}
