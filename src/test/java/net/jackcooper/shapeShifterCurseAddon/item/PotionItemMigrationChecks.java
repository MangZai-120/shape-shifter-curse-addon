package net.jackcooper.shapeShifterCurseAddon.item;

import java.util.Map;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;

/** 在模组注册和Mixin生效后调用，覆盖旧存档的真实ItemStack读取入口。 */
public final class PotionItemMigrationChecks {
    private int checks;

    private PotionItemMigrationChecks() {}

    private void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static int verify() {
        PotionItemMigrationChecks test = new PotionItemMigrationChecks();
        test.run();
        System.out.println("POTION_ITEM_MIGRATION_PASS checks=" + test.checks);
        return test.checks;
    }

    private static NbtCompound saved(String id, String potion) {
        NbtCompound source = new NbtCompound();
        source.putString("id", id);
        source.putByte("Count", (byte) 7);
        source.putInt("Slot", 12);
        NbtCompound tag = new NbtCompound();
        tag.putString("Potion", potion);
        tag.putInt("CustomPotionColor", 0x38ABCD);
        tag.putInt("TestTubeBottleUseTicks", 40);
        NbtCompound display = new NbtCompound();
        display.putString("Name", "{\"text\":\"migration potion\"}");
        NbtList lore = new NbtList();
        lore.add(NbtString.of("{\"text\":\"retained lore\"}"));
        display.put("Lore", lore);
        tag.put("display", display);
        NbtList effects = new NbtList();
        NbtCompound custom = new StatusEffectInstance(StatusEffects.SPEED, 900, 1).writeNbt(new NbtCompound());
        effects.add(custom);
        tag.put("CustomPotionEffects", effects);
        NbtCompound extra = new NbtCompound();
        extra.putString("owner", "retained external data");
        tag.put("ExtraData", extra);
        source.put("tag", tag);
        return source;
    }

    private void run() {
        Map<String, String> aliases = Map.of(
                "moon_dust_vial", "moon_dust_vial", "moon_dust_bottle", "moon_dust_bottle",
                "moon_dust_splash", "moon_dust_splash", "test_tube", "test_tube",
                "test_tube_potion", "test_tube_potion");
        for (var alias : aliases.entrySet()) {
            NbtCompound source = saved("ssca_neomagic:" + alias.getKey(), "minecraft:long_swiftness");
            NbtCompound before = source.copy();
            NbtCompound migrated = PotionItemMigration.rewrite(source);
            require(migrated != source && source.equals(before), "Migration must copy without mutating saved NBT");
            require(migrated.getString("id").equals("ssc_addon:" + alias.getValue()), "Known old ID must migrate");
            require(migrated.getByte("Count") == 7 && migrated.getInt("Slot") == 12, "Count and inventory slot must be retained");
            require(migrated.getCompound("tag").equals(before.getCompound("tag")), "Ordinary potion NBT, name, colour and lore must be retained");
            ItemStack loaded = ItemStack.fromNbt(source);
            require(Registries.ITEM.getId(loaded.getItem()).toString().equals("ssc_addon:" + alias.getValue()), "Actual fromNbt hook must load the new item");
            require(loaded.getCount() == 7 && loaded.getNbt().equals(before.getCompound("tag")), "Actual item loading must preserve the whole payload");
            require(source.equals(before), "Actual item loading must not mutate the old inventory compound");
            require(PotionItemMigration.rewrite(migrated) == migrated, "Already migrated data must be idempotent");
        }

        for (String item : new String[]{"ssca_neomagic:test_tube_potion", "ssc_addon:test_tube_potion"}) {
            for (String potion : new String[]{"ssca_neomagic:moon_dust_tube", "ssc_addon:moon_dust_tube"}) {
                NbtCompound source = saved(item, potion);
                NbtCompound before = source.copy();
                NbtCompound migrated = PotionItemMigration.rewrite(source);
                require(migrated.getString("id").equals("ssc_addon:moon_dust_vial"), "Generic moon-dust stack must become the dedicated vial");
                NbtCompound tag = migrated.getCompound("tag");
                require(!tag.contains("Potion") && !tag.contains("CustomPotionEffects"), "Old generic carrier tags must not duplicate the moon-dust dose");
                require(tag.getInt("CustomPotionColor") == 0x38ABCD && tag.getCompound("display").equals(before.getCompound("tag").getCompound("display")), "Moon-dust name, lore and colour must survive conversion");
                require(tag.getCompound("ExtraData").equals(before.getCompound("tag").getCompound("ExtraData")), "External NBT must survive conversion");
                ItemStack loaded = ItemStack.fromNbt(source);
                require(Registries.ITEM.getId(loaded.getItem()).toString().equals("ssc_addon:moon_dust_vial") && loaded.getCount() == 7, "Actual old moon-dust loading must resolve to the sole vial item");
                require(source.equals(before) && PotionItemMigration.rewrite(migrated) == migrated, "Moon-dust migration must be non-mutating and idempotent");
            }
        }

        for (String item : new String[]{"minecraft:potion", "ssc_addon:test_tube_potion", "ssca_neomagic:guiding_wand"}) {
            NbtCompound source = saved(item, "minecraft:healing");
            require(PotionItemMigration.rewrite(source) == source, "Unrelated, current and retired research items must remain untouched");
        }
    }
}
