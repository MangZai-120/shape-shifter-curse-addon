package net.jackcooper.shapeShifterCurseAddon.client.sound;

import net.jackcooper.shapeShifterCurseAddon.client.hud.SpellCastHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;

/**
 * 施法蓄力音（客户端循环音实例，jackcooper，2026-09-19 用户定稿）。
 *
 * <p>修复三个音效生命周期问题（替代服务端一次性 playSound、无法中途停止的方案）：</p>
 * <ul>
 *   <li><b>短蓄力截断</b>：蓄力总时长 ≤1s 的法术只播 0.7s；从 0.5s 起音量快速线性降到 0，
 *       到点自动停——不会拖到施法结束后还在响；</li>
 *   <li><b>CD 中不再响</b>：起播只由 STATE 包活跃沿驱动（服务端拒绝施法时不发 STATE），
 *       与服务端失败提示音彻底分离；</li>
 *   <li><b>施法结束立即停</b>：HUD 状态清除（STATE inactive / 掉包看门狗）→ 本实例每 tick
 *       轮询到后立即 setDone，短 CD 连放时旧音即刻切断，不会与取消音叠加长鸣。</li>
 * </ul>
 *
 * <p>实现：标准 {@link MovingSoundInstance}（循环 + LINEAR 空间衰减），每 tick 跟随施法者坐标
 * （声音从玩家位置发出、随移动同步）；音调随蓄力进度 0.65→0.90 上升；基准音量 1.2、
 * 传播最远 24 格（12 格内近满音量、向外线性衰减、24 格外听不到）。驱动：{@link SpellCastHud}
 * STATE 沿调用 {@link #onChannelStart}。</p>
 */
public class SpellChargeSoundInstance extends MovingSoundInstance {

	/** 短蓄力判定阈值（秒）。 */
	private static final float SHORT_CAST_SECONDS = 1.0F;
	/** 短蓄力的实际播放时长（秒）。 */
	private static final float SHORT_PLAY_SECONDS = 0.7F;
	/** 短蓄力淡出起点（秒）：0.5s 起快速降低。 */
	private static final float SHORT_FADE_FROM = 0.5F;
	/** 基准音量（施法者本人处响度）。 */
	private static final float BASE_VOLUME = 1.2F;
	/** 空间衰减补偿：原版 LINEAR 衰减为 volume×16/dist，丏0.75 后满音段约 12 格、
	 * 24 格处恰好归零（用户定稿最远 24 格）。 */
	private static final float RANGE_COMPENSATION = 0.75F;
	/** 音调包络：起点 0.65、随进度升到 0.90（与旧服务端公式一致）。 */
	private static final float PITCH_FROM = 0.65F;
	private static final float PITCH_TO = 0.90F;

	/** 跟随的施法者（客户端镜像实体）。 */
	private final net.minecraft.entity.Entity caster;
	private final long startMillis = System.currentTimeMillis();
	private final float durationSeconds;

	private SpellChargeSoundInstance(net.minecraft.entity.Entity caster, float durationSeconds) {
		super(SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT, SoundCategory.PLAYERS, Random.create());
		this.caster = caster;
		this.durationSeconds = durationSeconds;
		this.repeat = true;           // 循环续播覆盖整个蓄力期
		this.relative = false;        // 空间音：从施法者位置广播，带距离衰减与立体声定位
		this.volume = BASE_VOLUME * RANGE_COMPENSATION;
		this.x = caster.getX();
		this.y = caster.getY();
		this.z = caster.getZ();
	}

	/** 播放入口：新施法开始（STATE 活跃沿）时调用；短蓄力自动截断。 */
	public static void onChannelStart(net.minecraft.entity.Entity caster, float durationTicks) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getSoundManager() == null) return;
		client.getSoundManager().play(new SpellChargeSoundInstance(caster, durationTicks / 20.0F));
	}

	@Override
	public void tick() {
		// 施法已结束（HUD 状态清除或进入退场）→ 立即停（修复「释放后还在响」）
		if (SpellCastHud.getState() == null) {
			this.setDone();
			return;
		}
		// 空间广播：每帧跟随施法者坐标（声音从玩家位置发出、随移动同步）
		this.x = caster.getX();
		this.y = caster.getY();
		this.z = caster.getZ();
		float elapsed = (System.currentTimeMillis() - startMillis) / 1000.0F;
		// 短蓄力（≤1s）：只播 0.7s（修复「1 秒内蓄力拖长音」）
		if (durationSeconds <= SHORT_CAST_SECONDS && elapsed >= SHORT_PLAY_SECONDS) {
			this.setDone();
			return;
		}
		// 音调随蓄力进度上升（HUD 本地推进的有效进度：锚点校准 + tick 差，非裸包内值——
		// 校准包每 20t 一发，裸用包内值会让 pitch 每 20t 阶梯跳变而非平滑上升）
		SpellCastHud.State state = SpellCastHud.getState();
		if (state != null && state.duration() > 0) {
			int elapsedTicks = net.jackcooper.shapeShifterCurseAddon.client.hud.SpellCastHud.getEffectiveElapsed(state);
			float progress = Math.min(1.0F, (float) elapsedTicks / state.duration());
			this.pitch = PITCH_FROM + (PITCH_TO - PITCH_FROM) * progress;
		}
		// 短蓄力淡出：0.5s 起快速线性降 0（在空间衰减基础上叠加）
		if (durationSeconds <= SHORT_CAST_SECONDS && elapsed > SHORT_FADE_FROM) {
			float fade = (elapsed - SHORT_FADE_FROM) / (SHORT_PLAY_SECONDS - SHORT_FADE_FROM);
			this.volume = BASE_VOLUME * RANGE_COMPENSATION * Math.max(0.0F, 1.0F - fade);
		}
	}
}
