package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SP悦灵群体治疗 - 白名单治疗系统
 * 
 * 当JSON蓄力完成后设置 heal_execute 资源为1，
 * 此类的tick检测到后执行白名单过滤治疗，然后重置为0。
 * 
 * 白名单存储在SP悦灵玩家的 command tags 中，
 * 格式: "ssc_allay_wl:<目标玩家UUID>"
 */
public class AllaySPGroupHeal {

    private AllaySPGroupHeal() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    private static final Identifier HEAL_EXECUTE_ID = new Identifier("my_addon", "form_allay_sp_group_heal_heal_execute");
    private static final double HEAL_RADIUS = 20.0;
    private static final float HEAL_AMOUNT = 20.0f;
    public static final String WHITELIST_TAG_PREFIX = "ssc_allay_wl:";

    /**
     * 每tick检查是否需要执行治疗
     */
    public static void tick(ServerPlayerEntity player) {
        int healExecute = getResourceValue(player, HEAL_EXECUTE_ID);
        if (healExecute != 1) return;

        // 立即重置触发器（不同步，避免重置飘浮计时器）
        setResourceValueNoSync(player, HEAL_EXECUTE_ID, 0);

        // 执行白名单过滤治疗
        executeWhitelistHeal(player);
    }

    /**
     * 执行白名单过滤的群体治疗
     * 白名单为空时治疗所有活体（原版行为）；有白名单时只治疗白名单内的玩家及其召唤物
     */
    private static void executeWhitelistHeal(ServerPlayerEntity allayPlayer) {
        ServerWorld world = (ServerWorld) allayPlayer.getWorld();
        Set<String> tags = allayPlayer.getCommandTags();

        // 检查白名单是否为空
        boolean whitelistEmpty = tags.stream().noneMatch(t -> t.startsWith(WHITELIST_TAG_PREFIX));

        // 治疗自身
        allayPlayer.heal(HEAL_AMOUNT);
        spawnHealParticles(world, allayPlayer);
        // 只有SP悦灵自己能听见
        allayPlayer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);

