package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * A cosmetic power with ActionOverTimePower's original four-tick phase and condition sampling.
 * Its cheap per-tick lease keeps an active scene alive; particles and gameplay never run here.
 */
public final class BlueFireRingVisualPower extends Power {
    private UUID eventId;
    private final int layer;
    private final double radius;
    private final boolean flameLayer;
    private int phase = -1;
    private long epoch;
    private boolean displaying;

    private BlueFireRingVisualPower(PowerType<?> type, LivingEntity entity, double radius,
                                    boolean flameLayer, int layer) {
        super(type, entity);
        this.radius = radius;
        this.flameLayer = flameLayer;
        this.layer = layer;
        // Apoli also instantiates factories with a null entity while validating power data.
        setTicking(true);
    }

    @Override
    public void tick() {
        if (!(entity.getWorld() instanceof ServerWorld world)) return;
        if (eventId == null) {
            // Separate leases preserve overlapping empowered and amulet-toggle layers.
            UUID actor = entity.getUuid();
            eventId = new UUID(actor.getMostSignificantBits() ^ 0x42524c554552494eL,
                    actor.getLeastSignificantBits() ^ (0x4756495355414c00L + layer));
        }
        if (phase < 0) {
            // Apoli records age % interval on the first tick and executes only on later ticks.
            phase = Math.floorMod(entity.age, 4);
            epoch = world.getTime();
            return;
        }
        if (Math.floorMod(entity.age, 4) == phase) displaying = isActive();
        if (!displaying) return;
        ParametricVisuals.touch(entity, eventId, ParametricVisuals.Kind.BLUE_FIRE_RING,
                entity.getUuid(), entity.getPos(), true, epoch, 0, Math.abs(radius),
                radius, flameLayer ? 1 : 0);
    }

    @Override
    public void onRemoved() {
        if (eventId != null && entity.getWorld() instanceof ServerWorld) {
            ParametricVisuals.stop(eventId, ParametricVisuals.Kind.BLUE_FIRE_RING);
        }
    }

    public static PowerFactory<BlueFireRingVisualPower> createFactory() {
        return new PowerFactory<BlueFireRingVisualPower>(new Identifier("ssc_addon", "blue_fire_ring_visual"),
                new SerializableData().add("radius", SerializableDataTypes.DOUBLE, 6.0)
                        .add("flame_layer", SerializableDataTypes.BOOLEAN, false)
                        .add("layer", SerializableDataTypes.INT, 0),
                data -> (type, entity) -> new BlueFireRingVisualPower(type, entity,
                        data.getDouble("radius"), data.getBoolean("flame_layer"), data.getInt("layer")))
                .allowCondition();
    }
}
