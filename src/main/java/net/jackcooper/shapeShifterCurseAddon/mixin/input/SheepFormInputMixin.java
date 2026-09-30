package net.jackcooper.shapeShifterCurseAddon.mixin.input;

import net.minecraft.client.MinecraftClient;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 变羊期间客户端输入封锁（羊了个羊，jackcooper，2026-09-29）：
 * 与 {@code StunnedInputMixin} 同三注入点——禁攻击（doAttack）、禁使用物品（doItemUse）、
 * 禁破坏方块（handleBlockBreaking）。服务端兜底在 {@code SheepFormGuard}（Fabric 三回调）。
 * 羊仍可移动/转身/跳（不清移动输入）——「变成一只没攻击力的羊」而非木桩。
 */
@Mixin(MinecraftClient.class)
public class SheepFormInputMixin {

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void ssca$sheepNoAttack(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.player.hasStatusEffect(SscAddon.SHEEP_FORM)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void ssca$sheepNoItemUse(CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.player.hasStatusEffect(SscAddon.SHEEP_FORM)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void ssca$sheepNoBreaking(boolean breaking, CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.player.hasStatusEffect(SscAddon.SHEEP_FORM) && breaking) {
			ci.cancel();
		}
	}
}
