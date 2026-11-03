package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;

/** The accepted spell identity and anomaly amount travel with its delayed effects. */
public record SpellAnomalyContext(Identifier spellId, String element, int level, double amount,
                                  UUID casterUUID, UUID castUUID) {
    public SpellAnomalyContext {
        Objects.requireNonNull(spellId);
        Objects.requireNonNull(element);
        Objects.requireNonNull(casterUUID);
        Objects.requireNonNull(castUUID);
        if (element.isBlank() || level < 1 || !Double.isFinite(amount) || amount < 0)
            throw new IllegalArgumentException("Invalid spell anomaly snapshot");
    }

    public NbtCompound write() {
        var nbt = new NbtCompound();
        nbt.putString("Spell", spellId.toString());
        nbt.putString("Element", element);
        nbt.putInt("Level", level);
        nbt.putDouble("Amount", amount);
        nbt.putUuid("Caster", casterUUID);
        nbt.putUuid("Cast", castUUID);
        return nbt;
    }

    /** Invalid/absent older snapshots produce no anomaly event. */
    public static SpellAnomalyContext read(NbtCompound nbt) {
        if (!nbt.contains("Spell", NbtElement.STRING_TYPE)
                || !nbt.contains("Element", NbtElement.STRING_TYPE)
                || !nbt.contains("Level", NbtElement.NUMBER_TYPE)
                || !nbt.contains("Amount", NbtElement.NUMBER_TYPE)
                || !nbt.containsUuid("Caster") || !nbt.containsUuid("Cast")) return null;
        try {
            return new SpellAnomalyContext(new Identifier(nbt.getString("Spell")), nbt.getString("Element"),
                    nbt.getInt("Level"), nbt.getDouble("Amount"), nbt.getUuid("Caster"), nbt.getUuid("Cast"));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
