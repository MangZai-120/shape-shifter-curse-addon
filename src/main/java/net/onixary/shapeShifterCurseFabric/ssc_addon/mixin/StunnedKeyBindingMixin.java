package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    private boolean isTargetKey(String key) {
        return key.equals("key.origins.primary_active") || 
               key.equals("key.origins.secondary_active") ||
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

    private boolean isPlayerStunned() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && (client.player.hasStatusEffect(SscAddon.STUN) || client.player.hasStatusEffect(SscAddon.PLAYING_DEAD));
    }

    @Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
    private void onWasPressed(CallbackInfoReturnable<Boolean> cir) {
        KeyBinding binding = (KeyBinding) (Object) this;
        String key = binding.getTranslationKey();
        
        if (isTargetKey(key) && isPlayerStunned()) {
            // Clear any buffered input so it doesn't trigger later
            this.timesPressed = 0;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void onIsPressed(CallbackInfoReturnable<Boolean> cir) {
        KeyBinding binding = (KeyBinding) (Object) this;
        String key = binding.getTranslationKey();
        
        if (isTargetKey(key) && isPlayerStunned()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setPressed", at = @At("HEAD"), cancellable = true)
    private void onSetPressed(boolean pressed, CallbackInfo ci) {
        KeyBinding binding = (KeyBinding) (Object) this;
        String key = binding.getTranslationKey();
        
        // If trying to press the key (pressed=true) while stunned, block it AND ensure internal state is false
        if (pressed && isTargetKey(key) && isPlayerStunned()) {
            this.pressed = false;
            ci.cancel();
        }
    }

    @Inject(method = "matchesKey", at = @At("HEAD"), cancellable = true)
    private void onMatchesKey(int key, int scancode, CallbackInfoReturnable<Boolean> cir) {
        KeyBinding binding = (KeyBinding) (Object) this;
        String translationKey = binding.getTranslationKey();
        
        if (isTargetKey(translationKey) && isPlayerStunned()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "matchesMouse", at = @At("HEAD"), cancellable = true)
    private void onMatchesMouse(int button, CallbackInfoReturnable<Boolean> cir) {
        KeyBinding binding = (KeyBinding) (Object) this;
        String translationKey = binding.getTranslationKey();
        
        if (isTargetKey(translationKey) && isPlayerStunned()) {
            cir.setReturnValue(false);
        }
    }
}
