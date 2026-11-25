package net.jackcooper.shapeShifterCurseAddon.ability.snowfox;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Each radial path follows its own terrain. Damage and models use exactly the same front. */
final class SnowFoxWave {
    private static final int RAYS = 48;
    private final Vec3d origin;
    private final Vec3d[] tips = new Vec3d[RAYS];
    private final Set<UUID> touched = new HashSet<>();
    private final Set<UUID> shock = new HashSet<>();
    private int tick;

    SnowFoxWave(ServerPlayerEntity p, Vec3d origin) {
        this.origin = origin;
        java.util.Arrays.fill(tips,origin);
        var world = p.getServerWorld();
        for (var t : world.getEntitiesByClass(LivingEntity.class,SnowFoxRoll.bounds(origin,2),e -> SnowFoxRoll.enemy(p,e))) {
            if (SnowFoxRoll.nearest(t.getBoundingBox(),origin).squaredDistanceTo(origin) <= 4
                    && t.damage(p.getDamageSources().playerAttack(p),8)) shock.add(t.getUuid());
        }
        SnowFoxSounds.play(p, origin, SnowFoxSounds.IMPACT, 1.2f, 1f);
        for (int i=0;i<16;i++) {
            double angle = Math.PI*2*i/16;
            world.spawnParticles(ParticleTypes.CLOUD,origin.x,origin.y+.12,origin.z,0,Math.cos(angle),.05,Math.sin(angle),.22);
        }
        send(p,java.util.List.of(origin));
    }

    boolean tick(ServerPlayerEntity p) {
        double radius = ++tick * .3;
        var world = p.getServerWorld();
        var visible = new ArrayList<Vec3d>();
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
            int stride = radius < 1.5 ? 8 : radius < 3 ? 4 : 2;
            if (tick % 2 == 0 && i % stride == (tick/2)%stride) visible.add(next);
        }
        if (!visible.isEmpty()) {
            send(p,visible);
            if (tick == 8 || tick == 16)
                SnowFoxSounds.play(p, visible.get(visible.size()/2), SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, .48f, tick == 8 ? .95f : 1.25f);
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

    private void send(ServerPlayerEntity p, java.util.List<Vec3d> spikes) {
        var buffer = PacketByteBufs.create();
        buffer.writeVarInt(spikes.size());
        for (var tip : spikes) {
            buffer.writeDouble(tip.x); buffer.writeDouble(tip.y); buffer.writeDouble(tip.z);
            buffer.writeFloat((float)Math.atan2(tip.z-origin.z,tip.x-origin.x));
        }
        for (var peer : PlayerLookup.around(p.getServerWorld(),origin,64))
            ServerPlayNetworking.send(peer,SnowFoxMotion.SPIKES,PacketByteBufs.copy(buffer));
    }
}
