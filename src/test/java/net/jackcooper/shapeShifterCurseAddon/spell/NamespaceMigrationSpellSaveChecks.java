package net.jackcooper.shapeShifterCurseAddon.spell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentStateManager;

public final class NamespaceMigrationSpellSaveChecks {
    private NamespaceMigrationSpellSaveChecks() {}

    public static void verify(MinecraftServer server) throws Exception {
        Path dir = Files.createTempDirectory("ssc-spell-save-test");
        Path oldFile = dir.resolve("ssca_neomagic.dat");
        Path currentFile = dir.resolve(NeoState.SAVE_KEY + ".dat");
        UUID player = UUID.randomUUID();
        try {
            NeoState old = new NeoState();
            old.player(player).form = "my_addon:snow_fox_sp";
            old.player(player).spellbookMasteryExpTen = 162;
            old.player(player).cooldowns.put(NeoModel.Blueprint.starter().identity(), 47);
            old.markDirty();
            old.save(oldFile.toFile());
            byte[] original = Files.readAllBytes(oldFile);
            var manager = new PersistentStateManager(dir.toFile(), server.getDataFixer());
            NeoState imported = NeoState.load(manager, () -> dir);
            var data = imported.player(player);
            if (data.spellbookMasteryExpTen != 162 || !data.form.equals("ssc_addon:snow_fox_frost_spirit")
                    || data.cooldowns.get(NeoModel.Blueprint.starter().identity()) != 47) {
                throw new AssertionError("Previous spell save lost player progress or cooldown");
            }
            data.spellbookMasteryExpTen = 207;
            manager.save();
            if (!Files.isRegularFile(currentFile) || !Arrays.equals(original, Files.readAllBytes(oldFile))) {
                throw new AssertionError("Migration must write only the new spell file");
            }
            var restarted = new PersistentStateManager(dir.toFile(), server.getDataFixer());
            if (NeoState.load(restarted, () -> dir).player(player).spellbookMasteryExpTen != 207) {
                throw new AssertionError("Existing current save must take precedence on the next load");
            }
        } finally {
            Files.deleteIfExists(currentFile);
            Files.deleteIfExists(oldFile);
            Files.deleteIfExists(dir);
        }
    }
}
