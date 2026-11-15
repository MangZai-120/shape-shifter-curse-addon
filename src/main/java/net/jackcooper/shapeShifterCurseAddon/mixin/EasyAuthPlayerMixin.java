package net.jackcooper.shapeShifterCurseAddon.mixin;

import net.jackcooper.shapeShifterCurseAddon.compat.EasyAuthBalanceCompatibility;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after EasyAuth's own authentication setter; authentication rules remain owned by EasyAuth. */
@Mixin(value = ServerPlayerEntity.class, priority = 900)
public abstract class EasyAuthPlayerMixin implements EasyAuthBalanceCompatibility.AuthenticationState {
    @Override public boolean ssca$isAuthenticated() {
        return EasyAuthBalanceCompatibility.readAuthenticationState(this);
    }

    @Inject(method = "easyAuth$setAuthenticated", at = @At("TAIL"), remap = false)
    private void ssca$syncAfterAuthentication(boolean authenticated, CallbackInfo ci) {
        EasyAuthBalanceCompatibility.authenticationChanged((ServerPlayerEntity) (Object) this, authenticated);
    }
}
