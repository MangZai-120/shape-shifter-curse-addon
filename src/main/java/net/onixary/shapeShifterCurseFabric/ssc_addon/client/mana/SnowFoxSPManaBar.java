package net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

import java.util.List;

@Environment(EnvType.CLIENT)
public class SnowFoxSPManaBar implements HudRenderCallback {
	private static final MinecraftClient mc = MinecraftClient.getInstance();

	// 近战形态纹理
	private static final Identifier MELEE_FULL = new Identifier("my_addon", "textures/gui/sp_snow_fox_mana_bar_melee_full.png");
	private static final Identifier MELEE_EMPTY = new Identifier("my_addon", "textures/gui/sp_snow_fox_mana_bar_melee_empty.png");
	// 远程形态纹理
	private static final Identifier RANGED_FULL = new Identifier("my_addon", "textures/gui/sp_snow_fox_mana_bar_ranged_full.png");
	private static final Identifier RANGED_EMPTY = new Identifier("my_addon", "textures/gui/sp_snow_fox_mana_bar_ranged_empty.png");

	private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_snow_fox_sp_resource");
	// 近战/远程切换状态：0=近战，1=远程
	private static final Identifier SWITCH_STATE_ID = new Identifier("my_addon", "form_snow_fox_sp_switch_state");

	@Override
	public void onHudRender(DrawContext context, float tickDelta) {
		if (mc.options.hudHidden || mc.player == null) return;

		PlayerEntity player = mc.player;
		VariableIntPower resourcePower = null;
		VariableIntPower switchStatePower = null;

		List<VariableIntPower> powers = PowerHolderComponent.KEY.get(player).getPowers(VariableIntPower.class);
		for (VariableIntPower power : powers) {
			Identifier id = power.getType().getIdentifier();
			if (id.equals(RESOURCE_ID)) {
				resourcePower = power;
			} else if (id.equals(SWITCH_STATE_ID)) {
				switchStatePower = power;
			}
		}

		if (resourcePower != null) {
			int current = resourcePower.getValue();
			int max = resourcePower.getMax();
			double percent = (double) current / (double) max;

			// 判断当前是近战还是远程
			boolean isRanged = switchStatePower != null && switchStatePower.getValue() == 1;

			// Default values
			int posType = 8;
			int offsetX = 100;
			int offsetY = -17;

			try {
				// 通过反射读取主模组配置，确保编译期依赖版本较低时也能兼容
				Object config = ShapeShifterCurseFabric.clientConfig;
				if (config != null) {
					Class<?> configClass = config.getClass();
					posType = configClass.getField("manaBarPosType").getInt(config);
					offsetX = configClass.getField("manaBarPosOffsetX").getInt(config);
					offsetY = configClass.getField("manaBarPosOffsetY").getInt(config);
				}
			} catch (Exception e) {
				// Use defaults if reflection fails
			}

			Pair<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(
					posType,
					offsetX,
					offsetY
			);

			renderBar(context, pos.getLeft(), pos.getRight(), percent, isRanged);
		}
	}

	private void renderBar(DrawContext context, int x, int y, double percent, boolean isRanged) {
		Identifier texFull = isRanged ? RANGED_FULL : MELEE_FULL;
		Identifier texEmpty = isRanged ? RANGED_EMPTY : MELEE_EMPTY;

		int barWidth = (int) Math.ceil(80 * percent);
		// 绘制空条
		context.drawTexture(texEmpty, x, y, 0, 0, 80, 5, 80, 5);
		// 绘制填充部分
		context.drawTexture(texFull, x, y, 0, 0, barWidth, 5, 80, 5);
	}
}
