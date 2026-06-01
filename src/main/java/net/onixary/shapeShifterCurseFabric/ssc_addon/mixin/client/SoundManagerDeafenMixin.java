package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 失聪：本机玩家自身处于 DEAFEN 状态时静音所有声音；不影响其他客户端
@Mixin(SoundManager.class)
public abstract class SoundManagerDeafenMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$deafenMute(SoundInstance sound, CallbackInfo ci) {
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null && player.hasStatusEffect(SscAddon.DEAFEN)) {
			ci.cancel();
		}
	}
}
