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
	/** 朔望蓄力锚：CHARGING 资源 0→1 跳变时刻（涨条分母用会话时长 balance）。 */
	private long novaChargeAnchor = Long.MIN_VALUE;
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
			novaChargeAnchor = Long.MIN_VALUE; // 换形态重置朔望蓄力锚（防残留锚跨形态误算）
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
			Cooldown cooldown = readCooldown(player, skill.cooldown(), skill.cooldownTotalTicks());
            var authoritative = net.jackcooper.shapeShifterCurseAddon.client.SkillCooldownClient.get(skill.authorityId());
            if (authoritative != null) {
                int remaining = authoritative.remainingNow(net.jackcooper.shapeShifterCurseAddon.client.SkillCooldownClient.now());
                cooldown = new Cooldown(remaining, Math.min(1.0, remaining / (double)Math.max(1, authoritative.total())));
            }
			double internalReady = readInternalReady(player, skill);
			// 释放条件未满足 → 半透明黑色遮罩；遮罩期间跳过 CD 渐变阴影，只保留倒计时数字
			boolean conditionBlocked = skill.condition() != null && !skill.condition().test(player);
			if (formId.equals(FormIdentifiers.SNOW_FOX_FROSTSPINE) && !skill.primary()) {
				// 凝棘（次技能）蓄力进度：读每 tick 缓存的法阵实体 PROGRESS（服务端权威，0-100 tick）
				// 无缓存值（未蓄力/已被强停）= -1，侧边条不显示
				internalReady = net.jackcooper.shapeShifterCurseAddon.client.ClientTickCache.frostForgeProgress();
			}
			if (formId.equals(FormIdentifiers.SNOW_FOX_FROSTSPINE) && skill.primary()) {
				// 寒棘狐主技能辅助栏：环绕冰锥数量（每根 +1/5，少一根少 1/5；无锥不显示）。
				// countdown 保持默认 false（数量非时间，非倒数条）
				int thorns = net.jackcooper.shapeShifterCurseAddon.client.ClientTickCache.hoverThornCount();
				internalReady = thorns > 0 ? thorns / 5.0 : -1;
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
			if (formId.equals(FormIdentifiers.OCELOT_SP) && skill.primary()) {
				// 风灵主技能辅助栏：飞行期间显示悬浮剩余——RISE 满格，HOVER 自满格倒数 hover_ticks（balance 可调）
				internalReady = net.jackcooper.shapeShifterCurseAddon.client.DashClientState.hoverRemainingFraction(
						SkillHudCatalog.balanceInt("abilities.wind_dash", "hover_ticks", 60));
				countdown = internalReady >= 0;
			}
			if (formId.equals(FormIdentifiers.SPIDER_SALTICIDAE) && skill.primary()) {
				// 跳蛛主技能辅助栏：安全丝拉回窗口剩余倒数（丝用掉/断掉/超时即归零）
				double silkFrac = net.jackcooper.shapeShifterCurseAddon.client.JumpKillSilkClient.recallWindowFraction();
				if (silkFrac >= 0) {
					internalReady = silkFrac;
					countdown = true;
				}
			}
			if (formId.equals(FormIdentifiers.OCELOT_NOVA) && !skill.primary()) {
				// 朔望次技能辅助栏：第 1 段灵跃后自满格倒数 leap_window（服务端推送），显示第 2 段过期时间
				internalReady = net.jackcooper.shapeShifterCurseAddon.client.NovaLeapClientState.remainingFraction(mc.world.getTime());
				countdown = internalReady >= 0;
			}
			if (formId.equals(FormIdentifiers.OCELOT_NOVA) && skill.primary()) {
				// 朔望主技能（舍身爆炸）辅助栏：蓄力期间自小到大涨条（0→1 = 蓄力进度，蓄满/中断即消）
				double chargeFrac = novaChargeFraction(player);
				if (chargeFrac >= 0) {
					internalReady = chargeFrac;
					countdown = false; // 涨条（非倒数）
				}
			}
			if (formId.equals(FormIdentifiers.AXOLOTL_SP) && skill.primary()
					&& "form_axolotl_sp_vortex_charge".equals(skill.cooldown().getPath())) {
				// SP 美西螈主技能（涡流冲击）辅助栏：蓄力力度自小到大涨条（vortex_state=已蓄 tick / max_ticks）
				int vortexTicks = PowerUtils.getClientResourceValue(player,
						net.jackcooper.shapeShifterCurseAddon.ability.VortexChargeManager.VORTEX_STATE);
				if (vortexTicks > 0) {
					internalReady = Math.min(1.0, vortexTicks
						/ (double) Math.max(1, net.jackcooper.shapeShifterCurseAddon.ability.VortexChargeManager.maxTicksForHud()));
					countdown = false; // 涨条（蓄力力度）
				}
			}
			if (formId.equals(FormIdentifiers.AXOLOTL_SP) && !skill.primary()
					&& "form_axolotl_sp_play_dead_activate".equals(skill.cooldown().getPath())) {
				// SP 美西螈次技能（假死）辅助栏：装死效果剩余时长倒数（自满格到零，6 秒）
				var deadEffect = player.getStatusEffect(net.jackcooper.shapeShifterCurseAddon.SscAddon.PLAYING_DEAD);
				if (deadEffect != null) {
					internalReady = Math.min(1.0, Math.max(1, deadEffect.getDuration())
						/ (double) net.jackcooper.shapeShifterCurseAddon.action.SscAddonActions.PLAY_DEAD_DURATION_TICKS);
					countdown = true;
				}
			}
			if ((formId.equals(FormIdentifiers.AXOLOTL_FLUORESCENT) || formId.equals(FormIdentifiers.AXOLOTL_ALING))
					&& !skill.primary() && "form_axolotl_fluorescent_tidal".equals(skill.cooldown().getPath())) {
				// 荧光幼灵/阿澪次技能（潮汐波动）辅助栏：拴人（潮汐束缚）阶段剩余倒数（只显拴人阶段；每 tick 缓存）
				double tetherFrac = net.jackcooper.shapeShifterCurseAddon.client.ClientTickCache.tidalTetherFraction();
				if (tetherFrac >= 0) {
					internalReady = tetherFrac;
					countdown = true;
				}
			}
			if (formId.equals(FormIdentifiers.FALLEN_ALLAY_SP) && skill.primary()) {
				// 堕落悦灵主技能（召唤恼鬼）辅助栏：召唤物存活倒数（自满格到零，提前全灭归零）
				double vexFrac = net.jackcooper.shapeShifterCurseAddon.client.FallenAllayVexClientState
						.remainingFraction(mc.world.getTime());
				if (vexFrac >= 0) {
					internalReady = vexFrac;
					countdown = true;
				}
			}
			if (formId.equals(FormIdentifiers.GOLDEN_SANDSTORM_SP) && !skill.primary()) {
				// 金沙岚次技能（引爆标记）：场上没有任何可引爆烙印（黄/橙/红；绿色冷却不可引爆）→ 黑色遮罩
				if (!net.jackcooper.shapeShifterCurseAddon.ability.ErosionBrandClientState.hasAnyDetonatable()) {
					conditionBlocked = true;
				}
			}
			boolean empowerForm = formId.equals(FormIdentifiers.FAMILIAR_FOX_SP) || formId.equals(FormIdentifiers.FAMILIAR_FOX_RED);
			if (empowerForm) {
				int empowerState = PowerUtils.getClientResourceValue(player, FormIdentifiers.EMPOWER_STATE);
				boolean empowered = (empowerState & KillEmpowerManager.STATE_READY) != 0;
				boolean empowerRing = (empowerState & KillEmpowerManager.STATE_RING) != 0;
				boolean ringActive = skill.primary() && (KillEmpowerCast.isNormalRingActive(player)
						|| empowerRing);
				if (skill.primary()) {
					Cooldown activation = readCooldown(player, skill.internalCooldown(), 0);
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

	/** 朔望蓄力进度（0→1）：CHARGING 资源为 1 时，锚 = 跳变时刻，分母读会话时长 balance；未蓄力 -1。 */
	private double novaChargeFraction(PlayerEntity player) {
		if (PowerUtils.getClientResourceValue(player, FormIdentifiers.OCELOT_NOVA_CHARGING) <= 0) {
			novaChargeAnchor = Long.MIN_VALUE;
			return -1;
		}
		long now = mc.world.getTime();
		if (novaChargeAnchor == Long.MIN_VALUE) novaChargeAnchor = now;
		int chargeTime = SkillHudCatalog.balanceInt("abilities.nova", "charge_time", 100);
		return Math.min(1.0, (now - novaChargeAnchor) / (double) Math.max(1, chargeTime));
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
			value = 1.0 - readCooldown(player, id, 0).remaining() / (double) skill.internalTicks();
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

	private Cooldown readCooldown(PlayerEntity player, Identifier id, int authoritativeTotal) {
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
		// 分母优先用服务端同步的配置总长（P1：CD 数据化字段）；未登记的技能回退观测最大值法。
		// 剩余值本身由资源预测通道（CountdownSync）保持 tick 级正确，不受分母来源影响。
		// 钳制：中途 reload 改短 CD 时旧 CD 剩余可能 > 新配置总长，取两者较大者保证分数 ≤1（与原观测法不变量一致）。
		int maximum = authoritativeTotal > 0
				? Math.max(authoritativeTotal, remaining)
				: trackedMaxValues.merge(id, remaining, Math::max);
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
