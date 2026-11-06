package net.ssca.neomagic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import net.jackcooper.shapeShifterCurseAddon.spell.CollapsePenalties;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** Real ServerWorld: exact health loss, timing, revival, form migration and packet state. */
public final class NeoCollapsePenaltyProbe {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void near(double actual,double expected,String why) { check(Math.abs(actual-expected)<1e-5,why+": "+actual+" != "+expected); }
    private static ServerPlayerEntity player(TestContext t,Identifier form) throws Exception {
        // TestContext's creative mock hard-codes isCreative() == true even after changeGameMode.
        var p=new ServerPlayerEntity(t.getWorld().getServer(),t.getWorld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"collapse-probe"));
        p.getServer().getPlayerManager().onPlayerConnect(new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),p);
        p.changeGameMode(GameMode.SURVIVAL);p.setNoGravity(true);
        RegPlayerFormComponent.PLAYER_FORM.get(p).nowForm=RegPlayerForms.playerForms.get(form);
        NeoRuntime.refreshForm(p);check(!p.isCreative(),"Probe uses actual survival player");
        p.getAbilities().invulnerable=false;return p;
    }
    private static void ticks(ServerPlayerEntity p,int count) { for(int i=0;i<count;i++)CollapsePenalties.tick(p); }
    private static void burden(ServerPlayerEntity p,double ratio) { NeoRuntime.data(p).burden=NeoRuntime.profile(p).safety()*ratio; }
    public static void start(TestContext t) {
        t.waitAndRun(70,()->{try { run(t); } catch(Exception e) { throw new RuntimeException(e); }});
    }
    private static void run(TestContext t) throws Exception {
        for(double n:new double[]{65,80,100,200,285,10000}) {
            check(CollapsePenalties.stage(0,n)==0,"Empty bar is safe");
            check(CollapsePenalties.stage(n,n)==0,"Exact threshold is safe");
            for(int stage=1;stage<=5;stage++) {
                double center=n*(1+(stage-.5)*.2);
                check(CollapsePenalties.stage(center,n)==stage,"Dynamic proportional band "+stage);
                if(stage<5){double edge=n*(1+stage*.2);check(CollapsePenalties.stage(edge,n)==stage,"Exact upper boundary");
                    check(CollapsePenalties.stage(edge+n*1e-6,n)==stage+1,"Immediately above boundary");}
            }
            check(CollapsePenalties.stage(4*n,n)==5,"Above 2N retains maximum penalty");
        }
        var p=player(t,new Identifier("shape-shifter-curse:original_shifter"));
        var speed=p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);speed.setBaseValue(.1);
        for(int stage=0;stage<=5;stage++) {
            CollapsePenalties.clear(p);p.setHealth(20);burden(p,stage==0?1:1+(stage-.5)*.2);ticks(p,1);
            near(speed.getValue(),.1*(1-CollapsePenalties.slow(stage)),"Actual movement attribute band "+stage);
            ticks(p,1);near(speed.getValue(),.1*(1-CollapsePenalties.slow(stage)),"Slow never stacks");
            float threshold=CollapsePenalties.bodyYawMovementThreshold(p,0.0025000002F);
            check(threshold>0,"Body turning retains a positive stationary-noise threshold");
            if(stage==0)near(threshold,0.0025000002F,"Healthy player keeps vanilla body threshold");
            if(stage==5)check(.02*.02>threshold&&.02*.02<0.0025000002F,"Slow walking below the vanilla threshold still turns the body");
            var hud=NeoNetwork.hudState(p);check(hud.getInt("CollapseStage")==stage,"HUD mirrors server band");
            check(hud.getInt("PlayerId")==p.getId(),"Packet identifies current player entity");
        }
        var collapseSlow=speed.getModifiers().stream().filter(m->m.getName().equals("SSCA collapse slowdown")).findFirst().orElseThrow();
        var mob=EntityType.ZOMBIE.create(t.getWorld());
        mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).addTemporaryModifier(collapseSlow);
        near(CollapsePenalties.bodyYawMovementThreshold(mob,0.0025000002F),0.0025000002F,"Non-player movement keeps vanilla body threshold");
        CollapsePenalties.clear(p);
        var unrelatedSlow=new EntityAttributeModifier(java.util.UUID.randomUUID(),"other mod slow",-.8,EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        speed.addTemporaryModifier(unrelatedSlow);
        near(CollapsePenalties.bodyYawMovementThreshold(p,0.0025000002F),0.0025000002F,"Unrelated slowdown does not opt into collapse body handling");
        speed.removeModifier(unrelatedSlow);
        for(int stage=3;stage<=5;stage++) {
            CollapsePenalties.clear(p);p.setHealth(20);p.setAbsorptionAmount(20);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,200,4));
            burden(p,1+(stage-.5)*.2);int interval=stage==3?20:10;float loss=stage==5?2:1;
            ticks(p,interval-1);near(p.getHealth(),20,"No early periodic damage "+stage);
            ticks(p,1);near(p.getHealth(),20-loss,"Exact first health loss "+stage);
            ticks(p,interval);near(p.getHealth(),20-2*loss,"Exact second health loss "+stage);
            near(p.getAbsorptionAmount(),20,"Absorption cannot absorb code loss");
            check(!p.hasStatusEffect(StatusEffects.WITHER)&&!p.hasStatusEffect(StatusEffects.BLINDNESS)
                    &&!p.hasStatusEffect(StatusEffects.NAUSEA),"No convertible server potion carriers");
            burden(p,1);ticks(p,20);near(p.getHealth(),20-2*loss,"Damage stops upon recovery");near(speed.getValue(),.1,"Slow clears upon recovery");
            near(CollapsePenalties.bodyYawMovementThreshold(p,0.0025000002F),0.0025000002F,"Recovery restores vanilla body turning threshold");
        }
        p.removeStatusEffect(StatusEffects.RESISTANCE);p.setAbsorptionAmount(0);
        CollapsePenalties.clear(p);p.setHealth(20);burden(p,1.5);ticks(p,9);burden(p,1.7);ticks(p,1);
        near(p.getHealth(),19,"Moving between harmful bands does not reset elapsed damage timer");
        p.addStatusEffect(new StatusEffectInstance(NeoMagic.BREAKDOWN,200));CollapsePenalties.clear(p);ticks(p,1);
        check(!p.hasStatusEffect(NeoMagic.BREAKDOWN),"Old saved random breakdown migrates away");
        p.setHealth(20);burden(p,1.9);NeoRuntime.backlash(p,1000);near(p.getHealth(),20,"Failed casts do not add duplicate instant damage");
        check(!NeoRuntime.blocked(p),"Collapse does not add an unrequested cast lock");
        CollapsePenalties.clear(p);p.setHealth(1);p.setStackInHand(Hand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));
        ticks(p,10);check(p.isAlive()&&p.getHealth()>0,"Vanilla totem still saves lethal collapse");
        check(p.getOffHandStack().isEmpty(),"Totem is consumed exactly once");
        p.clearStatusEffects();p.setAbsorptionAmount(0);p.setHealth(20);
        p.changeGameMode(GameMode.CREATIVE);ticks(p,40);near(p.getHealth(),20,"Creative mode remains damage immune");
        p.changeGameMode(GameMode.SPECTATOR);ticks(p,1);near(speed.getValue(),.1,"Spectator clears slow");
        p.changeGameMode(GameMode.SURVIVAL);burden(p,1.9);ticks(p,1);
        RegPlayerFormComponent.PLAYER_FORM.get(p).nowForm=RegPlayerForms.playerForms.get(FormIdentifiers.FAMILIAR_FOX_SP);
        NeoRuntime.refreshForm(p);near(NeoRuntime.data(p).burden,0,"Changing form resets collapse");near(speed.getValue(),.1,"Changing form removes prior slow immediately");
        var immune=player(t,FormIdentifiers.GOLDEN_SANDSTORM_SP);immune.setHealth(10);burden(immune,1.9);ticks(immune,10);
        near(immune.getHealth(),8,"Wither-linked form cannot convert code wither into healing");CollapsePenalties.clear(immune);
        var doomed=player(t,new Identifier("shape-shifter-curse:original_shifter"));doomed.setHealth(1);burden(doomed,1.9);ticks(doomed,10);
        check(!doomed.isAlive()&&doomed.getHealth()==0,"Lethal collapse reaches real vanilla death");CollapsePenalties.clear(doomed);
        var forms=new ArrayList<Map<String,Object>>();
        RegPlayerForms.playerForms.forEach((id,form)->forms.add(Map.of("id",id.toString(),"safety",NeoRules.profile(id.toString()).safety())));
        Files.writeString(Path.of("collapse-form-safety.json"),NeoState.JSON.toJson(forms));
        CollapsePenalties.clear(p);check(NeoRuntime.data(p).burden==0,"Cleanup does not change saved resource");
        System.out.println("Collapse penalties GameTest: "+checks+" checks passed; "+forms.size()+" registered form thresholds exported.");t.complete();
    }
}
