package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.LivingEntity;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

/**
 * 变羊期间第三人称锁定（羊了个羊，客户端，2026-09-29 用户定稿）。
 *
 * <p><b>行为</b>：本地玩家带 {@code SHEEP_FORM} 效果期间，相机视角强制第三人称背面
 *（{@code THIRD_PERSON_BACKWARD}）——第一人称相机在 1.235 眼高会钻进羊模型身体里，
 * 且玩家原模型已被 SheepFormRenderMixin 取消渲染，第一人称手臂/身体视觉全乱。
 * 效果开始时记录「原本是否第一人称」；效果结束时：<b>原本是第一人称才切回第一人称；
 * 原本就是第三人称的保持第三人称</b>（用户定稿「如果目标本身就用第三人称，结束后依然保留」）。</p>
 *
 * <p><b>实现</b>：{@code END_CLIENT_TICK} 每帧检查（不用 mixin GameOptions.getPerspective——
 * 那会被 leawind_third_person 等相机 mod 的每帧覆写打架，tick 恢复更稳）；玩家按键 F5
 * 切走的视角会在下一帧被压回，等价锁定。效果结束的检测也在这里：本地效果消失即恢复。</p>
 */
@Environment(EnvType.CLIENT)
public final class SheepFormCameraController {

	private SheepFormCameraController() {}

	/** 效果开始前的原始视角（null = 未变羊/未记录）。 */
	private static Perspective originalPerspective = null;

	public static void tick(MinecraftClient client) {
		if (client.player == null) {
			originalPerspective = null;
			return;
		}
		boolean sheep = client.player.hasStatusEffect(SscAddon.SHEEP_FORM);
		if (sheep) {
			if (originalPerspective == null) {
				// 变羊开始：记录当前视角（仅一次），随后强制第三人称背面
				originalPerspective = client.options.getPerspective();
			}
			if (client.options.getPerspective() == Perspective.FIRST_PERSON) {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			}
		} else if (originalPerspective != null) {
			// 变羊结束：原本是第一人称才切回；原本第三人称保持（用户定稿）
			if (originalPerspective == Perspective.FIRST_PERSON) {
				client.options.setPerspective(Perspective.FIRST_PERSON);
			}
			originalPerspective = null;
		}
	}

	/** 兼容占位（防未来误删警告）：LivingEntity 参数供服务端对称调用签名使用。 */
	@SuppressWarnings("unused")
	private static void unusedRef(LivingEntity e) {}
}
