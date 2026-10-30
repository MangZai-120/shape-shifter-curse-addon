package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.jackcooper.shapeShifterCurseAddon.ability.*;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.resource.*;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import net.onixary.shapeShifterCurseFabric.minion.*;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Per-cast commands and brand hit deduplication. Only active commands are ticked. */
public final class CourtSpellLink {
    private static final BalanceReader BAL=new BalanceReader("systems.form_casting");
    private static final ThreadLocal<Optional<Cast>> EFFECT=new ThreadLocal<>();
    private static final ThreadLocal<Integer> DAMAGE=new ThreadLocal<>();
    private CourtSpellLink(){}
    public interface ProjectileData {Cast ssca$court();void ssca$court(Cast cast);}
    public static Cast current(){var c=EFFECT.get();return c==null?FormSpellStyle.currentCourt():c.orElse(null);}
    public static Cast of(Entity e){return e instanceof ProjectileData d?d.ssca$court():current();}
    public static boolean suppressLegacy(){return DAMAGE.get()!=null||current()!=null;}
    public static boolean with(Cast cast,BooleanSupplier action){var prior=EFFECT.get();EFFECT.set(Optional.ofNullable(cast));try{return action.getAsBoolean();}finally{if(prior==null)EFFECT.remove();else EFFECT.set(prior);}}
    public static boolean damage(Cast cast,Entity owner,LivingEntity target,BooleanSupplier action){
        var prior=DAMAGE.get();DAMAGE.set(prior==null?1:prior+1);boolean hit;
        try{hit=action.getAsBoolean();}finally{if(prior==null)DAMAGE.remove();else DAMAGE.set(prior);}
        if(hit&&owner instanceof ServerPlayerEntity p)hit(cast,p,target);return hit;
    }
    public static void status(Cast cast,LivingEntity target,StatusEffectInstance effect){
        if(cast==null||effect.getDuration()<=0||effect.getEffectType().getCategory()!=StatusEffectCategory.HARMFUL||!(target.getWorld() instanceof ServerWorld w))return;
        var p=w.getServer().getPlayerManager().getPlayer(cast.owner);if(p!=null)hit(cast,p,target);
    }
    public static Cast create(ServerPlayerEntity p,net.ssca.neomagic.NeoState.Experiment record){
        int kind=FormUtils.isForm(p,FormIdentifiers.ANUBIS_WOLF_SP)?1:FormUtils.isGoldenSandstormSP(p)?2:0;
        if(kind==0)return null;var c=new Cast();c.id=UUID.fromString(record.id);c.owner=p.getUuid();c.kind=kind;c.price=BAL.i("court_soul_cost",5);return c;
    }
    public static final class Cast {
        public UUID id,owner;public int kind,price;public boolean ordered;long expires;
        final Set<UUID> hits=new HashSet<>(),members=new HashSet<>();
        public NbtCompound write(){var n=new NbtCompound();n.putUuid("Id",id);n.putUuid("Owner",owner);n.putInt("Kind",kind);n.putInt("Price",price);n.putBoolean("Ordered",ordered);n.putLong("Expires",expires);putIds(n,"Hits",hits);putIds(n,"Members",members);return n;}
        public static Cast read(NbtCompound n){if(!n.containsUuid("Id")||!n.containsUuid("Owner"))return null;var c=new Cast();c.id=n.getUuid("Id");c.owner=n.getUuid("Owner");c.kind=n.getInt("Kind");c.price=n.getInt("Price");if(c.kind<1||c.kind>2||c.price<0||c.price>1000)return null;c.ordered=n.getBoolean("Ordered");c.expires=n.getLong("Expires");readIds(n,"Hits",c.hits);readIds(n,"Members",c.members);return c;}
    }
    private static void putIds(NbtCompound n,String key,Set<UUID> ids){var list=new NbtList();ids.forEach(id->list.add(NbtString.of(id.toString())));n.put(key,list);}
    private static void readIds(NbtCompound n,String key,Set<UUID> ids){var list=n.getList(key,NbtElement.STRING_TYPE);for(int i=0;i<list.size();i++)try{ids.add(UUID.fromString(list.getString(i)));}catch(IllegalArgumentException ignored){}}
    private static final class Order {
        UUID owner,wolf,target;String dimension;long until,life;int previousAttack;boolean gone;
        NbtCompound write(){var n=new NbtCompound();n.putUuid("Owner",owner);n.putUuid("Wolf",wolf);if(target!=null)n.putUuid("Target",target);n.putString("World",dimension);n.putLong("Until",until);n.putLong("Life",life);n.putInt("Attack",previousAttack);n.putBoolean("Gone",gone);return n;}
        static Order read(NbtCompound n){if(!n.containsUuid("Owner")||!n.containsUuid("Wolf")||Identifier.tryParse(n.getString("World"))==null)return null;var o=new Order();o.owner=n.getUuid("Owner");o.wolf=n.getUuid("Wolf");o.target=n.containsUuid("Target")?n.getUuid("Target"):null;o.dimension=n.getString("World");o.until=n.getLong("Until");o.life=n.getLong("Life");o.previousAttack=n.getInt("Attack");o.gone=n.getBoolean("Gone");return o;}
    }
    private static final class Store extends PersistentState {
        final Map<UUID,Cast> casts=new HashMap<>();final Map<UUID,Order> orders=new HashMap<>();
        static Store get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(Store::read,Store::new,"ssca_court_spell_links");}
        static Store read(NbtCompound n){var s=new Store();var casts=n.getList("Casts",NbtElement.COMPOUND_TYPE);for(int i=0;i<casts.size();i++){var c=Cast.read(casts.getCompound(i));if(c!=null)s.casts.put(c.id,c);}var orders=n.getList("Orders",NbtElement.COMPOUND_TYPE);for(int i=0;i<orders.size();i++){var o=Order.read(orders.getCompound(i));if(o!=null)s.orders.put(o.wolf,o);}return s;}
        @Override public NbtCompound writeNbt(NbtCompound n){var cs=new NbtList();casts.values().forEach(c->cs.add(c.write()));n.put("Casts",cs);var os=new NbtList();orders.values().forEach(o->os.add(o.write()));n.put("Orders",os);return n;}
        Cast join(Cast proposed,long now){var c=casts.computeIfAbsent(proposed.id,id->proposed);if(!c.owner.equals(proposed.owner)||c.kind!=proposed.kind)return null;c.ordered|=proposed.ordered;c.hits.addAll(proposed.hits);c.members.addAll(proposed.members);c.expires=Math.max(c.expires,now+1200);markDirty();return c;}
    }
    public static void hit(Cast proposed,ServerPlayerEntity p,LivingEntity target){
        if(proposed==null||!p.getUuid().equals(proposed.owner)||!FormSpellStyle.legalTarget(p,target)
                ||proposed.kind==1&&!FormUtils.isForm(p,FormIdentifiers.ANUBIS_WOLF_SP)||proposed.kind==2&&!FormUtils.isGoldenSandstormSP(p))return;
        var s=Store.get(p.getServer());var c=s.join(proposed,p.getServer().getOverworld().getTime());if(c==null)return;
        if(c.kind==2){if(c.hits.add(target.getUuid())){proposed.hits.add(target.getUuid());GoldenSandstormErosionBrand.onSpellHit(p,target);s.markDirty();}return;}
        if(c.ordered||AnubisWolfSpSoulEnergy.getEnergy(p)<c.price)return;
        var world=p.getServerWorld();AnubisWolfMinionEntity wolf=null;boolean hasWolf=false;
        if(p instanceof IPlayerEntityMinion owner)for(UUID id:owner.shape_shifter_curse$getMinionsByMinionID(AnubisWolfMinionEntity.MinionID)){
            if(world.getEntity(id) instanceof AnubisWolfMinionEntity w&&w.isAlive()&&p.getUuid().equals(w.getOwnerUuid())){
                hasWolf=true;if(!DomainManager.blocksTargeting(w,target)&&(wolf==null||w.squaredDistanceTo(target)<wolf.squaredDistanceTo(target)))wolf=w;
            }
        }
        boolean summoned=false;
        if(wolf==null&&!hasWolf){var pos=safePosition(p,target);if(pos==null)return;wolf=AnubisWolfSpSummonWolves.spawnSpellWolf(p,pos,AnubisWolfSpDeathDomain.hasActiveDomain(p.getUuid()),false);summoned=wolf!=null;}
        if(wolf==null)return;
        if(!ResourceBars.consume(p,BarKeys.ANUBIS_SOUL,c.price)){if(summoned)remove(p,wolf);return;}
        long now=world.getTime();var order=s.orders.get(wolf.getUuid());if(order==null){order=new Order();order.owner=p.getUuid();order.wolf=wolf.getUuid();order.dimension=world.getRegistryKey().getValue().toString();}
        if(summoned)order.life=now+AnubisWolfSpSummonWolves.spellWolfDuration();
        order.target=target.getUuid();order.until=now+BAL.i("court_order_ticks",100);order.previousAttack=wolf.getLastAttackTime();s.orders.put(order.wolf,order);
        wolf.setSitting(false);wolf.setTarget(target);wolf.getNavigation().startMovingTo(target,BAL.d("court_rush_speed",1.5));
        c.ordered=true;proposed.ordered=true;s.markDirty();
    }
    /** Finite loaded-chunk search, real support/collision/fluid/hazard checks; never teleport a wolf. */
    public static BlockPos safePosition(ServerPlayerEntity p,LivingEntity target){
        var world=p.getServerWorld();double start=p.getRandom().nextDouble()*Math.PI*2;
        double minimum=BAL.d("court_spawn_min_distance",5);
        for(int ring=0;ring<3;ring++)for(int i=0;i<16;i++){
            double angle=start+i*Math.PI/8,radius=minimum+.75+ring;int x=(int)Math.floor(target.getX()+Math.cos(angle)*radius),z=(int)Math.floor(target.getZ()+Math.sin(angle)*radius);
            for(int dy=2;dy>=-3;dy--){var at=new BlockPos(x,(int)Math.floor(target.getY())+dy,z);var center=Vec3d.ofBottomCenter(at);
                if(center.squaredDistanceTo(target.getPos())<=minimum*minimum||p.squaredDistanceTo(center)>900||!world.getChunkManager().isChunkLoaded(at.getX()>>4,at.getZ()>>4)||!world.getWorldBorder().contains(at)||!world.isInBuildLimit(at))continue;
                var ground=at.down();var below=world.getBlockState(ground);if(!below.isSideSolidFullSquare(world,ground,Direction.UP)||!world.getFluidState(at).isEmpty()||!world.getFluidState(ground).isEmpty())continue;
                if(below.isOf(Blocks.MAGMA_BLOCK)||below.isOf(Blocks.CACTUS)||below.isIn(BlockTags.CAMPFIRES))continue;
                var box=MinionRegister.ANUBIS_WOLF_MINION.getDimensions().getBoxAt(center);if(!world.isSpaceEmpty(box)||world.getBlockState(at).isIn(BlockTags.FIRE)||world.getBlockState(at).isOf(Blocks.SWEET_BERRY_BUSH))continue;
                if(DomainManager.blocksCrossBoundary(world,p.getPos(),center)||DomainManager.blocksCrossBoundary(world,target.getPos(),center))continue;
                return at;
            }
        }return null;
    }
    private static void remove(ServerPlayerEntity owner,AnubisWolfMinionEntity wolf){if(owner instanceof IPlayerEntityMinion p)p.shape_shifter_curse$removeMinion(AnubisWolfMinionEntity.MinionID,wolf.getUuid());wolf.discard();}
    private static void tick(MinecraftServer server){
        if(server.getTicks()%5!=0)return;var s=Store.get(server);long global=server.getOverworld().getTime();
        if(server.getTicks()%20==0&&s.casts.values().removeIf(c->c.members.isEmpty()&&c.expires<global))s.markDirty();
        var it=s.orders.values().iterator();while(it.hasNext()){
            var o=it.next();var world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,new Identifier(o.dimension)));if(world==null)continue;
            var owner=server.getPlayerManager().getPlayer(o.owner);var entity=world.getEntity(o.wolf);if(o.gone){it.remove();s.markDirty();continue;}if(!(entity instanceof AnubisWolfMinionEntity wolf)){if(world.getTime()>o.until&&o.life==0){it.remove();s.markDirty();}continue;}
            boolean valid=owner!=null&&owner.isAlive()&&owner.getWorld()==world&&FormUtils.isForm(owner,FormIdentifiers.ANUBIS_WOLF_SP)&&o.owner.equals(wolf.getOwnerUuid());
            if(!wolf.isAlive()||!valid||o.life>0&&world.getTime()>=o.life){if(o.life>0)remove(owner,wolf);else {wolf.setTarget(null);wolf.getNavigation().stop();}it.remove();s.markDirty();continue;}
            if(o.target!=null){var e=world.getEntity(o.target);if(world.getTime()>=o.until||wolf.getLastAttackTime()!=o.previousAttack||!(e instanceof LivingEntity target)||!FormSpellStyle.legalTarget(owner,target)||DomainManager.blocksTargeting(wolf,target)){
                if(wolf.getTarget()!=null&&wolf.getTarget().getUuid().equals(o.target)){wolf.setTarget(null);wolf.getNavigation().stop();}o.target=null;if(o.life==0)it.remove();s.markDirty();
            }else{wolf.setTarget(target);wolf.getNavigation().startMovingTo(target,BAL.d("court_rush_speed",1.5));}}
        }
    }
    public static void register(){
        ServerTickEvents.END_SERVER_TICK.register(CourtSpellLink::tick);
        ServerEntityEvents.ENTITY_LOAD.register((e,w)->{if(e instanceof ProjectileData d){var proposed=current();if(d.ssca$court()==null&&proposed!=null&&e instanceof net.minecraft.entity.projectile.ProjectileEntity projectile&&projectile.getOwner()!=null&&projectile.getOwner().getUuid().equals(proposed.owner))d.ssca$court(proposed);if(d.ssca$court()!=null){var s=Store.get(w.getServer());var c=s.join(d.ssca$court(),w.getServer().getOverworld().getTime());if(c!=null){c.members.add(e.getUuid());d.ssca$court(c);s.markDirty();}}}});
        ServerEntityEvents.ENTITY_UNLOAD.register((e,w)->{if(e.getRemovalReason()!=Entity.RemovalReason.DISCARDED&&e.getRemovalReason()!=Entity.RemovalReason.KILLED)return;if(e instanceof ProjectileData d&&d.ssca$court()!=null){var s=Store.get(w.getServer());var c=s.casts.get(d.ssca$court().id);if(c!=null&&c.members.remove(e.getUuid()))s.markDirty();}if(e instanceof AnubisWolfMinionEntity){var s=Store.get(w.getServer());var order=s.orders.get(e.getUuid());if(order!=null){order.gone=true;s.markDirty();}}});
    }
}
