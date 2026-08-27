package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 占位技能物品防外流：禁止将 Red 药水袋、SP悦灵 治愈法杖与悦灵唱片机放入物品展示框（荧光展示框继承自本类，一并覆盖）。
 * <p>
 * 背景：这三件占位技能物品锁定在玩家背包固定槽位（药水袋=8、治愈法杖=0、唱片机=1），
 * 容器界面内的点击已在 ScreenHandlerMixin 于 onSlotClick HEAD 统一拦截（含箱子界面的
 * Shift 快速移动、SWAP、THROW）。但「手持右键展示框」走 ItemFrameEntity#interact，
 * 原版直接把手中物品放进展示框并 decrement(1)，完全不经过 PlayerInventory 的任何注入点，是唯一的漏网路径。
 * <p>
 * 不区分形态/模式无条件拦截：占位技能物品本就不应流通，防止指令获取或形态切换间隙被存进展示框。
 */
@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameGuardMixin {

    @Inject(
            method = "interact(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ssc_addon$blockLockedSkillInItemFrame(PlayerEntity player, Hand hand,
                                                       CallbackInfoReturnable<ActionResult> cir) {
        // 主手或副手任一持有占位技能物品即拒绝交互（副手路径同样会消耗手中物品）
        for (Hand h : Hand.values()) {
            ItemStack stack = player.getStackInHand(h);
            if (stack.isOf(SscAddon.POTION_BAG)
                    || stack.isOf(SscAddon.ALLAY_HEAL_WAND)
                    || stack.isOf(SscAddon.ALLAY_JUKEBOX)) {
                cir.setReturnValue(ActionResult.FAIL);
                return;
            }
        }
    }
}
