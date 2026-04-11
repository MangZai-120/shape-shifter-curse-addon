package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.PowerType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SkillBlocker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ActiveCooldownPower.class)
public class ActiveSelfPowerMixin {

    @Unique
    private static final String SHAPE_SHIFTER_CURSE_NAMESPACE = "shape-shifter-curse";

    @Unique
    private PowerType<?> type;

    @Unique
    private LivingEntity entity;

    @Inject(method = "onUse()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSkillUse(CallbackInfo cir) {
        if (this.type == null) {
            return;
        }

        Identifier powerId = this.type.getIdentifier();
        if (powerId == null) {
            return;
        }

        if (!SHAPE_SHIFTER_CURSE_NAMESPACE.equals(powerId.getNamespace())) {
            return;
        }

        if (!(this.entity instanceof ServerPlayerEntity player)) {
            return;
        }

        // Parse "namespace:form:skill" to get form and skill parts
        String fullId = powerId.toString();
        String[] parts = fullId.split(":", 3);
        if (parts.length < 3) {
            return;
        }
        // parts[0] = namespace (shape-shifter-curse)
        // parts[1] = form (e.g., snow_fox)
        // parts[2] = skill (e.g., melee_primary)
        String form = parts[1];
        String skill = parts[2];

        if (SkillBlocker.isSkillBlocked(player, form, skill)) {
            cir.cancel();
        }
    }

    @Unique
    public void setType(PowerType<?> type) {
        this.type = type;
    }

    @Unique
    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }
}
