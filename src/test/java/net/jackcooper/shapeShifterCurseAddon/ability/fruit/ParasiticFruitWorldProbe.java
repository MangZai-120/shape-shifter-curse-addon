package net.jackcooper.shapeShifterCurseAddon.ability.fruit;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.ParasiticAbsorptionManager;
import net.jackcooper.shapeShifterCurseAddon.balance.*;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.jackcooper.shapeShifterCurseAddon.entity.ParasiticSeedProjectile;
import net.jackcooper.shapeShifterCurseAddon.power.ParasiticFruitSeedPower;
import net.jackcooper.shapeShifterCurseAddon.state.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import java.util.*;
import java.nio.file.*;

/** Native game world, real effect mixins, CCA persistence, powers and projectile/field callbacks. */
public final class ParasiticFruitWorldProbe {
    private static int checks;
    private static void check(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    private static void stage(TestContext t,long tick,Runnable action) { t.runAtTick(tick,action); }
    private static ServerPlayerEntity player(TestContext t) throws Exception {
        var p=t.createMockCreativeServerPlayerInWorld();p.setNoGravity(true);p.getAbilities().invulnerable=true;
        FormUtils._setForm(p,RegPlayerForms.getPlayerForm(FormIdentifiers.BAT_PARASITIC_FRUIT));
        var field=BalanceIntegration.class.getDeclaredField("server");field.setAccessible(true);
        var state=(ServerBalanceState)field.get(null);state.buildSendSequence(p.getUuid(),"{}");state.playerAcked(p.getUuid(),state.revision());
        PowerUtils.setResourceValueAndSync(p,FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY,10);
        check(ParasiticFruitSeedPower.get(p)!=null && ParasiticFruitAbility.allowed(p),"Native form grants usable seed power");
        return p;
    }
    private static LivingEntity host(TestContext t,int x) {
        var entity=EntityType.COW.create(t.getWorld());entity.setNoGravity(true);entity.setInvulnerable(true);
        entity.refreshPositionAndAngles(x,90,0,0,0);t.getWorld().spawnEntity(entity);return entity;
    }
    private static void effect(LivingEntity host,StatusEffect effect,int duration,int level) {
        host.addStatusEffect(new StatusEffectInstance(effect,duration,level,false,false));
    }
    private static int amp(LivingEntity host,StatusEffect effect) {
        var value=host.getStatusEffect(effect);return value==null?-1:value.getAmplifier();
    }
    private static int energy(ServerPlayerEntity player) {return PowerUtils.getResourceValue(player,FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY);}
    private static List<ParasiticSeedProjectile> projectiles(TestContext t,ServerPlayerEntity caster) {
        return t.getWorld().getEntitiesByClass(ParasiticSeedProjectile.class,new Box(-100,0,-100,100,200,100),e -> e.getOwner()==caster);
    }
    public static void start(TestContext t) { stage(t,70,()-> {try {
        if(io.github.apace100.apoli.Apoli.config==null)io.github.apace100.apoli.Apoli.config=new io.github.apace100.apoli.util.ApoliConfig();
        FruitLayerLedgerTest.main(new String[0]);
        var a=player(t);var b=player(t);a.refreshPositionAndAngles(0,100,0,0,0);b.refreshPositionAndAngles(20,100,0,0,0);
        var overlay=host(t,50);
        effect(overlay,StatusEffects.SLOWNESS,300,0);
        FruitStatusOverlay.pulse(overlay,"vine",3,()->effect(overlay,StatusEffects.SLOWNESS,60,2));
        check(amp(overlay,StatusEffects.SLOWNESS)==2,"Fruit upgrades weaker external potion");
        FruitStatusOverlay.update(overlay,Map.of("vine",1));
        check(amp(overlay,StatusEffects.SLOWNESS)==0,"Tier drops immediately with surviving layers");
        FruitStatusOverlay.clear(overlay);
        check(amp(overlay,StatusEffects.SLOWNESS)==0&&overlay.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==300,"External weaker potion survives ownership removal");
        overlay.clearStatusEffects();
        FruitStatusOverlay.pulse(overlay,"vine",3,()->effect(overlay,StatusEffects.SLOWNESS,60,2));
        effect(overlay,StatusEffects.SLOWNESS,400,0);
        FruitStatusOverlay.clear(overlay);
        check(amp(overlay,StatusEffects.SLOWNESS)==0&&overlay.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==400,"Weaker external effect added while fruit is active is retained");
        overlay.clearStatusEffects();effect(overlay,StatusEffects.SLOWNESS,-1,4);
        FruitStatusOverlay.pulse(overlay,"vine",1,()->effect(overlay,StatusEffects.SLOWNESS,60,0));
        FruitStatusOverlay.clear(overlay);
        check(amp(overlay,StatusEffects.SLOWNESS)==4&&overlay.getStatusEffect(StatusEffects.SLOWNESS).isInfinite(),"Stronger infinite external potion remains unchanged");
        overlay.clearStatusEffects();
        FruitStatusOverlay.pulse(overlay,"vine",1,()->effect(overlay,StatusEffects.SLOWNESS,60,0));
        FruitStatusOverlay.pulse(overlay,"bitter",1,()->effect(overlay,StatusEffects.WEAKNESS,80,0));
        check(amp(overlay,StatusEffects.SLOWNESS)==0&&amp(overlay,StatusEffects.WEAKNESS)==0,"Different fruit statuses coexist");
        FruitStatusOverlay.update(overlay,Map.of("bitter",1));
        check(!overlay.hasStatusEffect(StatusEffects.SLOWNESS)&&overlay.hasStatusEffect(StatusEffects.WEAKNESS),"Removing one kind preserves another");
        overlay.clearStatusEffects();FruitStatusOverlay.clear(overlay);
        check(!overlay.hasStatusEffect(StatusEffects.WEAKNESS),"Milk/purification cannot resurrect an old effect");
        effect(overlay,StatusEffects.SLOWNESS,300,0);effect(overlay,StatusEffects.SLOWNESS,10,3);
        FruitStatusOverlay.pulse(overlay,"vine",2,()->effect(overlay,StatusEffects.SLOWNESS,80,1));
        stage(t,82,()->{
            FruitStatusOverlay.clear(overlay);
            check(amp(overlay,StatusEffects.SLOWNESS)==0&&overlay.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==288,
                    "Expired stronger baseline promotes and ages its external hidden effect");
        });
        ParasiticAbsorptionManager.addAbsorption(overlay,1,1);float hearts=overlay.getAbsorptionAmount();
        FruitStatusOverlay.pulse(overlay,"support",1,()->ParasiticAbsorptionManager.addAbsorption(overlay,0,1));
        FruitStatusOverlay.clear(overlay);
        check(hearts>0&&overlay.getAbsorptionAmount()>=hearts,"Already granted yellow hearts are never clawed back");
        // Selection is the only persisted field. Native player copy exercises CCA's respawn strategy.
        var choice=RegParasiticFruitStateComponent.STATE.get(a);choice.selection(FruitSelection.BITTER);choice.charge(true,25);
        var save=new NbtCompound();a.writeNbt(save);var loaded=t.createMockCreativeServerPlayerInWorld();loaded.readNbt(save);
        check(RegParasiticFruitStateComponent.STATE.get(loaded).selection()==FruitSelection.BITTER
                &&!RegParasiticFruitStateComponent.STATE.get(loaded).charging(),"Full player save restores choice, never partial charge");
        var respawn=t.createMockCreativeServerPlayerInWorld();respawn.copyFrom(a,false);
        check(RegParasiticFruitStateComponent.STATE.get(respawn).selection()==FruitSelection.BITTER,"Death respawn preserves choice via real CCA copy");
        choice.charge(false,0);
        var seed=new ParasiticSeedProjectile(t.getWorld(),a);seed.setSelection(FruitSelection.SOUR);seed.setTwinPod(true);
        var seedNbt=new NbtCompound();seed.writeCustomDataToNbt(seedNbt);var restored=new ParasiticSeedProjectile(t.getWorld(),a);restored.readCustomDataFromNbt(seedNbt);
        check(restored.getSelection()==FruitSelection.SOUR&&seedNbt.getBoolean("TwinPod"),"Projectile snapshot and Twin Pod survive NBT");
        var mixed=host(t,40);var pa=ParasiticFruitSeedPower.get(a);var pb=ParasiticFruitSeedPower.get(b);
        pa.plantSeed(a,mixed,FruitSelection.VINE);pb.plantSeed(b,mixed,FruitSelection.BITTER);pa.plantSeed(a,mixed,FruitSelection.BITTER);
        // A second isolated host makes independent lifetime changes observable without gameplay time shortcuts.
        var timed=host(t,45);pa.plantSeed(a,timed,35);
        ParasiticFruitAbility.start(a);
        check(choice.charging(),"Start begins a free authoritative charge");
        stage(t,79,()->{
            ParasiticFruitAbility.release(a);
            check(energy(a)==10&&projectiles(t,a).isEmpty(),"Nine-tick release costs nothing and emits nothing");
            check(SkillCooldowns.ready(a,ParasiticFruitAbility.SKILL_ID),"Short release does not start cooldown");
            ParasiticFruitAbility.start(a);
        });
        stage(t,89,()->{
            ParasiticFruitAbility.release(a);ParasiticFruitAbility.release(a);
            var shots=projectiles(t,a);
            check(energy(a)==9&&shots.size()==1,"Minimum charge emits and charges exactly once despite duplicate release");
            check(shots.get(0).getSelection()==FruitSelection.BITTER,"Throw snapshots selected fruit");
            choice.selection(FruitSelection.SOUR);
            check(shots.get(0).getSelection()==FruitSelection.BITTER,"Later choice cannot change an airborne seed");
            shots.forEach(e->e.discard());
            check(!SkillCooldowns.ready(a,ParasiticFruitAbility.SKILL_ID),"Successful release starts cooldown");
        });
        stage(t,94,()->{
            check(amp(mixed,StatusEffects.SLOWNESS)==1&&amp(mixed,StatusEffects.WEAKNESS)==2,"Native mixed fruit tiers include NPC bonus once");
            pb.plantSeed(b,mixed,FruitSelection.ROTTEN);
            check(!mixed.hasStatusEffect(StatusEffects.SLOWNESS)&&amp(mixed,StatusEffects.WEAKNESS)==2,"Fourth layer immediately removes evicted kind while retaining both bitter layers");
            var layers=ParasiticFruitLayers.layers(t.getWorld(),mixed.getUuid());
            check(layers.size()==3&&layers.get(0).fruit()==FruitSelection.BITTER&&layers.get(2).fruit()==FruitSelection.ROTTEN,"Native world uses the shared FIFO ledger");
        });
        stage(t,106,()->{
            check(ParasiticFruitLayers.layers(t.getWorld(),timed.getUuid()).isEmpty(),"Custom layer duration expires independently");
        });
        stage(t,116,()->{
            check(mixed.hasStatusEffect(StatusEffects.POISON)&&mixed.hasStatusEffect(StatusEffects.WEAKNESS),"Rooted fourth fruit coexists with existing fruit kind");
            ParasiticFruitLayers.removeOwner(a);
            check(ParasiticFruitLayers.layers(t.getWorld(),mixed.getUuid()).size()==2&&amp(mixed,StatusEffects.WEAKNESS)==1,"One caster leaving preserves other caster and lowers same-kind tier");
            SkillCooldowns.reset(a,ParasiticFruitAbility.SKILL_ID);
            ParasiticFruitAbility.start(a);a.addStatusEffect(new StatusEffectInstance(SscAddon.PURIFIED,20));
        });
        stage(t,127,()->{
            ParasiticFruitAbility.release(a);
            check(projectiles(t,a).isEmpty()&&energy(a)==9&&!choice.charging(),"Purification cancels incomplete charge without cost");
            a.removeStatusEffect(SscAddon.PURIFIED);choice.selection(FruitSelection.ROTTEN);
            FormUtils._setForm(a,RegPlayerForms.ORIGINAL_BEFORE_ENABLE);
        });
        stage(t,129,()->{
            check(choice.selection()==FruitSelection.AUTO,"Changing form resets selection");
            FormUtils._setForm(a,RegPlayerForms.getPlayerForm(FormIdentifiers.BAT_PARASITIC_FRUIT));
        });
        stage(t,132,()->{
            check(choice.selection()==FruitSelection.AUTO,"Returning to fruit bat keeps reset Automatic");
            PowerUtils.setResourceValueAndSync(a,FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY,10);
            SkillCooldowns.reset(a,ParasiticFruitAbility.SKILL_ID);a.setVelocity(0,0,0);
            ParasiticFruitAbility.start(a);
            // Twin Pod uses the real accessory inventory and normal release cost/cooldown path.
            var slots=dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(b).orElseThrow().getInventory();
            var slot=slots.values().stream().flatMap(m->m.values().stream()).filter(i->i.size()>0).findFirst().orElseThrow();
            slot.setStack(0,new net.minecraft.item.ItemStack(SscAddon.TWIN_POD));
            check(net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils.isWearing(b,SscAddon.TWIN_POD),"Twin Pod equipped through native accessory abstraction");
            SkillCooldowns.reset(b,ParasiticFruitAbility.SKILL_ID);ParasiticFruitAbility.start(b);
        });
        stage(t,142,()->{
            ParasiticFruitAbility.release(b);
            check(energy(b)==8&&projectiles(t,b).size()==1,"Twin Pod costs two seeds on successful release");
            projectiles(t,b).forEach(e->e.discard());
            var support=host(t,60);support.setHealth(2);
            // Mark this host as friendly to both casters, keeping a single support pulse across kinds/owners.
            a.addCommandTag(net.jackcooper.shapeShifterCurseAddon.ability.AllaySPGroupHeal.WHITELIST_TAG_PREFIX+support.getUuidAsString());
            b.addCommandTag(net.jackcooper.shapeShifterCurseAddon.ability.AllaySPGroupHeal.WHITELIST_TAG_PREFIX+support.getUuidAsString());
            pa.plantSeed(a,support,FruitSelection.VINE);pb.plantSeed(b,support,FruitSelection.BITTER);pa.plantSeed(a,support,FruitSelection.SOUR);
            stage(t,164,()->{
                check(support.getHealth()==4,"Three friendly layers across casters heal once, without multiplying by layer count");
                check(!SkillCooldowns.ready(b,ParasiticFruitAbility.SKILL_ID),"Twin Pod cooldown lasts longer than one second");
            });
        });
        stage(t,169,()->{
            check(choice.charging()&&projectiles(t,a).isEmpty(),"Full charge waits indefinitely for release");
            ParasiticFruitAbility.release(a);
            var shots=projectiles(t,a);check(energy(a)==9&&shots.size()==1,"Held full charge still throws only once");
            check(Math.abs(shots.get(0).getVelocity().length()-1.4)<0.03,"Full charge keeps the original launch speed");
            shots.forEach(e->e.discard());
        });
        stage(t,190,()->{
            check(SkillCooldowns.ready(b,ParasiticFruitAbility.SKILL_ID),"Twin Pod cooldown ends after two seconds");
            PowerUtils.setResourceValueAndSync(a,FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY,0);
            ParasiticFruitAbility.start(a);
            var stateB=RegParasiticFruitStateComponent.STATE.get(b);stateB.selection(FruitSelection.SOUR);
            ParasiticFruitAbility.start(b);
            b.teleport(b.getServer().getWorld(net.minecraft.world.World.NETHER),0,100,0,0,0);
        });
        stage(t,201,()->{
            ParasiticFruitAbility.release(a);
            check(energy(a)==0&&projectiles(t,a).isEmpty()&&SkillCooldowns.ready(a,ParasiticFruitAbility.SKILL_ID),"Insufficient seed energy does not create a projectile or cooldown");
            ParasiticFruitAbility.release(b);
            check(!RegParasiticFruitStateComponent.STATE.get(b).charging()&&energy(b)==8,
                    "Changing dimension cancels a charge without cost");
            check(RegParasiticFruitStateComponent.STATE.get(b).selection()==FruitSelection.SOUR,"Dimension change preserves selection");
            b.teleport(t.getWorld(),20,100,0,0,0);
        });
        stage(t,204,()->{
            // Hit resolution executes the actual projectile callback, then the ground field callback.
            try {
                var hit=ParasiticSeedProjectile.class.getDeclaredMethod("onCollision",net.minecraft.util.hit.HitResult.class);hit.setAccessible(true);
                var enemy=host(t,70);enemy.setSprinting(true);
                var auto=new ParasiticSeedProjectile(t.getWorld(),a);auto.setSelection(FruitSelection.AUTO);
                hit.invoke(auto,new net.minecraft.util.hit.EntityHitResult(enemy));enemy.setSprinting(false);
                check(ParasiticFruitLayers.layers(t.getWorld(),enemy.getUuid()).get(0).fruit()==FruitSelection.VINE,"Automatic resolves against the host at impact");
                var fieldSeed=new ParasiticSeedProjectile(t.getWorld(),a);fieldSeed.setSelection(FruitSelection.SOUR);
                var point=new net.minecraft.util.math.Vec3d(80,90,0);
                hit.invoke(fieldSeed,new net.minecraft.util.hit.BlockHitResult(point,net.minecraft.util.math.Direction.UP,net.minecraft.util.math.BlockPos.ofFloored(point),false));
                choice.selection(FruitSelection.ROTTEN);
                var picker=host(t,80);
                stage(t,207,()->check(ParasiticFruitLayers.layers(t.getWorld(),picker.getUuid()).stream().anyMatch(l->l.fruit()==FruitSelection.SOUR),
                        "Ground field retains the thrown selection despite later mode changes"));
            } catch(Exception e){throw new RuntimeException(e);}
        });
        stage(t,210,()->{
            try { Files.writeString(Path.of("fruit-world-result.json"),"{\"passed\":true,\"checks\":"+checks+"}"); }
            catch(Exception e){throw new RuntimeException(e);}
            System.out.println("PARASITIC_FRUIT_WORLD_PASS checks="+checks);t.complete();
        });
    } catch(Exception e) { throw new RuntimeException(e); } }); }
}
