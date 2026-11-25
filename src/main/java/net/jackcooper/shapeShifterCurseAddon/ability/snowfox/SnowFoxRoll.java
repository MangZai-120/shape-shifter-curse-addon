package net.jackcooper.shapeShifterCurseAddon.ability.snowfox;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

final class SnowFoxRoll {
    record Step(Vec3d feet, Vec3d contact, LivingEntity enemy) {}
    double mass = 10;
    private Vec3d velocity = Vec3d.ZERO;
    private Vec3d input;
    private double fallSpeed;
    private double snowHitDistance = .9;
    private boolean wasSteered;

    SnowFoxRoll(ServerPlayerEntity player) {
        double yaw = Math.toRadians(player.getYaw());
        input = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    static double fraction(double mass) { return MathHelper.clamp((mass - 10) / 40, 0, 1); }
    public static double radius(double mass) { return .75 + 1.75 * fraction(mass); }

    void steer(double x, double z) {
        Vec3d direction = new Vec3d(x, 0, z);
        if (direction.lengthSquared() > 0) wasSteered = true;
        if (wasSteered) input = direction.lengthSquared() > 1 ? direction.normalize() : direction;
    }

    Step advance(ServerPlayerEntity p, Vec3d feet) {
        Vec3d desired = input.lengthSquared() == 0 ? velocity.multiply(.985) : input.multiply(.4); // 8 blocks/second
        Vec3d change = desired.subtract(velocity);
        double acceleration = (16 - 8 * fraction(mass)) / 400;
        velocity = velocity.add(change.length() <= acceleration ? change : change.normalize().multiply(acceleration));
        Vec3d next = feet.add(velocity);
        double nextMass = Math.min(50, mass + velocity.horizontalLength() * 10 / 7);
        double radius = radius(nextMass);
        Box wall = blockAt(p, next, radius);
        if (wall != null) {
            double climb = wall.maxY - feet.y;
            Vec3d raised = next.add(0, climb, 0);
            if (climb > 0 && climb <= 1.00001 && blockAt(p, raised, radius) == null
                    && blockAt(p, feet.add(0, climb, 0), radius) == null) {
                next = raised;
                fallSpeed = 0;
            } else return new Step(feet, nearest(wall, next.add(0,radius,0)), null);
        }
        // Follow descending terrain and fall naturally from ledges. Test from just above the feet
        // so a slab, a snow layer and the top of a full block each give their exact landing plane.
        fallSpeed = Math.min(fallSpeed + .08, 1.6);
        var floor = p.getWorld().raycast(new RaycastContext(next.add(0,.001,0),next.add(0,-fallSpeed,0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        if (floor.getType() != HitResult.Type.MISS) { next = floor.getPos(); fallSpeed = 0; }
        else {
            Vec3d lowered = next.add(0, -fallSpeed, 0);
            if (blockAt(p, lowered, radius) == null) {
                next = lowered;
            } else {
                // The shell rim can already rest on a step while its centre is over the lower floor.
                double free = 0, blocked = fallSpeed;
                for (int i = 0; i < 9; i++) {
                    double drop = (free + blocked) * .5;
                    if (blockAt(p, next.add(0, -drop, 0), radius) == null) free = drop;
                    else blocked = drop;
                }
                next = next.add(0, -free, 0);
                fallSpeed = 0;
            }
        }
        mass = nextMass;
        Vec3d center = next.add(0,radius,0);
        Box touch = blockAt(p,next,radius);
        if (touch != null) return new Step(feet,nearest(touch,center),null);
        LivingEntity first = null;
        double closest = Double.POSITIVE_INFINITY;
        for (var target : p.getWorld().getEntitiesByClass(LivingEntity.class, bounds(center, radius), t -> enemy(p,t))) {
            Vec3d contact = nearest(target.getBoundingBox(),center);
            if (center.squaredDistanceTo(contact) > radius*radius) continue;
            double distance = contact.squaredDistanceTo(feet.add(0,radius,0));
            if (distance < closest) { closest = distance; first = target; }
        }
        if (first == null && fallSpeed == 0) {
            snowHitDistance += velocity.horizontalLength();
            if (snowHitDistance >= 1.5) {
                snowHitDistance %= 1.5;
                float size = (float)fraction(mass);
                SnowFoxSounds.play(p, next, SoundEvents.BLOCK_SNOW_HIT, .48f + .2f * size,
                        1f - .16f * size + p.getRandom().nextFloat() * .08f);
            }
        }
        return new Step(next, first == null ? null : nearest(first.getBoundingBox(),center), first);
    }

    static boolean enemy(ServerPlayerEntity owner, LivingEntity t) {
        if (t == owner || !t.isAlive() || t.isSpectator() || t.isTeammate(owner)) return false;
        if (t instanceof TameableEntity pet && pet.isOwner(owner)) return false;
        return !(t instanceof PlayerEntity other) || owner.getServer().isPvpEnabled() && owner.shouldDamagePlayer(other) && !other.isCreative();
    }

    static Box bounds(Vec3d center, double radius) {
        return Box.of(center, radius*2, radius*2, radius*2);
    }

    static Vec3d nearest(Box b, Vec3d p) {
        return new Vec3d(MathHelper.clamp(p.x,b.minX,b.maxX), MathHelper.clamp(p.y,b.minY,b.maxY),
                MathHelper.clamp(p.z,b.minZ,b.maxZ));
    }

    static Box blockAt(ServerPlayerEntity p, Vec3d feet, double radius) {
        Vec3d center = feet.add(0,radius,0);
        for (var shape : p.getWorld().getBlockCollisions(p,bounds(center,radius).contract(.0001))) {
            for (Box block : shape.getBoundingBoxes()) {
                if (nearest(block,center).squaredDistanceTo(center) < radius*radius-.0001) return block;
            }
        }
        return null;
    }

    static void burst(ServerPlayerEntity p, Vec3d feet, double mass, Vec3d contact, LivingEntity direct) {
        var world = p.getServerWorld();
        double r = radius(mass);
        Vec3d center = feet.add(0,r,0);
        for (var t : world.getEntitiesByClass(LivingEntity.class,bounds(contact,3),e -> enemy(p,e))) {
            if (nearest(t.getBoundingBox(),contact).squaredDistanceTo(contact) > 9) continue;
            if (t != direct && t.getBoundingBox().getCenter().squaredDistanceTo(center) < r*r) continue;
            t.damage(p.getDamageSources().playerAttack(p), (float)(6 + 12*fraction(mass)));
        }
        double size = fraction(mass);
        world.spawnParticles(ParticleTypes.SNOWFLAKE, contact.x, contact.y, contact.z, (int)(22 + 35 * size), .5, .4, .5, .14);
        var chips = new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK,
                net.minecraft.block.Blocks.SNOW_BLOCK.getDefaultState());
        world.spawnParticles(chips, contact.x, contact.y, contact.z, (int)(12 + 22 * size), .38, .3, .38, .19);
        SnowFoxSounds.play(p, contact, SoundEvents.BLOCK_SNOW_BREAK, (float)(.85 + .35 * size), (float)(1.12 - .3 * size));
        SnowFoxSounds.play(p, contact, SoundEvents.ENTITY_GENERIC_EXPLODE, (float)(.52 + .2 * size), 1.13f);
    }
}
