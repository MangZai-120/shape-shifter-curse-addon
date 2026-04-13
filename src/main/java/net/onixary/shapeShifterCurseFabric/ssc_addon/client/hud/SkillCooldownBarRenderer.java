package net.onixary.shapeShifterCurseFabric.ssc_addon.client.hud;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import me.shedaniel.autoconfig.AutoConfig;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能冷却条HUD渲染器
 * <p>
 * CD追踪策略：
 * 1. 形态切换：压制通用CD资源(SP_PRIMARY/SECONDARY_CD)的残留值，
 * 直到检测到技能触发（值偏离自然衰减）或资源归零才解除。
 * 形态专属资源（悦灵净化/群体治疗，堕灵尖啸/复仇，雪狐4技能）不压制。
 * 2. 雪狐SP：使用4个独立CD记录点，按switch_state选择对应资源直接读取。
 * 3. 百分比：通过检测资源值偏离自然衰减轨迹来捕获最大值。
 */
@Environment(EnvType.CLIENT)
public class SkillCooldownBarRenderer implements HudRenderCallback {
	private static final MinecraftClient mc = MinecraftClient.getInstance();

	// 贴图尺寸：4×20
	private static final int TEX_W = 4;
	private static final int TEX_H = 20;

	// 贴图路径
	private static final Identifier TEX_EMPTY = new Identifier("my_addon", "textures/gui/skill_cd_bar_empty.png");
	private static final Identifier TEX_FULL = new Identifier("my_addon", "textures/gui/skill_cd_bar_full.png");

	// 快捷栏尺寸
	private static final int HOTBAR_WIDTH = 182;
	private static final int HOTBAR_HEIGHT = 22;
	// CD条与快捷栏间距
	private static final int GAP = 3;
	// 技能触发偏差阈值：实际值与期望衰减值偏差超过此值视为技能触发
	private static final int DEVIATION_THRESHOLD = 2;

	// CD百分比计算：每个资源最近触发时的最大值
	private final Map<Identifier, Integer> trackedMaxValues = new HashMap<>();
	// 逐帧值追踪：用于配合tick计算期望衰减
	private final Map<Identifier, Integer> lastFrameValues = new HashMap<>();
	// 形态切换压制：仅对通用资源生效
	// 只有检测到技能触发（值偏离期望衰减）或归零才解除
	private final Map<Identifier, Integer> suppressionBaseline = new HashMap<>();

	private Identifier lastFormId = null;
	// 游戏tick追踪：计算帧间经过的tick数，用于期望衰减计算
	private long lastRenderTick = -1;
	private int ticksDelta = 1;

	@Override
	public void onHudRender(DrawContext context, float tickDelta) {
		if (mc.options.hudHidden || mc.player == null) return;

		SSCAddonConfig config = AutoConfig.getConfigHolder(SSCAddonConfig.class).getConfig();
		if (!config.showCdBar) return;

		PlayerEntity player = mc.player;

		// 计算帧间经过的tick数（用于期望衰减计算）
		if (mc.world != null) {
			long currentTick = mc.world.getTime();
			if (lastRenderTick >= 0) {
				ticksDelta = (int) (currentTick - lastRenderTick);
				if (ticksDelta < 1) ticksDelta = 1;
			}
			lastRenderTick = currentTick;
		}

		// 获取当前形态
		PlayerFormBase curForm;
		try {
			curForm = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).getCurrentForm();
		} catch (Exception e) {
			return;
		}
		if (curForm == null || curForm.FormID == null) return;

		Identifier formId = curForm.FormID;

		// 根据形态确定要显示的CD资源
		Identifier primaryCdId = FormIdentifiers.SP_PRIMARY_CD;
		Identifier secondaryCdId = FormIdentifiers.SP_SECONDARY_CD;

		if (formId.equals(FormIdentifiers.FALLEN_ALLAY_SP)) {
			primaryCdId = FormIdentifiers.FALLEN_ALLAY_VEX_CD;
			secondaryCdId = FormIdentifiers.FALLEN_ALLAY_SCREAM_CD;
		} else if (formId.equals(FormIdentifiers.ALLAY_SP)) {
			primaryCdId = FormIdentifiers.ALLAY_PURIFY_CD;
			secondaryCdId = FormIdentifiers.ALLAY_GROUP_HEAL_CD;
		} else if (formId.equals(FormIdentifiers.SNOW_FOX_SP)) {
			// 雪狐SP：根据switch_state选择对应模式的专属CD资源
			int switchState = getResourceValue(player, FormIdentifiers.SNOW_FOX_SWITCH_STATE);
			if (switchState == 1) {
				// 远程模式
				primaryCdId = FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD;
				secondaryCdId = FormIdentifiers.SNOW_FOX_RANGED_SECONDARY_CD;
			} else {
				// 近战模式（默认）
				primaryCdId = FormIdentifiers.SNOW_FOX_MELEE_PRIMARY_CD;
				secondaryCdId = FormIdentifiers.SNOW_FOX_MELEE_SECONDARY_CD;
			}
		}

