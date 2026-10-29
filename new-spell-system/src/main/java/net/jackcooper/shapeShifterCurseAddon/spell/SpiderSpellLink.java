package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.jackcooper.shapeShifterCurseAddon.block.WebMembraneOwners;
import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.onixary.shapeShifterCurseFabric.status_effects.EntangledEffectUtils;

import java.util.*;

/** 蜘蛛施法联动：仅起手/实际碰撞/跳杀命中工作，不添加每tick扫描或网络消息。 */
public final class SpiderSpellLink {
    private static final BalanceReader BAL = new BalanceReader("systems.form_casting");
    private SpiderSpellLink() {}
    public static int webCost() { return BAL.i("moon_weaver_projectile_mana",8); }
    public static boolean canWeave(ServerPlayerEntity p) {
        return FormUtils.isForm(p,FormIdentifiers.SPIDER_MOON_WEAVER)
                &&ResourceBars.get(p,BarKeys.VANILLA_MANA)>=webCost();
    }
    public interface WebProjectile { Impact ssca$weave(); }
    public static final class RollGroup {
        public final UUID id=UUID.randomUUID();
        public final Set<UUID> targets=new HashSet<>();
    }
    /** Only casts with extant saved projectile entities occupy this ledger; unloading is not destruction. */
    private static final class WeaveLedger extends PersistentState {
        private static final String KEY="ssca_spell_weave_hits";
        private static final class Entry {
            final Set<UUID> projectiles=new HashSet<>(),targets=new HashSet<>();
        }
        private final Map<UUID,Entry> casts=new HashMap<>();
        static WeaveLedger get(MinecraftServer server) {
            return server.getOverworld().getPersistentStateManager().getOrCreate(WeaveLedger::read,WeaveLedger::new,KEY);
        }
        static WeaveLedger read(NbtCompound nbt) {
            WeaveLedger result=new WeaveLedger();
            for(String key:nbt.getKeys())try {
                Entry entry=new Entry();NbtCompound data=nbt.getCompound(key);
                readSet(data,"Projectiles",entry.projectiles);readSet(data,"Targets",entry.targets);
                if(!entry.projectiles.isEmpty())result.casts.put(UUID.fromString(key),entry);
            }catch(IllegalArgumentException ignored){}
            return result;
        }
        static void readSet(NbtCompound data,String key,Set<UUID> into) {
            NbtList list=data.getList(key,NbtElement.STRING_TYPE);
            for(int i=0;i<list.size();i++)try{into.add(UUID.fromString(list.getString(i)));}catch(IllegalArgumentException ignored){}
        }
        static NbtList writeSet(Set<UUID> values) {NbtList list=new NbtList();values.forEach(id->list.add(NbtString.of(id.toString())));return list;}
        @Override public NbtCompound writeNbt(NbtCompound nbt) {
            casts.forEach((id,entry)->{NbtCompound data=new NbtCompound();data.put("Projectiles",writeSet(entry.projectiles));data.put("Targets",writeSet(entry.targets));nbt.put(id.toString(),data);});return nbt;
        }
        void join(UUID cast,UUID projectile) {
            if(casts.computeIfAbsent(cast,k->new Entry()).projectiles.add(projectile))markDirty();
        }
        void leave(UUID cast,UUID projectile) {
            Entry entry=casts.get(cast);if(entry==null||!entry.projectiles.remove(projectile))return;
            if(entry.projectiles.isEmpty())casts.remove(cast);markDirty();
        }
        boolean draw(UUID cast,UUID target) {
            Entry entry=casts.get(cast);
            if(entry==null||entry.targets.size()>=256||!entry.targets.add(target))return false;
            markDirty();return true;
        }
    }
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{
            var reason=entity.getRemovalReason();
            if((reason==net.minecraft.entity.Entity.RemovalReason.DISCARDED||reason==net.minecraft.entity.Entity.RemovalReason.KILLED)
                    &&entity instanceof WebProjectile projectile)projectile.ssca$weave().destroyed(world,entity.getUuid());
        });
    }
    /** 每颗弹体的服务端快照；穿刺曾直击实体后，后续落地也不铺网。 */
    public static final class Impact {
        private boolean enabled,direct;
        private Set<UUID> rolled;
        private UUID cast;
        public void enable() {enabled=true;}
        public void enable(UUID group) {enabled=true;cast=group;}
        public void enable(Set<UUID> group) {enabled=true;rolled=group;}
        public void loaded(ServerWorld world,UUID projectile) {if(cast!=null)WeaveLedger.get(world.getServer()).join(cast,projectile);}
        public void destroyed(ServerWorld world,UUID projectile) {if(cast!=null)WeaveLedger.get(world.getServer()).leave(cast,projectile);}
        public boolean enabled() {return enabled;}
        public void direct() {if(enabled)direct=true;}
        public void hit(ServerPlayerEntity caster,LivingEntity target) {
            if(!enabled)return;
            direct=true;
            if(!FormSpellStyle.legalTarget(caster,target))return;
            if(cast!=null){if(!WeaveLedger.get(caster.getServer()).draw(cast,target.getUuid()))return;}
            else {
                if(rolled==null)rolled=new HashSet<>();
                if(rolled.size()>=256||!rolled.add(target.getUuid()))return;
            }
            if(caster.getRandom().nextDouble()<BAL.d("moon_weaver_cocoon_chance",.15))
                EntangledEffectUtils.applyEntangledEffect(caster,target,
                        EntangledEffectUtils.ENTANGLED_DURATION_PER_LEVEL*(EntangledEffectUtils.ENTANGLED_MAX_LEVEL+1));
        }
        public void ground(ServerPlayerEntity caster,BlockHitResult hit) {
            if(!enabled||direct||hit.getSide()!=Direction.UP)return;
            // 一次碰撞只处理一次；不影响已存在的蛛网膜及其寿命/归属。
            enabled=false;
            ServerWorld world=caster.getServerWorld();BlockPos center=hit.getBlockPos().up();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                BlockPos pos=center.add(x,0,z),floor=pos.down();
                if(!world.isInBuildLimit(pos)||!world.isChunkLoaded(pos)||!caster.canModifyBlocks()
                        ||!world.canPlayerModifyAt(caster,pos)||!world.getBlockState(pos).isAir()
                        ||!world.getBlockState(floor).isSideSolidFullSquare(world,floor,Direction.UP))continue;
                boolean wet=false;
                for(Direction d:Direction.values())if(!world.getFluidState(pos.offset(d)).isEmpty()){wet=true;break;}
                if(wet)continue;
                var state=RegAddonBlocks.WEB_MEMBRANE.getDefaultState().with(ConnectingBlock.FACING_PROPERTIES.get(Direction.DOWN),true);
                if(world.setBlockState(pos,state))WebMembraneOwners.set(pos,caster.getUuid());
            }
        }
        public void write(NbtCompound nbt) {
            if(!enabled&&!direct)return;
            nbt.putBoolean("SscaWeaveEnabled",enabled);nbt.putBoolean("SscaWeaveDirect",direct);
            if(cast!=null)nbt.putUuid("SscaWeaveCast",cast);
            if(rolled!=null&&!rolled.isEmpty()){NbtList list=new NbtList();rolled.forEach(id->list.add(NbtString.of(id.toString())));nbt.put("SscaWeaveRolled",list);}
        }
        public void read(NbtCompound nbt) {
            enabled=nbt.getBoolean("SscaWeaveEnabled");direct=nbt.getBoolean("SscaWeaveDirect");rolled=null;
            cast=nbt.containsUuid("SscaWeaveCast")?nbt.getUuid("SscaWeaveCast"):null;
            NbtList list=nbt.getList("SscaWeaveRolled",NbtElement.STRING_TYPE);
            if(!list.isEmpty())rolled=new HashSet<>();
            for(int i=0;i<Math.min(256,list.size());i++)try{rolled.add(UUID.fromString(list.getString(i)));}catch(IllegalArgumentException ignored){}
        }
    }
    public static void directHit(ProjectileEntity projectile,LivingEntity target) {
        if(projectile.getOwner() instanceof ServerPlayerEntity caster)
            ((WebProjectile)projectile).ssca$weave().hit(caster,target);
    }
    /** 本次被动的冷却和消耗乘区；aim仅在构造效果时临时使用，不移动玩家视角。 */
    public record Trigger(Vec3d aim,boolean free) {
        public double growth() {return free?0:BAL.d("salticidae_curse_collapse_multiplier",.2);}
        public int cooldown(int normal) {return Math.max(1,(int)Math.ceil(normal*BAL.d("salticidae_curse_cooldown_multiplier",.1)));}
        public void effect(ServerPlayerEntity p,Runnable action) {
            float yaw=p.getYaw(),pitch=p.getPitch();Vec3d delta=net.ssca.neomagic.NeoLegacyCasting.effectTarget(aim).subtract(p.getEyePos());
            if(delta.lengthSquared()>1e-8){p.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));p.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
            try{action.run();}finally{p.setYaw(yaw);p.setPitch(pitch);}
        }
    }
    /** selected可用优先；否则随机一个书内非红色诅咒槽；无可用槽返回-1走白色兜底。 */
    public static int chooseSlot(ServerPlayerEntity p,net.minecraft.item.ItemStack book) {
        if(book==null||book.isEmpty())return -1;
        List<Integer> ready=new ArrayList<>();int selected=SpellbookData.getSelectedSlot(book);
        for(int slot=0;slot<SpellbookData.getSlotCount(book);slot++) {
            var scroll=SpellbookData.getScroll(book,slot);var spell=ScrollData.getSpell(scroll);
            if(spell==null||ArcaneAnalysis.isUnanalyzed(scroll)||spell.getElement()!=FormationElement.CURSE
                    ||spell.getRarity(ScrollData.getCastLevel(scroll))==SpellRarity.RED
                    ||SharedSpellCooldowns.getEffectiveCooldownEnd(p,scroll)>p.getWorld().getTime())continue;
            if(slot==selected)return slot;ready.add(slot);
        }
        return ready.isEmpty()?-1:ready.get(p.getRandom().nextInt(ready.size()));
    }
    public static void onJumpHit(ServerPlayerEntity p,LivingEntity target) {
        if(!FormUtils.isForm(p,FormIdentifiers.SPIDER_SALTICIDAE)||target.getWorld()!=p.getWorld()
                ||target==p||target.isRemoved()||target.isSpectator()
                ||net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils.isProtected(p,target)
                ||DomainManager.blocksTargeting(p,target))return;
        var book=SpellCastManager.getEquippedBook(p);int slot=chooseSlot(p,book);
        if(slot>=0){SpellCastManager.triggerSpiderCurse(p,slot,null,target);return;}
        List<Spell> white=SpellRegistry.all().stream().filter(s->s.getElement()==FormationElement.CURSE&&s.getRarity(1)==SpellRarity.WHITE).toList();
        if(!white.isEmpty())SpellCastManager.triggerSpiderCurse(p,-1,white.get(p.getRandom().nextInt(white.size())),target);
    }
}
