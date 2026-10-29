package net.ssca.neomagic;

import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.entity.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Real Minecraft damage, healing, native projectile/scroll/book casts, deferred payment and persistence. */
public final class NeoAllaySpellLinkProbe {
    private static int checks;
    private static final List<ProjectileEntity> projectiles=new ArrayList<>();
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-4,why+": "+a+" != "+b);}
    private static ServerPlayerEntity player(TestContext t,boolean fallen)throws Exception {
        var p=NeoFormCastingProbe.player(t,fallen?FormIdentifiers.FALLEN_ALLAY_SP:FormIdentifiers.ALLAY_SP,0);
        if(!fallen)p.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+p.getUuid());
        return p;
    }
    private static PigEntity pig(ServerPlayerEntity p,double x,double z) {
        var pig=EntityType.PIG.create(p.getServerWorld());pig.setAiDisabled(true);pig.setNoGravity(true);
        pig.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);pig.setHealth(200);
        pig.refreshPositionAndAngles(p.getX()+x,p.getEyeY()-pig.getHeight()*.5,p.getZ()+z,0,0);
        p.getServerWorld().getChunk(pig.getBlockPos());p.getServerWorld().spawnEntity(pig);return pig;
    }
    private static ServerPlayerEntity ally(TestContext t,ServerPlayerEntity p,double x,double z,boolean white)throws Exception {
        var other=player(t,false);other.refreshPositionAndAngles(p.getX()+x,p.getY(),p.getZ()+z,0,0);other.setHealth(5);
        if(white)p.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+other.getUuid());return other;
    }
    private static FormSpellStyle paid(ServerPlayerEntity p,String id,int level,double q,boolean direct) {
        var spell=SpellRegistry.get(id);var r=new NeoState.Experiment();r.blueprint=NeoLegacyCasting.descriptor(spell,level,0);r.spell=spell.getId().toString();
        r.form=NeoRuntime.form(p);r.profile=NeoRuntime.profile(p);r.dimension=p.getWorld().getRegistryKey().getValue().toString();r.result="released";r.normal=q;r.actual=q;r.paid=q;
        NeoRuntime.data(p).add(r);NeoRuntime.data(p).burden=q;NeoRuntime.data(p).recovery=NeoRules.recoveryDelay;NeoRuntime.data(p).lastPaymentTick=p.getServer().getTicks();
        var style=FormSpellStyle.plan(p,spell,level,0,0);check(style.pay(),"Accepted style payment");style.bindAllay(q,r,direct);return style;
    }
    private static NeoState.Experiment latest(ServerPlayerEntity p){return NeoRuntime.data(p).records.get(0);}
    private static void collide(ProjectileEntity p,LivingEntity victim)throws Exception {
        var m=ProjectileEntity.class.getDeclaredMethod("onCollision",HitResult.class);m.setAccessible(true);m.invoke(p,new EntityHitResult(victim));
    }
    private static SpellFireBoltEntity fire(ServerPlayerEntity p,FormSpellStyle style,float damage) {
        var fire=new SpellFireBoltEntity(p.getWorld(),p);fire.setDamage(damage);style.effect(()->p.getServerWorld().spawnEntity(fire));return fire;
    }
    private static ProjectileEntity markProjectile(ServerPlayerEntity owner) {
        return projectiles.stream().filter(p->p instanceof SpellCurseMarkEntity&&p.getOwner()==owner&&!p.isRemoved()).reduce((x,y)->y)
                .orElseThrow(()->new AssertionError("Native curse mark entity was not loaded at "+owner.getPos()));
    }
    private static ItemStack book(ServerPlayerEntity p) {
        var book=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);
        TrinketsApi.getTrinketComponent(p).orElseThrow().getInventory().get("moonlit").get("spellbook").setStack(0,book);return book;
    }
    private static NbtCompound store(ServerPlayerEntity p)throws Exception {
        var type=Class.forName(AllaySpellLink.class.getName()+"$Store");var get=type.getDeclaredMethod("get",net.minecraft.server.MinecraftServer.class);get.setAccessible(true);
        return ((net.minecraft.world.PersistentState)get.invoke(null,p.getServer())).writeNbt(new NbtCompound());
    }
    private static NeoRuntime.Active blueprint(ServerPlayerEntity p,Blueprint b) {
        var quote=NeoLegacyCasting.blueprintQuote(p,b);var s=new Session(b,quote);s.complete=true;s.paid=quote.actual();
        var r=new NeoState.Experiment();r.blueprint=b;r.form=NeoRuntime.form(p);r.profile=NeoRuntime.profile(p);r.dimension=p.getWorld().getRegistryKey().getValue().toString();r.actual=quote.actual();r.paid=quote.actual();
        var a=new NeoRuntime.Active(s,r,ItemStack.EMPTY,Hand.MAIN_HAND,new NeoEffects.Anchor(p.getPos(),null),new Random(1));
        a.style=FormSpellStyle.plan(p,b.carrier()==Carrier.PROJECTILE,b.range(),0,0,-1);a.style.bindAllay(quote.actual(),r,b.output()==Core.CURSE&&b.carrier()!=Carrier.AREA&&b.carrier()!=Carrier.SELF);
        NeoRuntime.data(p).add(r);NeoRuntime.data(p).burden=quote.actual();NeoRuntime.data(p).recovery=NeoRules.recoveryDelay;NeoRuntime.data(p).lastPaymentTick=p.getServer().getTicks();return a;
    }
    private static void explosionBatches(TestContext t)throws Exception {
        var p=player(t,false);p.setHealth(5);var style=paid(p,"explosion",1,20,false);
        var friend=pig(p,1,3);var first=pig(p,0,2);var second=pig(p,-2,4);p.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+friend.getUuid());
        ExplosionManager.beginCharge(p,p.getPos());style.effect(()->ExplosionManager.detonate(p,p.getPos(),8,null));
        var field=ExplosionManager.class.getDeclaredField("SEQUENCES");field.setAccessible(true);var map=(Map<?,?>)field.get(null);
        Object sequence=map.values().iterator().next();var pending=sequence.getClass().getDeclaredField("pendingTargets");pending.setAccessible(true);
        var damage=ExplosionManager.class.getDeclaredMethod("damageBatch",sequence.getClass());damage.setAccessible(true);
        pending.set(sequence,new ArrayList<>(List.of(friend)));damage.invoke(null,sequence);
        check(friend.getHealth()<200,"Explosion retains existing deliberate friendly fire");near(p.getHealth(),5,"Friendly explosion damage never feeds relay");near(NeoRuntime.data(p).burden,20,"Friendly-only batch has no surcharge");
        for(var enemy:List.of(first,second)){
            float hp=enemy.getHealth(),owner=p.getHealth();pending.set(sequence,new ArrayList<>(List.of(enemy)));damage.invoke(null,sequence);
            near(p.getHealth(),owner+(hp-enemy.getHealth())*.25,"Deferred explosion batch heals from its actual damage");near(NeoRuntime.data(p).burden,23,"All damage batches share one fifteen-percent surcharge");
        }
        map.values().remove(sequence);
    }
    private static void minimumHealing(TestContext t)throws Exception {
        var p=player(t,false);p.setHealth(5);
        // Keep this extra scenario inside the test's ticking chunks, away from the other players.
        var site=t.getAbsolutePos(new BlockPos(4,40,4)).toCenterPos();p.refreshPositionAndAngles(site.x,site.y,site.z,0,0);
        p.getCommandTags().remove(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+p.getUuid());
        var a=ally(t,p,2,0,true);var b=ally(t,p,-2,0,true);var c=ally(t,p,0,2,true);
        var style=paid(p,"fire_bolt",1,20,false);var enemy=pig(p,0,30);
        collide(fire(p,style,.1f),enemy);
        for(var member:List.of(p,a,b,c))near(member.getHealth(),5.25,"Tiny real projectile hit heals each wounded player at least 0.25, including self without a self whitelist entry");
        near(latest(p).allayHealing,1,"Minimum healing records actual total, rather than the smaller unmodified damage share");
        near(NeoRuntime.data(p).burden,23,"Minimum healing pays the same one-time surcharge");
        p.setHealth(19.875f);enemy.timeUntilRegen=0;collide(fire(p,style,.1f),enemy);
        near(p.getHealth(),20,"Minimum healing still caps at missing health");
        near(latest(p).allayHealing,1.875,"Partially capped minimum contributes only actual health restored");
        near(NeoRuntime.data(p).burden,23,"Repeated minimum healing does not repeat the surcharge");
        float before=a.getHealth();style.allay.damage(p,enemy,0);style.allay.damage(p,enemy,-1);style.allay.damage(p,enemy,Double.NaN);
        near(a.getHealth(),before,"Zero, negative and invalid damage cannot trigger the minimum");
        NbtCompound saved=style.allay.write();near(AllaySpellLink.Cast.read(saved).healMinimum,.25,"Minimum is retained in the cast snapshot");
        saved.remove("HealMinimum");near(AllaySpellLink.Cast.read(saved).healMinimum,.25,"Old projectile data adopts the minimum without losing its cast");
        saved.putDouble("HealMinimum",Double.NaN);check(AllaySpellLink.Cast.read(saved)==null,"Invalid persisted minimum is rejected");
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception {
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof ProjectileEntity p)projectiles.add(p);});
        var healer=player(t,false);healer.setHealth(5);
        var a=ally(t,healer,2,0,true);var b=ally(t,healer,-2,0,true);var edge=ally(t,healer,16,0,true);
        var far=ally(t,healer,16.01,0,true);var outsider=ally(t,healer,1,0,false);var full=ally(t,healer,0,2,true);full.setHealth(20);
        var pet=EntityType.WOLF.create(healer.getServerWorld());pet.setTamed(true);pet.setOwnerUuid(healer.getUuid());pet.setHealth(1);pet.setNoGravity(true);pet.refreshPositionAndAngles(healer.getX(),healer.getY(),healer.getZ()+1,0,0);healer.getServerWorld().spawnEntity(pet);
        var style=paid(healer,"fire_bolt",1,20,false);var enemy=pig(healer,0,30);collide(fire(healer,style,16),enemy);
        for(var member:List.of(healer,a,b,edge))near(member.getHealth(),6,"Sixteen damage yields four healing shared by four wounded players");
        near(far.getHealth(),5,"Outside spherical sixteen-block radius excluded");near(outsider.getHealth(),5,"Nonwhitelisted player excluded");near(full.getHealth(),20,"Full player takes no share");near(pet.getHealth(),1,"Pet excluded from player-only relay");
        near(NeoRuntime.data(healer).burden,23,"Exactly fifteen percent of twenty paid after first real healing");
        near(latest(healer).allayHealing,4,"Actual healing total recorded");near(latest(healer).allaySurcharge,3,"Extra payment recorded separately from failure quote");
        enemy.timeUntilRegen=0;collide(fire(healer,style,16),enemy);near(NeoRuntime.data(healer).burden,23,"A second hit shares healing without another surcharge");

        var healthy=player(t,false);var none=paid(healthy,"fire_bolt",1,20,false);collide(fire(healthy,none,8),pig(healthy,0,2));near(NeoRuntime.data(healthy).burden,20,"No wounded recipients costs no extra collapse");
        var lethal=player(t,false);lethal.setHealth(5);var lethalStyle=paid(lethal,"fire_bolt",1,20,false);var low=pig(lethal,0,2);low.setHealth(3);collide(fire(lethal,lethalStyle,100),low);
        near(lethal.getHealth(),5.75,"Overkill counts only three actual health lost");near(NeoRuntime.data(lethal).burden,23,"Lethal hit still charges only once");
        var shield=player(t,false);shield.setHealth(5);var shieldStyle=paid(shield,"fire_bolt",1,20,false);var armored=pig(shield,0,2);armored.setAbsorptionAmount(20);collide(fire(shield,shieldStyle,12),armored);
        near(shield.getHealth(),5,"Absorption-only hit creates no healing");near(NeoRuntime.data(shield).burden,20,"Absorption-only hit has no surcharge");
        var immune=player(t,false);immune.setHealth(5);immune.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);var immuneStyle=paid(immune,"fire_bolt",1,20,false);var god=pig(immune,0,2);god.setInvulnerable(true);collide(fire(immune,immuneStyle,12),god);
        near(immune.getHealth(),5,"Rejected damage cannot heal");near(NeoRuntime.data(immune).burden,20,"Rejected damage cannot charge");
        var capped=player(t,false);capped.setHealth(19.5f);var capStyle=paid(capped,"fire_bolt",1,20,false);collide(fire(capped,capStyle,12),pig(capped,0,2));near(capped.getHealth(),20,"Healing caps at recipient maximum");near(latest(capped).allayHealing,.5,"Actual capped healing measured");
        capStyle.allay.damage(capped,capped,100);near(capped.getHealth(),20,"Self damage never feeds relay");
        healthy.setHealth(5);var ordinary=pig(healthy,0,4);ordinary.damage(healthy.getDamageSources().playerAttack(healthy),8);near(healthy.getHealth(),5,"Ordinary melee never creates spell relay healing");
        explosionBatches(t);

        var savedOwner=player(t,false);savedOwner.setHealth(5);var savedStyle=paid(savedOwner,"fire_bolt",1,20,false);
        var first=fire(savedOwner,savedStyle,4);var pending=fire(savedOwner,savedStyle,4);var stale=pending.writeNbt(new NbtCompound());var savedTarget=pig(savedOwner,0,2);
        collide(first,savedTarget);pending.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        near(NeoRuntime.data(savedOwner).burden,23,"First flight pays one surcharge");
        var packed=store(savedOwner);var type=Class.forName(AllaySpellLink.class.getName()+"$Store");var read=type.getDeclaredMethod("read",NbtCompound.class);read.setAccessible(true);
        check(((net.minecraft.world.PersistentState)read.invoke(null,packed)).writeNbt(new NbtCompound()).equals(packed),"Payment and membership survive persistent codec roundtrip");

        // Native stand-alone scroll goes through the actual quote/binding/channel/release and projectile collision.
        var solo=player(t,false);var soloSite=t.getAbsolutePos(new BlockPos(5,65,5)).toCenterPos();solo.refreshPositionAndAngles(soloSite.x,soloSite.y,soloSite.z,0,0);solo.setHealth(5);var soloVictim=pig(solo,0,3);var scroll=ScrollData.create("fire_bolt");solo.getInventory().selectedSlot=2;solo.setStackInHand(Hand.MAIN_HAND,scroll);scroll.use(solo.getWorld(),solo,Hand.MAIN_HAND);
        var accepted=NeoLegacyCasting.active(solo);check(accepted!=null,"Actual scroll starts common channel");double quoted=accepted.session.quote.actual();
        var activeField=SpellChannelManager.class.getDeclaredField("ACTIVE");activeField.setAccessible(true);Object soloChannel=((Map<?,?>)activeField.get(null)).get(solo.getUuid());
        var sourceField=soloChannel.getClass().getDeclaredField("sourceValid");sourceField.setAccessible(true);var sourceValid=(java.util.function.BooleanSupplier)sourceField.get(soloChannel);
        t.waitAndRun(1,()->check(sourceValid.getAsBoolean(),"Scroll in ordinary hotbar slot remains valid beside the form's reserved healing wand"));

        // Actual equipped-book cone: two original hits, two legal extra enemies; only one may chain.
        var fallen=player(t,true);Vec3d scene=t.getAbsolutePos(new BlockPos(5,5,5)).toCenterPos();fallen.refreshPositionAndAngles(scene.x,scene.y,scene.z,0,0);
        var primaryA=pig(fallen,0,3);var primaryB=pig(fallen,.5,4);var secondaryA=pig(fallen,3.8,1);var secondaryB=pig(fallen,-3.8,1);
        var fallenBook=book(fallen);SpellbookData.setScroll(fallenBook,0,ScrollData.create("dread_whisper"));

        // Mark scene above the cone scene, inside the already-loaded chunk column.
        var marker=player(t,true);marker.refreshPositionAndAngles(scene.x,scene.y+20,scene.z,0,0);
        var marked=pig(marker,0,3);var nearest=pig(marker,2,3);var another=pig(marker,3,3);var outside=pig(marker,5.01,3);
        var markStyle=paid(marker,"curse_mark",3,20,true);

        var blueprintHealer=player(t,false);blueprintHealer.refreshPositionAndAngles(scene.x,scene.y+40,scene.z,0,0);blueprintHealer.setHealth(5);var bpVictim=pig(blueprintHealer,0,3);
        var bp=new Blueprint("relay",Core.THERMAL,Carrier.PROJECTILE,List.of(Control.CONSTRAIN),Core.FROST,1,0,8);
        var blueprintCast=blueprint(blueprintHealer,bp);
        check(NeoEffects.release(blueprintHealer,blueprintCast),"Actual blueprint projectile released");

        var alone=player(t,true);alone.refreshPositionAndAngles(scene.x,scene.y+140,scene.z,0,0);var one=pig(alone,0,3);var protectedMob=pig(alone,1,3);pig(alone,5.01,3);
        alone.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+protectedMob.getUuid());var aloneStyle=paid(alone,"curse_mark",1,20,true);
        var line=player(t,true);line.refreshPositionAndAngles(scene.x,scene.y+80,scene.z,0,0);var linePrimary=pig(line,0,3);var behind=pig(line,2,3);var visible=pig(line,-3,3);
        var wall=BlockPos.ofFloored(linePrimary.getEyePos()).east();for(int y=-1;y<=1;y++)line.getServerWorld().setBlockState(wall.up(y),net.minecraft.block.Blocks.STONE.getDefaultState());
        var lineStyle=paid(line,"curse_mark",1,20,true);
        var blueprintFallen=player(t,true);blueprintFallen.refreshPositionAndAngles(scene.x,scene.y+100,scene.z,0,0);var curseMain=pig(blueprintFallen,0,3);var curseExtra=pig(blueprintFallen,2,3);var curseThird=pig(blueprintFallen,3,3);
        var curseBlueprint=blueprint(blueprintFallen,new Blueprint("chain",Core.CURSE,Carrier.CONTACT,List.of(),Core.FROST,1,0,8));
        var mist=player(t,true);mist.refreshPositionAndAngles(scene.x,scene.y+120,scene.z,0,0);var mistTarget=pig(mist,0,2);var mistSpell=SpellRegistry.get("corrupt_mist");
        check(!mistSpell.supportsCurseChain(),"Self-centered persistent curse explicitly opts out");
        check(SpellChannelManager.start(mist,mistSpell,ScrollData.create("corrupt_mist"),1,true,77,20,40,()->true,v->mistSpell.cast(mist,0,true,1),cd->{}),"Actual corrupt mist begins its normal channel");

        t.waitAndRun(4,()->{try{
            var restored=new SpellFireBoltEntity(savedOwner.getWorld(),savedOwner);restored.readNbt(stale);check(savedOwner.getServerWorld().spawnEntity(restored),"Previously unloaded projectile reloads");
            check(((AllaySpellLink.ProjectileData)restored).ssca$allay().write().getBoolean("Charged"),"Authoritative ledger overrides stale unpaid projectile snapshot");
            savedTarget.timeUntilRegen=0;collide(restored,savedTarget);near(savedOwner.getHealth(),7,"Reloaded projectile still heals normally");near(NeoRuntime.data(savedOwner).burden,23,"Reload cannot charge the same cast twice");
            check(!store(savedOwner).contains(latest(savedOwner).id),"Last projectile destruction cleans its store entry");
            markStyle.effect(()->SpellRegistry.get("curse_mark").cast(marker,0,false,3));
            var bolt=markProjectile(marker);collide(bolt,marked);
            check(marked.hasStatusEffect(SscAddon.CURSE_MARK)&&nearest.hasStatusEffect(SscAddon.CURSE_MARK),"Native curse mark copied to closest extra target");
            check(nearest.getStatusEffect(SscAddon.CURSE_MARK).getDuration()==276,"Original fifteen-percent curse affinity retained on chained mark");
            check(!another.hasStatusEffect(SscAddon.CURSE_MARK)&&!outside.hasStatusEffect(SscAddon.CURSE_MARK),"No recursive or out-of-range chaining");
            near(NeoRuntime.data(marker).burden,23,"Mark adds exactly one fifteen-percent surcharge");check(latest(marker).fallenChains==1,"One chain recorded");
            check(fallen.getServerWorld().getEntitiesByClass(PigEntity.class,fallen.getBoundingBox().expand(6),e->e==primaryA||e==primaryB).size()==2,"Cone primaries visible to real entity queries");
            SpellCastManager.cast(fallen,0);check(NeoLegacyCasting.active(fallen)!=null,"Actual book cone begins preparation");
            aloneStyle.effect(()->SpellRegistry.get("curse_mark").cast(alone,0,true,1));var loneBolt=markProjectile(alone);collide(loneBolt,one);
            check(one.hasStatusEffect(SscAddon.CURSE_MARK)&&!protectedMob.hasStatusEffect(SscAddon.CURSE_MARK),"Original mark works while protected secondary is skipped");near(NeoRuntime.data(alone).burden,20,"No legal in-range secondary means no extra payment");
            check(!linePrimary.canSee(behind)&&linePrimary.canSee(visible),"Real block collision establishes blocked and visible chain candidates");
            lineStyle.effect(()->SpellRegistry.get("curse_mark").cast(line,0,true,1));var lineBolt=markProjectile(line);collide(lineBolt,linePrimary);
            check(!behind.hasStatusEffect(SscAddon.CURSE_MARK)&&visible.hasStatusEffect(SscAddon.CURSE_MARK),"Chain skips a closer enemy behind a wall");
            check(NeoEffects.release(blueprintFallen,curseBlueprint),"Actual direct curse blueprint releases");
            check(curseMain.hasStatusEffect(StatusEffects.WEAKNESS)&&curseExtra.hasStatusEffect(StatusEffects.WEAKNESS)&&!curseThird.hasStatusEffect(StatusEffects.WEAKNESS),"Blueprint direct curse copies damage/debuff to one extra target only");
            check(curseMain.getHealth()<200&&curseExtra.getHealth()<200&&curseBlueprint.record.fallenChains==1,"Blueprint chain deals its normal actual effect once");near(NeoRuntime.data(blueprintFallen).burden,curseBlueprint.session.quote.actual()*1.15,"Blueprint direct curse shares one surcharge");
        }catch(Exception e){throw new RuntimeException(e);}});
        t.waitAndRun(20,()->{
            check(soloVictim.getHealth()<200&&solo.getHealth()>5,"Actual scroll projectile damage relays healing: owner="+solo.getHealth()+", victim="+soloVictim.getHealth()+", result="+accepted.record.result+", bolts="+projectiles.stream().filter(p->p.getOwner()==solo).map(p->p.age+"/"+p.getPos()+"/removed="+p.isRemoved()).toList());near(NeoRuntime.data(solo).burden,quoted*1.15,"Actual common quote receives exactly fifteen percent additional collapse after impact");
            check(blueprintCast.record.damage>0&&blueprintCast.record.allayHealing>0,"Actual blueprint projectile relays real health damage");near(NeoRuntime.data(blueprintHealer).burden,blueprintCast.session.quote.actual()*1.15,"Blueprint surcharge uses actual mastery-adjusted Q");
        });
        t.waitAndRun(132,()->{
            check(primaryA.hasStatusEffect(StatusEffects.WEAKNESS)&&primaryB.hasStatusEffect(StatusEffects.WEAKNESS),"Native cone original targets retain their effects");
            int extras=(secondaryA.hasStatusEffect(StatusEffects.WEAKNESS)?1:0)+(secondaryB.hasStatusEffect(StatusEffects.WEAKNESS)?1:0);
            check(extras==1&&latest(fallen).fallenChains==1,"Multi-target cone chains to exactly one extra enemy for entire cast");
            near(latest(fallen).allaySurcharge,latest(fallen).actual*.15,"Native book cone pays only one extra fifteen percent");
            check(!SpellChannelManager.isCasting(fallen),"No extra channel or recursive cast is started");
            check(mistTarget.hasStatusEffect(StatusEffects.POISON),"Excluded corrupt mist still applies its original periodic poison");check(latest(mist).fallenChains==0&&latest(mist).allaySurcharge==0,"Persistent self-centered curse has neither chain nor surcharge");
            try{minimumHealing(t);}catch(Exception e){throw new RuntimeException(e);}
            System.out.println("NEO_ALLAY_LINK_PASS checks="+checks+" (actual damage, minimum per player, self/whitelist sharing, caps, absorption, overkill, native scroll/book, curse copy, one chain, blueprint, deferred cost and persistence)");t.complete();
        });
    }
}
