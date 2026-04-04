package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {

    /** 每个生物实例独立记录：最后一次看到挑衅玩家的世界时间（-1=未追踪） */
    @Unique
    private long ssc_addon$lastSawProvokedTarget = -1;

    /**
     * 检查亡灵是否应该忽略该玩家（保持中立）。
     * 返回true=忽略（不攻击），返回false=不忽略（可攻击）
     */
    @Unique
    private boolean ssc_addon$shouldUndeadIgnore(MobEntity mob, PlayerEntity player) {
        if (mob.getGroup() != EntityGroup.UNDEAD) return false;
        if (!FormUtils.isAnyForm(player, FormIdentifiers.ANUBIS_WOLF_SP, FormIdentifiers.GOLDEN_SANDSTORM_SP)) return false;
        // 玩家处于挑衅状态 → 亡灵可攻击
        return !UndeadNeutralState.isPlayerProvoked(player.getUuid(), mob.getWorld().getTime());
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void ssc_addon$onSetTarget(LivingEntity target, CallbackInfo ci) {
        if (target != null) {
            if (target.hasStatusEffect(SscAddon.PLAYING_DEAD)) {
                ci.cancel();
                return;
            }
            if (target.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
                ci.cancel();
                return;
            }
            MobEntity self = (MobEntity)(Object)this;
            // 劫掠阵营不攻击女巫使魔
            if ((self instanceof RaiderEntity || self instanceof VexEntity || self instanceof WitchEntity)
                    && target instanceof WitchFamiliarEntity) {
                ci.cancel();
                return;
            }
            // 女巫使魔不攻击劫掠阵营
            if (self instanceof WitchFamiliarEntity
                    && (target instanceof RaiderEntity || target instanceof VexEntity || target instanceof WitchEntity || target instanceof WitchFamiliarEntity)) {
                ci.cancel();
                return;
            }
            // 灾厄中立
            if ((self instanceof RaiderEntity || self instanceof VexEntity)
                    && target instanceof PlayerEntity player
                    && player.getCommandTags().contains("ssc_raid_friend")) {
                ci.cancel();
                return;
            }
            // 亡灵中立：阻止主动索敌，但允许受击后反击
            if (target instanceof PlayerEntity player
                    && ssc_addon$shouldUndeadIgnore(self, player)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "mobTick", at = @At("HEAD"), cancellable = true)
    private void ssc_addon$onMobTick(CallbackInfo ci) {
        MobEntity mob = (MobEntity)(Object)this;
        
        // 1. 眩晕逻辑
        if (mob.hasStatusEffect(SscAddon.STUN)) {
            ci.cancel();
            return;
        }
        
        LivingEntity target = mob.getTarget();
        if (target == null) {
            ssc_addon$lastSawProvokedTarget = -1;
            return;
        }

        // 2. 真隐身脱战
        if (target.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
            mob.setTarget(null);
            return;
        }

        // 3. 灾厄联盟脱战
        if ((mob instanceof RaiderEntity || mob instanceof VexEntity)
                && target instanceof PlayerEntity player
                && player.getCommandTags().contains("ssc_raid_friend")) {
            mob.setTarget(null);
            return;
        }

        // 4. 亡灵中立：基于视野的脱战机制（类似僵尸猪灵）
        if (mob.getGroup() == EntityGroup.UNDEAD
                && target instanceof PlayerEntity player
                && FormUtils.isAnyForm(player, FormIdentifiers.ANUBIS_WOLF_SP, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
            // 挑衅已过期 → 立即脱战
            if (!UndeadNeutralState.isPlayerProvoked(player.getUuid(), mob.getWorld().getTime())) {
                mob.setTarget(null);
                ssc_addon$lastSawProvokedTarget = -1;
                return;
            }
            long worldTime = mob.getWorld().getTime();
            if (mob.canSee(target)) {
                // 能看到目标 → 重置视野计时，同时刷新全局挑衅
                ssc_addon$lastSawProvokedTarget = worldTime;
                UndeadNeutralState.PROVOKE_TIMESTAMPS.put(player.getUuid(), worldTime);
            } else {
                // 看不到目标 → 脱战倒计时
                if (ssc_addon$lastSawProvokedTarget < 0) {
                    ssc_addon$lastSawProvokedTarget = worldTime;
                }
                if (worldTime - ssc_addon$lastSawProvokedTarget > UndeadNeutralState.SIGHT_TIMEOUT) {
                    mob.setTarget(null);
                    ssc_addon$lastSawProvokedTarget = -1;
                }
            }
        }
    }
}
