package net.jackcooper.shapeShifterCurseAddon.mixin;

import com.google.gson.JsonElement;
import io.github.apace100.apoli.power.PowerTypes;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Map;

@Mixin(PowerTypes.class)
public abstract class PowerCooldownValidationMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void ssca$validateCooldowns(Map<Identifier, List<JsonElement>> powers,
                                      ResourceManager resources, Profiler profiler, CallbackInfo ci) {
        powers.forEach((id, layers) -> layers.forEach(json -> {
            try { SkillCooldownSpec.validatePower(json); }
            catch (RuntimeException ex) {
                throw new IllegalArgumentException("Invalid cooldown in power " + id + ": " + ex.getMessage(), ex);
            }
        }));
    }
}
