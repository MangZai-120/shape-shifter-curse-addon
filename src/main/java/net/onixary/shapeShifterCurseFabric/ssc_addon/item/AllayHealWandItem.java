package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * SP悦灵单体治疗物品
 * 手持时准星对准20格内生物会高亮（白色框），
 * 右键治疗目标4血（2颗心），消耗12点能量，2.5秒冷却
 */
public class AllayHealWandItem extends Item {

    public static final float HEAL_AMOUNT = 4.0f;
    public static final int COOLDOWN_TICKS = 50; // 2.5 seconds
    public static final double MAX_RANGE = 20.0;
    public static final int MANA_COST = 12;

    private static final Identifier MANA_RESOURCE_ID = new Identifier("my_addon", "form_allay_sp_mana_resource");
    private static final Identifier MANA_COOLDOWN_ID = new Identifier("my_addon", "form_allay_sp_mana_cooldown_resource");

    public AllayHealWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            // Find the targeted entity
            LivingEntity target = getTargetedEntity(serverPlayer);

            if (target != null) {
                // Check line of sight (no block obstruction)
                boolean hasLineOfSight = hasLineOfSight(serverPlayer, target);

                if (hasLineOfSight) {
                    // Check mana
                    int currentMana = getManaValue(serverPlayer);
                    if (currentMana < MANA_COST) {
                        serverPlayer.sendMessage(Text.translatable("item.ssc_addon.allay_heal_wand.no_mana").formatted(Formatting.RED), true);
                        return TypedActionResult.fail(stack);
                    }

                    // Consume mana
                    setManaValue(serverPlayer, currentMana - MANA_COST);

                    // Trigger mana cooldown
                    triggerManaCooldown(serverPlayer);

                    // Heal the target
                    target.heal(HEAL_AMOUNT);

                    // Spawn heal particles
                    ServerWorld serverWorld = (ServerWorld) world;
                    serverWorld.spawnParticles(ParticleTypes.HEART,
                            target.getX(), target.getY() + target.getHeight() + 0.5, target.getZ(),
                            5, 0.3, 0.3, 0.3, 0.01);

                    // Play heal sound
                    // User hears private sound
                    serverPlayer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                    // Target and nearby players hear positional sound (exclude user to avoid double sound)
                    world.playSound(serverPlayer, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);

                    // Set cooldown
                    user.getItemCooldownManager().set(this, COOLDOWN_TICKS);

                    return TypedActionResult.success(stack);
                } else {
                    // Target is behind a wall
                    serverPlayer.sendMessage(Text.translatable("item.ssc_addon.allay_heal_wand.blocked").formatted(Formatting.RED), true);
                }
            } else {
                serverPlayer.sendMessage(Text.translatable("item.ssc_addon.allay_heal_wand.no_target").formatted(Formatting.GRAY), true);
            }
        }

        return TypedActionResult.pass(stack);
    }

    /**
     * Get the entity the player is looking at within MAX_RANGE
     */
    @Nullable
    public static LivingEntity getTargetedEntity(PlayerEntity player) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0f);
        Vec3d endPos = eyePos.add(lookDir.multiply(MAX_RANGE));

        // Get all entities in the range
        Box searchBox = player.getBoundingBox().expand(MAX_RANGE);
        Predicate<Entity> predicate = entity -> !entity.isSpectator() && entity.canHit() && entity instanceof LivingEntity && entity != player;

        double closestDist = MAX_RANGE * MAX_RANGE;
        LivingEntity closestEntity = null;

        for (Entity entity : player.getWorld().getOtherEntities(player, searchBox, predicate)) {
            Box entityBox = entity.getBoundingBox().expand(entity.getTargetingMargin());
            var optional = entityBox.raycast(eyePos, endPos);
            if (optional.isPresent()) {
                double dist = eyePos.squaredDistanceTo(optional.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closestEntity = (LivingEntity) entity;
                }
            }
        }

        return closestEntity;
    }

    /**
     * Check if there's a clear line of sight between player and target (no blocks in the way)
     */
    public static boolean hasLineOfSight(PlayerEntity player, LivingEntity target) {
        Vec3d eyePos = player.getEyePos();
        Vec3d targetPos = target.getEyePos();

        HitResult blockHit = player.getWorld().raycast(new RaycastContext(
                eyePos, targetPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        // If the block hit is beyond the target or missed, we have line of sight
        if (blockHit.getType() == HitResult.Type.MISS) {
            return true;
        }

        double blockDist = eyePos.squaredDistanceTo(blockHit.getPos());
        double targetDist = eyePos.squaredDistanceTo(targetPos);

        return blockDist >= targetDist;
    }

    // ===== Mana resource read/write =====

    private static int getManaValue(ServerPlayerEntity player) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(MANA_RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                return variablePower.getValue();
            }
        } catch (Exception e) {
            // Resource not found
        }
        return 0;
    }

    private static void setManaValue(ServerPlayerEntity player, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(MANA_RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(Math.max(0, value));
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
            // Resource not found
        }
    }

    private static void triggerManaCooldown(ServerPlayerEntity player) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(MANA_COOLDOWN_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(70); // 3.5 seconds cooldown
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
            // Resource not found
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.ssc_addon.allay_heal_wand.tooltip").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.ssc_addon.allay_heal_wand.tooltip.exclusive").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
