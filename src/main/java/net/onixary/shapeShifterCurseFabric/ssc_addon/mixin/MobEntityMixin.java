package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {

    /** 亡灵中立复仇窗口：被攻击后10秒内允许反击 */
    @Unique
    private static final int UNDEAD_REVENGE_WINDOW = 200;

    /**
     * 检查亡灵生物是否应该对目标玩家保持中立。
     * 条件：生物是亡灵 + 玩家是SP阿努比斯形态 + 玩家没有最近攻击过该生物
     */
    @Unique
    private boolean ssc_addon$shouldUndeadIgnore(MobEntity mob, PlayerEntity player) {
        if (mob.getGroup() != EntityGroup.UNDEAD) return false;
        if (!FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) return false;
        // 如果玩家最近攻击了该亡灵，允许复仇
        boolean provoked = mob.getAttacker() == player
                && (mob.age - mob.getLastAttackedTime()) < UNDEAD_REVENGE_WINDOW;
        return !provoked;
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
        if (target == null) return;

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

        // 4. 亡灵中立：清除超出复仇窗口的目标
        if (target instanceof PlayerEntity player
                && ssc_addon$shouldUndeadIgnore(mob, player)) {
            mob.setTarget(null);
        }
    }
}
