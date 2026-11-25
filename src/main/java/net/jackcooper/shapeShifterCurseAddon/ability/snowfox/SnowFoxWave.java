package net.jackcooper.shapeShifterCurseAddon.ability.snowfox;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.random.Random;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Damage lanes and individual visual spikes follow terrain at the same outward speed. */
final class SnowFoxWave {
    private static final int RAYS = 48;
    private static final double RADIUS = 6;
    private static final double SPEED = RADIUS / 20;
    private static final double MIN_SPACING = .68;
    private final Vec3d origin;
    private final Vec3d[] tips = new Vec3d[RAYS];
    private final Set<UUID> touched = new HashSet<>();
    private final Set<UUID> shock = new HashSet<>();
    private final List<VisualPath> pendingSpikes = new ArrayList<>();
    private final Random random;
    private int tick;

    private static final class VisualPath {
        final double distance, dx, dz;
        final Vec3d target;
        Vec3d feet;

        VisualPath(Vec3d origin, double distance, double angle) {
            this.distance = distance;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            target = origin.add(dx * distance, 0, dz * distance);
            feet = origin;
        }
    }

    private void scatterSpikes() {
        // Only the first and last points are timed explicitly. Every other point is
        // sampled over the area of the disk; its actual distance determines its arrival.
        pendingSpikes.add(new VisualPath(origin, .08 + .18 * random.nextDouble(), random.nextDouble() * Math.PI * 2));
        pendingSpikes.add(new VisualPath(origin, RADIUS, random.nextDouble() * Math.PI * 2));
        int count = 56 + random.nextInt(13);
        for (int attempt = 0; attempt < count * 12 && pendingSpikes.size() < count; attempt++) {
            var candidate = new VisualPath(origin, RADIUS * Math.sqrt(random.nextDouble()), random.nextDouble() * Math.PI * 2);
            boolean overlaps = false;
            for (var placed : pendingSpikes) {
                if (candidate.target.squaredDistanceTo(placed.target) < MIN_SPACING * MIN_SPACING) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) pendingSpikes.add(candidate);
        }
    }

    private void advanceSpikes(ServerPlayerEntity owner, double radius) {
        List<Vec3d> revealed = new ArrayList<>();
        for (var it = pendingSpikes.iterator(); it.hasNext();) {
            var spike = it.next();
            double r = Math.min(spike.distance, radius);
            spike.feet = ground(owner.getServerWorld(), owner, spike.feet, origin.x + spike.dx * r, origin.z + spike.dz * r);
            if (spike.feet == null) {
                it.remove();
            } else if (spike.distance <= radius) {
                revealed.add(spike.feet);
                it.remove();
            }
        }
        for (int start = 0; start < revealed.size(); start += 48)
            send(owner, revealed.subList(start, Math.min(start + 48, revealed.size())));
    }

    SnowFoxWave(ServerPlayerEntity p, Vec3d origin) {
        this.origin = origin;
        random = Random.create(p.getRandom().nextLong());
        scatterSpikes();
        java.util.Arrays.fill(tips,origin);
        var world = p.getServerWorld();
        for (var t : world.getEntitiesByClass(LivingEntity.class,SnowFoxRoll.bounds(origin,2),e -> SnowFoxRoll.enemy(p,e))) {
            if (SnowFoxRoll.nearest(t.getBoundingBox(),origin).squaredDistanceTo(origin) <= 4
                    && t.damage(p.getDamageSources().playerAttack(p),8)) shock.add(t.getUuid());
        }
        SnowFoxSounds.play(p, origin, SoundEvents.ENTITY_GENERIC_EXPLODE, .76f, 1.08f);
        SnowFoxSounds.play(p, origin, Blocks.ICE.getDefaultState().getSoundGroup().getBreakSound(), 1f, 1f);
        for (int i=0;i<16;i++) {
            double angle = Math.PI*2*i/16;
            world.spawnParticles(ParticleTypes.CLOUD,origin.x,origin.y+.12,origin.z,0,Math.cos(angle),.05,Math.sin(angle),.22);
        }
    }

    boolean tick(ServerPlayerEntity p) {
        double radius = ++tick * SPEED;
        var world = p.getServerWorld();
        var targets = world.getEntitiesByClass(LivingEntity.class,new Box(origin.add(-radius-1,-22,-radius-1),
                origin.add(radius+1,23,radius+1)),e -> SnowFoxRoll.enemy(p,e) && !touched.contains(e.getUuid()));
        for (int i=0;i<RAYS;i++) {
            Vec3d previous = tips[i];
            if (previous == null) continue;
            double angle = Math.PI*2*i/RAYS;
            Vec3d next = ground(world,p,previous,origin.x+Math.cos(angle)*radius,origin.z+Math.sin(angle)*radius);
            tips[i] = next;
            if (next == null) continue;
            // Widen the hit strip only enough to join adjacent rays at the outer edge.
            double width = .18 + radius*Math.PI/RAYS;
            for (var t : targets) {
                if (touched.contains(t.getUuid())) continue;
                var b = t.getBoundingBox();
                if (b.maxY < next.y || b.minY > next.y+1.1) continue;
                Vec3d at = SnowFoxRoll.nearest(b,next);
                if (Math.hypot(at.x-next.x,at.z-next.z) > width
                        || Math.hypot(at.x-origin.x,at.z-origin.z) > 6.0001) continue;
                touched.add(t.getUuid());
                int oldRegen = t.timeUntilRegen;
                if (shock.contains(t.getUuid())) t.timeUntilRegen = 0;
                t.damage(p.getDamageSources().playerAttack(p),6);
                t.timeUntilRegen = Math.max(oldRegen,t.timeUntilRegen);
            }
        }
        advanceSpikes(p, radius);
        if (tick == 5 || tick == 12 || tick == 19) {
            int lane = random.nextInt(RAYS);
            for (int i = 0; i < RAYS; i++) {
                var tip = tips[(lane + i) % RAYS];
                if (tip != null) {
                    SnowFoxSounds.play(p, tip, Blocks.ICE.getDefaultState().getSoundGroup().getBreakSound(),
                            .26f, .94f + random.nextFloat() * .16f);
                    break;
                }
            }
        }
        return tick < 20;
    }

    private Vec3d ground(ServerWorld world, ServerPlayerEntity owner, Vec3d old, double x, double z) {
        var from = new Vec3d(x,old.y+1.001,z);
        // Starting inside a two-high wall must stop this ray instead of selecting its interior.
        BlockPos bp = BlockPos.ofFloored(from);
        var shape = world.getBlockState(bp).getCollisionShape(world,bp);
        for (Box box : shape.getBoundingBoxes()) if (box.offset(bp).contains(from)) return null;
        var hit = world.raycast(new RaycastContext(from,new Vec3d(x,old.y-1.001,z),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,owner));
        return hit.getType() == HitResult.Type.MISS || Math.abs(hit.getPos().y-old.y)>1.0001 ? null : hit.getPos();
    }

    private void send(ServerPlayerEntity p, List<Vec3d> spikes) {
        var buffer = PacketByteBufs.create();
        buffer.writeVarInt(spikes.size());
        for (var tip : spikes) {
            buffer.writeDouble(tip.x); buffer.writeDouble(tip.y); buffer.writeDouble(tip.z);
            buffer.writeFloat(random.nextFloat() * (float)(2 * Math.PI));
        }
        for (var peer : PlayerLookup.around(p.getServerWorld(),origin,64))
            ServerPlayNetworking.send(peer,SnowFoxMotion.SPIKES,PacketByteBufs.copy(buffer));
    }
}
