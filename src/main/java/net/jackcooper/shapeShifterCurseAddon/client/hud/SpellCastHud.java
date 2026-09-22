package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 法术蓄力条 HUD（jackcooper，2026-09-19 重做）：替换原准星下方横条为
 * CD 条对侧（默认贴屏幕右缘、与左中 CD 条同高镜像对称）的竖向蓄力面板。
 * <ul>
 * <li><b>平时隐藏</b>：开始施法时面板从屏幕侧缘外滑入（0.3s ease-out，先快后慢）；</li>
 * <li><b>条旁倒计时</b>：窄条（14px）外侧显示剩余秒数（如 2.4），蓄满后显示就绪；数字随条一起滑入；</li>
 * <li><b>进度填充</b>：空框贴图打底 + 满图按进度自下而上裁切显示（注水式）；</li>
 * <li><b>退场</b>：正常释放完成后原地停留 0.5s，再 0.3s ease-in（先慢后快）加速滑出；
 * 被打断时不停留、快速滑出（0.15s），避免误导性残留；</li>
 * <li><b>零读条不显示</b>：instant 档（空间跳跃）无读条过程，不弹条；basic 档正常显示；</li>
 * <li><b>位置可编辑</b>：chargeBarPosType/OffsetX/OffsetY/chargeMirrorRight（仿 CD 条左右对换，贴左时材质镜像），位置编辑器可视化调整。</li>
 * </ul>
 * 数据源：服务端起手发的全量 {@code spell_channel_state} 包 + 每 20t 一次轻量校准包
 * （token+elapsed+标志位），elapsed 由客户端本地按 tick 推进（原版弓蓄力同款做法，
 * 施法期间包量较旧版每 tick 全量重发降 ~95%）。
 */
public final class SpellCastHud {
	/** 面板贴图（14×68 窄条，2026-09-19 用户重绘）：满=蓄力填充层，空=框体层；
	 * 左/右两版镜像贴图，仿 CD 条 TEX_PANEL / TEX_PANEL_RIGHT 双贴图选图显示。 */
	private static final Identifier TEX_FULL = new Identifier("ssc_addon", "textures/gui/spell_charge_bar_right.png");
	private static final Identifier TEX_EMPTY = new Identifier("ssc_addon", "textures/gui/spell_charge_bar_right_empty.png");
	private static final Identifier TEX_FULL_LEFT = new Identifier("ssc_addon", "textures/gui/spell_charge_bar_left.png");
	private static final Identifier TEX_EMPTY_LEFT = new Identifier("ssc_addon", "textures/gui/spell_charge_bar_left_empty.png");
	private static final int TEX_W = 14, TEX_H = 68;
	/** 滑入动画时长（tick，0.3 秒=6t）。 */
	private static final float SLIDE_IN_TICKS = 6.0F;
	/** 释放完成后的停留时长（tick，0.5 秒=10t）。 */
	private static final float HOLD_TICKS = 10.0F;
	/** 正常滑出时长（tick，0.3 秒=6t）。 */
	private static final float SLIDE_OUT_TICKS = 6.0F;
	/** 打断快速滑出时长（tick，0.15 秒=3t）。 */
	private static final float INTERRUPT_SLIDE_OUT_TICKS = 3.0F;
	/** 滑入/滑出距离（px）：一个条宽 + 余量，确保完全从屏幕外滑入。 */
	private static final float SLIDE_DIST = 40.0F;
	private static State state;

	public record State(java.util.UUID casterUuid, Spell spell, int token, int elapsed, int duration, SpellCastingRules.Mode mode,
			boolean released, boolean immobilized, boolean solo, boolean continuous, int cancelTicks,
			/** 锁定态（服务端 isLockedIn：领域壳扩张后/爆裂红白球生成后不可打断）：倒计时红显且忽略取消显示。 */
			boolean locked) {}

	private SpellCastHud() {}

	public static State getState() { return state; }

	/** 滑入动画起始时刻（客户端世界时间）：null→非 null 的沿触发，state 每 tick 重发不重置。 */
	private static Long slideInAt;
	/** 退场起始时刻与退场方式（null = 施法中）。 */
	private static Long endAt;
	private static boolean endedByInterrupt;
	/** 最后一次收到 STATE 包的时刻：校准包每 20t 一发，静默超 45t 视为会话已死（掉包看门狗）。 */
	private static long lastStateAt;
	/** 本地推进锚点：最近一次校准包的 (世界 tick, elapsed) 对；两包之间 elapsed 按差值推进。 */
	private static long anchorAt;
	private static int anchorElapsed;
	/** 客户端本地取消标志：施法键再次按下（AUTOMATIC 取消长按）时立即置位，红字即时显示；
	 * 服务端 cancelTicks 校准包到达后被权威值覆盖。 */
	private static boolean localCancelling;

