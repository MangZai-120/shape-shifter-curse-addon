package net.jackcooper.shapeShifterCurseAddon.tutorial;

import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;

/** Save compatibility and player isolation, including all centralized form renames. */
public final class FormTutorialStateTest {
    private static int checks;
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    public static void main(String[] args) {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        Identifier form = new Identifier("my_addon", "ocelot_wind_spirit");
        FormTutorialState state = new FormTutorialState();
        check(!state.hasSeen(alice, form), "A new player's form is unseen");
        check(state.markSeen(alice, form), "First display creates a viewing record");
        check(!state.markSeen(alice, form), "A repeated display is idempotent");
        check(!state.hasSeen(bob, form), "One player's display does not consume another player's tutorial");
        FormTutorialState restored = FormTutorialState.read(state.writeNbt(new NbtCompound()));
        check(restored.hasSeen(alice, form), "Viewing record survives the NBT roundtrip");
        check(!restored.hasSeen(bob, form), "Player isolation survives the NBT roundtrip");
        check(!restored.isDirty(), "A current-format read does not unnecessarily dirty the save");

        NbtCompound legacy = new NbtCompound(), players = new NbtCompound();
        legacy.putInt("Version", 1);
        NbtList aliases = new NbtList();
        FormIdMigration.aliases().keySet().stream().map(Identifier::toString)
                .forEach(id -> aliases.add(NbtString.of(id)));
        players.put(alice.toString(), aliases);
        legacy.put("Players", players);
        FormTutorialState migrated = FormTutorialState.read(legacy);
        FormIdMigration.aliases().forEach((oldId, newId) -> {
            check(migrated.hasSeen(alice, newId), "Renamed form retains its viewed record: " + oldId);
            check(migrated.hasSeen(alice, oldId), "Legacy lookup resolves to the same viewed record: " + oldId);
            check(!migrated.markSeen(alice, newId), "Renamed form is not recorded a second time: " + oldId);
        });
        check(migrated.isDirty(), "Renamed records are marked for save migration");
        String saved = migrated.writeNbt(new NbtCompound()).asString();
        for (Identifier oldId : FormIdMigration.aliases().keySet()) {
            check(!saved.contains('"' + oldId.toString() + '"'), "The next write removes legacy IDs: " + oldId);
        }
        NbtCompound unknownVersion = state.writeNbt(new NbtCompound());
        unknownVersion.putInt("Version", 2);
        rejects(unknownVersion, "An unsupported save version cannot silently reset viewing records");
        NbtCompound malformed = state.writeNbt(new NbtCompound());
        NbtList numbers = new NbtList();
        numbers.add(NbtInt.of(1));
        malformed.getCompound("Players").put(alice.toString(), numbers);
        rejects(malformed, "A malformed form list cannot silently drop viewing records");
        System.out.println("FORM_TUTORIAL_STATE_PASS: " + checks + " checks");
    }

    private static void rejects(NbtCompound tag, String why) {
        try {
            FormTutorialState.read(tag);
            throw new AssertionError(why);
        } catch (IllegalStateException expected) {
            check(true, why);
        }
    }
}
