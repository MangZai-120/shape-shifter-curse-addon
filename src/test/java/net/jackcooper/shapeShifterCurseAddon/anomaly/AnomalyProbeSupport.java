package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;

/** Helpers shared by the disposable real-world anomaly acceptance and performance probes. */
final class AnomalyProbeSupport {
    private AnomalyProbeSupport() {}

    static final class Checks {
        int count;

        void that(boolean condition, String message) {
            count++;
            if (!condition) throw new AssertionError(message);
        }

        void near(double actual, double expected, String message) {
            that(Math.abs(actual - expected) < 1e-5,
                    message + ": " + actual + " != " + expected);
        }
    }

    static PigEntity pig(TestContext test, Vec3d position, float maximumHealth) {
        PigEntity target = EntityType.PIG.create(test.getWorld());
        if (target == null) throw new AssertionError("Cannot create a real target");
        target.setAiDisabled(true);
        target.setNoGravity(true);
        target.noClip = true;
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(maximumHealth);
        target.setHealth(maximumHealth);
        target.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
        if (!test.getWorld().spawnEntity(target)) throw new AssertionError("Cannot spawn a real target");
        return target;
    }

    static void later(TestContext test, long ticks, Runnable action) {
        test.waitAndRun(ticks, () -> {
            try { action.run(); }
            catch (Throwable failure) {
                failure.printStackTrace();
                test.throwGameTestException(failure.toString());
            }
        });
    }

    static void report(String filename, Map<String, ?> values) {
        try {
            Files.writeString(Path.of(filename), net.ssca.neomagic.NeoState.JSON.toJson(values));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot save probe result " + filename, failure);
        }
    }

    /** Primitive preallocated samples keep observer allocations out of the measured tick body. */
    static final class Samples {
        final long[] nanos;
        final long[] bytes;
        int size;

        Samples(int capacity) {
            nanos = new long[capacity];
            bytes = new long[capacity];
        }

        void add(long elapsedNanos, long allocatedBytes) {
            if (size >= nanos.length) throw new AssertionError("Performance sample buffer overflow");
            nanos[size] = elapsedNanos;
            bytes[size++] = allocatedBytes;
        }

        Map<String, Object> statistics() {
            if (size == 0) throw new AssertionError("No real server tick samples");
            long[] ordered = Arrays.copyOf(nanos, size);
            Arrays.sort(ordered);
            long totalNanos = 0;
            long totalBytes = 0;
            int measuredBytes = 0;
            for (int i = 0; i < size; i++) {
                totalNanos += nanos[i];
                if (bytes[i] >= 0) { totalBytes += bytes[i]; measuredBytes++; }
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("samples", size);
            result.put("mean_ms", totalNanos / 1e6 / size);
            result.put("p50_ms", quantile(ordered, .50) / 1e6);
            result.put("p95_ms", quantile(ordered, .95) / 1e6);
            result.put("p99_ms", quantile(ordered, .99) / 1e6);
            result.put("max_ms", ordered[size - 1] / 1e6);
            result.put("allocation_samples", measuredBytes);
            result.put("allocated_bytes_per_tick", measuredBytes == 0 ? null : (double) totalBytes / measuredBytes);
            return result;
        }

        private static long quantile(long[] ordered, double percentile) {
            int index = Math.max(0, Math.min(ordered.length - 1,
                    (int) Math.ceil(ordered.length * percentile) - 1));
            return ordered[index];
        }
    }

    static final class AllocationCounter {
        private final com.sun.management.ThreadMXBean bean;

        AllocationCounter() {
            var candidate = ManagementFactory.getThreadMXBean();
            bean = candidate instanceof com.sun.management.ThreadMXBean supported
                    && supported.isThreadAllocatedMemorySupported() ? supported : null;
            if (bean != null && !bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        }

        long current(long serverThreadId) {
            return bean == null ? -1 : bean.getThreadAllocatedBytes(serverThreadId);
        }

        static long difference(long before, long after) {
            return before >= 0 && after >= before ? after - before : -1;
        }
    }
}
