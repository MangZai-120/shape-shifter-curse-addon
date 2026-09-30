package net.jackcooper.shapeShifterCurseAddon.mixin.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public class StunnedKeyBindingMixin {

	@Shadow
	private int timesPressed;

	@Shadow
	private boolean pressed;

	@Unique
	private boolean isTargetKey(String key) {
		return key.equals("key.shape-shifter-curse.active_skill_1") ||
				key.equals("key.shape-shifter-curse.active_skill_2") ||
				key.equals("key.ssc_addon.sp_primary") ||
				key.equals("key.ssc_addon.sp_secondary") ||
				key.equals("key.use") ||
				key.equals("key.attack") ||
				// 移动 / 跳跃 / 潜行 / 疾跑：装死(PLAYING_DEAD)期间必须彻底屏蔽，否则客户端
				// ClientPlayerEntity 仍会读到 movementInput 非零，travel(input) 内 updateVelocity
				// 重新给玩家加水平速度，导致 PlayingDeadEffect 服务端每 tick 清零的 velocity
				// 又被客户端推回去，玩家仍能 WASD 移动。STUN 已用 -100% 移速属性兜底，多一层
				// keybinding 屏蔽不会有副作用且能消除客户端预测性滑步。
				key.equals("key.forward") ||
				key.equals("key.back") ||
				key.equals("key.left") ||
				key.equals("key.right") ||
				key.equals("key.jump") ||
				key.equals("key.sneak") ||
				key.equals("key.sprint") ||
				key.equals("key.tacz.shoot.desc") ||
				key.equals("key.tacz.aim.desc") ||
				key.equals("key.tacz.inspect.desc") ||
				key.equals("key.tacz.reload.desc") ||
				key.equals("key.tacz.fire_select.desc") ||
				key.equals("key.tacz.crawl.desc") ||
				key.equals("key.tacz.refit.desc") ||
				key.equals("key.tacz.zoom.desc") ||
				key.equals("key.tacz.melee.desc");
	}

	@Unique
	private boolean isPlayerStunned() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return false;
		}
		return client.player.hasStatusEffect(SscAddon.STUN)
				|| client.player.hasStatusEffect(SscAddon.PLAYING_DEAD)
				|| TransformManager.transformTimer >= 0;
	}

	@Unique
	private boolean isPlayerSheep() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.player != null
				&& client.player.hasStatusEffect(SscAddon.SHEEP_FORM);
	}

	/** 变羊期间屏蔽的键（2026-09-29 用户定稿「羊能走但不能打/放技能」）：技能/攻击/使用/TACZ 枪械链，
	 *  **不含移动键**——移动键屏蔽会让 movementInput 拿不到按键直接完全不能走（首轮实机踩坑：
	 *  SHEEP_FORM 挂进 isPlayerStunned 后 isTargetKey 里的移动键全被 isPressed/setPressed 屏蔽），
	 *  限速由服务端移速上限修饰符负责（SheepFormRestoreHandler）。 */
	@Unique
	private boolean isSheepBlockedKey(String key) {
		return key.equals("key.shape-shifter-curse.active_skill_1") ||
				key.equals("key.shape-shifter-curse.active_skill_2") ||
				key.equals("key.ssc_addon.sp_primary") ||
				key.equals("key.ssc_addon.sp_secondary") ||
				key.equals("key.use") ||
				key.equals("key.attack") ||
				key.equals("key.tacz.shoot.desc") ||
				key.equals("key.tacz.aim.desc") ||
				key.equals("key.tacz.inspect.desc") ||
				key.equals("key.tacz.reload.desc") ||
				key.equals("key.tacz.fire_select.desc") ||
				key.equals("key.tacz.crawl.desc") ||
				key.equals("key.tacz.refit.desc") ||
				key.equals("key.tacz.zoom.desc") ||
				key.equals("key.tacz.melee.desc");
	}

	@Unique
	private boolean isPlayerRooted() {
		// ROOTED：只锁移动/跳跃（不锁视角/技能键），荧光幼灵法阵激光蓄力/释放期使用
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.player != null && client.player.hasStatusEffect(SscAddon.ROOTED);
	}

	@Unique
	private boolean isMovementKey(String key) {
		return key.equals("key.forward") || key.equals("key.back")
				|| key.equals("key.left") || key.equals("key.right")
				|| key.equals("key.jump") || key.equals("key.sneak") || key.equals("key.sprint");
	}

	@Unique
	private boolean shouldBlock(String key) {
		return (isTargetKey(key) && isPlayerStunned())
				|| (isMovementKey(key) && isPlayerRooted())
				|| (isSheepBlockedKey(key) && isPlayerSheep());
	}

	@Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
	private void onWasPressed(CallbackInfoReturnable<Boolean> cir) {
		KeyBinding binding = (KeyBinding) (Object) this;
		String key = binding.getTranslationKey();

		if (isTargetKey(key) && isPlayerStunned()) {
			// Clear any buffered input so it doesn't trigger later
			this.timesPressed = 0;
			cir.setReturnValue(false);
		} else if (isMovementKey(key) && isPlayerRooted()) {
			this.timesPressed = 0;
			cir.setReturnValue(false);
		} else if (isSheepBlockedKey(key) && isPlayerSheep()) {
			this.timesPressed = 0;
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
	private void onIsPressed(CallbackInfoReturnable<Boolean> cir) {
		KeyBinding binding = (KeyBinding) (Object) this;
		String key = binding.getTranslationKey();

		if (shouldBlock(key)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "setPressed", at = @At("HEAD"), cancellable = true)
	private void onSetPressed(boolean pressed, CallbackInfo ci) {
		KeyBinding binding = (KeyBinding) (Object) this;
		String key = binding.getTranslationKey();

		// If trying to press the key (pressed=true) while stunned, block it AND ensure internal state is false
		if (pressed && shouldBlock(key)) {
			this.pressed = false;
			ci.cancel();
		}
	}

	@Inject(method = "matchesKey", at = @At("HEAD"), cancellable = true)
	private void onMatchesKey(int key, int scancode, CallbackInfoReturnable<Boolean> cir) {
		KeyBinding binding = (KeyBinding) (Object) this;
		String translationKey = binding.getTranslationKey();

		if (shouldBlock(translationKey)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "matchesMouse", at = @At("HEAD"), cancellable = true)
	private void onMatchesMouse(int button, CallbackInfoReturnable<Boolean> cir) {
		KeyBinding binding = (KeyBinding) (Object) this;
		String translationKey = binding.getTranslationKey();

		if (shouldBlock(translationKey)) {
			cir.setReturnValue(false);
		}
	}
}
