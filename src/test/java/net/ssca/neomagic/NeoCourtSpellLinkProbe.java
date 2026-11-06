package net.ssca.neomagic;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.*;
import net.jackcooper.shapeShifterCurseAddon.entity.*;
import net.jackcooper.shapeShifterCurseAddon.resource.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.onixary.shapeShifterCurseFabric.minion.IPlayerEntityMinion;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Actual native hits, real Apoli action-on-hit, native minion AI, shared brand gates and reload. */
public final class NeoCourtSpellLinkProbe {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-5,why+": "+a+" != "+b);}
    private static ServerPlayerEntity player(TestContext t,Identifier form)throws Exception{
        var p=NeoFormCastingProbe.player(t,form,0);NeoRuntime.data(p).spellbookMasteryExpTen=120000;
        var floor=p.getBlockPos().down();for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)p.getServerWorld().setBlockState(floor.add(x,0,z),Blocks.STONE.getDefaultState());
        return p;
    }
    private static void power(ServerPlayerEntity p,String id){var type=PowerTypeRegistry.get(new Identifier("my_addon",id));check(type!=null,"Real registered power "+id);PowerHolderComponent.KEY.get(p).addPower(type,new Identifier("ssc_addon","court_test"));}
    private static void soul(ServerPlayerEntity p,int value){power(p,"form_anubis_wolf_sp_soul_energy");AnubisWolfSpSoulEnergy.setEnergy(p,value);}
    private static PigEntity pig(ServerPlayerEntity p,double x,double z){var mob=EntityType.PIG.create(p.getServerWorld());mob.setAiDisabled(true);mob.setNoGravity(true);mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);mob.setHealth(200);mob.refreshPositionAndAngles(p.getX()+x,p.getY(),p.getZ()+z,0,0);check(p.getServerWorld().spawnEntity(mob),"Loaded test target");return mob;}
    private static FormSpellStyle style(ServerPlayerEntity p){var spell=SpellRegistry.get("fire_bolt");var st=FormSpellStyle.plan(p,spell,1,0,0);check(st.pay(),"Actual style accepted");var record=new NeoState.Experiment();record.spell=spell.getId().toString();record.blueprint=NeoLegacyCasting.descriptor(spell,1,0);record.form=NeoRuntime.form(p);record.dimension=p.getWorld().getRegistryKey().getValue().toString();record.profile=NeoRuntime.profile(p);NeoRuntime.data(p).add(record);st.bindBat(record);check(st.court!=null,"Form cast owns court snapshot");return st;}
    private static void hit(ServerPlayerEntity p,LivingEntity target,FormSpellStyle st){target.timeUntilRegen=0;st.effect(()->check(SpellHitHelper.hitRaw(p,target,1,0)==SpellHitHelper.HitResult.HIT,"Actual spell damage applies"));}
    private static List<AnubisWolfMinionEntity> wolves(ServerPlayerEntity p){var result=new ArrayList<AnubisWolfMinionEntity>();for(var id:((IPlayerEntityMinion)p).shape_shifter_curse$getMinionsByMinionID(AnubisWolfMinionEntity.MinionID))if(p.getServerWorld().getEntity(id) instanceof AnubisWolfMinionEntity w&&w.isAlive())result.add(w);return result;}
    private static void color(ServerPlayerEntity p,LivingEntity target,String expected){check(Objects.equals(GoldenSandstormErosionBrand.getBrandColor(p.getUuid(),target.getUuid()),expected),"Shared brand color should be "+expected+", actual "+GoldenSandstormErosionBrand.getBrandColor(p.getUuid(),target.getUuid()));}
    private static void collide(ProjectileEntity p,LivingEntity target)throws Exception{var m=ProjectileEntity.class.getDeclaredMethod("onCollision",HitResult.class);m.setAccessible(true);m.invoke(p,new EntityHitResult(target));}
    private static void roundtrip(ServerPlayerEntity p)throws Exception{var type=Class.forName(CourtSpellLink.class.getName()+"$Store");var get=type.getDeclaredMethod("get",net.minecraft.server.MinecraftServer.class);get.setAccessible(true);var stored=(net.minecraft.world.PersistentState)get.invoke(null,p.getServer());var nbt=stored.writeNbt(new NbtCompound());var read=type.getDeclaredMethod("read",NbtCompound.class);read.setAccessible(true);var restored=(net.minecraft.world.PersistentState)read.invoke(null,nbt);check(restored.writeNbt(new NbtCompound()).equals(nbt),"Cast deduplication and command lifetimes persist exactly");p.getServer().getOverworld().getPersistentStateManager().set("ssca_court_spell_links",restored);}
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception{
        var p=player(t,FormIdentifiers.ANUBIS_WOLF_SP);soul(p,20);var target=pig(p,0,3);var cast=style(p);hit(p,target,cast);near(AnubisWolfSpSoulEnergy.getEnergy(p),15,"First command consumes five souls");check(wolves(p).size()==1,"No existing wolf creates one native registered wolf");var wolf=wolves(p).get(0);check(wolf.squaredDistanceTo(target)>25,"Supplemental wolf starts beyond five blocks from the target");check(wolf.getTarget()==target,"Real minion starts targeting hit enemy");near(wolf.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE),4,"Native level-three attack preserved");
        hit(p,pig(p,2,3),cast);near(AnubisWolfSpSoulEnergy.getEnergy(p),15,"Same cast never issues a second order even to another target");var copy=CourtSpellLink.Cast.read(cast.court.write());CourtSpellLink.hit(copy,p,target);near(AnubisWolfSpSoulEnergy.getEnergy(p),15,"NBT copy cannot reset order flag");
        var next=style(p);hit(p,target,next);near(AnubisWolfSpSoulEnergy.getEnergy(p),10,"Another spell may command existing owned wolf");check(wolves(p).size()==1,"Existing wolf reused without extra summon");float hp=target.getHealth();
        var poor=player(t,FormIdentifiers.ANUBIS_WOLF_SP);soul(poor,4);var poorTarget=pig(poor,0,3);hit(poor,poorTarget,style(poor));near(AnubisWolfSpSoulEnergy.getEnergy(poor),4,"Insufficient souls keep ordinary spell and no partial payment");check(wolves(poor).isEmpty(),"Insufficient souls never summon");
        var separate=player(t,FormIdentifiers.ANUBIS_WOLF_SP);separate.refreshPositionAndAngles(p.getX()+2,p.getY(),p.getZ(),0,0);soul(separate,10);hit(separate,target,style(separate));check(wolves(separate).size()==1&&!wolves(separate).get(0).getUuid().equals(wolf.getUuid()),"Nearby other-player wolves are never borrowed or mistaken for owned minions");
        var unsafe=player(t,FormIdentifiers.ANUBIS_WOLF_SP);soul(unsafe,10);var unsafeTarget=pig(unsafe,0,3);var floor=unsafe.getBlockPos().down();for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)unsafe.getServerWorld().setBlockState(floor.add(x,0,z),Blocks.MAGMA_BLOCK.getDefaultState());hit(unsafe,unsafeTarget,style(unsafe));near(AnubisWolfSpSoulEnergy.getEnergy(unsafe),10,"No safe ground means no soul fee");check(wolves(unsafe).isEmpty(),"Hazardous floor never gets fallback summon");
        var debuff=player(t,FormIdentifiers.ANUBIS_WOLF_SP);soul(debuff,10);var debuffTarget=pig(debuff,0,3);var ds=style(debuff);ds.effect(()->debuffTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,200,0),debuff));near(AnubisWolfSpSoulEnergy.getEnergy(debuff),5,"Successful pure debuff triggers command");
        var gold=player(t,FormIdentifiers.GOLDEN_SANDSTORM_SP);power(gold,"form_golden_sandstorm_sp_erosion_brand_hit");var branded=pig(gold,0,3);var gs=style(gold);hit(gold,branded,gs);color(gold,branded,"yellow");hit(gold,branded,gs);color(gold,branded,"yellow");
        var prism=player(t,FormIdentifiers.GOLDEN_SANDSTORM_SP);var prismTarget=pig(prism,0,3);var prismItem=(net.jackcooper.shapeShifterCurseAddon.item.ErosionSandPrismItem)SscAddon.EROSION_SAND_PRISM;prismItem.onEquip(new ItemStack(prismItem),prism,null);GoldenSandstormErosionBrand.onPlayerAttack(prism,prismTarget);color(prism,prismTarget,"yellow");
        var friend=pig(gold,2,3);gold.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+friend.getUuid());gs.effect(()->friend.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,100,0),gold));color(gold,friend,null);
        var markTarget=pig(gold,-2,3);var markStyle=style(gold);var missile=new SpellCurseMarkEntity(gold.getWorld(),gold);markStyle.effect(()->missile.setOwner(gold));missile.setDuration(200);missile.setLevel(1);var packed=missile.writeNbt(new NbtCompound());var restored=new SpellCurseMarkEntity(gold.getWorld(),gold);restored.readNbt(packed);check(((CourtSpellLink.ProjectileData)restored).ssca$court()!=null,"Delayed projectile retains cast snapshot through NBT");collide(restored,markTarget);color(gold,markTarget,"yellow");
        var book=player(t,FormIdentifiers.ANUBIS_WOLF_SP);soul(book,10);var bookTarget=pig(book,0,3);
        var solo=player(t,FormIdentifiers.ANUBIS_WOLF_SP);soul(solo,10);var soloTarget=pig(solo,0,3);
        for(var member:List.of(book,solo)){var victim=member==book?bookTarget:soloTarget;var spell=SpellRegistry.get("fire_bolt");check(SpellChannelManager.start(member,spell,ScrollData.create("fire_bolt"),1,member==solo,916,10,60,()->true,at->{var fire=new SpellFireBoltEntity(member.getWorld(),member);fire.setDamage(1);try{collide(fire,victim);}catch(Exception e){throw new RuntimeException(e);}},cd->{}),"Actual native book/solo channel accepted");}
        var blue=player(t,FormIdentifiers.ANUBIS_WOLF_SP);soul(blue,10);var blueTarget=pig(blue,0,3);var blueprint=new Blueprint("court",Core.CURSE,Carrier.LOCK,List.of(),Core.CURSE,1,8,20);NeoRuntime.data(blue).known.addAll(net.ssca.neomagic.NeoTestFixtures.SAMPLES.keySet());var wand=new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(blueprint));blue.setStackInHand(Hand.MAIN_HAND,wand);net.ssca.neomagic.NeoTestFixtures.start(blue,Hand.MAIN_HAND);check(NeoRuntime.ACTIVE.containsKey(blue.getUuid()),"Actual blueprint channel accepted");
        t.waitAndRun(15,()->{for(var member:List.of(book,solo,blue)){near(AnubisWolfSpSoulEnergy.getEnergy(member),5,"Book, solo and blueprint each pay one command fee");check(wolves(member).size()==1,"All release paths share native supplemental summoning");}});
        t.waitAndRun(20,()->{hit(gold,branded,style(gold));color(gold,branded,"yellow");});
        t.waitAndRun(40,()->{GoldenSandstormErosionBrand.onPlayerAttack(gold,branded);color(gold,branded,"orange");hit(gold,branded,style(gold));color(gold,branded,"orange");});
        t.waitAndRun(51,()->{prismItem.accessoryTick(new ItemStack(prismItem),prism,null);hit(prism,prismTarget,style(prism));color(prism,prismTarget,"yellow");});
        t.waitAndRun(52,()->{prismItem.accessoryTick(new ItemStack(prismItem),prism,null);hit(prism,prismTarget,style(prism));color(prism,prismTarget,"orange");});
        t.waitAndRun(80,()->{hit(gold,branded,style(gold));color(gold,branded,"red");check(target.getHealth()<hp,"Commanded native wolf actually reaches and damages its target");});
        t.waitAndRun(81,()->{hit(gold,branded,style(gold));color(gold,branded,"red");});
        t.waitAndRun(120,()->{hit(gold,branded,style(gold));color(gold,branded,"green");try{roundtrip(p);}catch(Exception e){throw new RuntimeException(e);}});
        t.waitAndRun(121,()->{hit(gold,branded,style(gold));color(gold,branded,"green");});
        t.waitAndRun(225,()->{hit(gold,branded,gs);color(gold,branded,"green");hit(gold,branded,style(gold));color(gold,branded,"yellow");});
        t.waitAndRun(610,()->{check(wolves(p).isEmpty(),"Supplemental native wolf expires after original thirty-second lifetime");System.out.println("NEO_COURT_LINK_PASS checks="+checks);t.complete();});
    }
}
