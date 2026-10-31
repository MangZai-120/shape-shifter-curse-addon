package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.item.PotionItemMigration;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 在原版按物品ID查注册表前转换旧药水NBT，保留原有读取异常和空栈处理。 */
@Mixin(ItemStack.class)
public abstract class PotionItemMigrationMixin {
    @ModifyVariable(method = "fromNbt(Lnet/minecraft/nbt/NbtCompound;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static NbtCompound ssc_addon$migratePotionItem(NbtCompound source) {
        return PotionItemMigration.rewrite(source);
    }
}