		// 形态切换检测
		if (!formId.equals(lastFormId)) {
			lastFormId = formId;
			trackedMaxValues.clear();
			suppressionBaseline.clear();

			// 仅压制通用CD资源（形态专属资源属于当前形态，不压制）
			int pv = getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD);
			int sv = getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD);
			if (pv > 0) suppressionBaseline.put(FormIdentifiers.SP_PRIMARY_CD, pv);
			if (sv > 0) suppressionBaseline.put(FormIdentifiers.SP_SECONDARY_CD, sv);

			// 初始化帧值追踪，防止首帧残留值被误判为"技能触发"
			lastFrameValues.clear();
			lastFrameValues.put(primaryCdId, getResourceValue(player, primaryCdId));
			lastFrameValues.put(secondaryCdId, getResourceValue(player, secondaryCdId));
		}

		// 计算快捷栏位置
		int scaledWidth = mc.getWindow().getScaledWidth();
		int scaledHeight = mc.getWindow().getScaledHeight();
		int hotbarX = (scaledWidth - HOTBAR_WIDTH) / 2;

		// CD条Y坐标：底部对齐快捷栏底部，整体上移1像素
		int barY = scaledHeight - TEX_H - 1;

		// 主要技能CD条（快捷栏左侧）
		int leftBarX = hotbarX - GAP - TEX_W;
		renderCdBarWithNumber(context, player, primaryCdId, leftBarX, barY, true, false);

		// 次要技能CD条（快捷栏右侧）
		int rightBarX = hotbarX + HOTBAR_WIDTH + GAP;
		renderCdBarWithNumber(context, player, secondaryCdId, rightBarX, barY, false, false);
	}

	/**
	 * 渲染一个CD条及数字
	 * @param hideWhenReady 为true时，CD=0不渲染（可选CD条专用）
	 */
	private void renderCdBarWithNumber(DrawContext context, PlayerEntity player, Identifier cdId,
	                                   int x, int y, boolean isPrimary, boolean hideWhenReady) {
		int currentCd = getResourceValue(player, cdId);

		// 可选CD条：不在冷却中则不渲染
		if (hideWhenReady && currentCd <= 0) return;

		int prevFrameVal = lastFrameValues.getOrDefault(cdId, 0);
		lastFrameValues.put(cdId, currentCd);

		double cdPercent = 0.0;
		int remainSec = 0;

		// 技能触发检测：计算期望衰减值，判断实际值是否偏离自然衰减轨迹
		if (currentCd > 0) {
			int expectedCd = Math.max(0, prevFrameVal - ticksDelta);
			boolean skillTriggered = Math.abs(currentCd - expectedCd) > DEVIATION_THRESHOLD;

			// 技能触发时解除形态切换压制（无论模式是否匹配都需要）
			if (skillTriggered) {
				suppressionBaseline.remove(cdId);
			}

			// 形态切换压制检查（仅通用资源，技能触发前不显示）
			if (suppressionBaseline.containsKey(cdId)) {
				renderCdBar(context, x, y, 0.0);
				return;
			}

			// 只有CD可见时才更新trackedMax（避免隐藏期间网络抖动污染最大值）
			if (skillTriggered) {
				trackedMaxValues.put(cdId, currentCd);
			}
			int trackedMax = trackedMaxValues.getOrDefault(cdId, currentCd);
			if (currentCd > trackedMax) {
				trackedMax = currentCd;
				trackedMaxValues.put(cdId, trackedMax);
			}
			cdPercent = (double) currentCd / trackedMax;
			remainSec = (int) Math.ceil(currentCd / 20.0);
		} else {
			trackedMaxValues.remove(cdId);
			suppressionBaseline.remove(cdId);
		}

		renderCdBar(context, x, y, cdPercent);

		// 显示剩余秒数（0.75倍缩放，靠近快捷栏中间）
		SSCAddonConfig cfg = AutoConfig.getConfigHolder(SSCAddonConfig.class).getConfig();
		if (remainSec > 0 && cfg.showCdSeconds) {
			String text = String.valueOf(remainSec);
			float scale = 0.75f;
			int textW = (int) (mc.textRenderer.getWidth(text) * scale);
			int textX;
			if (isPrimary) {
				textX = x - textW - 1;
			} else {
				textX = x + TEX_W + 1;
			}
			int textY = y + (TEX_H - (int) (8 * scale)) / 2;
			context.getMatrices().push();
			context.getMatrices().translate(textX, textY, 0);
			context.getMatrices().scale(scale, scale, 1.0f);
			context.drawText(mc.textRenderer, Text.literal(text),
					0, 0, 0xFFFFFF, true);
			context.getMatrices().pop();
		}
	}

	/**
	 * 渲染一个竖向CD条
	 */
	private void renderCdBar(DrawContext context, int x, int y, double cdPercent) {
		if (cdPercent <= 0) {
			context.drawTexture(TEX_FULL, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
			return;
		}

		// 先绘制empty作为完整背景
		context.drawTexture(TEX_EMPTY, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

		if (cdPercent >= 1.0) return;

		// 计算就绪段数
		int cdSegments = (int) Math.ceil(cdPercent * 5);
		if (cdSegments > 5) cdSegments = 5;
		int readySegments = 5 - cdSegments;
		if (readySegments <= 0) return;

		int readyH = Math.round(TEX_H * readySegments / 5.0f);
		if (readyH <= 0) return;
		if (readyH > TEX_H) readyH = TEX_H;

		// 从底部向上叠加full纹理
		int fullStartY = y + (TEX_H - readyH);
		float fullUV = TEX_H - readyH;
		context.drawTexture(TEX_FULL, x, fullStartY, 0, fullUV, TEX_W, readyH, TEX_W, TEX_H);
	}

	/**
	 * 从Apoli VariableIntPower读取当前值（客户端侧）
	 */
	private int getResourceValue(PlayerEntity player, Identifier resourceId) {
		try {
			List<VariableIntPower> powers = PowerHolderComponent.KEY.get(player)
					.getPowers(VariableIntPower.class);
			for (VariableIntPower power : powers) {
				if (power.getType().getIdentifier().equals(resourceId)) {
					return power.getValue();
				}
			}
		} catch (Exception ignored) {
		}
		return 0;
	}
}
