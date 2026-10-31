package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.nbt.NbtCompound;

/**
 * 把旧试验田药水存档转成正式附属物品；只在命中旧数据时复制，不改写调用方的存档对象。
 * 物品栏、箱子、药水袋和药水实体都经 ItemStack.fromNbt 读取，嵌套容器在内容被读取时同样迁移。
 */
public final class PotionItemMigration {
    private static final String VIAL = "ssc_addon:moon_dust_vial";
    private static final String FILLED_TUBE = "ssc_addon:test_tube_potion";
    private static final String OLD_MOON_POTION = "ssca_neomagic:moon_dust_tube";
    private static final String MOON_POTION = "ssc_addon:moon_dust_tube";

    private PotionItemMigration() {}

    public static NbtCompound rewrite(NbtCompound source) {
        if (source == null) return null;
        String originalId = source.getString("id");
        String targetId = switch (originalId) {
            case "ssca_neomagic:moon_dust_vial" -> VIAL;
            case "ssca_neomagic:moon_dust_bottle" -> "ssc_addon:moon_dust_bottle";
            case "ssca_neomagic:moon_dust_splash" -> "ssc_addon:moon_dust_splash";
            case "ssca_neomagic:test_tube" -> "ssc_addon:test_tube";
            case "ssca_neomagic:test_tube_potion" -> FILLED_TUBE;
            default -> originalId;
        };
        boolean renamed = !targetId.equals(originalId);
        boolean currentPotionItem = targetId.equals(VIAL) || targetId.equals(FILLED_TUBE)
                || targetId.equals("ssc_addon:moon_dust_bottle") || targetId.equals("ssc_addon:moon_dust_splash");
        if (!renamed && !currentPotionItem) return source;

        NbtCompound oldTag = source.getCompound("tag");
        String oldPotion = oldTag.getString("Potion");
        boolean genericMoon = targetId.equals(FILLED_TUBE)
                && (oldPotion.equals(OLD_MOON_POTION) || oldPotion.equals(MOON_POTION));
        boolean renamePotion = oldPotion.equals(OLD_MOON_POTION);
        if (!renamed && !genericMoon && !renamePotion) return source;

        NbtCompound result = source.copy();
        result.putString("id", genericMoon ? VIAL : targetId);
        if (genericMoon) {
            // 月尘已用专属物品结算二十点剂量，清掉旧通用载体，防止同名双份或重复施效。
            NbtCompound tag = result.getCompound("tag");
            tag.remove("Potion");
            tag.remove("CustomPotionEffects");
        } else if (renamePotion) {
            result.getCompound("tag").putString("Potion", MOON_POTION);
        }
        return result;
    }
}