        // 获取范围内所有活体
        Box box = allayPlayer.getBoundingBox().expand(HEAL_RADIUS);
        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != allayPlayer && e.isAlive() && e.squaredDistanceTo(allayPlayer) <= HEAL_RADIUS * HEAL_RADIUS);

        for (LivingEntity entity : entities) {
            // 白名单为空时治疗所有玩家和友好生物；有白名单时进行过滤
            if (whitelistEmpty) {
                if (entity instanceof ServerPlayerEntity || !(entity instanceof Monster)) {
                    entity.heal(HEAL_AMOUNT);
                    spawnHealParticles(world, entity);
                    // 播放声音：治疗者听见私有声音，其他人听见空间声音
                    allayPlayer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);
                    world.playSound(allayPlayer, entity.getX(), entity.getY(), entity.getZ(), 
                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);
                }
            } else if (shouldHeal(entity, tags)) {
                entity.heal(HEAL_AMOUNT);
                spawnHealParticles(world, entity);
                // 播放声音：治疗者听见私有声音，其他人听见空间声音
                allayPlayer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);
                world.playSound(allayPlayer, entity.getX(), entity.getY(), entity.getZ(), 
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);
            }
        }
    }

    /**
     * 判断实体是否应被治疗
     */
    public static boolean shouldHeal(LivingEntity entity, Set<String> allayTags) {
        // 如果是玩家，检查白名单
        if (entity instanceof ServerPlayerEntity targetPlayer) {
            return isInWhitelist(allayTags, targetPlayer.getUuid());
        }

        // 如果是可驯服的实体（狼、猫等），检查主人是否在白名单
        if (entity instanceof TameableEntity tameable) {
            UUID ownerUuid = tameable.getOwnerUuid();
            if (ownerUuid != null) {
                return isInWhitelist(allayTags, ownerUuid);
            }
            return false; // 无主的可驯服生物不治疗
        }

        // 检查 ssc_owner: command tag（通过 mark_owner 命令标记的实体）
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith("ssc_owner:")) {
                try {
                    UUID ownerUuid = UUID.fromString(tag.substring("ssc_owner:".length()));
                    if (isInWhitelist(allayTags, ownerUuid)) return true;
                } catch (IllegalArgumentException e) {
                    // 无效UUID，跳过
                }
            }
        }

        // 最后检查实体本身的UUID是否直接在白名单中
        return isInWhitelist(allayTags, entity.getUuid());
    }

    /**
     * 将目标实体的所有者（如果是宠物）或实体本身（如果是普通生物/玩家）加入白名单
     */
    public static void addToWhitelist(ServerPlayerEntity allayPlayer, LivingEntity target) {
        if (target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
            allayPlayer.getCommandTags().add(WHITELIST_TAG_PREFIX + tameable.getOwnerUuid().toString());
        } else {
            // 普通生物或玩家，添加自身UUID
            allayPlayer.getCommandTags().add(WHITELIST_TAG_PREFIX + target.getUuid().toString());
        }
    }

    /**
     * 将目标实体的所有者（如果是宠物）或实体本身（如果是普通生物/玩家）从白名单移除
     */
    public static void removeFromWhitelist(ServerPlayerEntity allayPlayer, LivingEntity target) {
        String tagToRemove;
        if (target instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
            tagToRemove = WHITELIST_TAG_PREFIX + tameable.getOwnerUuid().toString();
        } else {
            tagToRemove = WHITELIST_TAG_PREFIX + target.getUuid().toString();
        }

	    allayPlayer.getCommandTags().remove(tagToRemove);
    }

    /**
     * 检查UUID是否在白名单中
     */
    public static boolean isInWhitelist(Set<String> allayTags, UUID targetUuid) {
        return allayTags.contains(WHITELIST_TAG_PREFIX + targetUuid.toString());
    }

    /**
     * 便捷方法：检查一个实体是否在玩家的白名单中（供唱片机等外部系统使用）
     * 白名单为空时返回true（允许所有玩家和友好生物）
     */
    public static boolean isInWhitelist(ServerPlayerEntity allayPlayer, LivingEntity entity) {
        Set<String> tags = allayPlayer.getCommandTags();
        boolean whitelistEmpty = tags.stream().noneMatch(t -> t.startsWith(WHITELIST_TAG_PREFIX));
        if (whitelistEmpty) {
            return entity instanceof ServerPlayerEntity || !(entity instanceof Monster);
        }
        return shouldHeal(entity, tags);
    }

    /**
     * 在实体位置生成爱心粒子
     */
    private static void spawnHealParticles(ServerWorld world, LivingEntity entity) {
        world.spawnParticles(ParticleTypes.HEART,
                entity.getX(), entity.getY() + entity.getHeight() + 0.5, entity.getZ(),
                5, 0.3, 0.3, 0.3, 0.01);
    }

    // ===== 白名单管理方法（供命令调用） =====

    /**
     * 添加玩家到白名单
     */
    public static boolean addToWhitelist(ServerPlayerEntity allayPlayer, ServerPlayerEntity targetPlayer) {
        String tag = WHITELIST_TAG_PREFIX + targetPlayer.getUuid().toString();
        if (allayPlayer.getCommandTags().contains(tag)) {
            return false; // 已在白名单中
        }
        allayPlayer.addCommandTag(tag);
        return true;
    }

    /**
     * 从白名单移除玩家
     */
    public static boolean removeFromWhitelist(ServerPlayerEntity allayPlayer, ServerPlayerEntity targetPlayer) {
        String tag = WHITELIST_TAG_PREFIX + targetPlayer.getUuid().toString();
        return allayPlayer.getCommandTags().remove(tag);
    }

    /**
     * 从白名单按UUID移除
     */
    public static boolean removeFromWhitelistByUuid(ServerPlayerEntity allayPlayer, UUID targetUuid) {
        String tag = WHITELIST_TAG_PREFIX + targetUuid.toString();
        return allayPlayer.getCommandTags().remove(tag);
    }

    /**
     * 清空白名单
     */
    public static int clearWhitelist(ServerPlayerEntity allayPlayer) {
        int count = 0;
        var iterator = allayPlayer.getCommandTags().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(WHITELIST_TAG_PREFIX)) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }

    /**
     * 获取白名单中的所有UUID
     */
    public static java.util.List<UUID> getWhitelistUuids(ServerPlayerEntity allayPlayer) {
        java.util.List<UUID> uuids = new java.util.ArrayList<>();
        for (String tag : allayPlayer.getCommandTags()) {
            if (tag.startsWith(WHITELIST_TAG_PREFIX)) {
                try {
                    uuids.add(UUID.fromString(tag.substring(WHITELIST_TAG_PREFIX.length())));
                } catch (IllegalArgumentException e) {
                    // 无效UUID，跳过
                }
            }
        }
        return uuids;
    }

    // ===== Apoli资源读写工具 =====

    private static int getResourceValue(ServerPlayerEntity player, Identifier resourceId) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                return variablePower.getValue();
            }
        } catch (Exception e) {
            // Resource not found
        }
        return 0;
    }

    private static void setResourceValue(ServerPlayerEntity player, Identifier resourceId, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(value);
                // 只同步特定power，避免全量sync重置飘浮power客户端的ascendProgress
                PowerHolderComponent.syncPower(player, powerType);
            }
        } catch (Exception e) {
            // Resource not found
        }
    }

    /**
     * 设置资源值但不同步到客户端，避免重置飘浮等power的内部计时器
     * 仅用于不需要客户端感知的内部触发器（如 heal_execute）
     */
    private static void setResourceValueNoSync(ServerPlayerEntity player, Identifier resourceId, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(value);
                // 不调用 PowerHolderComponent.sync(player)，避免重置飘浮计时器
            }
        } catch (Exception e) {
            // Resource not found
        }
    }
}
