package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaTeleport;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import org.joml.Vector3f;

/**
 * 契灵 - 次要技能瞬移：纯客户端按键监听 + 落点预览。
 *
 * - RAYCAST：按下次要键 → 立即向服务端发送传送包
 * - PLATFORM：按下次要键 → 客户端持续渲染紫色粒子预览落点；松开按键 → 发送传送包
 *
 * 服务端会根据收到包时的玩家朝向重新计算并校验落点，客户端预览仅用于视觉提示。
 */
@Environment(EnvType.CLIENT)
public final class MancianimaTeleportClient {

	private static final Vector3f PURPLE = new Vector3f(0.7f, 0.2f, 1.0f);

	private static boolean wasKeyPressed = false;
	/** 上一帧是否处于"按住预览中"状态（防止跨形态/跨模式残留） */
	private static boolean previewing = false;
	/** 预览粒子节流计数（每 2 tick 刷一次，更跟手） */
	private static int previewParticleTick = 0;

	private MancianimaTeleportClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MancianimaTeleportClient::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			wasKeyPressed = false;
			previewing = false;
			return;
		}

		// 仅契灵形态生效
		if (!isMancianima(player)) {
			wasKeyPressed = false;
			previewing = false;
			return;
		}

		KeyBinding key = SscAddonKeybindings.getSecondaryKey();
		if (key == null) return;
		boolean isPressed = key.isPressed();

		SSCAddonClientConfig cfg;
		try {
			cfg = SSCAddonConfig.client();
		} catch (Exception e) {
			return;
		}
		SSCAddonClientConfig.MancianimaTeleportMode mode = cfg.mancianimaTeleportMode;

		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVector().normalize();

		if (mode == SSCAddonClientConfig.MancianimaTeleportMode.RAYCAST) {
			// 按下边沿 → 发包
			if (isPressed && !wasKeyPressed) {
				sendTeleportPacket((byte) 0);
			}
			previewing = false;
		} else {
			// PLATFORM 模式：按住期间渲染预览，松开发包
			if (isPressed) {
				// 仅在能传送时显示预览（CD 已就绪 + 法力足够）
				boolean canTeleport = PowerUtils.getClientResourceValue(player, FormIdentifiers.SP_SECONDARY_CD) <= 0
						&& ManaUtils.getPlayerMana(player) >= MancianimaTeleport.MANA_COST;
				if (canTeleport) {
					Vec3d landing = MancianimaTeleport.computePlatformLanding(world, eye, look, player);
					if (landing != null && (previewParticleTick++ % 2) == 0) {
						renderPreview(world, landing);
					}
				}
				previewing = true;
			} else if (wasKeyPressed && previewing) {
				// 边沿：松开 → 发包（服务端会重算并验证）
				sendTeleportPacket((byte) 1);
				previewing = false;
				previewParticleTick = 0;
			}
		}

		wasKeyPressed = isPressed;
	}

	private static void renderPreview(ClientWorld world, Vec3d landing) {
		DustParticleEffect dust = new DustParticleEffect(PURPLE, 1.2f);
		// 在落点（脚部）上方铺一圈紫色粉尘，呈现一个落地标记
		double cx = landing.x;
		double cy = landing.y + 0.05;
		double cz = landing.z;
		// 中心点
		world.addParticle(dust, cx, cy, cz, 0, 0, 0);
		// 周围 8 个偏移点形成圆环
		for (int i = 0; i < 8; i++) {
			double ang = (Math.PI * 2.0 / 8.0) * i;
			double ox = Math.cos(ang) * 0.45;
			double oz = Math.sin(ang) * 0.45;
			world.addParticle(dust, cx + ox, cy, cz + oz, 0, 0, 0);
		}
		// 一根向上的细柱方便从远处看到
		for (int i = 1; i <= 3; i++) {
			world.addParticle(dust, cx, cy + i * 0.4, cz, 0, 0, 0);
		}
	}

	private static void sendTeleportPacket(byte mode) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeByte(mode);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_MANCIANIMA_TELEPORT, buf);
	}

	private static boolean isMancianima(ClientPlayerEntity player) {
		try {
			IForm form = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;
			if (form == null) return false;
			Identifier id = form.getFormID();
			return id != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(id);
		} catch (Exception e) {
			return false;
		}
	}
}
