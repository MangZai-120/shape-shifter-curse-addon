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
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.compat.SpiderJumpAnimController;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

/**
 * 月织蛛、跳蛛二段跳 - 客户端跳跃键检测器。
 * 空中按下跳跃键（边沿触发）时手动复刻 vanilla 跳跃速度，包含疾跑前冲。
 * 通知形态跳跃控制器重新开始动作，并由原版 FSM 衔接下落、落地动作；
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
			jumpAvailable = false;
			return;
		}
		// 落地：补满二段跳额度、归零离地计时
		if (player.isOnGround()) {
			airTicks = 0;
			jumpAvailable = true; // 落地补满一次二段跳额度
		} else if (airTicks < MIN_AIR_TICKS + 10) {
			airTicks++;
		}
		boolean pressed = client.options.jumpKey.isPressed();
		// 边沿触发 + 仅月织蛛/跳蛛 + 仅空中 + 有额度 + 离地满 MIN_AIR_TICKS（一次滞空只允许一次二段跳，防连按上天）
		// 挂着蛛丝时（蛛丝荡漾）禁用二段跳：空格此时用作收绳
		if (pressed && !wasJumpPressed && !player.isOnGround() && jumpAvailable && airTicks >= MIN_AIR_TICKS
				&& (FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER)
						|| FormUtils.isForm(player, FormIdentifiers.SPIDER_SALTICIDAE))
				&& !net.jackcooper.shapeShifterCurseAddon.client.SpiderMoonWeaverSwingClient.isLocalActive()) {
			// 手动复刻 vanilla 跳跃速度：velY 覆盖为跳跃初速、疾跑时自带水平前冲（走路/跑步两套），
			doDoubleJump(player);
			jumpAvailable = false; // 消耗本次滞空的二段跳额度，落地才补满
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
	 * 二段跳通知动画控制器重新播放动作；仅重置动画系统自己的落差计数。
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
		SpiderJumpAnimController.restartJump(player);
	}
}
