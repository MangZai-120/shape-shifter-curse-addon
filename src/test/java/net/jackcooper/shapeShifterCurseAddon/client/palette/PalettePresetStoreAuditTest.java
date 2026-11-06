package net.jackcooper.shapeShifterCurseAddon.client.palette;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exercises real filename identity and replace-on-success persistence in a disposable directory. */
public final class PalettePresetStoreAuditTest {
    public static int run() throws Exception {
        int checks = 0;
        String first = PalettePresetStore.scopeKey("world", "C:/saves/测试世界");
        String second = PalettePresetStore.scopeKey("world", "C:/saves/魔法学院");
        require(!first.equals(second), "Different Chinese save directories have distinct filenames"); checks++;
        require(!PalettePresetStore.scopeKey("world", "C:/saves/a").equals(
                PalettePresetStore.scopeKey("world", "C:/saves/b")), "Same display name may use different save directories"); checks++;
        require(first.equals(PalettePresetStore.scopeKey("world", "C:/saves/测试世界")), "Scope identity is stable"); checks++;
        require(first.matches("world_[a-f0-9]{64}"), "Scope filename is safe without lossy replacement"); checks++;

        Path directory = Files.createTempDirectory("ssca-palette-audit-");
        Path file = directory.resolve("palette.json");
        Path blocked = directory.resolve("blocked.json");
        try {
            require(PalettePresetStore.writeAtomically(file, "{\"name\":\"原预设\"}"), "Initial write succeeds"); checks++;
            require(PalettePresetStore.writeAtomically(file, "{\"name\":\"新预设\"}"), "Existing file is replaced"); checks++;
            require(Files.readString(file).contains("新预设"), "Replacement contains the complete UTF-8 draft"); checks++;
            Files.createDirectory(blocked);
            Files.writeString(blocked.resolve("keep"), "previous");
            require(!PalettePresetStore.writeAtomically(blocked, "replacement"), "Failed destination reports failure"); checks++;
            require(Files.readString(blocked.resolve("keep")).equals("previous"), "Failure preserves existing destination contents"); checks++;
            try (var files = Files.list(directory)) {
                require(files.noneMatch(path -> path.toString().endsWith(".tmp")), "Temporary files are removed after success and failure"); checks++;
            }
        } finally {
            Files.deleteIfExists(blocked.resolve("keep"));
            Files.deleteIfExists(blocked);
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        return checks + scopeTransitions();
    }

    private static int scopeTransitions() throws Exception {
        int checks = 0;
        Path directory = Files.createTempDirectory("ssca-palette-scope-");
        String worldA = PalettePresetStore.scopeKey("world", "save-directory-a");
        String worldB = PalettePresetStore.scopeKey("world", "save-directory-b");
        var scope = new java.util.concurrent.atomic.AtomicReference<>(worldA);
        var store = new PalettePresetStore(directory, scope::get, () -> "world_legacy");
        Path fileA = directory.resolve("palette_presets_scope_" + worldA + ".json");
        try {
            store.reloadForCurrentScope();
            store.getSlots().get(0).name = "world draft before debounce";
            store.getSlots().get(0).code = "WORLD";
            store.markDirty();
            require(store.setGlobalSync(true), "Immediate scope switch flushes pending edits first"); checks++;
            require(Files.readString(fileA).contains("world draft before debounce"), "Old scope is saved to its own file"); checks++;
            require(store.getSlots().get(0).isEmpty(), "Global scope does not inherit world data"); checks++;
            store.getSlots().get(0).name = "global draft before resize";
            store.getSlots().get(0).code = "GLOBAL";
            store.markDirty();
            store.reloadForCurrentScope();
            require(store.getSlots().get(0).name.equals("global draft before resize"), "Layout-style reload cannot overwrite a pending draft"); checks++;
            require(store.setGlobalSync(false) && store.getSlots().get(0).code.equals("WORLD"), "Switching back restores the original scope"); checks++;

            store.getSlots().get(0).name = "unsaved but retained";
            store.markDirty();
            Files.delete(fileA);
            Files.createDirectory(fileA);
            Files.writeString(fileA.resolve("keep"), "blocked destination");
            require(!store.setGlobalSync(true), "A failed flush aborts scope switching"); checks++;
            require(!store.isGlobalSync() && store.getSlots().get(0).name.equals("unsaved but retained"), "Failure preserves scope and draft"); checks++;
            store.reloadForCurrentScope();
            require(store.getSlots().get(0).name.equals("unsaved but retained"), "Resize/reload also retains an unflushable draft"); checks++;
            Files.delete(fileA.resolve("keep"));
            Files.delete(fileA);
            require(store.save() && Files.readString(fileA).contains("unsaved but retained"), "Retry persists the retained draft"); checks++;

            Path legacy = directory.resolve("palette_presets_scope_world_legacy.json");
            Files.writeString(legacy, "{\"slots\":[{\"name\":\"legacy retained\",\"code\":\"OLD\"}]}");
            scope.set(worldB);
            store.reloadForCurrentScope();
            require(store.getSlots().get(0).code.equals("OLD"), "Upgrade reads old scoped presets"); checks++;
            require(Files.exists(legacy) && Files.exists(directory.resolve("palette_presets_scope_" + worldB + ".json")), "Migration preserves old file and writes isolated identity"); checks++;
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
        return checks;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
