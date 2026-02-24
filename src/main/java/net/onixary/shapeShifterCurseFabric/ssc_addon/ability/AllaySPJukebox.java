package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.AllayJukeboxItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SP悦灵唱片机区域效果Tick逻辑
 * - 回血模式：每5秒给20格内白名单生物回1HP
 * - 加速模式：持续给20格内白名单生物+10%移速
 * - 每秒消耗1点充能
 * - 播放自定义音乐
 */
public class AllaySPJukebox {

    public static final double RANGE = 20.0;
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("a3b4c5d6-e7f8-9012-3456-789abcdef012");
    private static final String SPEED_MODIFIER_NAME = "allay_jukebox_speed";
    private static final double SPEED_BONUS = 0.10; // 10% speed

    // Track per-player: -1 = not playing, 0 = speed music, 1 = heal music
    private static final Map<UUID, Integer> playerMusicState = new HashMap<>();

    /**
     * Stop all jukebox music for a player by sending StopSoundS2CPacket
     */
    public static void stopAllMusic(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new StopSoundS2CPacket(SscAddon.ALLAY_HEAL_MUSIC_ID, SoundCategory.RECORDS));
        player.networkHandler.sendPacket(new StopSoundS2CPacket(SscAddon.ALLAY_SPEED_MUSIC_ID, SoundCategory.RECORDS));
        playerMusicState.put(player.getUuid(), -1);
    }

    /**
     * Stop old music and immediately play the new mode's music from the beginning
     */
    private static void switchMusic(ServerPlayerEntity player, int newMode) {
        // Stop both old sounds first
        player.networkHandler.sendPacket(new StopSoundS2CPacket(SscAddon.ALLAY_HEAL_MUSIC_ID, SoundCategory.RECORDS));
        player.networkHandler.sendPacket(new StopSoundS2CPacket(SscAddon.ALLAY_SPEED_MUSIC_ID, SoundCategory.RECORDS));

        // Play new music immediately
        SoundEvent newSound = (newMode == AllayJukeboxItem.MODE_SPEED) ? SscAddon.ALLAY_SPEED_MUSIC_EVENT : SscAddon.ALLAY_HEAL_MUSIC_EVENT;
        ServerWorld serverWorld = player.getServerWorld();
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                newSound, SoundCategory.RECORDS, 0.2f, 1.0f);

        playerMusicState.put(player.getUuid(), newMode);
    }

    /**
     * Called every tick for each allay_sp player from the server tick event
     */
    public static void tick(ServerPlayerEntity player) {
        PlayerFormBase currentForm = FormAbilityManager.getForm(player);
        boolean isAllaySp = currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "allay_sp"));

        // Check if cleanup is needed (if form changed OR item is missing/inactive)
        // Note: we check form first. If not Allay SP, we just cleanup and return.
        if (!isAllaySp) {
            Integer currentState = playerMusicState.getOrDefault(player.getUuid(), -1);
            if (currentState != -1) {
                stopAllMusic(player);
                removeSpeedFromAll(player);
            }
            return;
        }

        // Find jukebox item in inventory (should be in slot 1)
        ItemStack jukeboxStack = player.getInventory().getStack(1);
        
        // If item is missing, treat as inactive -> cleanup
        if (!jukeboxStack.isOf(SscAddon.ALLAY_JUKEBOX)) {
            Integer currentState = playerMusicState.getOrDefault(player.getUuid(), -1);
            if (currentState != -1) {
                stopAllMusic(player);
                removeSpeedFromAll(player);
            }
            return;
        }

        boolean isActive = AllayJukeboxItem.isActive(jukeboxStack);

        if (!isActive) {
            // Remove speed modifiers from all nearby entities when deactivated
            removeSpeedFromAll(player);
            // Stop music if it was playing
            Integer lastState = playerMusicState.get(player.getUuid());
            if (lastState != null && lastState != -1) {
                stopAllMusic(player);
            }
            return;
        }

        int charge = AllayJukeboxItem.getCharge(jukeboxStack);
        if (charge <= 0) {
            // Out of charge, deactivate
            AllayJukeboxItem.setActive(jukeboxStack, false);
            removeSpeedFromAll(player);
            stopAllMusic(player);
            return;
        }

        // Consume 1 charge per second (every 20 ticks)
        if (player.age % 20 == 0) {
            AllayJukeboxItem.setCharge(jukeboxStack, charge - 1);
        }

        int mode = AllayJukeboxItem.getMode(jukeboxStack);
        List<LivingEntity> nearbyEntities = getNearbyWhitelistEntities(player);

        // ===== Music: detect mode change or first activation =====
        Integer lastMusicMode = playerMusicState.getOrDefault(player.getUuid(), -1);
        if (lastMusicMode != mode) {
            // Mode changed or first time: stop old, play new immediately
            switchMusic(player, mode);
        }

        if (mode == AllayJukeboxItem.MODE_HEAL) {
            // Heal mode: every 3 seconds (60 ticks), heal 1 HP
            if (player.age % 60 == 0) {
                for (LivingEntity entity : nearbyEntities) {
                    entity.heal(1.0f);
                }
            }
            // Remove speed modifier when in heal mode
            removeSpeedFromAll(player);
        } else {
            // Speed mode: apply 10% speed modifier
            for (LivingEntity entity : nearbyEntities) {
                applySpeedModifier(entity);
            }
        }

        // Clean up speed modifiers from entities that moved out of range when in speed mode
        if (mode == AllayJukeboxItem.MODE_SPEED) {
            cleanupOutOfRangeEntities(player, nearbyEntities);
        }
    }

    private static List<LivingEntity> getNearbyWhitelistEntities(ServerPlayerEntity player) {
        Box box = new Box(
                player.getX() - RANGE, player.getY() - RANGE, player.getZ() - RANGE,
                player.getX() + RANGE, player.getY() + RANGE, player.getZ() + RANGE
        );

        return player.getServerWorld().getEntitiesByClass(LivingEntity.class, box, entity -> {
            double dist = entity.squaredDistanceTo(player);
            if (dist > RANGE * RANGE) return false;
            if (entity == player) return true;
            // Use the allay whitelist
            return AllaySPGroupHeal.isInWhitelist(player, entity);
        });
    }

    private static void applySpeedModifier(LivingEntity entity) {
        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr == null) return;

        EntityAttributeModifier existing = speedAttr.getModifier(SPEED_MODIFIER_UUID);
        if (existing == null) {
            speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                    SPEED_MODIFIER_UUID, SPEED_MODIFIER_NAME,
                    SPEED_BONUS, EntityAttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    private static void removeSpeedModifier(LivingEntity entity) {
        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr == null) return;
        speedAttr.removeModifier(SPEED_MODIFIER_UUID);
    }

    private static void removeSpeedFromAll(ServerPlayerEntity player) {
        Box box = new Box(
                player.getX() - RANGE - 10, player.getY() - RANGE - 10, player.getZ() - RANGE - 10,
                player.getX() + RANGE + 10, player.getY() + RANGE + 10, player.getZ() + RANGE + 10
        );
        List<LivingEntity> all = player.getServerWorld().getEntitiesByClass(LivingEntity.class, box, e -> true);
        for (LivingEntity entity : all) {
            removeSpeedModifier(entity);
        }
    }

    private static void cleanupOutOfRangeEntities(ServerPlayerEntity player, List<LivingEntity> inRange) {
        // Every 2 seconds, clean up speed modifiers from entities that left range
        if (player.age % 40 != 0) return;

        Box bigBox = new Box(
                player.getX() - RANGE - 20, player.getY() - RANGE - 20, player.getZ() - RANGE - 20,
                player.getX() + RANGE + 20, player.getY() + RANGE + 20, player.getZ() + RANGE + 20
        );
        List<LivingEntity> allNearby = player.getServerWorld().getEntitiesByClass(LivingEntity.class, bigBox, e -> true);
        for (LivingEntity entity : allNearby) {
            if (!inRange.contains(entity)) {
                removeSpeedModifier(entity);
            }
        }
    }
}
