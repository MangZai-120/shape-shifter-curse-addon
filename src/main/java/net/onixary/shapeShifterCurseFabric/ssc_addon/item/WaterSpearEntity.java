package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.List;

public class WaterSpearEntity extends TridentEntity {

    public WaterSpearEntity(EntityType<? extends TridentEntity> entityType, World world) {
        super(entityType, world);
    }

    public WaterSpearEntity(World world, LivingEntity owner, ItemStack stack) {
        super(world, owner, stack);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        World world = this.getWorld();
        
        if (!world.isClient && entity instanceof LivingEntity target) {
            // Direct damage
            float damage = 4.0f;
            target.damage(this.getDamageSources().trident(this, this.getOwner()), damage);
            
            // Apply slowness
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
            
            // Area damage
            double x = target.getX();
            double y = target.getY() + target.getHeight() / 2;
            double z = target.getZ();
            
            // Get entities within 1.5 block radius (3 block diameter)
            List<Entity> nearbyEntities = world.getOtherEntities(this.getOwner(), new Box(x - 1.5, y - 1.5, z - 1.5, x + 1.5, y + 1.5, z + 1.5));
            for (Entity nearEntity : nearbyEntities) {
                if (nearEntity instanceof LivingEntity living && nearEntity != this.getOwner() && nearEntity != target) {
                    living.damage(this.getDamageSources().trident(this, this.getOwner()), 4.0f);
                }
            }
            
            // Play splash sound and particles
            world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.0F, 0.8F);
            
            // Spawn particles on server
            if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.SPLASH, x, y, z, 30, 1.0, 0.5, 1.0, 0.1);
                serverWorld.spawnParticles(ParticleTypes.BUBBLE, x, y, z, 20, 1.0, 0.5, 1.0, 0.05);
            }
        }
        
        // Remove the spear after hitting
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        World world = this.getWorld();
        
        if (!world.isClient) {
            // Play splash sound (no area damage for block hits)
            world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.8F, 1.0F);
            
            // Spawn particles
            if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 20, 0.5, 0.3, 0.5, 0.1);
            }
        }
        
        // Remove the spear after hitting block (no area damage)
        this.discard();
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // Spawn water trail particles
        World world = this.getWorld();
        if (world.isClient && !this.inGround) {
            for (int i = 0; i < 2; i++) {
                world.addParticle(ParticleTypes.DRIPPING_WATER, 
                    this.getX() + (world.random.nextDouble() - 0.5) * 0.3,
                    this.getY() + (world.random.nextDouble() - 0.5) * 0.3,
                    this.getZ() + (world.random.nextDouble() - 0.5) * 0.3,
                    0, 0, 0);
            }
        }
    }
    
    @Override
    protected SoundEvent getHitSound() {
        return SoundEvents.ENTITY_GENERIC_SPLASH;
    }
    
    @Override
    public boolean hasNoGravity() {
        return false;
    }
}
