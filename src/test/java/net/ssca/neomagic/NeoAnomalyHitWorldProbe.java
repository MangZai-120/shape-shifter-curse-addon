package net.ssca.neomagic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyContext;
import net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits;
import net.jackcooper.shapeShifterCurseAddon.entity.SpellFrostSpikeEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import static net.ssca.neomagic.NeoModel.*;

/** Tests spell identities and real hit transport without choosing any gameplay anomaly amounts. */
public final class NeoAnomalyHitWorldProbe {
    private record Hit(UUID target, SpellAnomalyContext context) {}
    private static final List<Hit> hits = new ArrayList<>();
    private static final List<PigEntity> targets = new ArrayList<>();
    private static int checks;

    private static void check(boolean value,String why) {
        checks++;
        if(!value)throw new AssertionError(why);
    }

    public static void start(TestContext t) {
        t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});
    }

    private static PigEntity pig(ServerPlayerEntity p,double x,double z) {
        var mob=EntityType.PIG.create(p.getServerWorld());
        mob.setAiDisabled(true);mob.setNoGravity(true);
        mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);mob.setHealth(200);
        mob.refreshPositionAndAngles(p.getX()+x,p.getEyeY()-mob.getHeight()*.5,p.getZ()+z,0,0);
        check(p.getServerWorld().spawnEntity(mob),"Real target spawns");targets.add(mob);return mob;
    }

    private static void clear() {
        targets.forEach(PigEntity::discard);targets.clear();hits.clear();
    }

    private static void collide(ProjectileEntity projectile,LivingEntity target)throws Exception {
        String name=net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver().mapMethodName(
                "intermediary","net.minecraft.class_1676","method_7488","(Lnet/minecraft/class_239;)V");
        var collision=ProjectileEntity.class.getDeclaredMethod(name,HitResult.class);
        collision.setAccessible(true);collision.invoke(projectile,new EntityHitResult(target));
    }

    private static void cast(ServerPlayerEntity p,String id,float power) {
        SpellRegistry.get(id).castAtTarget(p,power,true,1,ItemStack.EMPTY,null);
        check(SpellAnomalyHits.current()==null,"Named cast restores the anomaly scope");
    }

    private static List<ProjectileEntity> missiles(ServerPlayerEntity p,String id) {
        return p.getServerWorld().getEntitiesByClass(ProjectileEntity.class,p.getBoundingBox().expand(5),e->{
            var context=((SpellAnomalyHits.ProjectileData)e).ssca$anomaly();
            return e.getOwner()==p&&context!=null&&context.spellId().getPath().equals(id);
        });
    }

    private static long count(String spell,LivingEntity target) {
        return hits.stream().filter(h->h.target.equals(target.getUuid())&&h.context.spellId().getPath().equals(spell)).count();
    }

    private static void run(TestContext t)throws Exception {
        var oldResolver=SpellAnomalyHits.amountResolver();
        var p=NeoFormCastingProbe.player(t,new Identifier("shape-shifter-curse","original_shifter"),0);
        SpellAnomalyHits.amountResolver((spell,level)->switch(spell.getId().getPath()){
            case "frost_spike"->7;case "ice_barrage"->11;default->0;
        });
        try(var listening=SpellAnomalyHits.register((target,context)->hits.add(new Hit(target.getUuid(),context)))) {
            var spikeTarget=pig(p,0,2);cast(p,"frost_spike",6);
            var spike=missiles(p,"frost_spike").get(0);
            var snapshot=((SpellAnomalyHits.ProjectileData)spike).ssca$anomaly();
            check(snapshot.amount()==7&&snapshot.element().equals("ice"),"Launch snapshots configured amount and element");
            check(snapshot.casterUUID().equals(p.getUuid()),"Launch snapshots original caster");
            var nbt=spike.writeNbt(new NbtCompound());spike.discard();
            SpellAnomalyHits.amountResolver((spell,level)->13);
            var restored=new SpellFrostSpikeEntity(p.getWorld(),p);restored.readNbt(nbt);
            check(snapshot.equals(((SpellAnomalyHits.ProjectileData)restored).ssca$anomaly()),"Real projectile NBT preserves original spell and amount across reconfiguration");
            collide(restored,spikeTarget);
            check(count("frost_spike",spikeTarget)==1&&spikeTarget.getHealth()<200,"Restored projectile produces exactly one real damage hit");
            check(hits.get(0).context.amount()==7,"Delayed impact never reads the new amount resolver");
            var invalid=snapshot.write();invalid.putDouble("Amount",Double.NaN);
            check(SpellAnomalyContext.read(invalid)==null,"Malformed stored amounts cannot create an anomaly snapshot");

            clear();var burstTarget=pig(p,0,2);cast(p,"ice_barrage",4);
            var barrage=missiles(p,"ice_barrage");
            check(barrage.size()==3,"Actual barrage creates three labelled projectiles");
            check(barrage.stream().allMatch(e->e instanceof SpellFrostSpikeEntity),"Barrage and single spike really share the same entity type");
            collide(barrage.get(0),burstTarget);
            check(count("ice_barrage",burstTarget)==1&&count("frost_spike",burstTarget)==0,"Shared projectile type retains distinct named spell identity");
            barrage.forEach(ProjectileEntity::discard);

            clear();var immune=pig(p,0,2);immune.setInvulnerable(true);
            // The GameTest mock overrides creative checks; use a real mob source for vanilla invulnerability.
            var ordinarySource=pig(p,0,-2);
            check(SpellHitHelper.hitRaw(ordinarySource,immune,6,0,snapshot)==SpellHitHelper.HitResult.IMMUNE&&hits.isEmpty(),"Rejected damage emits no anomaly event");
            immune.setInvulnerable(false);p.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+immune.getUuid());
            check(SpellHitHelper.hitRaw(p,immune,6,0,snapshot)==SpellHitHelper.HitResult.PROTECTED&&hits.isEmpty(),"Existing whitelist protection emits no anomaly event");
            p.removeScoreboardTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+immune.getUuid());

            SpellAnomalyHits.amountResolver((spell,level)->0);
            clear();var a=pig(p,-1,2);var b=pig(p,1,2);cast(p,"flame_nova",4);
            check(count("flame_nova",a)==1&&count("flame_nova",b)==1,"Zero-amount fire AOE still reports each actual target once");
            check(hits.size()==2&&hits.get(0).context.castUUID().equals(hits.get(1).context.castUUID()),"AOE targets share one cast snapshot");
            hits.clear();a.timeUntilRegen=0;b.timeUntilRegen=0;cast(p,"void_erosion",0);
            check(count("void_erosion",a)==1&&count("void_erosion",b)==1&&hits.size()==2,"Two successful debuffs generate one zero-amount event per target");

            hits.clear();cast(p,"corrupt_mist",0);
            var mistType=Class.forName("net.jackcooper.shapeShifterCurseAddon.ability.CorruptMistManager");
            var mists=mistType.getDeclaredField("MISTS");mists.setAccessible(true);
            Object mist=((List<?>)mists.get(null)).get(0);
            var pulse=mistType.getDeclaredMethod("pulse",mist.getClass());pulse.setAccessible(true);pulse.invoke(null,mist);
            check(count("corrupt_mist",a)==1&&count("corrupt_mist",b)==1,"Delayed mist pulses retain their spell snapshot after cast scope exits");

            clear();a=pig(p,-1,2);b=pig(p,1,2);var explosion=SpellAnomalyHits.create(p,SpellRegistry.get("explosion"),1);
            ExplosionManager.beginCharge(p,p.getPos());
            SpellAnomalyHits.with(explosion,()->ExplosionManager.detonate(p,p.getPos(),4,null));
            var sequences=ExplosionManager.class.getDeclaredField("SEQUENCES");sequences.setAccessible(true);
            Object sequence=((java.util.Map<?,?>)sequences.get(null)).values().iterator().next();
            var pending=sequence.getClass().getDeclaredField("pendingTargets");pending.setAccessible(true);pending.set(sequence,new ArrayList<>(List.of(a,b)));
            var batch=ExplosionManager.class.getDeclaredMethod("damageBatch",sequence.getClass());batch.setAccessible(true);batch.invoke(null,sequence);
            check(count("explosion",a)==1&&count("explosion",b)==1,"Deferred explosion batches report every damaged target from their release snapshot");

            clear();a=pig(p,0,2);p.setPitch(0);p.setYaw(0);
            var blueprint=new Blueprint("probe",Core.THERMAL,Carrier.CONTACT,List.of(),Core.FROST,1,0,4);
            var quote=NeoRules.quote(blueprint,0);var session=new Session(blueprint,quote);session.complete=true;session.paid=quote.actual();
            var record=new NeoState.Experiment();record.blueprint=blueprint;record.dimension=p.getWorld().getRegistryKey().getValue().toString();
            var active=new NeoRuntime.Active(session,record,ItemStack.EMPTY,Hand.MAIN_HAND,new NeoEffects.Anchor(a.getPos(),a.getUuid()),new Random(1));
            check(NeoEffects.release(p,active),"Actual hostile blueprint releases");
            check(hits.size()==1&&hits.get(0).context.spellId().getNamespace().equals("ssca_neomagic")&&hits.get(0).context.element().equals("fire"),"Blueprint hostile hit retains final-output identity and zero default amount");
            check(record.damage>0&&hits.get(0).context.amount()==0,"Original blueprint damage is measured before its anomaly event");

            var outer=snapshot;
            SpellAnomalyHits.with(outer,()->{
                try{SpellAnomalyHits.with(explosion,(Runnable)()->{throw new IllegalStateException("probe");});}
                catch(IllegalStateException expected){check(SpellAnomalyHits.current()==outer,"Nested failing effect restores the outer spell scope");}
            });
            check(SpellAnomalyHits.current()==null,"Exception unwinding never leaks spell identity into unrelated damage");
        } finally {
            clear();SpellAnomalyHits.amountResolver(oldResolver);
        }
        System.out.println("ANOMALY_HIT_WORLD_PASS: "+checks+" checks, real projectile NBT/identity, AOE, debuff, deferred mist/explosion and blueprint hits; gameplay amounts remain unconfigured.");
        t.complete();
    }
}
