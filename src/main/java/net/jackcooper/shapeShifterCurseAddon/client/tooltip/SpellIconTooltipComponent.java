package net.jackcooper.shapeShifterCurseAddon.client.tooltip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class SpellIconTooltipComponent implements TooltipComponent {
	private static final int ICON_SIZE = 18;
	private static final int TITLE_ROW_HEIGHT = 12;
	private static final int COMPONENT_HEIGHT = 8;

	private final Identifier texture;

	public SpellIconTooltipComponent(Identifier texture) {
		this.texture = texture;
	}

	public static void formatTooltip(TextRenderer textRenderer, List<Text> lines, int screenWidth) {
		if (lines.isEmpty()) return;
		String padding = " ";
		while (textRenderer.getWidth(padding) < ICON_SIZE + 4) padding += " ";
		List<Text> wrapped = new ArrayList<>();
		int maxWidth = Math.max(1, Math.min(240, screenWidth - 24));
		for (int index = 0; index < lines.size(); index++) {
			Text source = index == 0 ? Text.literal(padding).append(lines.get(index)) : lines.get(index);
			if (textRenderer.getWidth(source) <= maxWidth) {
				wrapped.add(source);
				continue;
			}
			for (var ordered : textRenderer.wrapLines(source, maxWidth)) {
				var line = Text.empty();
				ordered.accept((characterIndex, style, codePoint) -> {
					line.append(Text.literal(Character.toString(codePoint)).setStyle(style));
					return true;
				});
				wrapped.add(line);
			}
		}
		lines.clear();
		lines.addAll(wrapped);
	}

	@Override
	public int getHeight() {
		return COMPONENT_HEIGHT;
	}

	@Override
	public int getWidth(TextRenderer textRenderer) {
		return ICON_SIZE;
	}

	@Override
	public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext context) {
		context.drawTexture(texture, x, y - TITLE_ROW_HEIGHT,
				ICON_SIZE, ICON_SIZE, 0, 0, 32, 32, 32, 32);
	}
}