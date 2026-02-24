package net.onixary.shapeShifterCurseFabric.ssc_addon;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpUpgradeItem extends Item {

    /** Map storing the mapping from Base Form ID to Target SP Form ID
     Key: Current Origin ID (Identifier), Value: Target Origin ID (Identifier)
     */
    protected static final Map<Identifier, Identifier> UPGRADE_MAP = new HashMap<>();

    public static final RegistryKey<DamageType> CURSED_EROSION = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("my_addon", "cursed_erosion"));
    public static final RegistryKey<DamageType> CURSED_BURST = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("my_addon", "cursed_burst"));

    static {
        // Registers the SP evolution for Familiar Fox
        // From: shape-shifter-curse:form_familiar_fox_3
        // To:   my_addon:form_familiar_fox_sp
        registerUpgrade("shape-shifter-curse", "familiar_fox_3", "my_addon", "familiar_fox_sp");
        registerUpgrade("shape-shifter-curse", "axolotl_3", "my_addon", "axolotl_sp");
        
        // Future forms can be added here easily like:
        // registerUpgrade("shape-shifter-curse", "form_axolotl_3", "my_addon", "axolotl_sp");
    }

    public static void registerUpgrade(String fromNamespace, String fromPath, String toNamespace, String toPath) {
        UPGRADE_MAP.put(new Identifier(fromNamespace, fromPath), new Identifier(toNamespace, toPath));
    }

    public SpUpgradeItem(Settings settings) {
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
            Identifier targetFormId = getTargetFormId(player);
            boolean isCursedMoon = CursedMoon.isCursedMoon(world);
            boolean isValidForm = targetFormId != null;
            boolean isAlreadySP = isAlreadySP(player);

            if (isCursedMoon && isAlreadySP) {
                // Deadly Resonance: Already SP + Cursed Moon
                player.sendMessage(Text.translatable("message.ssc_addon.evolution.fail.already_sp").formatted(Formatting.RED, Formatting.ITALIC), false);
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
                world.createExplosion(player, player.getX(), player.getY(), player.getZ(), 6.0f, World.ExplosionSourceType.BLOCK);
                player.damage(world.getDamageSources().create(CURSED_BURST), 100000.0f);
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
            } else if (isValidForm && isCursedMoon) {
                // Success: Base Form + Cursed Moon
                
                // 5% Chance for Red Form (when upgrading to SP Fox)
                if (targetFormId.equals(new Identifier("my_addon", "familiar_fox_sp")) && world.random.nextFloat() < 0.05f) {
                    Identifier redFormId = new Identifier("my_addon", "familiar_fox_red");
                    PlayerFormBase redForm = RegPlayerForms.getPlayerForm(redFormId);
                    if (redForm != null) {
                        TransformManager.handleDirectTransform(player, redForm, false);

                        // 10 Minutes = 12000 ticks
                        long expireTime = world.getTime() + 12000;
                        player.addCommandTag("ssc_addon_red_expire:" + expireTime);

                        player.sendMessage(Text.translatable("message.ssc_addon.red_transformation_special").formatted(Formatting.GREEN), false);
                    }
                }


                PlayerFormBase formBase = RegPlayerForms.getPlayerForm(targetFormId);
                if (formBase != null) {
                    TransformManager.handleDirectTransform(player, formBase, false);
                    player.sendMessage(Text.translatable("message.ssc_addon.evolution.success").formatted(Formatting.GREEN, Formatting.ITALIC), false);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    if (!player.getAbilities().creativeMode) {
                        stack.decrement(1);
                    }
                }
            } else {
                // Failure Logic
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }

                if (!isCursedMoon) {
                    // No Moon Condition
                    if (isValidForm || isAlreadySP) {
                        // Correct Form (or SP), but No Moon
                        player.sendMessage(Text.translatable("message.ssc_addon.evolution.fail.no_moon").formatted(Formatting.RED, Formatting.ITALIC), false);
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
                        // No damage in no moon condition
                        // player.damage(player.getDamageSources().magic(), 0.0f); 
                    } else {
                        // Wrong Form AND No Moon -> Fatal Fail
                        player.sendMessage(Text.translatable("message.ssc_addon.evolution.fail.both").formatted(Formatting.RED, Formatting.ITALIC), false);
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
                        // No damage in no moon condition
                    }
                } else {
                    // Cursed Moon Present, but Wrong Form (and not SP)
                    player.sendMessage(Text.translatable("message.ssc_addon.evolution.fail.bad_form").formatted(Formatting.RED, Formatting.ITALIC), false);
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    player.damage(world.getDamageSources().create(CURSED_EROSION), 10.0f);
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 20, 1));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20 * 20, 1));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 20 * 20, 1));
                }
            }
        }
        return stack;
    }
    
    // Helper to check if player currently has a form that can be upgraded
    /*
    private boolean canUpgrade(PlayerEntity player) {
        return getTargetFormId(player) != null;
    }
     */

    private boolean isAlreadySP(PlayerEntity player) {
        // OriginComponent component = ModComponents.ORIGIN.get(player);
        // for (Identifier targetFormId : UPGRADE_MAP.values()) {
        //     if (component.getOrigins().values().stream().anyMatch(o -> o.getIdentifier().equals(targetFormId))) {
        //         return true;
        //     }
        // }
        // return false;
        Identifier playerFormID = getPlayerFormID(player);
        if (playerFormID == null) return false;
        for (Identifier id : UPGRADE_MAP.values()) {
            if (id.equals(playerFormID)) {
                return true;
            }
        }
        return false;
    }

    private Identifier getPlayerFormID(PlayerEntity player) {
        // 玩家本身为null返回null
        if (player == null) return null;
        // 组件为null直接返回null
        PlayerFormComponent playerFormComponent = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (playerFormComponent == null) return null;
        // 获取当前形态，形态为null返回null
        PlayerFormBase currentForm = playerFormComponent.getCurrentForm();
        if (currentForm == null) return null;
        // 形态的FormID为null返回null
        return currentForm.FormID;
    }

    private Identifier getTargetFormId(PlayerEntity player) {
        // OriginComponent component = ModComponents.ORIGIN.get(player);
        // // Iterate through all origins the player has to see if any match our upgrade map
        // for (Map.Entry<Identifier, Identifier> entry : UPGRADE_MAP.entrySet()) {
        //     Identifier currentFormId = entry.getKey();
        //     Identifier targetFormId = entry.getValue();
        //
        //     // Check if player has the 'current' origin
        //     // We check specifically on the cursed_origin layer, or generally if layers aren't strict in the map
        //     // For safety, let's check if the player possesses this origin in ANY layer,
        //     // though typically it's the main layer.
        //     if (component.getOrigins().values().stream().anyMatch(o -> o.getIdentifier().equals(currentFormId))) {
        //         return targetFormId;
        //     }
        // }
        // return null;
        Identifier playerFormID = getPlayerFormID(player);
        // playerFormID为null时，直接返回null，不进入遍历
        if (playerFormID == null) return null;
        for (Map.Entry<Identifier, Identifier> entry : UPGRADE_MAP.entrySet()) {
            Identifier currentFormId = entry.getKey();
            if (playerFormID.equals(currentFormId)) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("item.ssc_addon.sp_upgrade_thing.tooltip"));
    }
}
