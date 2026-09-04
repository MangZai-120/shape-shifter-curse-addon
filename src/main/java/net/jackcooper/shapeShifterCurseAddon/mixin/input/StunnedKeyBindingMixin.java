package net.jackcooper.shapeShifterCurseAddon.mixin.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
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
		// 原版 1.10.1（PR #508）已为 startTransform 黑屏期接入移动冻结 mixin（PlayerMovementControlMixin
		// 的 noMoveTick 机制，getMovementSpeed 返回 0）。原版触发的变身过渡（催化剂/抑制剂/诅咒之月等）
		// 现在服务端会自动发 sendNoMoveTick 冻结移动。
		// 本 mixin 的 keybinding 屏蔽是另一层保险：除了移动键，还屏蔽技能/攻击/使用键，
		// 并消除客户端预测性滑步（装死场景尤其需要）。两层互不冲突。
		// SSC 1.9.2：TransformManager 无公开 transformTimer 字段，改用客户端变身状态标记
		// （ShapeShifterCurseFabricClient.isClientTransforming，同步包 receiveTransformState 维护，语义等价：变身过渡期间为 true）
		return client.player.hasStatusEffect(SscAddon.STUN)
				|| client.player.hasStatusEffect(SscAddon.PLAYING_DEAD)
				|| net.onixary.shapeShifterCurseFabric.client.ShapeShifterCurseFabricClient.isClientTransforming(client.player.getUuid());
	}

	@Unique
	private boolean isMovementKey(String key) {
		return key.equals("key.forward") || key.equals("key.back")
				|| key.equals("key.left") || key.equals("key.right")
				|| key.equals("key.jump") || key.equals("key.sneak") || key.equals("key.sprint");
	}

	@Unique
	private boolean isPlayerRooted() {
		// ROOTED：只锁移动/跳跃（不锁视角/技能键），荧光幼灵法阵激光蓄力/释放期使用
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.player != null && client.player.hasStatusEffect(SscAddon.ROOTED);
	}

	@Unique
	private boolean shouldBlock(String key) {
		return (isTargetKey(key) && isPlayerStunned()) || (isMovementKey(key) && isPlayerRooted());
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
