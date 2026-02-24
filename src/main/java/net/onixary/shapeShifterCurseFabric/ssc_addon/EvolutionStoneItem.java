package net.onixary.shapeShifterCurseFabric.ssc_addon;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;

public class EvolutionStoneItem extends Item {

    public EvolutionStoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 32;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && user instanceof PlayerEntity player) {
            Identifier playerFormID = getPlayerFormID(player);
            
            Identifier targetFormId = null;
            boolean canEvolve = false;

            if (playerFormID != null) {
                // Allow Wild Cat (Feral Cat SP) to evolve to Wild Cat SP
                if (playerFormID.equals(new Identifier("shape-shifter-curse", "feral_cat_sp"))) {
                    targetFormId = new Identifier("my_addon", "wild_cat_sp");
                    canEvolve = true;
                }
                // Allow Snow Fox 3 (permanent phase) to evolve to Snow Fox SP
                else if (playerFormID.equals(new Identifier("shape-shifter-curse", "snow_fox_3"))) {
                    targetFormId = new Identifier("my_addon", "snow_fox_sp");
                    canEvolve = true;
                }
            }

            if (canEvolve) {
                PlayerFormBase formBase = RegPlayerForms.getPlayerForm(targetFormId);
                if (formBase != null) {
                    TransformManager.handleDirectTransform(player, formBase, false);
                    player.sendMessage(Text.translatable("message.ssc_addon.evolution_stone.success").formatted(Formatting.GREEN, Formatting.BOLD), false);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    if (!player.getAbilities().creativeMode) {
                        stack.decrement(1);
                    }
                } else {
                    player.sendMessage(Text.literal("Error: Target form not found! ID: " + targetFormId).formatted(Formatting.RED), false);
                }
            } else {
                player.sendMessage(Text.translatable("message.ssc_addon.evolution_stone.no_response").formatted(Formatting.RED), true);
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable World world, java.util.List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        String key = "item.ssc_addon.evolution_stone.tooltip";
        if (I18n.hasTranslation(key)) {
            String translated = I18n.translate(key);
            for (String line : translated.split("\n")) {
                tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
            }
        } else {
            tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
        }
    }

    private Identifier getPlayerFormID(PlayerEntity player) {
        if (player == null) return null;
        PlayerFormComponent playerFormComponent = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (playerFormComponent == null) return null;
        PlayerFormBase currentForm = playerFormComponent.getCurrentForm();
        if (currentForm == null) return null;
        return currentForm.FormID;
    }
}
