package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerCast;
import net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerManager;
import net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerState;
import net.jackcooper.shapeShifterCurseAddon.ability.MancianimaMarkClientState;
import net.jackcooper.shapeShifterCurseAddon.ability.MancianimaMarkManager;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class SkillCooldownBarRenderer implements HudRenderCallback {
	private static final MinecraftClient mc = MinecraftClient.getInstance();

	private static final Identifier TEX_PANEL = new Identifier("my_addon", "textures/gui/skill_cd_panel.png");
	private static final Identifier TEX_PANEL_RIGHT = new Identifier("my_addon", "textures/gui/skill_cd_panel_right.png");
	private static final String SSCA_FORM_NAMESPACE = "my_addon";
	public static final int ICON_SIZE = 20;
	public static final int SLOT_WIDTH = 33;
	public static final int SLOT_HEIGHT = 34;
	public static final int SLOT_STEP = 34;
	public static final int PANEL_HEIGHT = 68;
	private static final int INTERNAL_HEIGHT = 24;
	private final Map<Identifier, Integer> trackedMaxValues = new HashMap<>();
	/** readInternalReady 的按 tick 缓存（skillId → 0~1；同一 tick 内帧间直读，免每帧 getPower 扫描）。 */
	private final Map<Identifier, Double> internalReadyCache = new HashMap<>();
	private long internalCacheTick = Long.MIN_VALUE;
	private Identifier lastFormId;
	private PlayerEntity lastPlayer;
	private Object lastWorld;

	@Override
	public void onHudRender(DrawContext context, float tickDelta) {
		PlayerEntity player = mc.player;
		if (player != lastPlayer || mc.world != lastWorld) {
			trackedMaxValues.clear();
			lastFormId = null;
			lastPlayer = player;
			lastWorld = mc.world;
		}
		if (player == null || mc.world == null) return;
		IForm form = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;
		Identifier formId = form == null ? null : form.getFormID();
		if (!java.util.Objects.equals(formId, lastFormId)) {
			trackedMaxValues.clear();
			lastFormId = formId;
		}
		if (formId == null || !SSCA_FORM_NAMESPACE.equals(formId.getNamespace())) return;
		SSCAddonClientConfig config = SSCAddonConfig.client();
		if (mc.options.hudHidden || !config.showCdBar) return;
		config.migrateSkillHudLayout();
		var skills = SkillHudCatalog.forHud(formId, player);
		if (skills.isEmpty()) return;
		var anchor = net.onixary.shapeShifterCurseFabric.util.UIPositionUtils
				.getCorrectPosition(config.cdBarPosType, 0, 0);
		int width = mc.getWindow().getScaledWidth();
		int height = mc.getWindow().getScaledHeight();
		Layout layout = panelLayout(anchor.getLeft() + config.cdBarPosOffsetX,
				anchor.getRight() + config.cdBarPosOffsetY,
				config.cdMirrorRight, width, height);
		drawPanel(context, layout.primaryX(), layout.primaryY(), config.cdMirrorRight);
		for (var skill : skills) {
			int x = skill.primary() ? layout.primaryX() : layout.secondaryX();
			int y = skill.primary() ? layout.primaryY() : layout.secondaryY();
			Cooldown cooldown = readCooldown(player, skill.cooldown());
			double internalReady = readInternalReady(player, skill);
			// 释放条件未满足 → 半透明黑色遮罩；遮罩期间跳过 CD 渐变阴影，只保留倒计时数字
			boolean conditionBlocked = skill.condition() != null && !skill.condition().test(player);
			if (formId.equals(FormIdentifiers.SNOW_FOX_FROSTSPINE) && !skill.primary()) {
				// 凝棘（次技能）蓄力进度：读每 tick 缓存的法阵实体 PROGRESS（服务端权威，0-100 tick）
				// 无缓存值（未蓄力/已被强停）= -1，侧边条不显示
				internalReady = net.jackcooper.shapeShifterCurseAddon.client.ClientTickCache.frostForgeProgress();
			}
			if (formId.equals(FormIdentifiers.AXOLOTL_FLUORESCENT) && skill.primary()
					// 海晶吊坠佩戴判定走每 tick 缓存（装备不逐帧变化）
					&& !net.jackcooper.shapeShifterCurseAddon.client.ClientTickCache.isWearing(
						net.jackcooper.shapeShifterCurseAddon.SscAddon.SEA_CRYSTAL_PENDANT)) {
				internalReady = -1;
			}
			if (formId.equals(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA) && skill.primary()) {
				// 契灵主技能辅助栏：只显 3 秒烙印稳定门（黄标→升红、红标→引爆）；5s 标记 CD 由主图标数字/渐变显示
				double locked = Math.max(0, MancianimaMarkClientState.getStageEndTick() - mc.world.getTime())
					/ (double) MancianimaMarkManager.STAGE_GATE_TICKS;
				internalReady = 1 - Math.min(1, locked);
			}
			double cooldownShade = cooldown.fraction();
			int cooldownSeconds = (int) Math.ceil(cooldown.remaining() / 20.0);
			boolean countdown = false;
			boolean empowerForm = formId.equals(FormIdentifiers.FAMILIAR_FOX_SP) || formId.equals(FormIdentifiers.FAMILIAR_FOX_RED);
			if (empowerForm) {
				int empowerState = PowerUtils.getClientResourceValue(player, FormIdentifiers.EMPOWER_STATE);
				boolean empowered = (empowerState & KillEmpowerManager.STATE_READY) != 0;
				boolean empowerRing = (empowerState & KillEmpowerManager.STATE_RING) != 0;
				boolean ringActive = skill.primary() && (KillEmpowerCast.isNormalRingActive(player)
						|| empowerRing);
				if (skill.primary()) {
					Cooldown activation = readCooldown(player, skill.internalCooldown());
					if (activation.remaining() > cooldown.remaining()) {
						cooldownShade = activation.fraction();
						cooldownSeconds = (int) Math.ceil(activation.remaining() / 20.0);
					}
				}
				if (ringActive || empowered) {
					conditionBlocked = false;
				}
				if (empowered) {
					countdown = true;
					int empowerTicks = PowerUtils.getClientResourceValue(player, FormIdentifiers.EMPOWER_TICKS);
					cooldownShade = 0;
					cooldownSeconds = 0;
					internalReady = KillEmpowerState.countdownFraction(empowerTicks, KillEmpowerManager.EMPOWER_TICKS);
				}
				if (skill.primary() && empowerRing) {
					countdown = true;
					int ringTicks = PowerUtils.getClientResourceValue(player, FormIdentifiers.EMPOWER_RING_TICKS);
					int ringMax = PowerUtils.getClientResourceValue(player, FormIdentifiers.EMPOWER_RING_DURATION);
					internalReady = KillEmpowerState.countdownFraction(ringTicks, ringMax);
				}
				if (ringActive) {
					cooldownShade = 1;
					cooldownSeconds = 0;
				}
			}
			drawSkillSlot(context, skill.resolveIcon(player), x, y,
					conditionBlocked ? 0 : cooldownShade,
					cooldownSeconds,
					internalReady, skill.primary(), config.showCdSeconds,
					config.cdMirrorRight, conditionBlocked, countdown);
		}
	}

	private double readInternalReady(PlayerEntity player, SkillHudCatalog.Skill skill) {
		Identifier id = skill.internalCooldown();
		if (id == null || skill.internalTicks() <= 0 || !PowerTypeRegistry.contains(id)) return -1;
		io.github.apace100.apoli.power.PowerType<?> type = PowerTypeRegistry.get(id);
		// getPower 的 O(power数) 扫描按 (skillId, tick) 缓存：进度每 tick 变化，帧间直读
		long tick = player.getWorld().getTime();
		if (tick != internalCacheTick) {
			internalCacheTick = tick;
			internalReadyCache.clear();
		}
		double hit = internalReadyCache.getOrDefault(id, Double.NaN);
		if (!Double.isNaN(hit)) return hit;
		Power power = PowerHolderComponent.KEY.get(player).getPower(type);
		double value;
		if (!(power instanceof CooldownPower) && !(power instanceof VariableIntPower)) {
			value = -1;
		} else {
			value = 1.0 - readCooldown(player, id).remaining() / (double) skill.internalTicks();
		}
		internalReadyCache.put(id, value);
		return value;
	}

	public record Layout(int primaryX, int primaryY, int secondaryX, int secondaryY) {}

	public static Layout panelLayout(int x, int y, boolean mirrorRight, int width, int height) {
		x = clampPosition(x, width, SLOT_WIDTH);
		if (mirrorRight) x = Math.max(0, width - x - SLOT_WIDTH);
		y = clampPosition(y, height, PANEL_HEIGHT);
		return new Layout(x, y, x, y + SLOT_STEP);
	}

	private static int clampPosition(int position, int screenSize, int size) {
		return Math.max(0, Math.min(Math.max(0, screenSize - size), position));
	}

	private record Cooldown(int remaining, double fraction) {}

	private Cooldown readCooldown(PlayerEntity player, Identifier id) {
		if (id == null || !PowerTypeRegistry.contains(id)) return new Cooldown(0, 0);
		io.github.apace100.apoli.power.PowerType<?> type = PowerTypeRegistry.get(id);
		Power power = PowerHolderComponent.KEY.get(player).getPower(type);
		if (power instanceof CooldownPower cooldown) {
			int remaining = Math.max(0, cooldown.getRemainingTicks());
			return new Cooldown(remaining, remaining / (double) Math.max(1, cooldown.cooldownDuration));
		}
		int remaining = power instanceof VariableIntPower resource ? Math.max(0, resource.getValue()) : 0;
		if (remaining == 0) {
			trackedMaxValues.remove(id);
			return new Cooldown(0, 0);
		}
		int maximum = trackedMaxValues.merge(id, remaining, Math::max);
		return new Cooldown(remaining, remaining / (double) maximum);
	}

	public static void drawPanel(DrawContext context, int x, int y, boolean mirrorRight) {
		context.drawTexture(mirrorRight ? TEX_PANEL_RIGHT : TEX_PANEL, x, y, 0, 0,
				SLOT_WIDTH, PANEL_HEIGHT, SLOT_WIDTH, PANEL_HEIGHT);
		int barX = x + (mirrorRight ? 29 : 2);
		context.fill(barX, y + 8, barX + 2, y + 32, 0xFF8B8B8B);
		context.fill(barX, y + 36, barX + 2, y + 60, 0xFF8B8B8B);
	}

	public static void drawSkillSlot(DrawContext context, Identifier icon, int x, int y,
	                                double cooldownFraction, int seconds, double internalReadyFraction,
	                                boolean primary, boolean showSeconds, boolean mirrorRight) {
		drawSkillSlot(context, icon, x, y, cooldownFraction, seconds, internalReadyFraction,
				primary, showSeconds, mirrorRight, false);
	}

	public static void drawSkillSlot(DrawContext context, Identifier icon, int x, int y,
	                                double cooldownFraction, int seconds, double internalReadyFraction,
	                                boolean primary, boolean showSeconds, boolean mirrorRight,
	                                boolean conditionBlocked) {
		drawSkillSlot(context, icon, x, y, cooldownFraction, seconds, internalReadyFraction,
				primary, showSeconds, mirrorRight, conditionBlocked, false);
	}

	private static void drawSkillSlot(DrawContext context, Identifier icon, int x, int y,
	                                 double cooldownFraction, int seconds, double internalReadyFraction,
	                                 boolean primary, boolean showSeconds, boolean mirrorRight,
	                                 boolean conditionBlocked, boolean countdown) {
		int iconX = x + (mirrorRight ? 3 : 10);
		int iconY = y + (primary ? 10 : 4);
		context.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF8B8B8B);
		context.getMatrices().push();
		context.getMatrices().translate(iconX, iconY, 0);
		context.getMatrices().scale(ICON_SIZE / 32.0f, ICON_SIZE / 32.0f, 1.0f);
		context.drawTexture(icon, 0, 0, 0, 0, 32, 32, 32, 32);
		context.getMatrices().pop();
		int shadeHeight = (int) Math.ceil(ICON_SIZE * Math.max(0, Math.min(1, cooldownFraction)));
		if (shadeHeight > 0) {
			context.fill(iconX, iconY, iconX + ICON_SIZE, iconY + shadeHeight, 0xB0000000);
		}
		// 释放条件未满足：整幅半透明黑色遮罩（替代 CD 渐变，倒计时数字照常叠加显示）
		if (conditionBlocked) {
			context.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0x99000000);
		}
		if (internalReadyFraction >= 0) {
			int readyHeight = countdown ? KillEmpowerState.countdownHeight(internalReadyFraction, INTERNAL_HEIGHT)
					: (int) Math.floor(INTERNAL_HEIGHT * Math.max(0, Math.min(1, internalReadyFraction)));
			if (readyHeight > 0) {
				int barBottom = primary ? 32 : 26;
				int textureBottom = primary ? 32 : 60;
				int barOffset = mirrorRight ? 29 : 2;
				context.drawTexture(mirrorRight ? TEX_PANEL_RIGHT : TEX_PANEL,
						x + barOffset, y + barBottom - readyHeight, barOffset, textureBottom - readyHeight,
						2, readyHeight, SLOT_WIDTH, PANEL_HEIGHT);
			}
		}
		if (showSeconds && seconds > 0) {
			String number = Integer.toString(seconds);
			int textWidth = mc.textRenderer.getWidth(number);
			float scale = Math.min(1.0f, (ICON_SIZE - 2.0f) / Math.max(1, textWidth));
			context.getMatrices().push();
			context.getMatrices().translate(iconX + (ICON_SIZE - textWidth * scale) / 2,
					iconY + (ICON_SIZE - mc.textRenderer.fontHeight * scale) / 2, 0);
			context.getMatrices().scale(scale, scale, 1.0f);
			context.drawText(mc.textRenderer, number, 0, 0, 0xFFFFFFFF, true);
			context.getMatrices().pop();
		}
	}
}
