package net.jackcooper.shapeShifterCurseAddon.mixin.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class StunnedInputMixin {

	@Shadow public ClientPlayerEntity player;

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
		if (player != null && player.hasStatusEffect(SscAddon.STUN)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void onDoItemUse(CallbackInfo ci) {
		if (player != null && player.hasStatusEffect(SscAddon.STUN)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
		if (player != null && player.hasStatusEffect(SscAddon.STUN) && breaking) {
			ci.cancel();
		}
	}
}
