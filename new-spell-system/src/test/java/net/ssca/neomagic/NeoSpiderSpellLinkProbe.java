package net.ssca.neomagic;

import dev.emi.trinkets.api.TrinketsApi;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.JumpKillManager;
import net.jackcooper.shapeShifterCurseAddon.block.*;
import net.jackcooper.shapeShifterCurseAddon.entity.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;
import java.util.*;

/** Real server-world casting, native jump damage, projectile callbacks and persisted snapshots. */
public final class NeoSpiderSpellLinkProbe {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-5,why+": "+a+" != "+b);}
    private static ServerPlayerEntity player(TestContext t,boolean moon,double energy)throws Exception {
        var p=NeoFormCastingProbe.player(t,moon?FormIdentifiers.SPIDER_MOON_WEAVER:FormIdentifiers.SPIDER_SALTICIDAE,energy);
        if(moon){ManaUtils.setManaTypeID(p,new net.minecraft.util.Identifier("shape-shifter-curse","web_resource"));ManaUtils.setPlayerMana(p,energy);}
        return p;
    }
    private static PigEntity pig(ServerPlayerEntity p) {
        var pig=EntityType.PIG.create(p.getServerWorld());pig.setAiDisabled(true);pig.setNoGravity(true);
        pig.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);pig.setHealth(200);
        pig.refreshPositionAndAngles(p.getX(),p.getY(),p.getZ()+1,0,0);p.getServerWorld().spawnEntity(pig);return pig;
    }
    private static ItemStack book(ServerPlayerEntity p) {
        var b=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setLevel(b,3);
        TrinketsApi.getTrinketComponent(p).orElseThrow().getInventory().get("moonlit").get("spellbook").setStack(0,b);
        check(SpellCastManager.getEquippedBook(p)==b,"Real book equipment");return b;
    }
    private static BlockHitResult floor(ServerPlayerEntity p,int offset) {
        BlockPos floor=BlockPos.ofFloored(p.getPos()).add(offset,-1,0);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            p.getServerWorld().setBlockState(floor.add(x,0,z),Blocks.STONE.getDefaultState());
            p.getServerWorld().setBlockState(floor.add(x,1,z),Blocks.AIR.getDefaultState());
        }
        return new BlockHitResult(Vec3d.ofBottomCenter(floor.up()),Direction.UP,floor,false);
    }
    private static int webs(ServerPlayerEntity p,BlockHitResult floor) {
        int count=0;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(p.getWorld().getBlockState(floor.getBlockPos().up().add(x,0,z)).isOf(RegAddonBlocks.WEB_MEMBRANE))count++;
        return count;
    }
    private static void collide(ProjectileEntity e,HitResult hit)throws Exception {
        var m=ProjectileEntity.class.getDeclaredMethod("onCollision",HitResult.class);m.setAccessible(true);m.invoke(e,hit);
    }
    private static void jump(ServerPlayerEntity p,LivingEntity victim)throws Exception {
        var m=JumpKillManager.class.getDeclaredMethod("onHit",ServerPlayerEntity.class,net.minecraft.server.world.ServerWorld.class,LivingEntity.class);
        m.setAccessible(true);m.invoke(null,p,p.getServerWorld(),victim);
    }
    private static NeoState.Experiment latest(ServerPlayerEntity p){return NeoRuntime.data(p).records.get(0);}
    private static NbtCompound ledger(ServerPlayerEntity p)throws Exception {
        var type=Class.forName(SpiderSpellLink.class.getName()+"$WeaveLedger");
        var get=type.getDeclaredMethod("get",net.minecraft.server.MinecraftServer.class);get.setAccessible(true);
        return ((net.minecraft.world.PersistentState)get.invoke(null,p.getServer())).writeNbt(new NbtCompound());
    }
    private static void nextRoll(ServerPlayerEntity p,boolean success) {
        for(long seed=0;seed<100000;seed++){p.getRandom().setSeed(seed);if((p.getRandom().nextDouble()<.15)==success){p.getRandom().setSeed(seed);return;}}
        throw new AssertionError("No reproducible random seed");
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception {
        for(int energy:new int[]{0,7,8,24}) {
            var p=player(t,true,energy);var spell=SpellRegistry.get("ice_barrage");
            check(SpellChannelManager.start(p,spell,ScrollData.create("ice_barrage"),1,false,1,20,40,()->true,v->{},cd->{}),"Barrage accepted with energy "+energy);
            near(ManaUtils.getPlayerMana(p),energy>=8?energy-8:energy,"One eight-energy payment per cast; insufficient falls back");
            SpellChannelManager.cancelSelf(p);
        }
        var moon=player(t,true,24);var style=FormSpellStyle.plan(moon,SpellRegistry.get("fire_bolt"),1,8,8);
        check(style.pay()&&style.pay(),"Style payment is idempotent");near(ManaUtils.getPlayerMana(moon),16,"Repeated payment never debits twice");
        var ground=floor(moon,4);var fire=new SpellFireBoltEntity(moon.getWorld(),moon);
        style.effect(()->moon.getServerWorld().spawnEntity(fire));
        check(((SpiderSpellLink.WebProjectile)fire).ssca$weave().enabled(),"Actual entity-load attaches accepted spell snapshot");
        collide(fire,ground);check(webs(moon,ground)==9,"Ground collision creates only three-by-three membrane");
        check(moon.getUuid().equals(WebMembraneOwners.get(ground.getBlockPos().up())),"Existing membrane owner/whitelist path retained");
        var area=floor(moon,8);var spike=new SpellFrostSpikeEntity(moon.getWorld(),moon);
        style.effect(()->moon.getServerWorld().spawnEntity(spike));spike.setPierceCount(3);
        var victim=pig(moon);collide(spike,new EntityHitResult(victim));collide(spike,area);
        check(webs(moon,area)==0,"Piercing entity hit cannot later leave ground web");
        NbtCompound saved=new NbtCompound();spike.writeNbt(saved);
        var loaded=new SpellFrostSpikeEntity(moon.getWorld(),moon);loaded.readNbt(saved);
        var afterLoad=floor(moon,12);collide(loaded,afterLoad);check(webs(moon,afterLoad)==0,"Direct-hit suppression survives save/load");
        var fresh=new SpellFireBoltEntity(moon.getWorld(),moon);style.effect(()->moon.getServerWorld().spawnEntity(fresh));fresh.writeNbt(saved=new NbtCompound());
        var restored=new SpellFireBoltEntity(moon.getWorld(),moon);restored.readNbt(saved);
        check(((SpiderSpellLink.WebProjectile)restored).ssca$weave().enabled(),"Unresolved weave snapshot survives save/load");
        var wall=floor(moon,16);((SpiderSpellLink.WebProjectile)restored).ssca$weave().ground(moon,new BlockHitResult(wall.getPos(),Direction.NORTH,wall.getBlockPos(),false));
        check(webs(moon,wall)==0,"Wall collision does not create a ground patch");
        // Exercise actual SSC cocoon conversion over a reproducible sample, without assuming one particular draw wins.
        moon.getRandom().setSeed(948623);int cocoon=0;
        for(int i=0;i<1000;i++) {
            victim.removeStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT);victim.removeStatusEffect(RegOtherStatusEffects.ENTANGLED_EFFECT);
            var impact=new SpiderSpellLink.Impact();impact.enable();impact.hit(moon,victim);
            var effect=victim.getStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT);
            if(effect!=null){cocoon++;check(effect.getDuration()==300,"Native mob cocoon lasts fifteen seconds");}
        }
        check(cocoon>=110&&cocoon<=190,"Fifteen-percent server draw distribution: "+cocoon);
        var friend=player(t,false,0);var safe=new SpiderSpellLink.Impact();safe.enable();
        for(int i=0;i<100;i++)safe.hit(moon,friend);
        check(!friend.hasStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT),"Protected player never cocooned");
        moon.addCommandTag(net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+moon.getUuid());
        moon.getServer().setPvpEnabled(true);
        for(int i=0;i<100&&!friend.hasStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT);i++){
            var hostileHit=new SpiderSpellLink.Impact();hostileHit.enable();hostileHit.hit(moon,friend);
        }
        check(friend.getStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT)!=null
                &&friend.getStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT).getDuration()==100,"Hostile player gets native five-second cocoon");
        friend.removeStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT);
        var volleyOwner=player(t,true,16);var volley=FormSpellStyle.plan(volleyOwner,SpellRegistry.get("ice_barrage"),1,8,8);check(volley.pay(),"One accepted volley group");
        var same=pig(volleyOwner);var missiles=new ArrayList<SpellFrostSpikeEntity>();
        for(int i=0;i<3;i++){var missile=new SpellFrostSpikeEntity(volleyOwner.getWorld(),volleyOwner);missiles.add(missile);volley.effect(()->volleyOwner.getServerWorld().spawnEntity(missile));}
        var anchor=new SpellFireBoltEntity(volleyOwner.getWorld(),volleyOwner);volley.effect(()->volleyOwner.getServerWorld().spawnEntity(anchor));
        nextRoll(volleyOwner,false);collide(missiles.get(0),new EntityHitResult(same));
        check(!same.hasStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT),"First volley draw reproducibly loses");
        for(int i=1;i<3;i++){same.timeUntilRegen=0;nextRoll(volleyOwner,true);collide(missiles.get(i),new EntityHitResult(same));}
        check(!same.hasStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT),"Later effective hits in same volley cannot draw again even with winning next random value");
        var savedLedger=ledger(volleyOwner);String group=volley.weave.id.toString();
        check(savedLedger.getCompound(group).getList("Targets",8).size()==1,"Exactly one shared target draw persisted for the cast");
        ((SpiderSpellLink.WebProjectile)anchor).ssca$weave().loaded(volleyOwner.getServerWorld(),anchor.getUuid());
        check(ledger(volleyOwner).getCompound(group).getList("Projectiles",8).size()==1,"Reloading same projectile UUID does not duplicate ledger membership");
        var ledgerType=Class.forName(SpiderSpellLink.class.getName()+"$WeaveLedger");var read=ledgerType.getDeclaredMethod("read",NbtCompound.class);read.setAccessible(true);
        var roundtrip=((net.minecraft.world.PersistentState)read.invoke(null,savedLedger)).writeNbt(new NbtCompound());
        check(roundtrip.equals(savedLedger),"Per-cast targets and projectile membership survive actual NBT codec roundtrip");
        anchor.discard();check(!ledger(volleyOwner).contains(group),"Last projectile destruction releases persistent cast ledger");
        var marked=pig(volleyOwner);marked.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(SscAddon.CURSE_MARK,1000,4));
        var markStyle=FormSpellStyle.plan(volleyOwner,SpellRegistry.get("curse_mark"),1,30,30);check(markStyle.pay(),"Distinct curse mark cast has a distinct draw group");
        var mark=new SpellCurseMarkEntity(volleyOwner.getWorld(),volleyOwner);markStyle.effect(()->volleyOwner.getServerWorld().spawnEntity(mark));
        nextRoll(volleyOwner,true);collide(mark,new EntityHitResult(marked));
        check(marked.getStatusEffect(SscAddon.CURSE_MARK).getAmplifier()==4,"Existing stronger curse mark is unchanged");
        check(marked.hasStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT),"Unrefreshed curse mark still draws cocoon");
        int offset=24;
        for(ProjectileEntity projectile:List.of(new SpellFireBoltEntity(moon.getWorld(),moon),new SpellFrostSpikeEntity(moon.getWorld(),moon),
                new SpellMoonlightArrowEntity(moon.getWorld(),moon),new SpellCurseMarkEntity(moon.getWorld(),moon),new BeepSheepEntity(moon.getWorld(),moon))) {
            style.effect(()->moon.getServerWorld().spawnEntity(projectile));var tile=floor(moon,offset);offset+=4;
            collide(projectile,tile);check(webs(moon,tile)==9,"Actual ground callback supports "+projectile.getType());
        }
        var impactFloor=floor(moon,offset);var meteor=new SpellMeteorEntity(moon.getWorld(),moon);
        meteor.setImpactTarget(impactFloor.getPos().x,impactFloor.getPos().y,impactFloor.getPos().z);
        style.effect(()->moon.getServerWorld().spawnEntity(meteor));
        for(int i=0;i<100&&!meteor.isRemoved();i++)meteor.tick();
        check(meteor.isRemoved()&&webs(moon,impactFloor)==9,"Real meteor descent distinguishes ground impact and leaves one patch: "+meteor.getPos()+"; webs="+webs(moon,impactFloor));
        var blueprintCaster=player(t,true,8);var blueprintFloor=floor(blueprintCaster,0);
        var bp=new NeoModel.Blueprint("web bolt",NeoModel.Core.THERMAL,NeoModel.Carrier.PROJECTILE,List.of(),NeoModel.Core.FROST,1,0,8);
        NeoRuntime.data(blueprintCaster).known.addAll(net.ssca.neomagic.NeoTestFixtures.SAMPLES.keySet());var wand=new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND);
        wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(bp));blueprintCaster.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,wand);blueprintCaster.setPitch(90);
        net.ssca.neomagic.NeoTestFixtures.start(blueprintCaster,net.minecraft.util.Hand.MAIN_HAND);
        near(ManaUtils.getPlayerMana(blueprintCaster),0,"Actual blueprint runtime pays eight native web energy");
        for(int i=0;i<12;i++)NeoEffects.tick(blueprintCaster.getServer());
        check(webs(blueprintCaster,blueprintFloor)==9,"Actual research projectile ground collision creates membrane");

        var spider=player(t,false,0);var b=book(spider);var target=pig(spider);
        SpellbookData.setScroll(b,0,ScrollData.create("curse_mark",3));SpellbookData.setScroll(b,1,ScrollData.create("dread_whisper",2));SpellbookData.setSelectedSlot(b,1);
        check(SpiderSpellLink.chooseSlot(spider,b)==1,"Selected ready curse is preferred");
        int normal=SpellNumbers.finalManaCost(SpellRegistry.get("dread_whisper"),b,spider,2,SpellbookData.getScroll(b,1));
        double quote=NeoLegacyCasting.quote(spider,SpellRegistry.get("dread_whisper"),2,0,normal).actual();
        float yaw=spider.getYaw(),pitch=spider.getPitch();jump(spider,target);
        var result=latest(spider);
        check(result.spell.endsWith(":dread_whisper")&&result.result.equals("released")&&!SpellChannelManager.isCasting(spider),"Native jump hit immediately releases selected spell");
        near(result.paid,quote*.2,"Triggered owned spell pays twenty percent of its normal actual Q");
        near(spider.getYaw(),yaw,"Automatic aim preserves yaw");near(spider.getPitch(),pitch,"Automatic aim preserves pitch");
        int cd=SpellNumbers.finalCooldownTicks(SpellRegistry.get("dread_whisper"),2,1,1,1);
        near(SharedSpellCooldowns.getCooldownEndOf(spider,SpellRegistry.get("dread_whisper"))-spider.getWorld().getTime(),Math.ceil(cd*.1),"Only ten percent normal cooldown remains");
        check(ScrollData.getUses(SpellbookData.getScroll(b,1))==ScrollData.getUses(ScrollData.create("dread_whisper",2)),"Passive never consumes scroll uses");
        check(SpiderSpellLink.chooseSlot(spider,b)==0,"Selected cooldown falls through to another ready carried curse");
        var other=SpellRegistry.get("curse_mark");long existing=spider.getWorld().getTime()+800;SharedSpellCooldowns.record(spider,other,existing);
        check(SpiderSpellLink.chooseSlot(spider,b)==-1,"All carried curses in cooldown select white fallback");
        NeoRuntime.data(spider).burden=0;target.timeUntilRegen=0;jump(spider,target);
        check(latest(spider)!=result&&latest(spider).blueprint.scale()==1,"All-CD jump uses a white level-one spell");near(latest(spider).paid,0,"All-CD fallback adds no collapse");
        check(SharedSpellCooldowns.getCooldownEndOf(spider,other)>=existing,"Free fallback never shortens an existing long manual cooldown");
        var empty=player(t,false,0);empty.getInventory().setStack(0,ScrollData.create("curse_mark",5));var lone=pig(empty);jump(empty,lone);
        check(latest(empty).result.equals("released")&&latest(empty).paid==0,"No book still triggers a free white curse");
        check(latest(empty).blueprint.scale()==1,"Inventory orange curse is excluded from carried selection");
        var immune=player(t,false,0);var nohit=pig(immune);immune.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);nohit.setInvulnerable(true);jump(immune,nohit);
        check(NeoRuntime.data(immune).records.isEmpty(),"Rejected jump damage does not proc a spell");
        var aimed=player(t,false,0);Vec3d center=t.getAbsolutePos(new BlockPos(5,5,5)).toCenterPos();
        aimed.refreshPositionAndAngles(center.x,center.y,center.z,150,50);var aimedBook=book(aimed);
        SpellbookData.setScroll(aimedBook,0,ScrollData.create("dread_whisper"));var aimedTarget=pig(aimed);
        var fallingCaster=player(t,true,8);Vec3d meteorSite=t.getAbsolutePos(new BlockPos(9,5,7)).toCenterPos();
        var direct=pig(fallingCaster);direct.refreshPositionAndAngles(meteorSite.x,meteorSite.y,meteorSite.z,0,0);
        var splash=pig(fallingCaster);splash.refreshPositionAndAngles(meteorSite.x+2,meteorSite.y,meteorSite.z,0,0);
        final int sampled=cocoon;
        t.waitAndRun(4,()->{try{
            check(aimed.getServerWorld().getEntitiesByClass(PigEntity.class,aimed.getBoundingBox().expand(4),e->e==aimedTarget).size()==1,"Aim test target is visible to real entity queries");
            jump(aimed,aimedTarget);
            var weakness=aimedTarget.getStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS);
            check(weakness!=null&&weakness.getDuration()==132,"Automatic cone aims at jump victim and retains original ten-percent curse duration");
            near(aimed.getYaw(),150,"Native cone proc never turns player camera");
            var falling=new SpellMeteorEntity(fallingCaster.getWorld(),fallingCaster);falling.setDamage(2);falling.setRadius(3);
            falling.setImpactTarget(direct.getX(),direct.getY()-1,direct.getZ());
            var fallingStyle=FormSpellStyle.plan(fallingCaster,SpellRegistry.get("meteor"),1,80,80);check(fallingStyle.pay(),"Meteor body bonus paid");
            var sibling=new SpellFireBoltEntity(fallingCaster.getWorld(),fallingCaster);fallingStyle.effect(()->fallingCaster.getServerWorld().spawnEntity(sibling));
            fallingStyle.effect(()->fallingCaster.getServerWorld().spawnEntity(falling));
            for(int i=0;i<100&&!falling.isRemoved();i++)falling.tick();
            var snapshot=falling.writeNbt(new NbtCompound());
            check(falling.isRemoved()&&snapshot.getBoolean("SscaWeaveDirect"),"Actual falling meteor detects direct entity collision");
            var rolls=ledger(fallingCaster).getCompound(snapshot.getUuid("SscaWeaveCast").toString()).getList("Targets",8);
            check(rolls.size()==1&&rolls.getString(0).equals(direct.getUuidAsString()),"Meteor rolls cocoon only for direct target, never its splash victims");
            sibling.discard();
            System.out.println("Neo spider spell link checks passed: "+checks+"; native cocoon wins: "+sampled);t.complete();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
