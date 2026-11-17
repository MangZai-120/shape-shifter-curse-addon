package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.RenderContextTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/** All GUI drawItem overloads converge here; only the SSCA spear needs a predicate override. */
@Mixin(DrawContext.class)
public class WaterSpearInventoryMixin {
    @WrapMethod(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIII)V")
    private void ssca$spearGui(LivingEntity entity, World world, ItemStack stack, int x, int y,
                              int seed, int z, Operation<Void> original) {
        if (!stack.isOf(SscAddon.WATER_SPEAR)) {
            original.call(entity, world, stack, x, y, seed, z);
            return;
        }
        boolean previous = RenderContextTracker.isGuiContext();
        RenderContextTracker.setGuiContext(true);
        try {
            original.call(entity, world, stack, x, y, seed, z);
        } finally {
            RenderContextTracker.setGuiContext(previous);
        }
    }
}
