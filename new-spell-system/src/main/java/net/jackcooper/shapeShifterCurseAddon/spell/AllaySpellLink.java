package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.PersistentState;
import net.ssca.neomagic.*;
import java.util.*;
import java.util.function.Predicate;

/** Accepted cast snapshot; extra collapse is charged once, only after a real benefit. */
public final class AllaySpellLink {
    private static final BalanceReader BAL=new BalanceReader("systems.form_casting");
    private AllaySpellLink() {}
    public interface ProjectileData {
        Cast ssca$allay();
        void ssca$allay(Cast cast);
    }
    public static Cast create(ServerPlayerEntity p,double cost,NeoState.Experiment record,boolean directCurse) {
        int kind=FormUtils.isForm(p,FormIdentifiers.ALLAY_SP)?1:
                directCurse&&FormUtils.isForm(p,FormIdentifiers.FALLEN_ALLAY_SP)?2:0;
        return kind==0?null:new Cast(p,kind,cost,record);
    }
    public static Entity owner(Entity source) {return source instanceof ProjectileEntity projectile?projectile.getOwner():source;}
    public static Cast context(Entity source) {
        return source instanceof ProjectileData projectile?projectile.ssca$allay():FormSpellStyle.currentAllay();
    }
    public static boolean hostile(ServerPlayerEntity p,LivingEntity target) {
        return target!=p&&target.getWorld()==p.getWorld()&&!target.isSpectator()&&!(target instanceof net.minecraft.entity.decoration.ArmorStandEntity)
                &&!WhitelistUtils.isProtected(p,target)&&!p.isTeammate(target)
                &&(!(target instanceof ServerPlayerEntity other)||p.shouldDamagePlayer(other))&&!DomainManager.blocksTargeting(p,target);
    }
    public static final class Cast {
        public final UUID id,owner;
        public final int kind;
        public final double cost,extraRate,healRate,healMinimum,healRange,chainRange;
        private final Set<UUID> members=new HashSet<>();
        private boolean charged,chained,processing;
        private Cast(ServerPlayerEntity p,int kind,double cost,NeoState.Experiment record) {
            this(UUID.fromString(record.id),p.getUuid(),kind,cost,BAL.d("allay_spell_extra_collapse",.15),
                    BAL.d("allay_damage_heal_ratio",.25),BAL.d("allay_damage_heal_minimum",.25),BAL.d("allay_damage_heal_range",16),BAL.d("fallen_curse_chain_range",5));
        }
        private Cast(UUID id,UUID owner,int kind,double cost,double extraRate,double healRate,double healMinimum,double healRange,double chainRange) {
            this.id=id;this.owner=owner;this.kind=kind;this.cost=cost;this.extraRate=extraRate;
            this.healRate=healRate;this.healMinimum=healMinimum;this.healRange=healRange;this.chainRange=chainRange;
        }
        private NeoState.Experiment record(ServerPlayerEntity p) {
            return NeoRuntime.data(p).records.stream().filter(e->e.id.equals(id.toString())).findFirst().orElse(null);
        }
        private boolean actor(ServerPlayerEntity p) {return p.getUuid().equals(owner)&&p.isAlive()&&!p.isRemoved()&&!p.isDisconnected();}
        private void changed(ServerPlayerEntity p) {
            Store.get(p.getServer()).markDirty();NeoRuntime.dirty(p);
        }
        private void charge(ServerPlayerEntity p) {
            if(charged)return;charged=true;
            double extra=cost*extraRate;
            if(extra>0){var data=NeoRuntime.data(p);data.burden+=extra;data.recovery=NeoRules.recoveryDelay;data.lastPaymentTick=p.getServer().getTicks();}
            var record=record(p);if(record!=null)record.allaySurcharge+=extra;
            changed(p);NeoNetwork.hud(p);NeoNetwork.send(p,false);
        }
        public void damage(ServerPlayerEntity p,LivingEntity target,double actualHealthLost) {
            if(kind!=1||processing||!actor(p)||actualHealthLost<=0||!Double.isFinite(actualHealthLost)||!hostile(p,target))return;
            List<ServerPlayerEntity> allies=p.getServerWorld().getPlayers(other->other.isAlive()&&!other.isSpectator()
                    &&other.getHealth()<other.getMaxHealth()&&p.squaredDistanceTo(other)<=healRange*healRange
                    &&WhitelistUtils.isBuffTarget(p,other)&&!DomainManager.blocksTargeting(p,other));
            if(allies.isEmpty())return;
            double share=Math.max(healMinimum,actualHealthLost*healRate/allies.size()),healed=0;
            processing=true;
            try{for(var ally:allies){float before=ally.getHealth();ally.heal((float)share);healed+=Math.max(0,ally.getHealth()-before);}}
            finally{processing=false;}
            if(healed>0){var record=record(p);if(record!=null)record.allayHealing+=healed;charge(p);changed(p);}
        }
        /** One successful secondary effect per whole cast; no recursive chain or primary-target overlap. */
        public boolean chain(ServerPlayerEntity p,LivingEntity primary,Set<UUID> excluded,Predicate<LivingEntity> effect) {
            if(kind!=2||chained||processing||!actor(p)||!hostile(p,primary))return false;
            LivingEntity best=null;double distance=chainRange*chainRange;
            for(var candidate:p.getServerWorld().getEntitiesByClass(LivingEntity.class,primary.getBoundingBox().expand(chainRange),
                    e->e!=primary&&!excluded.contains(e.getUuid())&&FormSpellStyle.legalTarget(p,e))) {
                double d=primary.squaredDistanceTo(candidate);
                if(d<=distance&&primary.canSee(candidate)&&(best==null||d<distance)){best=candidate;distance=d;}
            }
            if(best==null)return false;
            processing=true;boolean applied;
            try{applied=effect.test(best);}finally{processing=false;}
            if(!applied)return false;
            chained=true;var record=record(p);if(record!=null)record.fallenChains++;
            p.getServerWorld().spawnParticles(ParticleTypes.WITCH,primary.getX(),primary.getBodyY(.5),primary.getZ(),4,.15,.2,.15,0);
            p.getServerWorld().spawnParticles(ParticleTypes.WITCH,best.getX(),best.getBodyY(.5),best.getZ(),6,.15,.2,.15,0);
            charge(p);changed(p);return true;
        }
        public NbtCompound write() {
            NbtCompound n=new NbtCompound();n.putUuid("Id",id);n.putUuid("Owner",owner);n.putInt("Kind",kind);
            n.putDouble("Cost",cost);n.putDouble("Extra",extraRate);n.putDouble("Heal",healRate);n.putDouble("HealMinimum",healMinimum);n.putDouble("Radius",healRange);n.putDouble("Chain",chainRange);
            n.putBoolean("Charged",charged);n.putBoolean("Chained",chained);return n;
        }
        public static Cast read(NbtCompound n) {
            if(!n.containsUuid("Id")||!n.containsUuid("Owner"))return null;
            int kind=n.getInt("Kind");double cost=n.getDouble("Cost"),extra=n.getDouble("Extra"),heal=n.getDouble("Heal"),radius=n.getDouble("Radius"),chain=n.getDouble("Chain");
            double minimum=n.contains("HealMinimum",NbtElement.NUMBER_TYPE)?n.getDouble("HealMinimum"):BAL.d("allay_damage_heal_minimum",.25);
            if((kind!=1&&kind!=2)||!Double.isFinite(cost+extra+heal+minimum+radius+chain)||cost<0||extra<0||extra>1||heal<0||heal>1||minimum<0||minimum>20||radius<0||radius>128||chain<0||chain>128)return null;
            Cast cast=new Cast(n.getUuid("Id"),n.getUuid("Owner"),kind,cost,extra,heal,minimum,radius,chain);
            cast.charged=n.getBoolean("Charged");cast.chained=n.getBoolean("Chained");return cast;
        }
    }
    /** Kept outside player sync data; unloaded projectiles retain membership across restarts. */
    private static final class Store extends PersistentState {
        private final Map<UUID,Cast> casts=new HashMap<>();
        static Store get(MinecraftServer server) {return server.getOverworld().getPersistentStateManager().getOrCreate(Store::read,Store::new,"ssca_allay_spell_links");}
        static Store read(NbtCompound n) {
            Store store=new Store();for(String key:n.getKeys()){
                NbtCompound entry=n.getCompound(key);Cast cast=Cast.read(entry);if(cast==null)continue;
                NbtList members=entry.getList("Members",NbtElement.STRING_TYPE);
                for(int i=0;i<members.size();i++)try{cast.members.add(UUID.fromString(members.getString(i)));}catch(IllegalArgumentException ignored){}
                if(!cast.members.isEmpty())store.casts.put(cast.id,cast);
            }return store;
        }
        @Override public NbtCompound writeNbt(NbtCompound n) {
            casts.forEach((id,cast)->{NbtCompound entry=cast.write();NbtList list=new NbtList();cast.members.forEach(p->list.add(NbtString.of(p.toString())));entry.put("Members",list);n.put(id.toString(),entry);});return n;
        }
        Cast join(Cast proposed,UUID projectile) {
            Cast cast=casts.computeIfAbsent(proposed.id,id->proposed);
            boolean changed=cast.charged!= (cast.charged||proposed.charged)||cast.chained!=(cast.chained||proposed.chained);
            cast.charged|=proposed.charged;cast.chained|=proposed.chained;
            if(cast.members.add(projectile)||changed)markDirty();return cast;
        }
        void leave(Cast cast,UUID projectile) {
            if(cast==null)return;Cast actual=casts.get(cast.id);
            if(actual!=null&&actual.members.remove(projectile)){if(actual.members.isEmpty())casts.remove(cast.id);markDirty();}
        }
    }
    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(!(entity instanceof ProjectileData data))return;
            Cast cast=FormSpellStyle.currentAllay();
            if(cast!=null&&entity instanceof ProjectileEntity projectile&&projectile.getOwner()!=null&&projectile.getOwner().getUuid().equals(cast.owner))data.ssca$allay(cast);
            if(data.ssca$allay()!=null)data.ssca$allay(Store.get(world.getServer()).join(data.ssca$allay(),entity.getUuid()));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{
            var reason=entity.getRemovalReason();
            if(entity instanceof ProjectileData data&&data.ssca$allay()!=null&&(reason==Entity.RemovalReason.DISCARDED||reason==Entity.RemovalReason.KILLED))Store.get(world.getServer()).leave(data.ssca$allay(),entity.getUuid());
        });
    }
}
