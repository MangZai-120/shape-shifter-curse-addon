package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class OrcafisRockEntity extends ProjectileEntity implements FlyingItemEntity {
    private static final double GRAVITY = .055;
    private static final int FLIGHT = 24;
    public static final int PICKUP = 24, RELEASE = 40;
    private static final ItemStack ROCK = new ItemStack(Items.TUFF);
    private int flightTicks;

    public OrcafisRockEntity(EntityType<? extends OrcafisRockEntity> type, World world) { super(type,world); }
    @Override public ItemStack getStack() { return ROCK; }
    @Override protected void initDataTracker() { }

    public static Vec3d carriedCenter(Vec3d mouth,float yaw) {
        return mouth.add(Vec3d.fromPolar(0,yaw).multiply(.52)).add(0,.18,0);
    }

    static void toss(OrcafisEntity owner, Vec3d mouth, Vec3d target) {
        World world = owner.getWorld();
        Vec3d start = carriedCenter(mouth,owner.bodyYaw);
        Vec3d end = new Vec3d(target.x,OrcafisLandAttacks.groundY(world,target.x,target.z,target.y),target.z);
        var rock = new OrcafisRockEntity(RegAddonEntities.ORCAFIS_ROCK,world);
        rock.setOwner(owner);
        rock.setPosition(start);
        rock.setVelocity(end.subtract(start).multiply(1.0/FLIGHT).add(0,GRAVITY*(FLIGHT-1)/2,0));
        world.spawnEntity(rock);
    }

    @Override public void tick() {
        super.tick();
        Vec3d start = getPos(), velocity = getVelocity(), next = start.add(velocity);
        if (getWorld() instanceof ServerWorld world) {
            var hit = world.raycast(new RaycastContext(start,next,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,this));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3d impact = hit.getPos();
                setPosition(impact);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,Blocks.TUFF.getDefaultState()),
                        impact.x,impact.y+.2,impact.z,55,.65,.22,.65,.25);
                playSound(OrcafisSounds.ROCK_IMPACT, 2f, 1f);
                for (var player : world.getPlayers()) {
                    if (OrcafisEntity.isHuntTarget(player) && player.squaredDistanceTo(impact) <= 9)
                        player.damage(getDamageSources().thrown(this,getOwner()),36);
                }
                discard();
                return;
            }
            if (++flightTicks >= 200 || next.y < world.getBottomY()-16) { discard(); return; }
        }
        setPosition(next);
        setVelocity(velocity.add(0,-GRAVITY,0));
    }

    @Override protected void writeCustomDataToNbt(NbtCompound tag) {
        super.writeCustomDataToNbt(tag);
        tag.putInt("FlightTicks",flightTicks);
    }
    @Override protected void readCustomDataFromNbt(NbtCompound tag) {
        super.readCustomDataFromNbt(tag);
        flightTicks = tag.getInt("FlightTicks");
    }
}
