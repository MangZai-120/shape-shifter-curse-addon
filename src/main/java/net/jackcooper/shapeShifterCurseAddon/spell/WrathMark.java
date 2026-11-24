package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public final class WrathMark {
    public static final Identifier OUTLINES=new Identifier("ssc_addon","wrath_outlines");
    private static final double RADIUS=16;
    public interface Access {
        WrathMark ssca$getWrathMark();
        void ssca$setWrathMark(WrathMark mark);
    }
    private final UUID caster;
    private final float penalty;
    private int remaining, attackDelay;
    private UUID previousTarget, forcedTarget;
    private Set<UUID> sent;
    public static boolean isActive(LivingEntity entity) {
        return ((Access) entity).ssca$getWrathMark() != null;
    }

    private WrathMark(UUID caster,int duration,float penalty) {
        this.caster=caster;
        this.remaining=duration;
        this.penalty=penalty;
    }

    public static boolean apply(ServerPlayerEntity caster,LivingEntity victim,int duration,float penalty) {
        if(victim.hasStatusEffect(SscAddon.PURIFIED) || WhitelistUtils.isProtected(caster,victim)) return false;
        purify(victim);
        var mark=new WrathMark(caster.getUuid(),duration,penalty);
        if(victim instanceof MobEntity mob && mob.getTarget()!=null) mark.previousTarget=mob.getTarget().getUuid();
        ((Access)victim).ssca$setWrathMark(mark);
        mark.updateTargets(victim,caster);
        var world=caster.getServerWorld();
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,victim.getX(),victim.getBodyY(.85),victim.getZ(),5,.25,.12,.25,0);
        AddonSoundRange.play(world, null,victim.getBlockPos(),SoundEvents.ENTITY_EVOKER_CAST_SPELL,SoundCategory.PLAYERS,.9F,.7F);
        return true;
    }

    public static void onHit(LivingEntity attacker,LivingEntity hit) {
        var mark=((Access)attacker).ssca$getWrathMark();
        if(mark==null || !(attacker.getWorld() instanceof ServerWorld world)
                || !(world.getPlayerByUuid(mark.caster) instanceof ServerPlayerEntity owner)) return;
        if(attacker instanceof ServerPlayerEntity && (mark.sent==null || !mark.sent.contains(hit.getUuid()))) return;
        if(!mark.candidate(owner,attacker,hit)) return;
        purify(attacker);
        AddonSoundRange.play(world, null,attacker.getBlockPos(),SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.PLAYERS,.7F,1.35F);
    }

    public void tick(LivingEntity victim) {
        if(!(victim.getWorld() instanceof ServerWorld world)) return;
        var owner=world.getPlayerByUuid(caster);
        if(!(owner instanceof ServerPlayerEntity player) || !victim.isAlive()
                || victim.hasStatusEffect(SscAddon.PURIFIED) || WhitelistUtils.isProtected(player,victim)) {
            purify(victim);
            return;
        }
        if(attackDelay>0) attackDelay--;
        if((sent==null && victim instanceof ServerPlayerEntity) || remaining%5==0) updateTargets(victim,player);
        if(((Access)victim).ssca$getWrathMark()!=this) return;
        if(--remaining<=0) {
            clear(victim);
            victim.damage(SpellDamageSource.of(world.getDamageSources()),penalty);
            AddonSoundRange.play(world, null,victim.getBlockPos(),SoundEvents.ENTITY_WARDEN_HEARTBEAT,SoundCategory.PLAYERS,.75F,1.25F);
        }

    }

    public static boolean blocksMobHit(MobEntity attacker,LivingEntity target) {
        var mark=((Access)attacker).ssca$getWrathMark();
        if(mark==null || !(attacker.getWorld() instanceof ServerWorld world)) return false;
        return !(world.getPlayerByUuid(mark.caster) instanceof ServerPlayerEntity owner)
                || !target.getUuid().equals(mark.forcedTarget) || !mark.candidate(owner,attacker,target);
    }
    private boolean candidate(ServerPlayerEntity owner,LivingEntity victim,LivingEntity other) {
        return other!=victim && other!=owner && other.isAlive() && !other.isSpectator()
                && !(other instanceof net.minecraft.entity.decoration.ArmorStandEntity)
                && (!(other instanceof PlayerEntity player) || !player.isCreative())
                && other.squaredDistanceTo(victim)<=RADIUS*RADIUS && !WhitelistUtils.isProtected(owner,other)
                && !DomainManager.blocksTargeting(victim,other);
    }
    private void updateTargets(LivingEntity victim,ServerPlayerEntity owner) {
        var targets=victim.getWorld().getEntitiesByClass(LivingEntity.class,victim.getBoundingBox().expand(RADIUS),
                other->candidate(owner,victim,other));
        if(victim instanceof ServerPlayerEntity player) {
            var next=targets.stream().map(LivingEntity::getUuid).collect(Collectors.toSet());
            if(!next.equals(sent)) { send(player,next,remaining); sent=next; }
        } else if(victim instanceof MobEntity mob) {
            var nearest=targets.stream().min(Comparator.comparingDouble(victim::squaredDistanceTo)).orElse(null);
            if(nearest==null) { releaseTarget(mob); return; }
            forcedTarget=nearest.getUuid();
            mob.setTarget(nearest);
            if(mob.isAiDisabled()) return;
            mob.getLookControl().lookAt(nearest,30,30);
            mob.getNavigation().startMovingTo(nearest,1);
            if(attackDelay>0 || !mob.canSee(nearest) || mob.squaredDistanceTo(nearest)>Math.pow(mob.getWidth()+nearest.getWidth()+0.75,2)) return;
            attackDelay=20;
            mob.swingHand(Hand.MAIN_HAND);
            if(mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)!=null) mob.tryAttack(nearest);
            else nearest.damage(mob.getDamageSources().mobAttack(mob),2);
        }
    }
    private void releaseTarget(MobEntity mob) {
        if(forcedTarget!=null && mob.getTarget()!=null && mob.getTarget().getUuid().equals(forcedTarget)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        forcedTarget=null;
    }
    private static void send(ServerPlayerEntity victim,Set<UUID> ids,int ticks) {
        var buf=PacketByteBufs.create();
        buf.writeIdentifier(victim.getWorld().getRegistryKey().getValue());
        buf.writeUuid(victim.getUuid());
        buf.writeVarInt(ticks);
        buf.writeCollection(ids,(b,id)->b.writeUuid(id));
        ServerPlayNetworking.send(victim,OUTLINES,buf);
    }
    public static void clear(LivingEntity victim) {
        var access=(Access)victim;
        var mark=access.ssca$getWrathMark();
        if(mark==null) return;
        access.ssca$setWrathMark(null);
        if(victim instanceof ServerPlayerEntity player) send(player,Set.of(),0);
        if(victim instanceof MobEntity mob && victim.getWorld() instanceof ServerWorld world) {
            mark.releaseTarget(mob);
            if(mob.getTarget()==null && mark.previousTarget!=null && world.getEntity(mark.previousTarget) instanceof LivingEntity old && old.isAlive()) mob.setTarget(old);
        }
    }
    public static void purify(LivingEntity victim) {
        clear(victim);
    }

    public NbtCompound write() {
        var tag=new NbtCompound();tag.putUuid("Caster",caster);tag.putInt("Duration",remaining);
        tag.putFloat("Penalty",penalty);
        tag.putInt("AttackDelay",attackDelay);
        if(previousTarget!=null) tag.putUuid("Previous",previousTarget);
        if(forcedTarget!=null) tag.putUuid("Forced",forcedTarget);
        return tag;
    }
    public static WrathMark read(NbtCompound tag, LivingEntity victim) {
        if(!tag.containsUuid("Caster") || tag.getInt("Duration")<=0) return null;
        var mark=new WrathMark(tag.getUuid("Caster"),tag.getInt("Duration"),tag.getFloat("Penalty"));
        mark.attackDelay=tag.getInt("AttackDelay");
        // dev.26 saved the vanilla effect it installed alongside the mark.
        if (!victim.getWorld().isClient && tag.getBoolean("OwnsBlindness")) {
            StatusEffectInstance old = victim.getStatusEffect(StatusEffects.BLINDNESS);
            if (old != null && old.getAmplifier() == 0 && !old.isInfinite()
                    && Math.abs(old.getDuration() - mark.remaining) <= 1) {
                victim.removeStatusEffect(StatusEffects.BLINDNESS);
            }
        }
        if(tag.containsUuid("Previous")) mark.previousTarget=tag.getUuid("Previous");
        if(tag.containsUuid("Forced")) mark.forcedTarget=tag.getUuid("Forced");
        return mark;
    }
}
