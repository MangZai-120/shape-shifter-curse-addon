package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.AllayJukeboxItem;

import java.util.List;
import java.util.UUID;

/**
 * SP悦灵唱片机区域效果 - 已重构为JSON驱动
 * 保留常量和工具方法供JSON powers和functions使用
 */
public class AllaySPJukebox {
    private AllaySPJukebox() {
        // Utility class
    }

    public static final double RANGE = 20.0;
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("a3b4c5d6-e7f8-9012-3456-789abcdef012");
    private static final String SPEED_MODIFIER_NAME = "allay_jukebox_speed";
    private static final double SPEED_BONUS = 0.10; // 10% speed

    /**
     * 获取附近的白名单实体（供JSON powers和functions使用）
     * 保留此方法是因为JSON functions需要访问白名单逻辑
     */
    public static List<LivingEntity> getNearbyWhitelistEntities(ServerPlayerEntity player) {
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

    /**
     * 应用速度修饰符到实体（供JSON functions使用）
     */
    public static void applySpeedModifier(LivingEntity entity) {
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

    /**
     * 移除实体的速度修饰符（供JSON functions使用）
     */
    public static void removeSpeedModifier(LivingEntity entity) {
        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr == null) return;
        speedAttr.removeModifier(SPEED_MODIFIER_UUID);
    }

    /**
     * 移除所有附近实体的速度修饰符（供JSON functions使用）
     */
    public static void removeSpeedFromAll(ServerPlayerEntity player) {
        Box box = new Box(
                player.getX() - RANGE - 10, player.getY() - RANGE - 10, player.getZ() - RANGE - 10,
                player.getX() + RANGE + 10, player.getY() + RANGE + 10, player.getZ() + RANGE + 10
        );
        List<LivingEntity> all = player.getServerWorld().getEntitiesByClass(LivingEntity.class, box, e -> true);
        for (LivingEntity entity : all) {
            removeSpeedModifier(entity);
        }
    }

    /**
     * 清理超出范围实体的速度修饰符（供JSON functions使用）
     */
    public static void cleanupOutOfRangeEntities(ServerPlayerEntity player, List<LivingEntity> inRange) {
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

    /**
     * 切换音乐到指定模式（供JSON functions使用）
     * @param player 播放器
     * @param newMode 新模式 (0=速度, 1=治疗)
     */
    public static void switchMusic(ServerPlayerEntity player, int newMode) {
        // Stop both old sounds first
        player.networkHandler.sendPacket(new StopSoundS2CPacket(SscAddon.ALLAY_HEAL_MUSIC_ID, SoundCategory.RECORDS));
        player.networkHandler.sendPacket(new StopSoundS2CPacket(SscAddon.ALLAY_SPEED_MUSIC_ID, SoundCategory.RECORDS));

        // Play new music immediately
        SoundEvent newSound = (newMode == AllayJukeboxItem.MODE_SPEED) ? SscAddon.ALLAY_SPEED_MUSIC_EVENT : SscAddon.ALLAY_HEAL_MUSIC_EVENT;
        ServerWorld serverWorld = player.getServerWorld();
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                newSound, SoundCategory.RECORDS, 0.2f, 1.0f);
    }

    /**
     * 停止所有音乐（供JSON functions使用）
     * @param player 播放器
     */
    public static void stopAllMusic(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new StopSoundS2CPacket(SscAddon.ALLAY_HEAL_MUSIC_ID, SoundCategory.RECORDS));
        player.networkHandler.sendPacket(new StopSoundS2CPacket(SscAddon.ALLAY_SPEED_MUSIC_ID, SoundCategory.RECORDS));
    }
}
