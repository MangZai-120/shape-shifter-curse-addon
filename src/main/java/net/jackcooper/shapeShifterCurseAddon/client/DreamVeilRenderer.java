package net.jackcooper.shapeShifterCurseAddon.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

/**
 * 食梦魔「入梦」目标屏幕粉色晕影 —— 客户端 HUD 渲染器。
 *
 * <p>仿原版反胃绿色边框（{@code GameRenderer.renderNausea}，1.20.1 反汇编核对）：
 * 复用原版灰度渐晕贴图 {@code textures/misc/nausea.png}（256×256，边缘白中心黑、无色），
 * 经 {@code setShaderColor} 粉色染色 + ONE/ONE 加法混合，只有贴图白色边缘（=屏幕四周）
 * 向画面叠加粉光，中心黑色不叠加 → 视觉上是一圈粉色晕影边框。
 * 淡入时贴图从 2 倍缩到 1 倍（同原版），边框从屏幕外「收拢」进来。</p>
 *
 * <p>同步：服务端在目标入梦 / 刷新时向<b>目标本人</b>发
 * {@link SscAddonNetworking#PACKET_DREAM_VEIL}（varint durationTicks）；
 * duration&le;0 表示提前出梦（食梦魔死亡/断线/失形）立即熄灭。
 * 自然到期由客户端按收到时长自行淡出，无需服务端再发包。</p>
 */
@Environment(EnvType.CLIENT)
public final class DreamVeilRenderer implements HudRenderCallback {

	/** 复用原版反胃灰度渐晕贴图（无色，可任意染色；资源包替换原版贴图会连带生效）。 */
	private static final Identifier VEIL_TEXTURE = new Identifier("textures/misc/nausea.png");

	/** 粉色染色，与入梦粉红描边同色源 0xFF6EC7 → (1.0, 0.43, 0.78)。 */
	private static final float TINT_R = 1.0F;
	private static final float TINT_G = 0.43F;
	private static final float TINT_B = 0.78F;
	/** 加法混合下的整体亮度增益（实机偏亮/偏暗时调此常量）。 */
	private static final float BRIGHTNESS = 0.55F;
	/** 淡入/淡出时长（tick，1.25 秒）。 */
	private static final int FADE_TICKS = 25;

	/** 本次入梦晕影开始的客户端世界时间（-1 = 未激活）。 */
	private static long startWorldTime = -1L;
	/** 到期的客户端世界时间（-1 = 未激活）。 */
	private static long endWorldTime = -1L;

	private DreamVeilRenderer() {
	}

	public static void register() {
		// S2C：入梦晕影状态（仅目标本人收到；payload = duration + 入梦你的食梦魔 UUID）
		ClientPlayNetworking.registerGlobalReceiver(SscAddonNetworking.PACKET_DREAM_VEIL,
				(client, handler, buf, responseSender) -> {
					int duration = buf.readVarInt();
					java.util.UUID nightmareUuid = buf.readUuid();
					client.execute(() -> {
						if (client.world == null) return;
						long now = client.world.getTime();
						if (duration <= 0) {
							// 该食梦魔的入梦关系结束：移除镜像（另一食梦魔关系不受影响）
							net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager
									.clientUpdateDreamedBy(nightmareUuid, -1L);
							// 晕影是否熄灭取决于是否还有其它入梦关系 → 简化：无任何关系时熄灭
							if (!net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager
									.clientHasAnyDream(now)) {
								startWorldTime = -1L;
								endWorldTime = -1L;
							}
							return;
						}
						// 维护「被谁入梦」镜像（供 entity_glow 透视拦截）
						net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager
								.clientUpdateDreamedBy(nightmareUuid, now + duration);
						// 已入梦再被刷新：只延长到期，不重置开始（避免淡入动画重播）
						if (startWorldTime < 0L || endWorldTime <= now) {
							startWorldTime = now;
						}
						endWorldTime = now + duration;
					});
				});
		// 断线/换服清理，防重连后残留旧晕影与入梦镜像
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			startWorldTime = -1L;
			endWorldTime = -1L;
			net.jackcooper.shapeShifterCurseAddon.ability.NightmareDreamManager.clientClear();
		});
		HudRenderCallback.EVENT.register(new DreamVeilRenderer());
	}

	@Override
	public void onHudRender(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		// 死亡界面不渲染（纯净出梦视觉）；与原版反胃一致：不受 hudHidden 影响
		if (client.world == null || client.player == null || client.player.isDead()) return;
		if (startWorldTime < 0L) return;

		long now = client.world.getTime();
		if (endWorldTime <= now) {
			// 自然到期：本地自清理（服务端不再发结束包）
			startWorldTime = -1L;
			endWorldTime = -1L;
			return;
		}

		// 平滑强度：淡入 → 保持 → 淡出（用 tickDelta 插值消抖）
		float progressNow = now + tickDelta;
		float fadeIn = (progressNow - startWorldTime) / FADE_TICKS;
		float fadeOut = (endWorldTime - progressNow) / FADE_TICKS;
		float strength = MathHelper.clamp(Math.min(fadeIn, fadeOut), 0.0F, 1.0F);
		if (strength <= 0.0F) return;

		int w = context.getScaledWindowWidth();
		int h = context.getScaledWindowHeight();

		// 仿原版：以屏幕中心缩放（强度越低贴图越大、白边推出屏幕外），淡入时边框从四周收拢
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		float scale = MathHelper.lerp(strength, 2.0F, 1.0F);
		matrices.translate(w / 2.0F, h / 2.0F, 0.0F);
		matrices.scale(scale, scale, scale);
		matrices.translate(-w / 2.0F, -h / 2.0F, 0.0F);

		// 状态序列与原版 renderNausea 完全一致（进/出成对，不污染后续 HUD 渲染）
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE,
				GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
		float a = strength * BRIGHTNESS;
		context.setShaderColor(TINT_R * a, TINT_G * a, TINT_B * a, 1.0F);
		// 全贴图（256×256）拉伸铺满 w×h：边缘白=屏幕四周叠粉光，中心黑=不叠加
		context.drawTexture(VEIL_TEXTURE, 0, 0, 0.0F, 0.0F, w, h, w, h);
		context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		matrices.pop();
	}
}
