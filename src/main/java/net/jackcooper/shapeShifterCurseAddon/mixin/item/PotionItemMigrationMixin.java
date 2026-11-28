package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.item.PotionItemMigration;
import net.jackcooper.shapeShifterCurseAddon.util.NamespaceMigration;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 物品栏、容器和掉落实体读取旧物品时，迁移药水与 Apoli 装备能力。 */
@Mixin(ItemStack.class)
public abstract class PotionItemMigrationMixin {
    @ModifyVariable(method = "fromNbt(Lnet/minecraft/nbt/NbtCompound;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static NbtCompound ssc_addon$migrateSavedItem(NbtCompound source) {
        return NamespaceMigration.stackPowers(PotionItemMigration.rewrite(source));
    }
}
