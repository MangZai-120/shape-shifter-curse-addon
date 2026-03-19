package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.util.HudRender;
import net.minecraft.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.additional_power.VirtualTotemPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.AnkhStoneItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 VirtualTotemPower.use() 末尾注入安卡纹石逻辑
 * 时机：super.use() 已设置冷却、状态效果已施加 → 安卡纹石清除负面效果并缩短CD
 */
@Mixin(value = VirtualTotemPower.class, remap = false)
public abstract class AnkhStoneTotemMixin extends CooldownPower {

    // 满足编译器的虚构造，运行时不调用
    protected AnkhStoneTotemMixin(PowerType<?> type, LivingEntity entity, int cd, HudRender hr) {
        super(type, entity, cd, hr);
    }

    @Inject(method = "use", at = @At("TAIL"))
    private void onVirtualTotemUse(CallbackInfo ci) {
        AnkhStoneItem.onRevival(this.entity);
    }
}