	public static void clear() {
		state = null;
		slideInAt = null;
		endAt = null;
		endedByInterrupt = false;
		lastStateAt = 0;
		anchorAt = 0;
		anchorElapsed = 0;
		localCancelling = false;
		SpellChannelManager.setClientImmobile(null);
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SpellChannelManager.STATE, (client, handler, buf, sender) -> {
			if (!buf.readBoolean()) {
				client.execute(SpellCastHud::onChannelEnd);
				return;
			}
			// 双格式：full=true 全量首包（起手一次）/ false 轻量校准包（每 20t，仅 token+elapsed+标志位）
			boolean full = buf.readBoolean();
			State incoming;
			if (full) {
				Spell spell = SpellRegistry.get(buf.readIdentifier().getPath());
				java.util.UUID casterUuid = buf.readUuid();
				incoming = new State(casterUuid, spell, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
						buf.readEnumConstant(SpellCastingRules.Mode.class), buf.readBoolean(), buf.readBoolean(),
						buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
			} else {
				// 轻量校准：只更新 elapsed/已释放/取消计数/锁定态，静态字段沿用上一状态
				State old = state;
				if (old == null) return; // 全量首包丢了：无法恢复静态字段，丢弃（看门狗会兜底清场）
				incoming = new State(old.casterUuid(), old.spell(), buf.readVarInt(), buf.readVarInt(),
						old.duration(), old.mode(), buf.readBoolean(), old.immobilized(), old.solo(),
						old.continuous(), buf.readVarInt(), buf.readBoolean());
			}
			final boolean fullPacket = full;
			final State packetState = incoming;
			client.execute(() -> {
				State old = state;
				state = packetState;
				lastStateAt = now();
				// 本地推进锚点：记住 (当前tick, 包内elapsed)，之后每帧按 tick 差推进
				anchorAt = lastStateAt;
				anchorElapsed = packetState.elapsed();
				if (fullPacket) localCancelling = false; // 新施法起手：清本地取消标志
				// 新施法到达：作废旧退场（连放时旧 endAt 不能吃掉新 HUD），并触发滑入沿
				endAt = null;
				endedByInterrupt = false;
				if (old == null && packetState != null && packetState.duration() > 0) slideInAt = now();
				// 蓄力音：新施法开始沿起播（短蓄力自动截断；CD 中无 STATE 不响）。
				// 空间广播：跟随施法者坐标、24 格衰减——本人/旁观者各自听到对应响度。
				if (old == null && packetState != null && packetState.duration() > 0 && client.player != null) {
					// 领域蓄力除外（2026-09-22 定稿）：领域的服务端广播音（0-16 格零衰减、
					// 16-64 格线性归零）已覆盖施法者本人，跳过本机循环音保证施法者与
					// 他人听到的完全一致，避免双重音源音量叠加。
					if (packetState.spell() == null
							|| !packetState.spell().getId().getPath().equals("domain")) {
						// 施法者定位：本人施法用本地玩家；他人施法则从玩家列表查 UUID 对应实体
						var caster = client.player.networkHandler.getWorld().getPlayerByUuid(packetState.casterUuid());
						if (caster == null) caster = client.player;
						net.jackcooper.shapeShifterCurseAddon.client.sound.SpellChargeSoundInstance
								.onChannelStart(caster, packetState.duration());
					}
				}
				SpellChannelManager.setClientImmobile(packetState.immobilized() && client.player != null
						? client.player.getUuid() : null);
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			clear();
			slideInAt = null;
			endAt = null;
			endedByInterrupt = false;
		});
		HudRenderCallback.EVENT.register(SpellCastHud::render);
	}

	/** 施法键再次按下（AUTOMATIC 取消长按）本地即时置红字：不等 20t 校准包。 */
	public static void markLocalCancelling() {
		localCancelling = true;
	}

	/** 通道结束（服务端下发 inactive）：冻结本地推进快照 + 记录退场时刻与方式，进入滑出阶段。 */
	private static void onChannelEnd() {
		State current = state;
		if (current != null) {
			// 打断时进度必然未满（elapsed<duration）；正常结束/蓄满释放 elapsed≥duration
			// （用本地推进的有效值判定，而非可能滞后 ≤20t 的包内值）
			endedByInterrupt = effectiveElapsed(current) < current.duration();
			// 冻结：退场动画期间进度停在结束时刻，不再随本地推进增长
			anchorElapsed = effectiveElapsed(current);
			anchorAt = now();
		}
		endAt = current == null ? null : now();
		SpellChannelManager.setClientImmobile(null);
	}

	private static long now() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world == null ? 0 : client.world.getTime();
	}

	/** 滑出结束后彻底清空状态。 */
	private static void resetExit() {
		state = null;
		slideInAt = null;
		endAt = null;
		endedByInterrupt = false;
		lastStateAt = 0;
		anchorAt = 0;
		anchorElapsed = 0;
		localCancelling = false;
	}

	/** 本地推进的有效 elapsed：锚点（最近校准包）+ 锚点以来的 tick 差；蓄满后封顶在 duration。 */
	private static int effectiveElapsed(State current) {
		int advanced = anchorAt <= 0 ? 0 : (int) Math.max(0, now() - anchorAt);
		return Math.min(current.duration(), anchorElapsed + advanced);
	}

	/** 对外暴露本地推进 elapsed（蓄力音 pitch 等帧级消费者用；与 HUD 渲染同源）。 */
	public static int getEffectiveElapsed(State current) {
		return effectiveElapsed(current);
	}

	private static void render(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		State current = state;
		// ===== 掉包看门狗：校准包每 20t 一发，静默超 45t（且不在退场动画中）→ 会话已死，直接清 =====
		// 否则 inactive 包丢失时 state 永驻，后续书内施法全部被 active!=null 吞掉（同 bug1 锁死链）。
		if (current != null && endAt == null && lastStateAt > 0 && now() - lastStateAt > 45) {
			resetExit();
			return;
		}
		// ===== 退场状态机必须无条件推进（hudHidden/开界面/零读条也不能冻结，否则 state 永驻锁死后续施法）=====
		if (current != null && endAt != null) {
			long t = now();
			float since = t - endAt;
			float outTicks = endedByInterrupt ? INTERRUPT_SLIDE_OUT_TICKS : HOLD_TICKS + SLIDE_OUT_TICKS;
			if (since >= outTicks) {
				resetExit(); // 彻底清空：state=null，解锁下一次施法
				return;
			}
		}
		if (current == null || current.spell() == null || client.player == null || client.world == null
				|| client.options.hudHidden || client.currentScreen != null) return;
		// 零读条不绘制（instant 从未滑入；但退场计时已在上方推进）
		if (current.duration() <= 0) return;
		long t = now();
		// ===== 退场阶段：停留 → 加速滑出（打断则跳过停留快速滑出）=====
		if (endAt != null) {
			float since = t - endAt;
			if (endedByInterrupt) {
				drawPanel(context, client, current, easeInCubic(clamp01(since / INTERRUPT_SLIDE_OUT_TICKS)),
						tickDelta, true);
			} else if (since <= HOLD_TICKS) {
				drawPanel(context, client, current, 0.0F, tickDelta, true); // 停留期原位显示
			} else {
				drawPanel(context, client, current, easeInCubic(clamp01((since - HOLD_TICKS) / SLIDE_OUT_TICKS)),
						tickDelta, true);
			}
			return;
		}
		// ===== 施法中：从侧缘滑入（ease-out：初速最快、临就位最慢）=====
		float inP = slideInAt == null ? 1.0F : clamp01((t - slideInAt) / SLIDE_IN_TICKS);
		drawPanel(context, client, current, 1.0F - easeOutCubic(inP), tickDelta, false);
	}

	/**
	 * 绘制蓄力面板。
	 * @param outOffset 0=就位；0..1=离场位移比例（ease-in 后）或滑入剩余位移比例（1-ease-out 后）
	 * @param exiting true=退场阶段（进度冻结在结束时刻，不再随 tickDelta 推进）
	 */
	private static void drawPanel(DrawContext context, MinecraftClient client, State current,
			float outOffset, float tickDelta, boolean exiting) {
		SSCAddonClientConfig config = SSCAddonConfig.client();
		config.migrateSkillHudLayout(); // 蓄力条可能在不显示 CD 条时渲染，这里兜底跑布局迁移
		boolean onLeft = !config.chargeMirrorRight;
		var anchor = net.onixary.shapeShifterCurseFabric.util.UIPositionUtils
				.getCorrectPosition(config.chargeBarPosType, 0, 0);
		int screenW = context.getScaledWindowWidth();
		// 就位位置（与 CD 条 panelLayout 完全同式）：面板左缘贴「锚点+偏移」+ clamp 防出屏；
		// 贴右侧时做镜像换算（x = screenW - x - 条宽）——默认锚点4+偏移(0,-34)+贴右 → 条贴屏幕右缘，
		// 与左中 CD 条（左缘贴左缘、同 68 高同 Y）几何对称；offsetX 正值向右平移。
		int baseX = clampPosition(anchor.getLeft() + config.chargeBarPosOffsetX, screenW, TEX_W);
		if (config.chargeMirrorRight) baseX = Math.max(0, screenW - baseX - TEX_W);
		int baseY = clampPosition(anchor.getRight() + config.chargeBarPosOffsetY,
				context.getScaledWindowHeight(), TEX_H);
		// 位移方向：条在右 → 从右侧屏幕外滑入（+X 方向退场）；条在左 → 从左侧屏幕外（-X）
		float distance = outOffset * SLIDE_DIST * (onLeft ? -1 : 1);
		float x = baseX + distance;
		// ===== 进度与倒计时（elapsed 用本地推进值：锚点校准 + tick 差推进，蓄满封顶）=====
		int elapsed = effectiveElapsed(current);
		float progress = current.duration() <= 0 ? 1
				: clamp01((elapsed + (exiting ? 0 : tickDelta)) / (float) current.duration());
		boolean full = elapsed >= current.duration();
		// 锁定态（2026-09-23 用户定稿）：忽略取消显示（服务端不会取消，避免「取消中」红字永驻），
		// 倒计时改红提示「不可打断必须释放」。
		boolean locked = current.locked();
		boolean cancelling = !locked && (localCancelling || current.cancelTicks() > 0);
		// ===== 绘制：空框 → 满图自下而上裁切（左/右双贴图选图）→ 条旁倒计时 =====
		int drawX = (int) x;
		int drawY = baseY;
		Identifier texEmpty = onLeft ? TEX_EMPTY_LEFT : TEX_EMPTY;
		Identifier texFull = onLeft ? TEX_FULL_LEFT : TEX_FULL;
		context.drawTexture(texEmpty, drawX, drawY, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
		int fillH = (int) (TEX_H * progress);
		if (fillH > 0) {
			// 源区取满图底部 fillH 高 → 目标画在面板底部（注水式自下而上）
			context.drawTexture(texFull, drawX, drawY + TEX_H - fillH, 0, TEX_H - fillH, TEX_W, fillH, TEX_W, TEX_H);
		}
		// 中心倒计时：剩余秒数（一位小数）；蓄满→就绪/待释放；取消长按→红字
		String timeText;
		int color;
		if (cancelling) {
			timeText = Text.translatable("gui.ssc_addon.spell_cast.cancel_short").getString();
			color = 0xFFFF5555;
		} else if (full) {
			timeText = current.mode() == SpellCastingRules.Mode.RELEASE && !current.released()
					? Text.translatable("gui.ssc_addon.spell_cast.release_short").getString()
					: Text.translatable("gui.ssc_addon.spell_cast.ready_short").getString();
			color = 0xFFFFFF66;
		} else {
			float remainTicks = current.duration() - (elapsed + (exiting ? 0 : tickDelta));
			timeText = String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, remainTicks) / 20.0F);
			// 锁定态红显（2026-09-23 用户定稿）：不可打断阶段倒计时红色提示必须释放
			color = locked ? 0xFFFF5555 : 0xFFFFFFFF;
		}
		int textW = client.textRenderer.getWidth(timeText);
		// 窄条（14px）内放不下文字：倒计时画在条外侧、指向屏幕中心（条在右→文字在左，条在左→文字在右）
		int textX = onLeft ? drawX + TEX_W + 2 : drawX - textW - 2;
		int textY = drawY + TEX_H / 2 - client.textRenderer.fontHeight / 2;
		context.drawText(client.textRenderer, timeText, textX, textY, color, true);
	}

	private static float clamp01(float v) {
		return Math.max(0.0F, Math.min(1.0F, v));
	}

	/** 防出屏钳制（与 CD 条 clampPosition 同式）。 */
	private static int clampPosition(int position, int screenSize, int size) {
		return Math.max(0, Math.min(Math.max(0, screenSize - size), position));
	}

	/** ease-out（1-(1-t)^3）：初速最快、临近终点最慢——用于滑入。 */
	private static float easeOutCubic(float t) {
		return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
	}

	/** ease-in（t^3）：初速最慢、临近终点最快——用于滑出。 */
	private static float easeInCubic(float t) {
		return t * t * t;
	}
}