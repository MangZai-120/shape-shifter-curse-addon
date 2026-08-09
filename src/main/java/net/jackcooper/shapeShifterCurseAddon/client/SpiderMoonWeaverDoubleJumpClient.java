package net.jackcooper.shapeShifterCurseAddon.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

/**
 * 月织蛛二段跳 - 客户端跳跃键检测器。
 * 仅月织蛛形态：空中按下跳跃键（边沿触发）时手动复刻原版 vanilla 跳跃速度完成二段跳，
 * 跳跃速度含疾跑前冲（走路/跑步两套），动画由原版 v3 动画 FSM 依据「离地 + 垂直速度」自动播放 spider_3_jump→fall；
 * 同时发包通知服务端播二段跳音效粒子。落地后的首跳是 vanilla 跳跃，不处理。
 */
@Environment(EnvType.CLIENT)
public final class SpiderMoonWeaverDoubleJumpClient {

	private static boolean wasJumpPressed = false;
	/** 本地离地 tick 计数：与服务端对齐，避免紧贴首跳触发。 */
	private static int airTicks = 999;
	private static final int MIN_AIR_TICKS = 3;
	/** 本次滞空是否还有二段跳额度（落地补满、二段跳消耗）。玩家移动是客户端权威的，必须在客户端限次，否则连按跳跃键会无限二段跳上天。 */
	private static boolean jumpAvailable = false;
	/** 二段跳是否激活（从触发到落地）：期间监控 spider_3_jump 播放进度，在姿态结束前主动切 fall 消除 T-pose 空窗。 */
	private static boolean doubleJumpActive = false;

	private SpiderMoonWeaverDoubleJumpClient() {}

	/** 供动画调试 HUD 读取：本次滞空是否还有二段跳额度。 */
	public static boolean isJumpAvailable() {
		return jumpAvailable;
	}

