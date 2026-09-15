package net.jackcooper.shapeShifterCurseAddon.client.tooltip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class SpellIconTooltipComponent implements TooltipComponent {
	private static final int ICON_SIZE = 18;
	private static final int TITLE_ROW_HEIGHT = 12;
	private static final int COMPONENT_HEIGHT = 8;

	private final Identifier texture;

	public SpellIconTooltipComponent(Identifier texture) {
		this.texture = texture;
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