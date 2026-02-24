package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.List;

public class WaterSpearEntity extends TridentEntity {

    private ItemStack waterSpearStack = new ItemStack(SscAddon.WATER_SPEAR);

    public WaterSpearEntity(EntityType<? extends TridentEntity> entityType, World world) {
        super(SscAddon.WATER_SPEAR_ENTITY, world);
    }

    public WaterSpearEntity(World world, LivingEntity owner, ItemStack stack) {
        super(SscAddon.WATER_SPEAR_ENTITY, world);
        this.setOwner(owner);
        this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        this.waterSpearStack = stack.copy();
    }

    public ItemStack getWeaponStack() {
        return this.waterSpearStack;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("WaterSpear", 10)) {
            this.waterSpearStack = ItemStack.fromNbt(nbt.getCompound("WaterSpear"));
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("WaterSpear", this.waterSpearStack.writeNbt(new NbtCompound()));
    }


    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        World world = this.getWorld();
        
        if (!world.isClient && entity instanceof LivingEntity target) {
            // Direct damage
            float damage = 8.0f;
            target.damage(this.getDamageSources().trident(this, this.getOwner()), damage);
            
            // Apply slowness
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
            
            // Area damage
            doAreaDamage(target.getPos().add(0, target.getHeight() / 2, 0), target);
        }
        
        // Remove the spear after hitting
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        World world = this.getWorld();
        
        if (!world.isClient) {
            doAreaDamage(this.getPos(), null);
        }
        
        // Remove the spear after hitting block
        this.discard();
    }

    private void doAreaDamage(Vec3d pos, Entity directTarget) {
        World world = this.getWorld();
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        
        // Get entities within 1.5 block radius (3 block diameter)
        List<Entity> nearbyEntities = world.getOtherEntities(this.getOwner(), new Box(x - 1.5, y - 1.5, z - 1.5, x + 1.5, y + 1.5, z + 1.5));
        for (Entity nearEntity : nearbyEntities) {
            if (nearEntity instanceof LivingEntity living && nearEntity != this.getOwner() && nearEntity != directTarget) {
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

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        // Allow pickup only if player is SP Axolotl
        PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
        PlayerFormBase currentForm = component != null ? component.getCurrentForm() : null;

        if (currentForm != null && currentForm.FormID != null
                && currentForm.getPhase() == PlayerFormPhase.PHASE_SP
                && currentForm.FormID.getPath().contains("axolotl")) {
            super.onPlayerCollision(player);
        }
    }

}
