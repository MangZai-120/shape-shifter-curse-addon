package net.jackcooper.shapeShifterCurseAddon.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.OneAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils;

/** SSC's October 3 animation handoff, shared by the addon's humanoid forms. */
public final class FormAnimationCompat {
    public static final AbstractAnimStateController VANILLA_CONTROLLER =
            new OneAnimController((AnimUtils.AnimationHolderData) null);

    private FormAnimationCompat() {}

    public static boolean shouldUseVanillaAnimation(PlayerEntity player) {
        return CarryOnCompat.isInCarryingAnimation(player)
                || isSlashBlade(player.getMainHandStack().getItem());
    }

    public static boolean shouldUseAxolotlVanillaAnimation(PlayerEntity player) {
        // SSC keeps axolotl crawling while sneaking; allays also hand off while sneaking.
        return !player.isSneaking() && shouldUseVanillaAnimation(player);
    }

    private static boolean isSlashBlade(Object item) {
        return SlashBladeHolder.ITEM_CLASS != null && SlashBladeHolder.ITEM_CLASS.isInstance(item);
    }

    private static final class SlashBladeHolder {
        private static final Class<?> ITEM_CLASS = findItemClass();

        private static Class<?> findItemClass() {
            try {
                return Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade", false,
                        FormAnimationCompat.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }
}
