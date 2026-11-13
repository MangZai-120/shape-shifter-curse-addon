/*
 * Copyright (c) 2026 MangZai-120
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.util;

import net.jackcooper.shapeShifterCurseAddon.compat.DualAccessoryEquipment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.function.Predicate;

/** Queries functional equipment in every backend, respecting Trinkets duplicate priority. */
public final class TrinketUtils {
    private TrinketUtils() {}

    public static boolean isWearing(LivingEntity entity, Item item) {
        return isWearing(entity, stack -> stack.isOf(item));
    }

    public static boolean isWearingAuto(LivingEntity entity, Item item) {
        return isWearing(entity, item);
    }

    public static boolean isWearing(LivingEntity entity, Predicate<ItemStack> predicate) {
        return findFirstEquipped(entity, predicate) != null;
    }

    /** Returns the actual stack so spellbook mana, cooldown and NBT writes reach its inventory. */
    public static @Nullable ItemStack findFirstEquipped(LivingEntity entity, Predicate<ItemStack> predicate) {
        for (var slot : DualAccessoryEquipment.effective(entity)) {
            if (predicate.test(slot.stack())) return slot.stack();
        }
        return null;
    }
}
