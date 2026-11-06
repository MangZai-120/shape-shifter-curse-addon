package net.jackcooper.shapeShifterCurseAddon.tutorial;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;

/** Viewing records belong to a player in this save, independent of the form history. */
public final class FormTutorialState extends PersistentState {
    private static final String SAVE_ID = "ssca_form_tutorials";
    private final Map<UUID, Set<Identifier>> viewed = new HashMap<>();

    public static FormTutorialState get(MinecraftServer server) {
        var manager = server.getOverworld().getPersistentStateManager();
        FormTutorialState state = manager.get(FormTutorialState::read, SAVE_ID);
        if (state != null) return state;
        var path = server.getSavePath(WorldSavePath.ROOT).resolve("data/" + SAVE_ID + ".dat");
        if (Files.exists(path)) throw new IllegalStateException("Unreadable form tutorial data: " + path);
        state = new FormTutorialState();
        manager.set(SAVE_ID, state);
        return state;
    }

    public boolean hasSeen(UUID player, Identifier form) {
        var forms = viewed.get(player);
        return forms != null && forms.contains(FormIdMigration.canonical(form));
    }

    public boolean markSeen(UUID player, Identifier form) {
        if (player == null || form == null) return false;
        boolean added = viewed.computeIfAbsent(player, ignored -> new HashSet<>())
                .add(FormIdMigration.canonical(form));
        if (added) markDirty();
        return added;
    }

    static FormTutorialState read(NbtCompound tag) {
        if (tag.getInt("Version") != 1 || !tag.contains("Players", NbtElement.COMPOUND_TYPE)) {
            throw new IllegalStateException("Invalid form tutorial save format");
        }
        FormTutorialState state = new FormTutorialState();
        NbtCompound players = tag.getCompound("Players");
        boolean migrated = false;
        for (String playerId : players.getKeys()) {
            UUID player = UUID.fromString(playerId);
            if (!players.contains(playerId, NbtElement.LIST_TYPE)) {
                throw new IllegalStateException("Invalid form tutorial player record: " + playerId);
            }
            NbtList list = players.getList(playerId, NbtElement.STRING_TYPE);
            if (list.size() != ((NbtList) players.get(playerId)).size()) {
                throw new IllegalStateException("Invalid form tutorial form list: " + playerId);
            }
            Set<Identifier> forms = new HashSet<>();
            for (NbtElement entry : list) {
                Identifier original = Identifier.tryParse(entry.asString());
                if (original == null) throw new IllegalStateException("Invalid form tutorial form ID");
                Identifier canonical = FormIdMigration.canonical(original);
                migrated |= !original.equals(canonical);
                forms.add(canonical);
            }
            if (!forms.isEmpty()) state.viewed.put(player, forms);
        }
        if (migrated) state.markDirty();
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        tag.putInt("Version", 1);
        NbtCompound players = new NbtCompound();
        viewed.forEach((player, forms) -> {
            NbtList list = new NbtList();
            forms.stream().map(Identifier::toString).sorted()
                    .forEach(form -> list.add(NbtString.of(form)));
            players.put(player.toString(), list);
        });
        tag.put("Players", players);
        return tag;
    }
}
