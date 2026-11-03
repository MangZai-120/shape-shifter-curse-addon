package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationDrivers;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = DefaultModelAnimationSystem.class,remap = false)
public abstract class FrozenSscProceduralAnimationMixin {
    @WrapMethod(method = "processAnimation")
    private void ssca$resumeProceduralPhase(FormRenderer form,FormModel model,PlayerEntityRenderer renderer,
            PlayerEntity player,float limbAngle,float limbDistance,float delta,float animationProgress,
            float headYaw,float headPitch,Operation<Void> original) {
        int age = player.age;
        // SSC tail/wing math reads age directly. Limit the adjustment to this calculation, outside Gecko playback.
        player.age = (int)Math.floor(FrozenAnimationDrivers.vanillaTime(player,age + delta) - delta);
        try { original.call(form,model,renderer,player,limbAngle,limbDistance,delta,animationProgress,headYaw,headPitch); }
        finally { player.age = age; }
    }
}
