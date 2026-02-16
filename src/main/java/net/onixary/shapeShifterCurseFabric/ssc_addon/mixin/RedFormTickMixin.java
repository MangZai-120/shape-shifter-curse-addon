package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtCompound;

import java.util.HashSet;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public class RedFormTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        
        // Performance check: Only run logic every 20 ticks (1 second)
        if (player.age % 20 != 0) return;

        boolean isCursedMoon = CursedMoon.isCursedMoon(player.getWorld());
        
        // Reset the attempt tag if it is not Cursed Moon
        if (!isCursedMoon && player.getCommandTags().contains("ssc_addon_red_attempted")) {
            player.getCommandTags().remove("ssc_addon_red_attempted");
        }

        // Potion Bag Logic
        PlayerFormBase currentForm = FormAbilityManager.getForm(player);
        boolean isRedForm = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_red"));

        // SP Form + Cursed Moon Transformation Logic
        if (currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "familiar_fox_sp")) && isCursedMoon && !player.getCommandTags().contains("ssc_addon_red_attempted")) {
            player.addCommandTag("ssc_addon_red_attempted");
            // 5% Chance to transform to Red
            if (player.getRandom().nextFloat() < 0.05f) {
                Identifier redFormId = new Identifier("my_addon", "familiar_fox_red");
                PlayerFormBase redForm = RegPlayerForms.getPlayerForm(redFormId);
                if (redForm != null) {
                    TransformManager.handleDirectTransform(player, redForm, false);
                    
                    // 10 Minutes = 12000 ticks
                    long expireTime = player.getWorld().getTime() + 12000; 
                    player.addCommandTag("ssc_addon_red_expire:" + expireTime);
                    
                    player.sendMessage(Text.translatable("message.ssc_addon.red_transformation_special").formatted(Formatting.GREEN), false);
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    return; // Exit after successful transformation
                }
            }
        }

        if (isRedForm) {
            ItemStack stackInSlot8 = player.getInventory().getStack(8);
            if (!stackInSlot8.isOf(SscAddon.POTION_BAG)) {
                // If the player doesn't have the bag in slot 8
                if (!stackInSlot8.isEmpty()) {
                    // Try to move existing item to main inventory
                    if (!player.getInventory().insertStack(stackInSlot8)) {
                        // Inventory full, drop it
                        player.dropItem(stackInSlot8, false, true);
                    }
                    player.getInventory().setStack(8, ItemStack.EMPTY);
                }
                // Give Potion Bag
                player.getInventory().setStack(8, new ItemStack(SscAddon.POTION_BAG));
            }
        } else {
             // Not Red Form: Remove any Potion Bag found
             for (int i = 0; i < player.getInventory().size(); ++i) {
                 ItemStack stack = player.getInventory().getStack(i);
                 if (stack.isOf(SscAddon.POTION_BAG)) {
                     // Found a bag, drop its contents
                     if (stack.hasNbt() && stack.getNbt().contains("Items", 9)) {
                         NbtList list = stack.getNbt().getList("Items", 10);
                         for (int j = 0; j < list.size(); ++j) {
                             NbtCompound itemTag = list.getCompound(j);
                             ItemStack contentStack = ItemStack.fromNbt(itemTag);
                             if (!contentStack.isEmpty()) {
                                 player.dropItem(contentStack, false, true);
                             }
                         }
                     }
                     // Remove bag itself
                     player.getInventory().setStack(i, ItemStack.EMPTY);
                 }
             }
        }

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
