package net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ManaBarPos;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

@Environment(EnvType.CLIENT)
public class AllaySPManaBar implements HudRenderCallback {
	private static final MinecraftClient mc = MinecraftClient.getInstance();
	private static final Identifier BarTexFullID = new Identifier("my_addon", "textures/gui/allay_sp_mana_bar_full.png");
	private static final Identifier BarTexEmptyID = new Identifier("my_addon", "textures/gui/allay_sp_mana_bar_empty.png");
	private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_allay_sp_mana_resource");

	@Override
	public void onHudRender(DrawContext context, float tickDelta) {
		if (mc.options.hudHidden || mc.player == null) return;

		PlayerEntity player = mc.player;
		int[] resourceData = PowerUtils.getClientResourceValueAndMax(player, RESOURCE_ID);
		int current = resourceData[0];
		int max = resourceData[1];
		if (current <= 0 && max <= 1) return;
		double percent = (double) current / (double) max;

		int[] mp = ManaBarPos.get(8, 100, -17);
		int posType = mp[0];
		int offsetX = mp[1];
		int offsetY = mp[2];

		// Using standard method name seen in SnowFoxSPManaBar, assuming it exists
		Pair<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(
				posType,
				offsetX,
				offsetY
		);

		renderBar(context, tickDelta, pos.getLeft(), pos.getRight(), percent);
	}

	private void renderBar(DrawContext context, float tickDelta, int x, int y, double percent) {
		// Assuming texture width is 80 (same as Snow Fox) or use actual width?
		// Since I can't check texture width, I'll use 80 as default for compatibility with Snow Fox style
		int fullWidth = 80;
		int barWidth = (int) Math.ceil(fullWidth * percent);

		// Draw Empty
		context.drawTexture(BarTexEmptyID, x, y, 0, 0, fullWidth, 5, fullWidth, 5);
		// Draw Full (clipped)
		context.drawTexture(BarTexFullID, x, y, 0, 0, barWidth, 5, fullWidth, 5);
	}
}