	/** 供动画调试 HUD 读取：本地离地 tick 计数。 */
	public static int getAirTicks() {
		return airTicks;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpiderMoonWeaverDoubleJumpClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			wasJumpPressed = false;
			airTicks = 999;
			return;
		}
		// 落地：补满二段跳额度、归零离地计时、清二段跳激活标志
		if (player.isOnGround()) {
			airTicks = 0;
			jumpAvailable = true; // 落地补满一次二段跳额度
			doubleJumpActive = false;
		} else if (airTicks < MIN_AIR_TICKS + 10) {
			airTicks++;
		}
		// T-pose 空窗修复：二段跳激活期间，当 spider_3_jump 播到接近姿态结束(endTick-1)时，
		// 主动把 fallDistance 推到 >0.6，让 FSM 立即切 fall，消除 jump 姿态结束到 FSM 切换之间的 T-pose 空窗
		if (doubleJumpActive && !player.isOnGround() && shouldAdvanceToFall(player)) {
			player.fallDistance = 0.7f; // >0.6 阈值，触发 FSM 切 ANIM_STATE_FALL
		}
		boolean pressed = client.options.jumpKey.isPressed();
		// 边沿触发 + 仅月织蛛 + 仅空中 + 有额度 + 离地满 MIN_AIR_TICKS（一次滞空只允许一次二段跳，防连按上天）
		// 挂着蛛丝时（蛛丝荡漾）禁用二段跳：空格此时用作收绳
		if (pressed && !wasJumpPressed && !player.isOnGround() && jumpAvailable && airTicks >= MIN_AIR_TICKS
				&& FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER)
				&& !net.jackcooper.shapeShifterCurseAddon.client.SpiderMoonWeaverSwingClient.isLocalActive()) {
			// 手动复刻 vanilla 跳跃速度：velY 覆盖为跳跃初速、疾跑时自带水平前冲（走路/跑步两套），
			// 动画由原版 FSM 依离地+速度自动播 spider_3_jump→fall，无需任何自定义动画代码
			doDoubleJump(player);
			jumpAvailable = false; // 消耗本次滞空的二段跳额度，落地才补满
			doubleJumpActive = true; // 标记二段跳激活，期间监控 jump 动画进度以提前切 fall
			// 通知服务端播二段跳音效粒子（并广播给其他玩家）
			ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPIDER_MOON_WEAVER_DOUBLE_JUMP,
					new PacketByteBuf(Unpooled.buffer()));
		}
		wasJumpPressed = pressed;
	}

	/**
	 * 手动施加二段跳速度（跳跃初速 0.42 + 疾跑水平前冲 0.2）。
	 * 直接 setVelocity 而不调用原版 jump()，是为了绕过原版对 getJumpVelocity() 的 noJumpTick 拦截
	 * （变身禁跳窗口本意针对变身瞬间，不应压制二段跳技能）。
	 * <p>末尾清零<b>客户端</b> fallDistance：让二段跳的 JUMP→FALL 动画时序与一段跳完全一致——
	 * fallDistance 从 0 重新累积，下落要累积到 0.6 格才切 FALL，spider_3_jump（0.5s=10t）得以充分播放后
	 * 自然过渡到坠落动画；否则 fallDistance 未清（二段跳前已 >0.6）会让下落瞬间立刻切 FALL、jump 动画只播一半就被顶掉。
	 * 只清客户端（仅驱动本地动画 FSM），服务端 fallDistance 独立照常累积算摔伤，动画自然与摔伤判定两不误。
	 * <p><b>初速保持 0.42（不降为原版蜘蛛的 2/3）</b>：初速越低上升段越短、JUMP 阶段不足以让 spider_3_jump 充分展示，
	 * 会导致 jump→fall 衔接变差；0.42 时 JUMP 窗口≈10t 与 jump 动画时长匹配，衔接最自然。力度与动画衔接强关联，故保持 0.42。
	 */
	private static void doDoubleJump(ClientPlayerEntity player) {
		float jumpVel = 0.42f; // vanilla 基础跳跃初速（保持此值以保证 JUMP 阶段足够长、动画衔接自然）
		if (player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
			jumpVel += 0.1f * (player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1);
		}
		Vec3d v = player.getVelocity();
		player.setVelocity(v.x, jumpVel, v.z); // 覆盖 velY 为跳跃初速（同时清除下落速度）
		if (player.isSprinting()) { // 疾跑跳：沿朝向水平前冲 0.2
			float yaw = player.getYaw() * ((float) Math.PI / 180f);
			player.setVelocity(player.getVelocity().add(-MathHelper.sin(yaw) * 0.2f, 0.0, MathHelper.cos(yaw) * 0.2f));
		}
		player.velocityModified = true;
		player.fallDistance = 0.0f; // 清零客户端摔落累积，让 JUMP 时序与一段跳一致（下落 0.6 格后才切 FALL），jump 动画充分展示后自然过渡
	}

	/**
	 * 检查是否应提前从 spider_3_jump 切到 fall：当当前播放的 spider_3_jump 进度≥姿态结束点(endTick-1)时返回 true。
	 * 用于消除 jump 姿态结束(endTick)到 FSM 切换(fallDist>0.6)之间的 T-pose 空窗。
	 */
	private static boolean shouldAdvanceToFall(ClientPlayerEntity player) {
		try {
			dev.kosmx.playerAnim.api.layered.AnimationStack stack =
					dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(player);
			dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer kp = SpiderMoonWeaverAnimDebugHud.findActivePlayerFromStack(stack);
			if (kp == null) return false;
			dev.kosmx.playerAnim.core.data.KeyframeAnimation data = kp.getData();
			if (data == null) return false;
			// 动画名含 spider_3_jump 且播放进度≥姿态结束点(endTick-1) 时，提示应切 fall
			String name = (data.extraData != null) ? String.valueOf(data.extraData.get("name")) : "";
			if (name != null && name.contains("spider_3_jump") && kp.getCurrentTick() >= data.endTick - 1) {
				return true;
			}
		} catch (Throwable ignored) { }
		return false;
	}
}
