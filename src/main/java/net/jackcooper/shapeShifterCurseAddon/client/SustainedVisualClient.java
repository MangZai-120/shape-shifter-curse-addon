package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.network.SustainedVisuals;
import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe;
import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Kind;
import net.jackcooper.shapeShifterCurseAddon.network.SustainedVisuals.View;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Same particle recipes as the original server loops, advanced once per world tick. */
@Environment(EnvType.CLIENT)
public final class SustainedVisualClient {
    private static final DustParticleEffect GREEN = new DustParticleEffect(new Vector3f(0.30f, 0.85f, 0.30f), 1);
    private static final DustParticleEffect LETHAL = new DustParticleEffect(new Vector3f(0.72f, 0.12f, 0.12f), 0.9f);
    private static final DustParticleEffect EDGE = new DustParticleEffect(new Vector3f(0.68f, 0.68f, 0.70f), 0.8f);
    private static List<View> views = List.of();
    private static ClientWorld world;
    private static long received, lastTick = Long.MIN_VALUE;
    private SustainedVisualClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SustainedVisuals.STATE, (client, handler, buf, sender) -> {
            var dimension = buf.readIdentifier();
            long serverTime = buf.readLong();
            int count = buf.readVarInt();
            List<View> incoming = new ArrayList<>();
            for (int i = 0; i < count; i++) incoming.add(View.read(buf));
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null
                        || !client.world.getRegistryKey().getValue().equals(dimension)) return;
                if (world != client.world) clear();
                world = client.world;
                views = incoming;
                received = Math.max(serverTime, world.getTime());
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(SustainedVisualClient::tick);
    }

    private static void clear() { views = List.of(); world = null; lastTick = Long.MIN_VALUE; }

    private static void tick(MinecraftClient client) {
        if (client.world != world) { clear(); return; }
        if (world == null || client.isPaused() || views.isEmpty() || client.player == null) return;
        long now = world.getTime();
        if (now == lastTick) return;
        lastTick = now;
        if (now - received > 60) { views = List.of(); return; }
        for (View view : views) {
            int age = (int) Math.max(0, now - view.epoch());
            if (view.duration() > 0 && age > view.duration()) continue;
            Entity source = world.getEntityById(view.entityId());
            if (source != null && !source.getUuid().equals(view.key().source())) source = null;
            // Far-away force particles remain visible even outside entity tracking distance.
            Vec3d pos = source == null ? view.pos() : source.getPos();
            if (view.key().kind() == Kind.SEED_FIELD && source != null) {
                source.prevYaw = (age - 1) * 5f;
                source.setYaw(age * 5f);
                if (source instanceof net.minecraft.entity.decoration.ArmorStandEntity stand) {
                    stand.prevBodyYaw = source.prevYaw;
                    stand.bodyYaw = source.getYaw();
                    stand.prevHeadYaw = source.prevYaw;
                    stand.headYaw = source.getYaw();
                }
            }
            // Same source/time seed on both clients, independent of unrelated local particles.
            var random = new java.util.Random(view.key().source().getLeastSignificantBits() ^ now ^ view.key().kind().ordinal());
            VisualRecipe.emit(view.key().kind(), age, now, view.duration(), view.width(), view.eyeHeight(),
                    view.radius(), view.outerRadius(), random, batch ->
                        net.jackcooper.shapeShifterCurseAddon.client.particle.FirstPersonParticles.emit(
                            VisualRecipe.isDecoration(view.key().kind(), batch) ? view.decorationOwner() : null,
                            () -> cloud(view, particle(batch.particle()),
                                pos.add(batch.x(), batch.y(), batch.z()), batch.count(),
                                batch.dx(), batch.dy(), batch.dz(), batch.speed(), random)));
        }
    }

    private static ParticleEffect particle(VisualRecipe.Particle particle) {
        return switch (particle) {
            case SOUL_FIRE -> ParticleTypes.SOUL_FIRE_FLAME;
            case FLAME -> ParticleTypes.FLAME;
            case LAVA -> ParticleTypes.LAVA;
            case LARGE_SMOKE -> ParticleTypes.LARGE_SMOKE;
            case LETHAL_DUST -> LETHAL;
            case EDGE_DUST -> EDGE;
            case SNOWFLAKE -> ParticleTypes.SNOWFLAKE;
            case CLOUD -> ParticleTypes.CLOUD;
            case GREEN_DUST -> GREEN;
            case HAPPY_VILLAGER -> ParticleTypes.HAPPY_VILLAGER;
            case WARPED_SPORE -> ParticleTypes.WARPED_SPORE;
        };
    }

    /** Reproduce ParticleS2CPacket: count=0 is directed velocity; count>0 uses Gaussian spread. */
    private static void cloud(View view, ParticleEffect particle, Vec3d p, int count,
                              double dx, double dy, double dz, double speed, java.util.Random random) {
        var player = MinecraftClient.getInstance().player;
        double range = view.key().kind().range;
        if (player == null) return;
        if (view.key().kind() == Kind.FROST_STORM) {
            if (player.squaredDistanceTo(p) > range * range) return;
        } else if (!player.getBlockPos().isWithinDistance(p, range)) return;
        // The original packet serializes these fields as floats.
        dx = (float) dx; dy = (float) dy; dz = (float) dz; speed = (float) speed;
        if (count == 0) {
            world.addParticle(particle, view.key().kind().force, p.x, p.y, p.z, dx * speed, dy * speed, dz * speed);
        } else for (int i = 0; i < count; i++) {
            world.addParticle(particle, view.key().kind().force,
                    p.x + random.nextGaussian() * dx, p.y + random.nextGaussian() * dy,
                    p.z + random.nextGaussian() * dz, random.nextGaussian() * speed,
                    random.nextGaussian() * speed, random.nextGaussian() * speed);
        }
    }
}
