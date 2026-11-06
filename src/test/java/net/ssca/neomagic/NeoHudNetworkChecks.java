package net.ssca.neomagic;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/** Wire precision, receiver lifecycle and missing-baseline recovery, rather than an estimated byte formula. */
public final class NeoHudNetworkChecks {
    private static int checks;
    private NeoHudNetworkChecks() {}
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    public static int run() { return run(fixture()); }
    public static int run(NbtCompound fixture) {
        checks = 0;
        NbtCompound state = fixture.copy();
        NeoHudPacket.Receiver[] clients = new NeoHudPacket.Receiver[20];
        for (int i = 0; i < clients.length; i++) clients[i] = new NeoHudPacket.Receiver();
        var full = wire(NeoHudPacket.between(new NbtCompound(), state, 1, 0, true));
        for (var client : clients) {
            check(client.accept(full) == NeoHudPacket.Receiver.Result.APPLIED, "new connection accepts full");
            check(client.state().equals(state), "full preserves every authoritative field");
        }
        long compactBytes = size(full), legacyBytes = legacySize(state);
        NbtCompound initial = state.copy();
        var late = new NeoHudPacket.Receiver();
        for (int step = 1; step <= 200; step++) {
            NbtCompound previous = state.copy();
            state.putInt("Age", step - 1);
            state.putInt("CD", 1000 - step * 5);
            state.putInt("Recovery", step % 400);
            state.putDouble("C", Math.sqrt(step));
            state.putDouble("Paid", step * .03125);
            state.putDouble("Yaw", step % 2 == 0 ? -0.0 : Math.nextDown(360.0));
            state.putDouble("Pitch", -step / 17.0);
            if (step % 31 == 0) { state.putString("ResultId", "result-" + step); state.putString("Result", "failed"); }
            if (step % 31 == 1) { state.remove("ResultId"); state.remove("Result"); }
            if (step == 73) state.putString("Profile", "{\"safety\":123.125,\"reference\":false}");
            boolean forceFull = step % 20 == 0;
            var update = wire(NeoHudPacket.between(previous, state, step + 1, step, forceFull));
            compactBytes += size(update);
            legacyBytes += legacySize(state);
            if (!forceFull && step != 73) check(!update.values().contains("Profile"), "static profile absent from ordinary deltas");
            for (var client : clients) {
                check(client.accept(update) == NeoHudPacket.Receiver.Result.APPLIED, "ordered delta applies");
                check(client.state().equals(state), "20 clients retain identical exact HUD values");
                NbtCompound localDisplay = client.state();
                localDisplay.putBoolean("RecentFailure", true);
                check(!client.state().contains("RecentFailure"), "local flash flags never pollute baseline");
            }
            if (step == 19) {
                check(late.accept(update) == NeoHudPacket.Receiver.Result.MISSING_BASE, "late delta requests a full baseline");
                check(late.state().isEmpty(), "missing baseline leaves the previous state untouched");
            }
            if (step >= 20) {
                check(late.accept(update) == NeoHudPacket.Receiver.Result.APPLIED, "late observer starts at next complete snapshot");
                check(late.state().equals(state), "late observer converges to current values");
            }
        }
        check(compactBytes * 3 < legacyBytes, "representative active stream is below one third of named-NBT bytes");
        var receiver = clients[0];
        NbtCompound beforeGap = receiver.state();
        var skipped = NeoHudPacket.between(state, initial, 203, 202, false);
        check(receiver.accept(wire(skipped)) == NeoHudPacket.Receiver.Result.MISSING_BASE, "gap cannot silently corrupt merged state");
        check(receiver.state().equals(beforeGap), "gap preserves last valid state");
        var recovered = wire(NeoHudPacket.between(new NbtCompound(), initial, 204, 0, true));
        check(receiver.accept(recovered) == NeoHudPacket.Receiver.Result.APPLIED, "forced full repairs a gap");
        check(receiver.state().equals(initial), "forced full deletes keys absent from new state");
        check(receiver.accept(full) == NeoHudPacket.Receiver.Result.STALE, "queued old full cannot roll state back");
        for (String lifecycle : new String[]{"disconnect", "dimension", "respawn", "reload"}) {
            receiver.clear();
            check(receiver.revision() == 0 && receiver.state().isEmpty(), lifecycle + " clears baseline");
            check(receiver.accept(wire(skipped)) == NeoHudPacket.Receiver.Result.MISSING_BASE, lifecycle + " never applies a naked delta");
            check(receiver.accept(recovered) == NeoHudPacket.Receiver.Result.APPLIED, lifecycle + " accepts full immediately");
            check(receiver.state().equals(initial), lifecycle + " rebuilds the exact state");
        }
        var copy = fixture.copy();
        var immutable = NeoHudPacket.between(new NbtCompound(), copy, 1, 0, true);
        copy.putDouble("C", 999);
        check(!immutable.values().equals(copy), "queued packets own their NBT values");
        var legacy = PacketByteBufs.create();
        try { legacy.writeNbt(fixture); check(fixture.equals(legacy.readNbt()), "legacy full-NBT path remains readable"); }
        finally { legacy.release(); }
        malformed();
        System.out.println("HUD wire checks=" + checks + " compact=" + compactBytes + " legacy=" + legacyBytes
                + "; codec/20 mirrors only, not 20 native clients.");
        return checks;
    }

