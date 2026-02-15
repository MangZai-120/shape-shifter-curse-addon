package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public class RedFormTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        
        // Performance check: Only run logic every 20 ticks (1 second)
        if (player.age % 20 != 0) return;

        Set<String> tagsToRemove = new HashSet<>();
        boolean shouldRevert = false;
        long currentTime = player.getWorld().getTime();

        for (String tag : player.getCommandTags()) {
            if (tag.startsWith("ssc_addon_red_expire:")) {
                try {
                    long expireTime = Long.parseLong(tag.split(":")[1]);
                    long remainingTicks = expireTime - currentTime;

                    // Remaining time logic: simply check for expiration
                    if (currentTime >= expireTime) {
                        shouldRevert = true;
                        tagsToRemove.add(tag);
                    }
                } catch (NumberFormatException ignored) {
                    tagsToRemove.add(tag); // Invalid tag, remove it
                }
            }
        }

        if (!tagsToRemove.isEmpty()) {
            for (String tag : tagsToRemove) {
                player.getCommandTags().remove(tag);
            }
        }

        if (shouldRevert) {
            Identifier spFormId = new Identifier("my_addon", "familiar_fox_sp");
            PlayerFormBase spForm = RegPlayerForms.getPlayerForm(spFormId);
            if (spForm != null) {
                // Use setFormDirectly instead of handleDirectTransform to avoid animation
                TransformManager.setFormDirectly(player, spForm);
                
                // Spawn a large amount of white particles to cover the player
                if (player.getWorld() instanceof ServerWorld serverWorld) {
                     // 100 CLOUD particles + 50 POOF particles
                    serverWorld.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 100, 0.5, 1.0, 0.5, 0.1);
                    serverWorld.spawnParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.5, 1.0, 0.5, 0.1);
                }


                // Clear the negative effects immediately (just in case they were applied, though we removed that logic)
                player.removeStatusEffect(StatusEffects.SLOWNESS);
                player.removeStatusEffect(StatusEffects.JUMP_BOOST);

                // Send timeout message
                player.sendMessage(Text.translatable("message.ssc_addon.red_revert_timeout").formatted(Formatting.GREEN), false);
            }
        }
    }
}
