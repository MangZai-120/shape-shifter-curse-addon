package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.input;

import net.minecraft.client.MinecraftClient;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class StunnedInputMixin {

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.player.hasStatusEffect(SscAddon.STUN)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void onDoItemUse(CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.player.hasStatusEffect(SscAddon.STUN)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.player.hasStatusEffect(SscAddon.STUN) && breaking) {
			ci.cancel();
		}

	}
}
