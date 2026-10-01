package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import java.util.*;

public final class WorldRuneState extends PersistentState {
    public static final int VERSION = 1;
    private static final String KEY = "ssc_addon_rune_language";
    private UUID worldId;
    private long seed;
    private RuneLanguage language;
    private NbtCompound legacyPatterns = new NbtCompound();
    private WorldRuneState() {
        worldId = UUID.randomUUID(); seed = new java.security.SecureRandom().nextLong(); language = RuneLanguage.generate(seed);
        markDirty();
    }
    private WorldRuneState(NbtCompound n) {
        if (n.getInt("Version") != VERSION || !n.containsUuid("World")) throw new IllegalStateException("Unsupported or invalid saved rune language");
        worldId = n.getUuid("World"); seed = n.getLong("Seed"); language = new RuneLanguage(n.getIntArray("Meanings"), n.getIntArray("Rules"));
        legacyPatterns = n.getCompound("Patterns").copy();
    }
    public static WorldRuneState get(MinecraftServer server) {
        var manager=server.getOverworld().getPersistentStateManager();
        WorldRuneState state=manager.get(WorldRuneState::new,KEY);
        if(state!=null)return state;
        // PersistentStateManager logs decoding errors and returns null. Never replace an existing language.
        var saved=server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("data").resolve(KEY+".dat");
        if(java.nio.file.Files.exists(saved))throw new IllegalStateException("Cannot read saved rune language; refusing to replace "+saved);
        state=new WorldRuneState();manager.set(KEY,state);return state;
    }
    public UUID worldId() { return worldId; }
    public RuneLanguage language() { return language; }
    @Override public NbtCompound writeNbt(NbtCompound n) {
        n.putInt("Version", VERSION); n.putUuid("World", worldId); n.putLong("Seed", seed); n.putIntArray("Meanings", language.meanings()); n.putIntArray("Rules", language.rules());
        n.put("Patterns", legacyPatterns.copy()); return n;
    }
}
