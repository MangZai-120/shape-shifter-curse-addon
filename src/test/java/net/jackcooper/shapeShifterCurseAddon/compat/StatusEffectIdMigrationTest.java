package net.jackcooper.shapeShifterCurseAddon.compat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import net.fabricmc.fabric.impl.registry.sync.RegistryMapSerializer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

public final class StatusEffectIdMigrationTest {
    private static final String[] EFFECTS = {"disorder", "disintegration", "breakdown"};

    public static void main(String[] args) {
        NbtCompound file = new NbtCompound();
        NbtCompound registries = new NbtCompound();
        file.putInt("version", 1);
        file.put("registries", registries);
        NbtCompound effects = new NbtCompound();
        registries.put("minecraft:mob_effect", effects);
        for (int i = 0; i < EFFECTS.length; i++) effects.putInt("ssca_neomagic:" + EFFECTS[i], 92 + i);
        effects.putInt("ssc_addon:disintegration", 93);
        effects.putInt("ssca_neomagic:other", 91);
        effects.putInt("other_mod:disorder", 90);
        registries.put("minecraft:sound_event", effects.copy());

        var decoded = RegistryMapSerializer.fromNbt(file);
        StatusEffectIdMigration.migrate(decoded);
        NbtCompound migrated = RegistryMapSerializer.toNbt(decoded).getCompound("registries");
        NbtCompound current = migrated.getCompound("minecraft:mob_effect");
        for (int i = 0; i < EFFECTS.length; i++) {
            check(!current.contains("ssca_neomagic:" + EFFECTS[i]));
            check(current.getInt("ssc_addon:" + EFFECTS[i]) == 92 + i);
        }
        check(current.getInt("ssca_neomagic:other") == 91 && current.getInt("other_mod:disorder") == 90);
        check(migrated.getCompound("minecraft:sound_event").equals(effects));
        StatusEffectIdMigration.migrate(decoded);
        check(RegistryMapSerializer.toNbt(decoded).getCompound("registries").equals(migrated));
        StatusEffectIdMigration.migrate(Map.of());

        effects.putInt("ssc_addon:breakdown", 120);
        Map<Identifier, Object2IntMap<Identifier>> conflict = RegistryMapSerializer.fromNbt(file);
        try {
            StatusEffectIdMigration.migrate(conflict);
            throw new AssertionError("Conflicting numeric IDs were accepted");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("ssca_neomagic:breakdown=94"));
            check(expected.getMessage().contains("ssc_addon:breakdown=120"));
        }
        check(RegistryMapSerializer.toNbt(conflict).equals(file));
        verifyForge();
        System.out.println("PASS Fabric and Forge persisted registry namespace migrations");
    }

    private static void verifyForge() {
        NbtCompound level = new NbtCompound();
        NbtCompound fml = new NbtCompound();
        NbtCompound registries = new NbtCompound();
        level.put("fml", fml);
        fml.put("Registries", registries);
        NbtCompound effects = new NbtCompound();
        registries.put("minecraft:mob_effect", effects);
        NbtList ids = new NbtList();
        effects.put("ids", ids);
        ids.add(entry("ssca_neomagic:breakdown", 112));
        ids.add(entry("ssc_addon:breakdown", 112));
        ids.add(entry("other_mod:disorder", 113));
        ids.add(entry("ssca_neomagic:custom", 114));
        NbtList aliases = new NbtList();
        NbtCompound alias = new NbtCompound();
        alias.putString("K", "another_mod:previous_name");
        alias.putString("V", "ssca_neomagic:breakdown");
        aliases.add(alias);
        effects.put("aliases", aliases);
        NbtCompound sounds = new NbtCompound();
        NbtList soundIds = new NbtList();
        soundIds.add(entry("my_addon:orcafis.breach", 181));
        soundIds.add(entry("my_addon:snow_fox.plunge", 182));
        soundIds.add(entry("my_addon:orcafis.unrelated", 183));
        sounds.put("ids", soundIds);
        registries.put("minecraft:sound_event", sounds);
        registries.put("other:registry", sounds.copy());

        NbtCompound expected = level.copy();
        NbtCompound changed = expected.getCompound("fml").getCompound("Registries");
        changed.getCompound("minecraft:mob_effect").getList("ids", 10).remove(0);
        changed.getCompound("minecraft:mob_effect").getList("aliases", 10).getCompound(0).putString("V", "ssc_addon:breakdown");
        NbtList changedSounds = changed.getCompound("minecraft:sound_event").getList("ids", 10);
        changedSounds.getCompound(0).putString("K", "ssc_addon:orcafis.breach");
        changedSounds.getCompound(1).putString("K", "ssc_addon:snow_fox.plunge");
        NbtCompound migrated = ForgeSavedRegistryMigration.migrate(level);
        check(migrated.equals(expected));
        check(ids.size() == 4 && ids.getCompound(0).getString("K").equals("ssca_neomagic:breakdown"));
        check(ForgeSavedRegistryMigration.migrate(migrated) == migrated);
        soundIds.add(entry("ssc_addon:orcafis.breach", 184));
        try {
            ForgeSavedRegistryMigration.migrate(level);
            throw new AssertionError("Conflicting Forge raw IDs were accepted");
        } catch (IllegalStateException expectedConflict) {
            check(expectedConflict.getMessage().contains("ssc_addon:orcafis.breach"));
        }
        check(ids.size() == 4 && soundIds.size() == 4);
    }

    private static NbtCompound entry(String id, int value) {
        NbtCompound row = new NbtCompound();
        row.putString("K", id);
        row.putInt("V", value);
        return row;
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
