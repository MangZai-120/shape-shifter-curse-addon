package net.jackcooper.shapeShifterCurseAddon.cooldown;

import com.google.gson.JsonParser;
import io.github.apace100.calio.data.SerializableData;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.PacketByteBuf;
import java.io.*;
import java.util.*;

final class SkillCooldownPersistenceTest {
    static void run() throws Exception {
        parsing();
        staleCallbacksAndRetune();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID(), entity = UUID.randomUUID();
        var m = new SkillCastManager();
        m.force(owner, "test:cooldown", 100, 1000);
        m.force(other, "test:cooldown", 100, 1000);
        m.onPlayerRemoved(owner, 1020, ignored -> null);
        check(m.cooldown(owner, "test:cooldown").remaining(9000) == 80, "offline time is frozen");
        check(m.cooldown(other, "test:cooldown").remaining(1100) == 0, "other player still ticks");
        var restored = roundTrip(m);
        restored.onServerStarted(9000);
        check(restored.cooldown(owner, "test:cooldown").remaining(9000) == 80, "startup cannot delete paused CD");
        restored.onPlayerJoined(owner, 9000);
        check(restored.cooldown(owner, "test:cooldown").remaining(9030) == 50, "resume from remaining");
        restored.onPlayerJoined(owner, 9050);
        check(restored.cooldown(owner, "test:cooldown").remaining(9050) == 30, "duplicate join cannot reset");

        for (String mode : new String[]{"on_cast", "on_release", "on_end"}) {
            m = new SkillCastManager();
            long cast = m.begin(owner, "test:storm", new SkillCastManager.ResolvedConfig(100, 30, mode), 1000);
            m.released(cast, 1020);
            m.bindEntity(cast, entity);
            m.onPlayerRemoved(owner, 1040, ignored -> null);
            restored = roundTrip(m);
            var active = restored.control(owner, "test:storm");
            check(active != null && active.castId == cast && active.persistentEntity.equals(entity), "persistent effect binding restored");
            check(restored.begin(owner, "test:storm", active.config, 5000) < 0, "restored storm blocks duplicate cast");
            restored.finish(cast, 6000); // Entity can end while the owner is offline.
            int expected = mode.equals("on_cast") ? 60 : mode.equals("on_release") ? 80 : 100;
            check(restored.cooldown(owner, "test:storm").remaining(9000) == expected, "offline effect finish preserves mode: " + mode);
            restored.onPlayerJoined(owner, 9000);
            restored.finish(cast, 9010);
            check(restored.cooldown(owner, "test:storm").remaining(9010) == expected - 10, "duplicate entity removal is idempotent");
        }

        m = new SkillCastManager();
        long charging = m.begin(owner, "test:charge", new SkillCastManager.ResolvedConfig(100, 0, "on_cast"), 1000);
        restored = roundTrip(m);
        check(restored.control(owner, "test:charge") == null && restored.cooldown(owner, "test:charge").remaining(1000) == 0,
                "unrecoverable charging applies zero failure after restart");
        restored.onPlayerJoined(owner, 2000);
        check(restored.begin(owner, "test:charge", new SkillCastManager.ResolvedConfig(100, 0, "on_cast"), 2000) > charging,
                "cast sequence survives NBT");

        m = new SkillCastManager();
        long spark = m.begin(owner, "my_addon:form_upgrade_familiar_fox_spark", new SkillCastManager.ResolvedConfig(40, 0, "on_cast"), 1000);
        m.finish(spark, 1000);
        check(m.begin(owner, "my_addon:form_upgrade_familiar_fox_fire_ring", new SkillCastManager.ResolvedConfig(40, 0, "on_cast"), 1010) < 0,
                "spark and ring share one gate");
        check(roundTrip(m).cooldown(owner, "my_addon:form_upgrade_familiar_fox_spark").remaining(5000) == 40, "shared domain persists");

        var views = List.of(new SkillCastManager.View("test:active", 2, 0, 0),
                new SkillCastManager.View("test:failed", 0, 300, 300),
                new SkillCastManager.View("test:long", 0, Integer.MAX_VALUE, Integer.MAX_VALUE));
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            SkillCooldownPacket.write(buf, views);
            check(SkillCooldownPacket.read(buf).equals(views) && !buf.isReadable(), "production wire codec round trip");
            buf.clear(); buf.writeVarInt(-1);
            rejects(() -> SkillCooldownPacket.read(buf));
            buf.clear(); buf.writeVarInt(1); buf.writeString("test:bad"); buf.writeVarInt(0); buf.writeVarInt(-1); buf.writeVarInt(1);
            rejects(() -> SkillCooldownPacket.read(buf));
        } finally { buf.release(); }
        System.out.println("Cooldown parsing, NBT binary round trips, offline resume, persistent effect binding, shared domain and wire codec PASS.");
    }

    private static void staleCallbacksAndRetune() throws Exception {
        UUID owner = UUID.randomUUID();
        String skill = "test:callback";
        for (String mode : new String[]{"on_cast", "on_release", "on_end"}) {
            var m = new SkillCastManager();
            var config = new SkillCastManager.ResolvedConfig(100, 30, mode);
            long old = m.begin(owner, skill, config, 1000);
            m.complete(old, 1020);
            long deadline = m.cooldown(owner, skill).endTick;
            m.complete(old, 1030);
            m.fail(old, 1040);
            m.interrupt(old, 1050);
            check(m.cooldown(owner, skill).endTick == deadline, "duplicate callbacks preserve deadline: " + mode);
            long next = m.begin(owner, skill, config, 2000);
            check(next > old, "next cast accepted");
            m.released(old, 2010);
            m.complete(old, 2020);
            m.fail(old, 2030);
            m.interrupt(old, 2040);
            m.retune(old, 999);
            var active = m.control(owner, skill);
            check(active != null && active.castId == next && active.phase == SkillCastManager.PHASE_CHARGING
                    && active.config.cooldown == 100, "stale callbacks cannot change new cast: " + mode);
            m.complete(next, 2050);
            check(m.cooldown(owner, skill).remaining(2050) == (mode.equals("on_cast") ? 50 : 100),
                    "new cast settles with its own mode");
        }
        var m = new SkillCastManager();
        long cast = m.begin(owner, skill, new SkillCastManager.ResolvedConfig(100, 30, "on_end"), 1000);
        m.released(cast, 1010);
        m.bindEntity(cast, UUID.randomUUID());
        m.setDirty(false); // Simulate the last successful world save.
        m.retune(cast, 47);
        check(m.isDirty(), "retune before CD starts must schedule a save");
        var restored = roundTrip(m);
        check(restored.control(owner, skill).config.cooldown == 47, "retuned snapshot survives binary NBT");
        restored.finish(cast, 1100);
        check(restored.cooldown(owner, skill).remaining(1100) == 47, "restored end uses retuned duration");
        System.out.println("Duplicate/stale callbacks and on_end retune dirty/NBT persistence PASS.");
    }

    private static void parsing() {
        var fields = SkillCooldownSpec.addFields(new SerializableData(), 100, "on_end");
        var valid = JsonParser.parseString("{\"cooldown\":2401,\"fail_cooldown\":300,\"cooldown_start\":\"on_release\",\"extra_cooldowns\":{\"link\":600}}").getAsJsonObject();
        var spec = SkillCooldownSpec.read(fields.read(valid));
        check(spec.cooldown() == 2401 && spec.failCooldown() == 300 && spec.extra("link") == 600, "actual SerializableData fields");
        check(SkillCooldownSpec.read(fields.read(JsonParser.parseString("{}").getAsJsonObject())).cooldown() == 100, "defaults");
        for (String bad : new String[]{"-1", "1.5", "2147483648", "\"100\"", "null", "true"}) {
            var json = valid.deepCopy(); json.add("cooldown", JsonParser.parseString(bad));
            rejects(() -> SkillCooldownSpec.read(fields.read(json)));
            json.addProperty("type", "my_addon:fail_aware_active_self");
            rejects(() -> SkillCooldownSpec.validatePower(json));
        }
        var badStart = valid.deepCopy(); badStart.addProperty("cooldown_start", "typo");
        rejects(() -> SkillCooldownSpec.read(fields.read(badStart)));
        var nested = JsonParser.parseString("{\"type\":\"apoli:multiple\",\"key_activation\":{\"type\":\"my_addon:fail_aware_active_self\",\"extra_cooldowns\":{\"link\":-1}}}");
        rejects(() -> SkillCooldownSpec.validatePower(nested));
    }

    private static SkillCastManager roundTrip(SkillCastManager manager) throws Exception {
        var bytes = new ByteArrayOutputStream();
        NbtIo.write(manager.writeNbt(new NbtCompound()), new DataOutputStream(bytes));
        var nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        var restored = SkillCastManager.fromNbt(nbt);
        restored.onServerStarted(nbt.getLong("savedAt"));
        return restored;
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Invalid input was accepted");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
