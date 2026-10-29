package net.ssca.neomagic;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Server-owned traces, delayed structures and summons. Never calls old spell handlers. */
public final class NeoEffects {
    public record Anchor(Vec3d position,UUID target) {}
    private record Stored(NeoRuntime.Active active,int expires) {}
    private static final Map<UUID,Stored> STORED=new HashMap<>();
    private static final List<Bolt> BOLTS=new ArrayList<>();
    private static final Map<WolfEntity,Integer> SUMMONS=new HashMap<>();
    private static final String SUMMON_TAG="ssca_neomagic_summon";
    private static final class Bolt {
        final net.jackcooper.shapeShifterCurseAddon.spell.SpiderSpellLink.Impact weave=new net.jackcooper.shapeShifterCurseAddon.spell.SpiderSpellLink.Impact();
        final ServerPlayerEntity caster; final ServerWorld world; final NeoRuntime.Active active;
        Vec3d pos;Vec3d velocity;final double budget;double remaining;
        Bolt(ServerPlayerEntity p,NeoRuntime.Active a,Vec3d dir,double budget) {
            caster=p;world=p.getServerWorld();active=a;pos=p.getEyePos();velocity=dir.multiply(a.style==null?1:a.style.projectileSpeed);this.budget=budget;remaining=a.session.blueprint.range()*a.rangeMultiplier;
            if(a.style!=null&&a.style.moonWeaver)weave.enable(a.style.weave.targets);
        }
    }
    public static Vec3d aim(ServerPlayerEntity p,double range) {
        return p.getWorld().raycast(new RaycastContext(p.getEyePos(),p.getEyePos().add(p.getRotationVec(1).multiply(range)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getPos();
    }
    public static Anchor capture(ServerPlayerEntity p,Blueprint b) {
        return capture(p,b,0,0,NeoLegacyCasting.blueprintRangeMultiplier(p,b));
    }
    private static Anchor capture(ServerPlayerEntity p,Blueprint b,double yaw,double pitch,double multiplier) {
        double range=(b.carrier()==Carrier.CONTACT?Math.min(4,b.range()):b.range())*multiplier;
        Vec3d direction=Vec3d.fromPolar((float)(p.getPitch()+pitch),(float)(p.getYaw()+yaw));
        Vec3d end=p.getWorld().raycast(new RaycastContext(p.getEyePos(),p.getEyePos().add(direction.multiply(range)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getPos();
        LivingEntity entity=along(p,p.getEyePos(),end,b.carrier()==Carrier.LOCK?.8:.4);
        return new Anchor(entity==null?end:entity.getPos(),entity==null?null:entity.getUuid());
    }
    public static boolean preflight(ServerPlayerEntity p,Blueprint b,Anchor anchor) {
        if(b.carrier()==Carrier.LOCK && anchor.target==null)return false;
        if(b.carrier()==Carrier.CONTACT && anchor.target==null && !p.getWorld().getBlockState(BlockPos.ofFloored(anchor.position)).isOf(NeoMagic.TARGET))return false;
        if(b.core()==Core.SPACE||b.core()==Core.SUMMON)
            return p.getWorld().getWorldBorder().contains(BlockPos.ofFloored(anchor.position))&&p.getWorld().canPlayerModifyAt(p,BlockPos.ofFloored(anchor.position));
        return true;
    }
    private static LivingEntity along(ServerPlayerEntity p,Vec3d start,Vec3d end,double width) {
        return p.getWorld().getEntitiesByClass(LivingEntity.class,new Box(start,end).expand(width+1),e->e!=p&&e.isAlive()&&!e.isSpectator())
                .stream().filter(e->e.getBoundingBox().expand(width).raycast(start,end).isPresent())
                .min(Comparator.comparingDouble(e->e.getBoundingBox().expand(width).raycast(start,end).orElse(end).squaredDistanceTo(start))).orElse(null);
    }
    private static boolean allowed(ServerPlayerEntity caster,LivingEntity target,boolean hostile) {
        if(target==null||!target.isAlive()||target.isSpectator())return false;
        if(hostile) {
            if(target==caster||caster.isTeammate(target))return false;
            if(target instanceof ServerPlayerEntity other&&!caster.shouldDamagePlayer(other))return false;
            return AttackEntityCallback.EVENT.invoker().interact(caster,caster.getWorld(),Hand.MAIN_HAND,target,null)!=ActionResult.FAIL;
        }
        return true;
    }
    public static boolean hasStored(UUID id) {return STORED.containsKey(id);}
    public static void clearStored(UUID id) {var lost=STORED.remove(id);if(lost!=null)lost.active.record.result="storage_lost";}
    public static void clear() {
        STORED.values().forEach(s->s.active.record.result="storage_lost");STORED.clear();
        BOLTS.forEach(b->{b.active.record.result="effect_lost";b.active.record.pending=0;});BOLTS.clear();
        SUMMONS.keySet().forEach(Entity::discard);SUMMONS.clear();
    }
    public static boolean release(ServerPlayerEntity p,NeoRuntime.Active active) {
        if(active.session.quote.plan().stored()) {
            STORED.put(p.getUuid(),new Stored(active,p.getServer().getTicks()+200));return true;
        }
        return apply(p,active);
    }
    public static void trigger(ServerPlayerEntity p) {
        Stored s=STORED.remove(p.getUuid());if(s==null)return;
        if(!NeoRuntime.blocked(p)&&s.active.record.dimension.equals(p.getWorld().getRegistryKey().getValue().toString())&&apply(p,s.active)) {
            s.active.record.result="released";NeoRuntime.data(p).successful(s.active.session.blueprint);
            NeoRuntime.grantExperience(p,s.active);
        } else s.active.record.result="storage_lost";
        NeoRuntime.dirty(p);NeoNetwork.send(p,false);
    }
    private static boolean apply(ServerPlayerEntity p,NeoRuntime.Active a) {
        NeoLegacyCasting.beginBlueprintEffect(p,a);
        boolean released=healingBatch(a,()->applyNow(p,a));
        if(released)NeoLegacyCasting.blueprintReleased(p,a);
        return released;
    }
    private static <T> T healingBatch(NeoRuntime.Active active,java.util.function.Supplier<T> action) {
        var healing=active.style==null?null:active.style.healing;
        if(healing==null)return action.get();
        healing.begin();
        try{T result=action.get();healing.end();return result;}
        catch(RuntimeException|Error failure){healing.abort();throw failure;}
    }
    private static boolean applyNow(ServerPlayerEntity p,NeoRuntime.Active a) {
        Session s=a.session;Blueprint b=s.blueprint;Plan plan=s.quote.plan();
        double budget=9*b.scale()*plan.retention()*s.attenuation*a.powerMultiplier;
        if(b.carrier()==Carrier.PROJECTILE) {
            a.record.pending=plan.branches();
            for(int i=0;i<plan.branches();i++) {
                double fan=(i-(plan.branches()-1)/2.0)*8;
                Vec3d dir=Vec3d.fromPolar((float)(p.getPitch()+s.pitch),(float)(p.getYaw()+s.yaw+fan));
                BOLTS.add(new Bolt(p,a,dir,budget/plan.branches()));
            }
            return true;
        }
        double range=b.range()*a.rangeMultiplier;
        Vec3d endpoint=aim(p,range);LivingEntity target=null;
        if(b.carrier()==Carrier.LOCK) {
            Entity found=a.anchor.target==null?null:p.getServerWorld().getEntity(a.anchor.target);
            if(!(found instanceof LivingEntity living)||p.squaredDistanceTo(living)>range*range||!p.canSee(living))return false;
            target=living;endpoint=target.getPos();
            if(s.wrongTarget) {
                var candidates=p.getWorld().getEntitiesByClass(LivingEntity.class,target.getBoundingBox().expand(5),e->e!=p&&e!=found&&e.isAlive()&&p.canSee(e));
                if(candidates.isEmpty())return false;
                target=candidates.get(p.getRandom().nextInt(candidates.size()));endpoint=target.getPos();
            }
            endpoint=endpoint.add(Math.tan(Math.toRadians(s.yaw))*p.distanceTo(target),0,Math.tan(Math.toRadians(s.pitch))*p.distanceTo(target));
        } else if(b.carrier()==Carrier.SELF) {target=p;endpoint=p.getPos();}
        else if(b.carrier()==Carrier.CONTACT) {
            Anchor hit=capture(p,b,s.yaw,s.pitch,a.rangeMultiplier);endpoint=hit.position;
            Entity found=hit.target==null?null:p.getServerWorld().getEntity(hit.target);if(found instanceof LivingEntity living)target=living;
        } else endpoint=p.getEyePos().add(Vec3d.fromPolar((float)(p.getPitch()+s.pitch),(float)(p.getYaw()+s.yaw)).multiply(Math.min(range,p.getEyePos().distanceTo(endpoint))));
        if(b.output()==Core.SPACE) {
            LivingEntity moved=b.carrier()==Carrier.SELF?p:target;if(moved==null)return false;
            var movedTargets=new ArrayList<LivingEntity>();movedTargets.add(moved);
            if(plan.branches()>1) {
                var candidates=targets(p,moved.getPos(),3,false);candidates.remove(moved);
                candidates.stream().limit(plan.branches()-1).forEach(movedTargets::add);
            }
            int movedCount=0;
            for(LivingEntity traveler:movedTargets) {
            // Unsafe endpoints are intentional: no snapping to solid ground or safe air pockets.
            Vec3d direction=Vec3d.fromPolar((float)(p.getPitch()+s.pitch),(float)(p.getYaw()+s.yaw));
            Vec3d to=traveler.getPos().add(direction.multiply(Math.min(b.range(),4*b.scale())*a.rangeMultiplier*s.attenuation*plan.retention()/plan.branches()));
            BlockPos pos=BlockPos.ofFloored(to);
            if(!p.getWorld().getWorldBorder().contains(pos)||!p.getWorld().isInBuildLimit(pos)||!p.getWorld().getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)||!p.getWorld().canPlayerModifyAt(p,pos))continue;
            if(traveler!=p&&!allowed(p,traveler,true))continue;
            double traveled=traveler.getPos().distanceTo(to);traveler.requestTeleport(to.x,to.y,to.z);a.record.arrived+=traveled;a.record.hits++;movedCount++;
            }
            return movedCount>0;
        }
        if(b.output()==Core.SUMMON)return summon(p,a,endpoint,budget);
        if(b.carrier()==Carrier.AREA) {
            Vec3d side=Vec3d.fromPolar(0,p.getYaw()+90).multiply(3);
            for(int i=0;i<plan.branches();i++) {
                Vec3d center=endpoint.add(side.multiply(i-(plan.branches()-1)/2.0));
                var list=effectTargets(p,a,center,plan.focused()?1:3);
                if(list.isEmpty())measureBlock(p,a,center,budget/plan.branches());
                else distribute(p,a,list,budget/plan.branches());
            }
            return true;
        }
        if(target!=null) {
            if(b.carrier()==Carrier.LOCK&&target.getPos().distanceTo(endpoint)>1.25) {measureBlock(p,a,endpoint,budget);return true;}
            if(a.style!=null&&a.style.allay!=null&&a.style.allay.kind==2){
                var primaries=new ArrayList<LivingEntity>();primaries.add(target);
                if(plan.branches()>1){var extra=effectTargets(p,a,target.getPos(),b.carrier()==Carrier.CONTACT?2:5);extra.remove(target);extra.stream().limit(plan.branches()-1).forEach(primaries::add);}
                a.chainPrimaries=new HashSet<>();primaries.forEach(e->a.chainPrimaries.add(e.getUuid()));
                primaries.forEach(e->affect(p,a,e,budget/plan.branches()));return true;
            }
            affect(p,a,target,budget/plan.branches());
            if(plan.branches()>1) {
                var extra=effectTargets(p,a,target.getPos(),b.carrier()==Carrier.CONTACT?2:5);extra.remove(target);
                extra.stream().limit(plan.branches()-1).forEach(other->affect(p,a,other,budget/plan.branches()));
            }
            return true;
        }
        measureBlock(p,a,endpoint,budget);return b.carrier()!=Carrier.CONTACT;
    }
    private static boolean summon(ServerPlayerEntity p,NeoRuntime.Active a,Vec3d pos,double budget) {
        int count=Math.max(0,(int)Math.floor(a.session.quote.plan().branches()*a.session.attenuation));
        if(count==0)return false;
        for(int i=0;i<count;i++) {
            Vec3d at=pos.add(i*.6,0,0);BlockPos block=BlockPos.ofFloored(at);
            if(!p.getWorld().getChunkManager().isChunkLoaded(block.getX()>>4,block.getZ()>>4)||!p.getWorld().getWorldBorder().contains(block)||!p.getWorld().canPlayerModifyAt(p,block))continue;
            WolfEntity wolf=EntityType.WOLF.create(p.getServerWorld());if(wolf==null)continue;
            wolf.refreshPositionAndAngles(at.x,at.y,at.z,0,0);wolf.setTamed(true);wolf.setOwnerUuid(p.getUuid());
            double share=budget/count;
            wolf.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(Math.max(1,share));wolf.setHealth((float)share);
            wolf.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(Math.max(.01,share/3));
            wolf.addCommandTag(SUMMON_TAG);wolf.setCustomName(NeoMagic.text("summon_name"));
            if(!p.getWorld().isSpaceEmpty(wolf)) {wolf.discard();continue;}
            SUMMONS.put(wolf,p.getServer().getTicks()+200);
            if(p.getServerWorld().spawnEntity(wolf)){a.record.summons++;a.record.arrived+=share;}
            else {SUMMONS.remove(wolf);wolf.discard();}
        }
        return a.record.summons>0;
    }
    private static List<LivingEntity> targets(ServerPlayerEntity p,Vec3d center,double radius,boolean healing) {
        return p.getWorld().getEntitiesByClass(LivingEntity.class,new Box(center,center).expand(radius),e->allowed(p,e,!healing)&&e.squaredDistanceTo(center)<=radius*radius&&p.canSee(e));
    }
    private static List<LivingEntity> effectTargets(ServerPlayerEntity p,NeoRuntime.Active a,Vec3d center,double radius) {
        boolean lunar=a.session.blueprint.output()==Core.LUNAR;
        var list=targets(p,center,radius,lunar&&!a.session.reversed);
        if(lunar&&a.session.reversed&&p.squaredDistanceTo(center)<=radius*radius&&!list.contains(p))list.add(p);
        return list;
    }
    private static void distribute(ServerPlayerEntity p,NeoRuntime.Active a,List<LivingEntity> targets,double budget) {
        if(targets.isEmpty())return;
        double per=budget/targets.size();targets.forEach(e->affect(p,a,e,per));
    }
    private static void affect(ServerPlayerEntity p,NeoRuntime.Active a,LivingEntity target,double budget) {
        affect(p,a,target,budget,null);
    }
    private static void affect(ServerPlayerEntity p,NeoRuntime.Active a,LivingEntity target,double budget,
            net.jackcooper.shapeShifterCurseAddon.spell.SpiderSpellLink.Impact weave) {
        if(a.session.blueprint.output()==Core.LUNAR&&!a.session.reversed&&a.style!=null&&a.style.healing!=null) {
            a.style.healing.heal(target,budget,value->affectNow(p,a,target,value,weave));
        } else affectNow(p,a,target,budget,weave);
    }
    private static boolean affectNow(ServerPlayerEntity p,NeoRuntime.Active a,LivingEntity target,double budget,
            net.jackcooper.shapeShifterCurseAddon.spell.SpiderSpellLink.Impact weave) {
        Core core=a.session.blueprint.output();boolean healing=core==Core.LUNAR&&!a.session.reversed;
        boolean invertedSelf=target==p&&core==Core.LUNAR&&a.session.reversed;
        if(!invertedSelf&&!allowed(p,target,!healing))return false;
        a.record.arrived+=budget;a.record.hits++;
        float before=target.getHealth(),absorption=target.getAbsorptionAmount();
        boolean conditionBeforeHit=core==Core.THERMAL?target.getFireTicks()>0
                :net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.hasHarmfulEffect(target);
        boolean effective=false;
        switch(core) {
            case LUNAR -> {if(healing)target.heal((float)budget);else effective=target.damage(invertedSelf?p.getDamageSources().magic():p.getDamageSources().indirectMagic(p,p),(float)budget);}
            case THERMAL -> effective=target.damage(p.getDamageSources().indirectMagic(p,p),(float)budget);
            case FROST -> {if(effective=target.damage(p.getDamageSources().indirectMagic(p,p),(float)(budget*.5)))target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,(int)(budget*12),Math.min(3,(int)(budget/6))),p);}
            case CURSE -> {if(effective=target.damage(p.getDamageSources().indirectMagic(p,p),(float)(budget*.4)))target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,Math.round((int)(budget*20)*a.curseDurationMultiplier),0),p);}
            case VOID -> {if(effective=target.damage(p.getDamageSources().indirectMagic(p,p),(float)(budget*.8)))target.setAbsorptionAmount(Math.max(0,target.getAbsorptionAmount()-(float)(budget*.2)));}
            default -> { }
        }
        // Positive healing and collapse-inverted lunar damage never farm offensive resource refunds.
        if(effective&&!healing&&!a.session.reversed&&target!=p)NeoLegacyCasting.blueprintHit(p,a,target,conditionBeforeHit);
        if(effective&&weave!=null)weave.hit(p,target);
        var link=a.style==null?null:a.style.allay;
        if(effective&&!healing&&!a.session.reversed&&link!=null){
            link.damage(p,target,Math.max(0,before-target.getHealth()));
            if(core==Core.CURSE&&Set.of(Carrier.CONTACT,Carrier.LOCK,Carrier.PROJECTILE).contains(a.session.blueprint.carrier())
                    &&(a.session.blueprint.carrier()!=Carrier.PROJECTILE||weave!=null))
                link.chain(p,target,a.chainPrimaries,other->affectNow(p,a,other,budget,null));
        }
        a.record.damage+=Math.max(0,before-target.getHealth());a.record.absorbed+=Math.max(0,absorption-target.getAbsorptionAmount());a.record.healing+=Math.max(0,target.getHealth()-before);
        if(a.record.measurements.size()<32)a.record.measurements.add(new NeoState.Measurement(target.getUuidAsString(),net.minecraft.registry.Registries.ENTITY_TYPE.getId(target.getType()).toString(),target.getPos().toString(),budget,before,target.getHealth(),absorption,target.getAbsorptionAmount()));
        NeoRuntime.dirty(p);
        return effective;
    }
    private static boolean measureBlock(ServerPlayerEntity p,NeoRuntime.Active a,Vec3d pos,double budget) {
        BlockPos block=BlockPos.ofFloored(pos);
        for(BlockPos test:List.of(block,block.down(),block.north(),block.south(),block.east(),block.west()))if(p.getWorld().getBlockState(test).isOf(NeoMagic.TARGET)) {
            a.record.arrived+=budget;a.record.target="target@"+test.toShortString();
            NeoNetwork.discover(p,"carrier:AREA");NeoRuntime.dirty(p);return true;
        }
        return false;
    }
    public static void tick(MinecraftServer server) {
        for(var it=STORED.entrySet().iterator();it.hasNext();) {
            var entry=it.next();if(server.getTicks()>=entry.getValue().expires){entry.getValue().active.record.result="storage_expired";var p=server.getPlayerManager().getPlayer(entry.getKey());if(p!=null){NeoRuntime.dirty(p);NeoNetwork.send(p,false);}it.remove();}
        }
        for(var it=SUMMONS.entrySet().iterator();it.hasNext();) {
            var entry=it.next();if(!entry.getKey().isAlive()||server.getTicks()>=entry.getValue()){entry.getKey().discard();it.remove();}
        }
        for(var it=BOLTS.iterator();it.hasNext();) {
            Bolt bolt=it.next();
            if(!bolt.caster.isAlive()||bolt.caster.isDisconnected()||bolt.caster.getWorld()!=bolt.world||bolt.remaining<=0){finishBolt(bolt);it.remove();continue;}
            var style=bolt.active.style;
            if(style!=null&&style.target!=null&&net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle.legalTarget(bolt.caster,style.target)) {
                Vec3d delta=style.target.getBoundingBox().getCenter().subtract(bolt.pos);
                if(delta.lengthSquared()>1e-8)bolt.velocity=delta.normalize().multiply(bolt.velocity.length());
            }
            double step=Math.min(bolt.velocity.length(),bolt.remaining);
            Vec3d end=bolt.pos.add(bolt.velocity.normalize().multiply(step));
            BlockHitResult wall=bolt.world.raycast(new RaycastContext(bolt.pos,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,bolt.caster));
            Vec3d clipped=wall.getType()==HitResult.Type.BLOCK?wall.getPos():end;
            LivingEntity hit=along(bolt.caster,bolt.pos,clipped,bolt.active.session.quote.plan().focused()?.08:.3);
            if(hit!=null||wall.getType()==HitResult.Type.BLOCK) {
                healingBatch(bolt.active,()->{
                Plan plan=bolt.active.session.quote.plan();boolean plain=plan.branches()==1&&!plan.focused();
                if(hit!=null){bolt.weave.direct();affect(bolt.caster,bolt.active,hit,plain?bolt.budget*2/3:bolt.budget,bolt.weave);}
                if(plain) {
                    var nearby=effectTargets(bolt.caster,bolt.active,hit==null?clipped:hit.getPos(),2);
                    nearby.remove(hit);distribute(bolt.caster,bolt.active,nearby,bolt.budget/3);
                }
                if(hit==null){measureBlock(bolt.caster,bolt.active,clipped,plain?bolt.budget*2/3:bolt.budget);bolt.weave.ground(bolt.caster,wall);}
                return null;
                });
                finishBolt(bolt);it.remove();
            } else {bolt.pos=end;bolt.remaining-=step;bolt.world.spawnParticles(ParticleTypes.END_ROD,end.x,end.y,end.z,1,0,0,0,0);}
        }
    }
    private static void finishBolt(Bolt bolt) {
        var record=bolt.active.record;record.pending=Math.max(0,record.pending-1);
        if(record.pending==0)record.result=record.arrived>0?"resolved":"missed";
        NeoRuntime.dirty(bolt.caster);NeoNetwork.send(bolt.caster,false);
    }
    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity.getCommandTags().contains(SUMMON_TAG)&&!SUMMONS.containsKey(entity))entity.discard();});
    }
}
