package net.ssca.neomagic;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.entity.SpellCurseMarkEntity;
import net.jackcooper.shapeShifterCurseAddon.resource.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Real status application, spell payment, bounded generations, cleansing and persistent state. */
public final class NeoBatSpellLinkProbe {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-4,why+": "+a+" != "+b);}
    private static ServerPlayerEntity player(TestContext t,Identifier form,int height)throws Exception{
        var p=NeoFormCastingProbe.player(t,form,0);var at=t.getAbsolutePos(new BlockPos(4,height,4)).toCenterPos();p.refreshPositionAndAngles(at.x,at.y,at.z,0,0);return p;
    }
    private static PigEntity pig(ServerPlayerEntity p,double x,double z){
        var mob=EntityType.PIG.create(p.getServerWorld());mob.setAiDisabled(true);mob.setNoGravity(true);mob.refreshPositionAndAngles(p.getX()+x,p.getY(),p.getZ()+z,0,0);p.getServerWorld().getChunk(mob.getBlockPos());check(p.getServerWorld().spawnEntity(mob),"Test mob loaded");return mob;
    }
    private static void blood(ServerPlayerEntity p,int value){
        var type=PowerTypeRegistry.get(BarKeys.BAT_BLOOD.id);check(type!=null,"Actual bat blood power registered");
        PowerHolderComponent.KEY.get(p).addPower(type,new Identifier("ssc_addon","bat_test"));ResourceBars.set(p,BarKeys.BAT_BLOOD,value);
    }
    private static FormSpellStyle seed(ServerPlayerEntity p,LivingEntity target,StatusEffect effect,int duration){
        var spell=SpellRegistry.get("curse_mark");var style=FormSpellStyle.plan(p,spell,1,0,0);check(style.pay(),"Fruit style accepted");
        var r=new NeoState.Experiment();r.spell=spell.getId().toString();r.blueprint=NeoLegacyCasting.descriptor(spell,1,0);r.form=NeoRuntime.form(p);r.profile=NeoRuntime.profile(p);r.dimension=p.getWorld().getRegistryKey().getValue().toString();r.actual=25;r.paid=25;r.result="released";NeoRuntime.data(p).add(r);style.bindBat(r);
        style.effect(()->check(target.addStatusEffect(new StatusEffectInstance(effect,duration,0),p),"Actual harmful status applied"));return style;
    }
    private static void collide(ProjectileEntity p,LivingEntity target)throws Exception{var method=ProjectileEntity.class.getDeclaredMethod("onCollision",HitResult.class);method.setAccessible(true);method.invoke(p,new EntityHitResult(target));}
    private static NbtCompound save(ServerPlayerEntity p)throws Exception{var c=Class.forName(BatSpellLink.class.getName()+"$Store");var get=c.getDeclaredMethod("get",net.minecraft.server.MinecraftServer.class);get.setAccessible(true);return ((net.minecraft.world.PersistentState)get.invoke(null,p.getServer())).writeNbt(new NbtCompound());}
    public static void start(TestContext t){t.waitAndRun(70,()->{try{run(t);}catch(Exception e){throw new RuntimeException(e);}});}
    private static void run(TestContext t)throws Exception{
        t.getWorld().getServer().setPvpEnabled(true);
        var bat=player(t,FormIdentifiers.BAT_DESMODUS,20);blood(bat,15);
        var fire=SpellRegistry.get("fire_bolt");var plan=FormSpellStyle.plan(bat,fire,1,8,8);near(plan.collapseMultiplier,.65,"Blood reduces growth by exactly 35 percent");check(plan.pay(),"Fifteen blood paid");near(ResourceBars.get(bat,BarKeys.BAT_BLOOD),0,"Exactly fifteen blood removed");check(plan.pay(),"Repeated acceptance is idempotent");near(ResourceBars.get(bat,BarKeys.BAT_BLOOD),0,"No second blood fee");
        blood(bat,14);var ordinary=FormSpellStyle.plan(bat,fire,1,8,8);near(ordinary.collapseMultiplier,1,"Insufficient blood gives ordinary growth");check(ordinary.pay(),"Insufficient blood still casts");near(ResourceBars.get(bat,BarKeys.BAT_BLOOD),14,"No partial payment");
        blood(bat,15);check(SpellChannelManager.start(bat,fire,ScrollData.create("fire_bolt"),1,false,888,20,40,()->true,v->{},cd->{}),"Actual common channel accepts bat spell");near(NeoLegacyCasting.active(bat).session.quote.actual(),13,"Quote keeps the paid 0.65 snapshot after blood reaches zero");check(NeoLegacyCasting.active(bat).record.bloodSpent==15,"Blood payment is in research record");SpellChannelManager.cancelSelf(bat);
        var p=player(t,FormIdentifiers.BAT_PARASITIC_FRUIT,40);
        check(SpellRegistry.get("curse_mark").getConfig().debuff&&!fire.getConfig().debuff,"Status metadata excludes fire-only spells");
        near(FormSpellStyle.plan(p,fire,1,8,8).collapseMultiplier,1,"No surcharge for burning");near(FormSpellStyle.plan(p,SpellRegistry.get("explosion"),1,700,700).collapseMultiplier,1,"Red spell never becomes transmissible");
        near(FormSpellStyle.plan(p,SpellRegistry.get("curse_mark"),1,30,30).collapseMultiplier,1.25,"Harmful status spell pays 25 percent once");
        var root=pig(p,0,4);var a=pig(p,2,4);var b=pig(p,-2,4);var spare=pig(p,0,6.9);
        var grandchildren=List.of(pig(p,4.8,3.5),pig(p,4.8,4.5),pig(p,-4.8,3.5),pig(p,-4.8,4.5));var fourth=pig(p,7.6,4);
        var friend=pig(p,0,5);p.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+friend.getUuid());
        var style=seed(p,root,StatusEffects.WEAKNESS,360);check(style.bat!=null,"Accepted fruit cast has one spread ledger");
        BatSpellLink.applied(style.bat,root,new StatusEffectInstance(StatusEffects.WEAKNESS,360,4),p);
        var dwell=player(t,FormIdentifiers.BAT_PARASITIC_FRUIT,70);var dwellRoot=pig(dwell,0,4);var moving=pig(dwell,2.9,4);seed(dwell,dwellRoot,StatusEffects.SLOWNESS,350);
        var clean=player(t,FormIdentifiers.BAT_PARASITIC_FRUIT,100);var cleanRoot=pig(clean,0,4);var cleanTarget=pig(clean,2,4);seed(clean,cleanRoot,StatusEffects.WEAKNESS,350);
        var projectileCaster=player(t,FormIdentifiers.BAT_PARASITIC_FRUIT,130);projectileCaster.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+projectileCaster.getUuid());var marked=pig(projectileCaster,0,4);var markStyle=seed(projectileCaster,pig(projectileCaster,0,12),StatusEffects.WEAKNESS,5);
        var markChild=player(t,FormIdentifiers.BAT_DESMODUS,130);markChild.setPosition(projectileCaster.getX()+2,projectileCaster.getY(),projectileCaster.getZ()+4);
        var missile=new SpellCurseMarkEntity(projectileCaster.getWorld(),projectileCaster);markStyle.effect(()->missile.setOwner(projectileCaster));missile.setDuration(200);missile.setLevel(2);markStyle.effect(()->projectileCaster.getServerWorld().spawnEntity(missile));
        check(((BatSpellLink.ProjectileData)missile).ssca$bat()!=null,"Projectile owns its bat snapshot before delayed collision");
        var stored=missile.writeNbt(new NbtCompound());var restored=new SpellCurseMarkEntity(projectileCaster.getWorld(),projectileCaster);restored.readNbt(stored);check(((BatSpellLink.ProjectileData)restored).ssca$bat()!=null,"Projectile spread snapshot survives NBT");collide(missile,marked);check(marked.hasStatusEffect(SscAddon.CURSE_MARK),"Native curse projectile applies its actual status");
        var npcToPlayer=player(t,FormIdentifiers.BAT_PARASITIC_FRUIT,160);npcToPlayer.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+npcToPlayer.getUuid());
        var npcRoot=pig(npcToPlayer,0,4);var playerChild=player(t,FormIdentifiers.BAT_DESMODUS,160);playerChild.setPosition(npcToPlayer.getX()+2,npcToPlayer.getY(),npcToPlayer.getZ()+4);seed(npcToPlayer,npcRoot,StatusEffects.WEAKNESS,300);
        var playerToNpc=player(t,FormIdentifiers.BAT_PARASITIC_FRUIT,190);playerToNpc.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+playerToNpc.getUuid());
        var playerRoot=player(t,FormIdentifiers.BAT_DESMODUS,190);playerRoot.setPosition(playerToNpc.getX(),playerToNpc.getY(),playerToNpc.getZ()+4);var npcChild=pig(playerToNpc,2,4);seed(playerToNpc,playerRoot,StatusEffects.WEAKNESS,300);
        check(FormSpellStyle.legalTarget(npcToPlayer,playerChild),"Fixture player is a legal nonwhitelisted PvP target");
        check(playerChild.getServerWorld().getEntity(playerChild.getUuid())==playerChild,"Fixture player is in the world entity index");
        t.waitAndRun(25,()->{moving.setPosition(dwell.getX()+3.1,dwell.getY(),dwell.getZ()+4);cleanRoot.removeStatusEffect(StatusEffects.WEAKNESS);cleanRoot.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,300,0));});
        t.waitAndRun(50,()->{check(!a.hasStatusEffect(StatusEffects.WEAKNESS)&&!b.hasStatusEffect(StatusEffects.WEAKNESS),"No transmission within the first 2.5 seconds");moving.setPosition(dwell.getX()+2.9,dwell.getY(),dwell.getZ()+4);});
        t.waitAndRun(75,()->{check(a.hasStatusEffect(StatusEffects.WEAKNESS)&&b.hasStatusEffect(StatusEffects.WEAKNESS),"Two closest eligible neighbors become generation two");check(!spare.hasStatusEffect(StatusEffects.WEAKNESS),"Root cannot infect a third neighbor");check(!friend.hasStatusEffect(StatusEffects.WEAKNESS),"Whitelist excluded");check(!moving.hasStatusEffect(StatusEffects.SLOWNESS),"Leaving radius resets continuous exposure");check(!cleanTarget.hasStatusEffect(StatusEffects.WEAKNESS),"Cleansed spell cannot resume spreading another unrelated status");});
        t.waitAndRun(75,()->{check(playerChild.hasStatusEffect(StatusEffects.WEAKNESS),"NPC to player transmission succeeds");check(playerChild.getStatusEffect(StatusEffects.WEAKNESS).getAmplifier()==0,"NPC-only amplifier bonus never leaks to player recipient");check(playerRoot.getStatusEffect(StatusEffects.WEAKNESS).getAmplifier()==0,"Initial player recipient has no NPC enhancement");check(npcChild.hasStatusEffect(StatusEffects.WEAKNESS)&&npcChild.getStatusEffect(StatusEffects.WEAKNESS).getAmplifier()==1,"Player to NPC transmission applies the original NPC bonus exactly once");});
        t.waitAndRun(75,()->{check(markChild.hasStatusEffect(SscAddon.CURSE_MARK),"Delayed native curse spreads to a legal player");check(marked.getStatusEffect(SscAddon.CURSE_MARK).getAmplifier()==1&&markChild.getStatusEffect(SscAddon.CURSE_MARK).getAmplifier()==1,"A native spell without the NPC bonus retains its original base level when spreading to a player");});
        t.waitAndRun(125,()->{check(moving.hasStatusEffect(StatusEffects.SLOWNESS),"Reentry requires a fresh continuous exposure");for(var child:grandchildren)check(child.hasStatusEffect(StatusEffects.WEAKNESS),"Generation two infects at most two generation-three targets");check(!fourth.hasStatusEffect(StatusEffects.WEAKNESS),"Generation three never transmits");check(grandchildren.get(0).getStatusEffect(StatusEffects.WEAKNESS).getDuration()<=root.getStatusEffect(StatusEffects.WEAKNESS).getDuration()+1,"Propagation retains remaining duration");check(root.getStatusEffect(StatusEffects.WEAKNESS).getAmplifier()==1,"Initial cast retains the original fruit NPC debuff level bonus");check(grandchildren.get(0).getStatusEffect(StatusEffects.WEAKNESS).getAmplifier()==root.getStatusEffect(StatusEffects.WEAKNESS).getAmplifier(),"Propagation copies effective strength without reapplying the original NPC bonus each generation");check(NeoRuntime.data(p).records.get(0).sporeInfections==6,"One isolated root yields exactly six secondary infections");});
        t.waitAndRun(145,()->{dwellRoot.clearStatusEffects();moving.clearStatusEffects();marked.clearStatusEffects();npcRoot.clearStatusEffects();playerChild.clearStatusEffects();playerRoot.clearStatusEffects();npcChild.clearStatusEffects();});
        t.waitAndRun(165,()->{try{var packed=save(p);var c=Class.forName(BatSpellLink.class.getName()+"$Store");var read=c.getDeclaredMethod("read",NbtCompound.class);read.setAccessible(true);var copy=(net.minecraft.world.PersistentState)read.invoke(null,packed);check(copy.writeNbt(new NbtCompound()).equals(packed),"Branch counters and visited targets survive persistent roundtrip");p.getServer().getOverworld().getPersistentStateManager().set("ssca_bat_spell_links",copy);}catch(Exception e){throw new RuntimeException(e);}});
        t.waitAndRun(220,()->{check(!fourth.hasStatusEffect(StatusEffects.WEAKNESS)&&!spare.hasStatusEffect(StatusEffects.WEAKNESS),"Restoring state does not reopen consumed quotas");System.out.println("NEO_BAT_LINK_PASS checks="+checks+", queries="+BatSpellLink.queries+", attempts="+BatSpellLink.attempts);t.complete();});
    }
}
