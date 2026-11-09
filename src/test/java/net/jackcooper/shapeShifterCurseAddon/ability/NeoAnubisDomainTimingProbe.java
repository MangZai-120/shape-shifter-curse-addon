package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Exercises the real domain tick loop, including the final sustain tick and the retraction boundary. */
public final class NeoAnubisDomainTimingProbe {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void verify(ServerPlayerEntity player, boolean enhanced, int expectedTicks, int radius)
            throws ReflectiveOperationException {
        Class<?> dataType = Class.forName(AnubisWolfSpDeathDomain.class.getName() + "$DomainData");
        var constructor = dataType.getDeclaredConstructor(ServerWorld.class, BlockPos.class, int.class);
        constructor.setAccessible(true);
        Object data = constructor.newInstance(player.getServerWorld(), player.getBlockPos(), (int) player.getY());
        Field phase = field(dataType, "phase");
        Field ticks = field(dataType, "ticksElapsed");
        Field extent = field(dataType, "currentRadius");
        phase.set(data, Enum.valueOf((Class) phase.getType(), "SUSTAINING"));
        field(dataType, "enhanced").setBoolean(data, enhanced);
        extent.setDouble(data, radius);
        Map<UUID, Object> domains = (Map<UUID, Object>) field(AnubisWolfSpDeathDomain.class, "ACTIVE_DOMAINS").get(null);
        domains.put(player.getUuid(), data);
        String label = enhanced ? "enhanced" : "ordinary";
        for (int elapsed = 1; elapsed < expectedTicks; elapsed++) {
            AnubisWolfSpDeathDomain.tick(player);
            check(phase.get(data).toString().equals("SUSTAINING"), label + " ends early at tick " + elapsed);
            check(ticks.getInt(data) == elapsed, label + " clock advances exactly once at tick " + elapsed);
        }
        AnubisWolfSpDeathDomain.tick(player);
        check(phase.get(data).toString().equals("RETRACTING"), label + " retracts exactly at configured duration");
        check(ticks.getInt(data) == 0, label + " retraction clock starts at zero");
        check(extent.getDouble(data) == radius, label + " starts retraction at its full radius");
        AnubisWolfSpDeathDomain.tick(player);
        check(Math.abs(extent.getDouble(data) - (radius - .25)) < 1e-8,
                label + " retains its quarter-block-per-tick retraction speed");
        AnubisWolfSpDeathDomain.forceRestoreAll();
    }
    public static void start(TestContext context) {
        context.waitAndRun(70, () -> {
            try {
                ServerPlayerEntity player = NeoFormCastingProbe.player(context, FormIdentifiers.ANUBIS_WOLF_SP, 0);
                verify(player, false, 300, 24);
                verify(player, true, 500, 32);
                System.out.println("ANUBIS_DOMAIN_TIMING_PASS " + checks + " checks");
                context.complete();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
