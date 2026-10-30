package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.resource.*;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.ssca.neomagic.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Per-cast blood payment and bounded, server-owned spell-debuff transmission. */
public final class BatSpellLink {
    private static final BalanceReader BAL=new BalanceReader("systems.form_casting");
    private static final ThreadLocal<Boolean> PROPAGATING=new ThreadLocal<>();
    private static final ThreadLocal<Optional<Cast>> EFFECT=new ThreadLocal<>();
    public static boolean with(Cast cast,java.util.function.BooleanSupplier action){var previous=EFFECT.get();EFFECT.set(Optional.ofNullable(cast));try{return action.getAsBoolean();}finally{if(previous==null)EFFECT.remove();else EFFECT.set(previous);}}
    public static boolean transmitting(){return Boolean.TRUE.equals(PROPAGATING.get());}
    private static boolean copy(LivingEntity target,StatusEffectInstance effect,ServerPlayerEntity owner){
        Boolean previous=PROPAGATING.get();PROPAGATING.set(true);
        try{return with(null,()->target.addStatusEffect(effect,owner));}finally{if(previous==null)PROPAGATING.remove();else PROPAGATING.set(previous);}
    }
    public static long queries,attempts;
    private static final boolean PROFILE=Boolean.getBoolean("neomagic.batProfile");
    public static long profileNanos,profileTicks,profileMaxNanos;
    private BatSpellLink() {}
    public interface ProjectileData { Cast ssca$bat(); void ssca$bat(Cast cast); }
    public static Cast current(){var value=EFFECT.get();return value==null?FormSpellStyle.currentBat():value.orElse(null);}
    public static Cast of(Entity source){return source instanceof ProjectileData data?data.ssca$bat():current();}
    public static double bloodMultiplier(net.minecraft.entity.player.PlayerEntity p) {
        return FormUtils.isForm(p,FormIdentifiers.BAT_DESMODUS)&&ResourceBars.get(p,BarKeys.BAT_BLOOD)>=BAL.i("bat_blood_cost",15)
                ?1-BAL.d("bat_blood_reduction",.35):1;
    }
    public static boolean spreading(net.minecraft.entity.player.PlayerEntity p,boolean eligible){return eligible&&FormUtils.isForm(p,FormIdentifiers.BAT_PARASITIC_FRUIT);}
    public static double spreadMultiplier(net.minecraft.entity.player.PlayerEntity p,boolean eligible){return spreading(p,eligible)?1+BAL.d("fruit_debuff_extra_collapse",.25):1;}
    public static Blood planBlood(ServerPlayerEntity p){return new Blood(p,bloodMultiplier(p)<1?BAL.i("bat_blood_cost",15):0);}
    public static final class Blood {
        private final ServerPlayerEntity player;private final int price;private boolean paid;
        public final double multiplier;
        private Blood(ServerPlayerEntity p,int price){player=p;this.price=price;multiplier=price>0?1-BAL.d("bat_blood_reduction",.35):1;}
        public boolean pay(){if(paid)return true;if(price>0&&!ResourceBars.consume(player,BarKeys.BAT_BLOOD,price))return false;paid=true;return true;}
        public int spent(){return paid?price:0;}
    }
    public static final class Cast {
        public UUID id,owner;
        public double radius;
        public int dwell,children,generations,npcBonus;
        public Cast() {}
        public Cast(ServerPlayerEntity p,NeoState.Experiment record){id=UUID.fromString(record.id);owner=p.getUuid();radius=BAL.d("fruit_debuff_radius",3);dwell=BAL.i("fruit_debuff_dwell_ticks",50);children=BAL.i("fruit_debuff_children",2);generations=BAL.i("fruit_debuff_generations",3);npcBonus=net.jackcooper.shapeShifterCurseAddon.ability.NpcCombatEnhancements.fruitDebuffLevelBonus();}
        public NbtCompound write(){NbtCompound n=new NbtCompound();n.putUuid("Id",id);n.putUuid("Owner",owner);n.putDouble("Radius",radius);n.putInt("Dwell",dwell);n.putInt("Children",children);n.putInt("Generations",generations);n.putInt("NpcBonus",npcBonus);return n;}
        private boolean valid(){return id!=null&&owner!=null&&Double.isFinite(radius)&&radius>0&&radius<=16&&dwell>=1&&dwell<=1200&&children>=1&&children<=2&&generations>=1&&generations<=3&&npcBonus>=0&&npcBonus<=255;}
        public static Cast read(NbtCompound n){if(!n.containsUuid("Id")||!n.containsUuid("Owner"))return null;Cast c=new Cast();c.id=n.getUuid("Id");c.owner=n.getUuid("Owner");c.radius=n.getDouble("Radius");c.dwell=n.getInt("Dwell");c.children=n.getInt("Children");c.generations=n.getInt("Generations");c.npcBonus=n.getInt("NpcBonus");return c.valid()?c:null;}
    }
    private static final class Debuff {
        String type;int amplifier,npcBonus;long until;boolean ambient,particles,icon;
        Debuff() {}
        Debuff(StatusEffectInstance e,long now){type=Registries.STATUS_EFFECT.getId(e.getEffectType()).toString();amplifier=e.getAmplifier();until=now+e.getDuration();ambient=e.isAmbient();particles=e.shouldShowParticles();icon=e.shouldShowIcon();}
        StatusEffect effect(){return Registries.STATUS_EFFECT.get(Identifier.tryParse(type));}
    }
    private static final class Node {
        UUID target;int generation=1,children;
        Map<String,Debuff> effects=new LinkedHashMap<>();
        transient Map<UUID,Long> near=new HashMap<>();
    }
    private static final class Group {
        Cast cast;String world;long expires;
        transient RegistryKey<net.minecraft.world.World> worldKey;
        Map<UUID,Node> nodes=new LinkedHashMap<>();
    }
    private static final class Saved {Map<UUID,Group> groups=new LinkedHashMap<>();}
    private static final class Store extends PersistentState {
        Saved saved=new Saved();
        final Map<UUID,Set<Node>> index=new HashMap<>();
        void index(Node node){index.computeIfAbsent(node.target,id->Collections.newSetFromMap(new IdentityHashMap<>())).add(node);}
        void unindex(Node node){var nodes=index.get(node.target);if(nodes!=null){nodes.remove(node);if(nodes.isEmpty())index.remove(node.target);}}
        static Store get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(Store::read,Store::new,"ssca_bat_spell_links");}
        static Store read(NbtCompound n){
            Store s=new Store();if(!n.contains("Data",NbtElement.BYTE_ARRAY_TYPE))return s;
            Saved saved=NeoState.JSON.fromJson(new String(n.getByteArray("Data"),StandardCharsets.UTF_8),Saved.class);
            if(saved==null||saved.groups==null)throw new IllegalArgumentException("Invalid bat spell state");
            for(var entry:saved.groups.entrySet()){
                Group g=entry.getValue();if(g==null||g.cast==null||!g.cast.valid()||!entry.getKey().equals(g.cast.id)||Identifier.tryParse(g.world)==null||g.nodes==null)throw new IllegalArgumentException("Invalid bat cast");
                for(var node:g.nodes.entrySet()){
                    Node v=node.getValue();if(v==null||!node.getKey().equals(v.target)||v.generation<1||v.generation>g.cast.generations||v.children<0||v.children>g.cast.children||v.effects==null)throw new IllegalArgumentException("Invalid bat node");
                    for(var d:v.effects.values())if(d==null||d.type==null||Identifier.tryParse(d.type)==null||d.amplifier<0||d.amplifier>255||d.npcBonus<0||d.npcBonus>d.amplifier)throw new IllegalArgumentException("Invalid bat effect");
                }
            }s.saved=saved;saved.groups.values().forEach(g->g.nodes.values().forEach(s::index));return s;
        }
        @Override public NbtCompound writeNbt(NbtCompound n){n.putInt("Version",1);n.putByteArray("Data",NeoState.JSON.toJson(saved).getBytes(StandardCharsets.UTF_8));return n;}
    }
    /** Called only after a spell successfully adds a harmful status; fire and native powers never enter here. */
    public static void applied(Cast cast,LivingEntity target,StatusEffectInstance applied,Entity source){
        if(cast==null||applied==null||applied.getDuration()<=0||applied.getEffectType().getCategory()!=StatusEffectCategory.HARMFUL||!(target.getWorld() instanceof ServerWorld world))return;
        ServerPlayerEntity owner=world.getServer().getPlayerManager().getPlayer(cast.owner);
        if(owner==null||!FormSpellStyle.legalTarget(owner,target))return;
        StatusEffectInstance active=target.getStatusEffect(applied.getEffectType());if(active==null)return;
        Store s=Store.get(world.getServer());Group g=s.saved.groups.computeIfAbsent(cast.id,id->{Group v=new Group();v.cast=cast;v.world=world.getRegistryKey().getValue().toString();return v;});
        if(!g.world.equals(world.getRegistryKey().getValue().toString()))return;
        Node node=g.nodes.computeIfAbsent(target.getUuid(),id->{Node v=new Node();v.target=id;s.index(v);return v;});
        Debuff d=new Debuff(applied,world.getTime());d.amplifier=Math.min(d.amplifier,active.getAmplifier());
        boolean enhanced=net.jackcooper.shapeShifterCurseAddon.ability.NpcCombatEnhancements.isEnemyNpc(source,target)
                &&source instanceof LivingEntity living&&FormUtils.isForm(living,FormIdentifiers.BAT_PARASITIC_FRUIT);
        d.npcBonus=enhanced?Math.min(d.amplifier,cast.npcBonus):0;d.until=Math.min(d.until,world.getTime()+active.getDuration());
        node.effects.put(d.type,d);g.expires=Math.max(g.expires,d.until+200);s.markDirty();
    }
    private static List<Debuff> active(Node n,LivingEntity source,long now){
        List<Debuff> active=new ArrayList<>();
        Iterator<Debuff> it=n.effects.values().iterator();
        while(it.hasNext()){
            var d=it.next();StatusEffect type=d.effect();if(type==null){it.remove();continue;}
            StatusEffectInstance effect=source.getStatusEffect(type);
            if(effect==null||d.until<=now||effect.getDuration()<=0||effect.getAmplifier()<d.amplifier){it.remove();continue;}
            Debuff copy=new Debuff();copy.type=d.type;copy.amplifier=d.amplifier;copy.npcBonus=d.npcBonus;copy.until=Math.min(d.until,now+effect.getDuration());copy.ambient=d.ambient;copy.particles=d.particles;copy.icon=d.icon;active.add(copy);
        }return active;
    }
    private static void tick(MinecraftServer server){
        Store s=Store.get(server);Iterator<Group> groups=s.saved.groups.values().iterator();
        while(groups.hasNext()){
            Group g=groups.next();if(g.worldKey==null)g.worldKey=RegistryKey.of(RegistryKeys.WORLD,new Identifier(g.world));ServerWorld world=server.getWorld(g.worldKey);
            if(world==null)continue;long now=world.getTime();
            if(now>=g.expires){g.nodes.values().forEach(s::unindex);groups.remove();s.markDirty();continue;}
            ServerPlayerEntity owner=server.getPlayerManager().getPlayer(g.cast.owner);
            if(owner==null||owner.getWorld()!=world||!owner.isAlive())continue;
            // Existing contacts get cheap distance checks each tick: a brief exit must reset exposure.
            // New neighbors are found only by the staggered local query below, once per five ticks.
            for(Node n:g.nodes.values())if(n.near!=null&&!n.near.isEmpty()){
                Entity source=world.getEntity(n.target);
                if(!(source instanceof LivingEntity living)||!living.isAlive()){n.near.clear();continue;}
                n.near.keySet().removeIf(id->{Entity e=world.getEntity(id);return !(e instanceof LivingEntity target)||!target.isAlive()||g.nodes.containsKey(id)||living.squaredDistanceTo(target)>g.cast.radius*g.cast.radius;});
            }
            List<Node> batch=new ArrayList<>();
            for(Node n:g.nodes.values())if(n.generation<g.cast.generations&&n.children<g.cast.children&&Math.floorMod(n.target.hashCode(),5)==Math.floorMod(server.getTicks(),5))batch.add(n);
            for(Node n:batch){
                if(n.near==null)n.near=new HashMap<>();
                Entity entity=world.getEntity(n.target);
                if(!(entity instanceof LivingEntity source)){n.near.clear();continue;}
                if(!source.isAlive()||source.isRemoved()){n.near.clear();n.effects.clear();s.markDirty();continue;}
                List<Debuff> effects=active(n,source,now);if(effects.isEmpty()){n.near.clear();continue;}
                queries++;
                List<LivingEntity> nearby=world.getEntitiesByClass(LivingEntity.class,source.getBoundingBox().expand(g.cast.radius),e->!g.nodes.containsKey(e.getUuid())&&FormSpellStyle.legalTarget(owner,e)&&source.squaredDistanceTo(e)<=g.cast.radius*g.cast.radius);
                Set<UUID> present=new HashSet<>();nearby.forEach(e->present.add(e.getUuid()));n.near.keySet().retainAll(present);
                nearby.sort(Comparator.<LivingEntity>comparingDouble(source::squaredDistanceTo).thenComparing(e->e.getUuid().toString()));
                for(LivingEntity target:nearby){
                    long since=n.near.computeIfAbsent(target.getUuid(),id->now);if(now-since<=g.cast.dwell)continue;
                    attempts++;Node child=new Node();child.target=target.getUuid();child.generation=n.generation+1;
                    for(Debuff d:effects){int duration=(int)Math.min(Integer.MAX_VALUE,d.until-now);int base=Math.max(0,d.amplifier-d.npcBonus),bonus=target instanceof net.minecraft.entity.player.PlayerEntity?0:g.cast.npcBonus;int amplifier=base+bonus;
                        if(duration>0&&copy(target,new StatusEffectInstance(d.effect(),duration,amplifier,d.ambient,d.particles,d.icon),owner)){
                        StatusEffectInstance received=target.getStatusEffect(d.effect());
                        if(received!=null){Debuff actual=new Debuff(received,now);actual.amplifier=Math.min(amplifier,received.getAmplifier());actual.npcBonus=Math.min(bonus,Math.max(0,actual.amplifier-base));actual.until=Math.min(d.until,actual.until);child.effects.put(actual.type,actual);}
                    }}
                    if(child.effects.isEmpty())continue;
                    g.nodes.put(child.target,child);s.index(child);n.children++;n.near.remove(child.target);s.markDirty();
                    var record=NeoRuntime.data(owner).records.stream().filter(r->r.id.equals(g.cast.id.toString())).findFirst().orElse(null);if(record!=null){record.sporeInfections++;NeoRuntime.dirty(owner);}
                    if(n.children>=g.cast.children){n.near.clear();break;}
                }
            }
        }
    }
    public static void cured(LivingEntity target,StatusEffect effect){
        if(!(target.getWorld() instanceof ServerWorld world)||world.getServer()==null||world.getServer().getOverworld()==null)return;
        Store s=Store.get(world.getServer());Set<Node> nodes=s.index.get(target.getUuid());if(nodes==null)return;
        String key=effect==null?null:Registries.STATUS_EFFECT.getId(effect).toString();
        for(Node node:nodes){if(key==null)node.effects.clear();else node.effects.remove(key);node.near.clear();}s.markDirty();
    }
    public static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(!PROFILE){tick(server);return;}
        long before=System.nanoTime();tick(server);long elapsed=System.nanoTime()-before;
        profileNanos+=elapsed;profileTicks++;profileMaxNanos=Math.max(profileMaxNanos,elapsed);
    });}
}