    private static NeoHudPacket.Update wire(NeoHudPacket.Update value) {
        PacketByteBuf packet = PacketByteBufs.create();
        try {
            NeoHudPacket.write(packet, value);
            var decoded = NeoHudPacket.read(packet);
            check(!packet.isReadable(), "decoder consumes exactly its packet");
            check(value.full() == decoded.full() && value.revision() == decoded.revision()
                    && value.base() == decoded.base() && value.removed() == decoded.removed()
                    && value.values().equals(decoded.values()), "packet roundtrip preserves all types and precision");
            return decoded;
        } finally { packet.release(); }
    }
    private static int size(NeoHudPacket.Update value) {
        PacketByteBuf packet = PacketByteBufs.create();
        try { NeoHudPacket.write(packet, value); return packet.readableBytes(); }
        finally { packet.release(); }
    }
    private static int legacySize(NbtCompound value) {
        PacketByteBuf packet = PacketByteBufs.create();
        try { packet.writeNbt(value); return packet.readableBytes(); }
        finally { packet.release(); }
    }
    private static void malformed() {
        for (long[] masks : new long[][]{{1L << 63, 0}, {1, 1}, {0, 1}}) {
            PacketByteBuf packet = PacketByteBufs.create();
            try {
                packet.writeBoolean(false); packet.writeVarLong(2); packet.writeVarLong(1);
                packet.writeLong(masks[0]); packet.writeLong(masks[1]);
                expectInvalid(() -> NeoHudPacket.read(packet), "invalid masks cannot be merged");
            } finally { packet.release(); }
        }
        var wrongType = fixture(); wrongType.putString("C", "0.5");
        PacketByteBuf packet = PacketByteBufs.create();
        try { expectInvalid(() -> NeoHudPacket.write(packet, NeoHudPacket.between(new NbtCompound(), wrongType, 1, 0, true)), "field types are fixed"); }
        finally { packet.release(); }
        var futureField = fixture(); futureField.putInt("UnknownFutureField", 1);
        expectInvalid(() -> NeoHudPacket.between(new NbtCompound(), futureField, 1, 0, true), "future fields require a protocol version");
        expectInvalid(() -> new NeoHudPacket.Update(false, 2, 2, new NbtCompound(), 0), "delta cannot reference its own revision");
        expectInvalid(() -> new NeoHudPacket.Update(true, 1, 1, new NbtCompound(), 0), "full has no base revision");
    }
    private static void expectInvalid(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { check(true, message); }
    }

    private static NbtCompound fixture() {
        NbtCompound n = new NbtCompound();
        for (String key : new String[]{"Visible", "BookEquipped", "HasSpell", "Stored", "Reference"}) n.putBoolean(key, true);
        for (String key : new String[]{"PlayerId", "MasteryExpTen", "CastingLevel", "Recovery", "RecoveryDelay", "LockBoundary", "CD", "Age", "Prep", "CollapseStage", "Blocked"}) n.putInt(key, key.equals("Age") ? -1 : 37);
        for (String key : new String[]{"DangerMax", "C", "S", "BodyS", "FrostSpellMultiplier", "Q", "Failure", "ForecastFailure", "Offset", "FailureBasis", "Paid", "Yaw", "Pitch", "Cumulative"}) n.putDouble(key, Math.PI);
        n.putLong("FrostReadyUntil", 1234567890123L); n.putLong("NovaQuickCastUntil", Long.MAX_VALUE - 7);
        n.putString("Dimension", "minecraft:overworld"); n.putString("Form", "ssc_addon:test_form");
        n.putString("Spell", "ssc_addon:frost_spike");
        n.putString("Profile", "{\"safety\":100,\"reference\":true}");
        n.putString("ResultId", "result-0"); n.putString("Result", "failed");
        return n;
    }
}
