package net.jackcooper.shapeShifterCurseAddon.entity;

import java.util.Map;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.SnowFoxSpFrostStorm;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceSnapshot;
import net.jackcooper.shapeShifterCurseAddon.balance.SscBalanceSchema;
import net.minecraft.nbt.NbtCompound;

/** Real Fabric-transformed entity/NBT methods; no running world or chunk persistence claim. */
public final class FrostStormIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("frost.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "frost-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(FrostStormIntegrationTest.class.getName() + "$Probe", true, loader).getMethod("run").invoke(null);
    }

    public static final class Probe {
        public static void run() throws Exception {
            var schema = SscBalanceSchema.create();
            var published = BalanceIntegration.class.getDeclaredField("publishedServer");
            published.setAccessible(true);
            published.set(null, BalanceSnapshot.defaults(schema));
            var storm = new FrostStormEntity(SscAddon.FROST_STORM_ENTITY, null);
            check(SscAddon.FROST_STORM_ENTITY.isSaveable(), "registered entity type is saveable");
            var old = new NbtCompound();
            storm.writeCustomDataToNbt(old);
            old.putInt("TicksAlive", 59);
            old.putUuid("Owner", UUID.randomUUID());
            storm.readCustomDataFromNbt(old);
            published.set(null, BalanceSnapshot.fromTree(schema,
                    Map.of("schema_version", 1L, "abilities", Map.of("frost_storm", Map.of("duration", 400L, "damage_per_second", 4.0))), "test-B"));
            SnowFoxSpFrostStorm.clearAll();
            var saved = new NbtCompound();
            storm.writeCustomDataToNbt(saved);
            check(saved.equals(old), "reload clear preserves all six snapshot fields, age and owner");
            var fresh = new FrostStormEntity(SscAddon.FROST_STORM_ENTITY, null);
            var freshNbt = new NbtCompound();
            fresh.writeCustomDataToNbt(freshNbt);
            check(freshNbt.getCompound("Balance").getInt("duration") == 400, "new instance uses B duration");
            check(freshNbt.getCompound("Balance").getDouble("damage_per_second") == 4.0, "new instance uses B damage");
            SnowFoxSpFrostStorm.resetForServerStart();
            fresh.readCustomDataFromNbt(saved);
            var restored = new NbtCompound();
            fresh.writeCustomDataToNbt(restored);
            check(restored.equals(saved), "NBT restore under B retains A snapshot, age and owner");
            // Isolate the entity's lifetime branch from world I/O and visual/damage effects.
            // This is not a real ServerWorld tick or a chunk persistence test.
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            var world = (net.minecraft.client.world.ClientWorld) unsafe.allocateInstance(net.minecraft.client.world.ClientWorld.class);
            var resumed = new LifetimeProbe(world); // Uninitialized world's isClient is false.
            resumed.readCustomDataFromNbt(saved);
            resumed.tick();
            var nextTick = new NbtCompound();
            resumed.writeCustomDataToNbt(nextTick);
            check(!resumed.isRemoved() && nextTick.getInt("TicksAlive") == 60, "first resumed tick advances 59 to 60 without discard");
            for (int i = 60; i < 200; i++) resumed.tick();
            check(!resumed.isRemoved(), "restored A instance survives through tick 200");
            resumed.tick();
            check(resumed.isRemoved(), "restored A instance expires at original tick 201 under B");
            var legacy = new FrostStormEntity(SscAddon.FROST_STORM_ENTITY, null);
            legacy.readCustomDataFromNbt(new NbtCompound());
            var legacyNbt = new NbtCompound();
            legacy.writeCustomDataToNbt(legacyNbt);
            check(legacyNbt.getInt("TicksAlive") == 0 && legacyNbt.getCompound("Balance").equals(freshNbt.getCompound("Balance")),
                    "missing legacy fields keep construction defaults and zero age");
            var cdField = SnowFoxSpFrostStorm.class.getDeclaredField("COOLDOWN_PLAYERS");
            cdField.setAccessible(true);
            @SuppressWarnings("unchecked") var cooldowns = (Map<UUID, Long>) cdField.get(null);
            var player = UUID.randomUUID();
            cooldowns.put(player, 600L);
            SnowFoxSpFrostStorm.clearAll();
            check(cooldowns.get(player) == 600L, "reload retains cooldown");
            SnowFoxSpFrostStorm.resetForServerStart();
            check(cooldowns.isEmpty(), "new server clears old tick-clock cooldowns");
            System.out.println("FrostStorm: registered saving, A/B snapshots, NBT age/owner roundtrip, resumed lifetime 59->60->200->discard, legacy defaults and cooldown lifecycle PASS; chunk/world reload NOT tested.");
        }

        private static final class LifetimeProbe extends FrostStormEntity {
            LifetimeProbe(net.minecraft.world.World world) { super(SscAddon.FROST_STORM_ENTITY, world); }
            @Override public void baseTick() { /* Skip vanilla world/environment processing. */ }
        }

        private static void check(boolean result, String message) {
            if (!result) throw new AssertionError(message);
        }
    }
}
