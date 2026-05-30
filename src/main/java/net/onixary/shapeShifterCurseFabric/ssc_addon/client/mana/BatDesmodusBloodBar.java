package net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

/**
 * 吸血蝙蝠形态 - 雾血资源条 HUD。
 * 仅在玩家拥有雾血资源（即处于吸血蝙蝠形态）时渲染，复用暗色调灵魂条贴图契合吸血主题。
 * 位置复用魔力条配置，吸血蝙蝠形态无魔力条，两者不冲突。
 */
@Environment(EnvType.CLIENT)
public class BatDesmodusBloodBar implements HudRenderCallback {
	private static final MinecraftClient mc = MinecraftClient.getInstance();
	private static final Identifier BarTexFullID = new Identifier("my_addon", "textures/gui/anubis_wolf_sp_soul_bar_full.png");
	private static final Identifier BarTexEmptyID = new Identifier("my_addon", "textures/gui/anubis_wolf_sp_soul_bar_empty.png");
	private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_bat_desmodus_blood_resource");

	@Override
	public void onHudRender(DrawContext context, float tickDelta) {
		if (mc.options.hudHidden || mc.player == null) return;

		PlayerEntity player = mc.player;
		int[] resourceData = PowerUtils.getClientResourceValueAndMax(player, RESOURCE_ID);
		int current = resourceData[0];
		int max = resourceData[1];
		// 非吸血蝙蝠形态无此资源 -> 不渲染
		if (current <= 0 && max <= 1) return;
		double percent = (double) current / (double) max;

		int posType = 8;
		int offsetX = 100;
		int offsetY = -17;

		try {
			Object config = ShapeShifterCurseFabric.clientConfig;
			if (config != null) {
				Class<?> configClass = config.getClass();
				posType = configClass.getField("manaBarPosType").getInt(config);
				offsetX = configClass.getField("manaBarPosOffsetX").getInt(config);
				offsetY = configClass.getField("manaBarPosOffsetY").getInt(config);
			}
		} catch (Exception e) {
			// 反射失败时使用默认值
		}

		Pair<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(
				posType,
				offsetX,
				offsetY
		);

		renderBar(context, tickDelta, pos.getLeft(), pos.getRight(), percent);
	}

	private void renderBar(DrawContext context, float tickDelta, int x, int y, double percent) {
		int barWidth = (int) Math.ceil(80 * percent);
		// 绘制空槽
		context.drawTexture(BarTexEmptyID, x, y, 0, 0, 80, 5, 80, 5);
		// 绘制已填充部分（按百分比裁剪）
		context.drawTexture(BarTexFullID, x, y, 0, 0, barWidth, 5, 80, 5);
	}
}
