package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.jackcooper.shapeShifterCurseAddon.client.SscForceSneakClient;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2C;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModPacketsS2C.class)
public abstract class SscForceSneakReceiveMixin {
    @WrapOperation(method = "*", remap = false, at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC,
            target = "Lnet/onixary/shapeShifterCurseFabric/client/ClientPlayerStateManager;shouldForceSneak:Z", remap = false))
    private static void ssca$receiveForcedSneak(boolean value, Operation<Void> original) {
        original.call(SscForceSneakClient.receive(value));
    }
}
