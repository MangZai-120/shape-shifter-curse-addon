package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;

public class AllayFriendMarkerEntity extends ThrownItemEntity {

    public AllayFriendMarkerEntity(EntityType<? extends ThrownItemEntity> entityType, World world) {
        super(entityType, world);
    }

    public AllayFriendMarkerEntity(World world, LivingEntity owner) {
        super(SscAddon.FRIEND_MARKER_ENTITY_TYPE, owner, world);
    }

    public AllayFriendMarkerEntity(World world, double x, double y, double z) {
        super(SscAddon.FRIEND_MARKER_ENTITY_TYPE, x, y, z, world);
    }

    @Override
    protected Item getDefaultItem() {
        return SscAddon.FRIEND_MARKER;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.age > 40) {
            this.discard();
        }
    }

    @Override
    public void handleStatus(byte status) {
        if (status == 3) {
            double d = 0.08D;
            for(int i = 0; i < 8; ++i) {
                this.getWorld().addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, this.getStack()), this.getX(), this.getY(), this.getZ(), ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D);
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        Entity entity = entityHitResult.getEntity();
        Entity owner = this.getOwner();

        if (owner instanceof ServerPlayerEntity player && entity instanceof LivingEntity target) {
            AllaySPGroupHeal.addToWhitelist(player, target);
            // Optionally print a message or play a sound
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) {
            this.getWorld().sendEntityStatus(this, (byte)3);
            this.discard();
        }
    }
}
