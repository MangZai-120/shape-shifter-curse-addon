package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.network.NeoNetwork;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;

/** Actual progressive payment and cooldown callbacks, beyond the pure Session tests. */
public final class NeoFailureWindowAudit {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void start(TestContext t) {
        t.waitAndRun(70, () -> {
            try {
                start(t, NeoBookWorldProbe.player(t), NeoBookWorldProbe.player(t));
                t.waitAndRun(24, t::complete);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }
    public static void start(TestContext t,ServerPlayerEntity risky,ServerPlayerEntity protectedPlayer) {
        var id=SpellRegistry.get("fire_bolt").getId();int[] effects={0,0},cooldowns={0,0};
        var ordinary=new Spell(id,SpellRarity.WHITE){
            @Override public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity p,int level,boolean solo){return new SpellCastingRules.Profile(20,1,false);}
            @Override public void cast(ServerPlayerEntity p,float power,boolean solo){}
        };
        boolean[] releaseLocked={false};
        var locking=new Spell(id,SpellRarity.WHITE){
            @Override public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity p,int level,boolean solo){return new SpellCastingRules.Profile(20,1,false);}
            @Override public int getLockInTick(){return 15;}
            @Override public boolean isLockedIn(ServerPlayerEntity p){return releaseLocked[0];}
            @Override public void tickChannel(ServerPlayerEntity p,int level,net.minecraft.item.ItemStack scroll,int ticks){if(ticks>=5)releaseLocked[0]=true;}
            @Override public void cast(ServerPlayerEntity p,float power,boolean solo){}
        };
        check(NeoRuntime.profile(risky).safety()==100&&NeoRuntime.profile(protectedPlayer).safety()==100,"Fixtures have N=100 and 2N=200");
        check(SpellChannelManager.start(risky,ordinary,ScrollData.create("fire_bolt"),1,false,801,300,100,()->true,v->effects[0]++,c->cooldowns[0]=c,()->{}),"Expensive ordinary cast starts while safe");
        check(SpellChannelManager.start(protectedPlayer,locking,ScrollData.create("fire_bolt"),1,false,802,300,100,()->true,v->effects[1]++,c->cooldowns[1]=c,()->{}),"Expensive protected cast starts while safe");
        var first=NeoLegacyCasting.active(risky);var second=NeoLegacyCasting.active(protectedPlayer);
        check(first.session.failureProbability==1&&second.session.failureProbability==1,"Both use their projected C+Q>2N failure draw");
        t.waitAndRun(3,()->{
            check(SpellChannelManager.isCasting(risky)&&NeoRuntime.data(risky).burden<=100,"Actual server channel stays active inside safety");
            check(NeoNetwork.hudState(risky).getDouble("Failure")==0&&NeoNetwork.hudState(risky).getDouble("ForecastFailure")==1,"HUD distinguishes current safety from projected whole-cast risk");
        });
        t.waitAndRun(11,()->{
            check(second.session.failureBoundary==15&&second.session.isFailureProtected(),"Live release state protects earlier than the predicted lock tick");
            check(SpellChannelManager.isCasting(protectedPlayer)&&NeoRuntime.data(protectedPlayer).burden>100,"Protected release keeps paying through actual overload");
            check(NeoNetwork.hudState(protectedPlayer).getDouble("Failure")==0,"HUD reports no failure risk in the live protected release stage");
        });
        t.waitAndRun(23,()->{
            check(first.session.failed&&NeoRuntime.data(risky).burden>100,"Actual ordinary channel fails only after crossing N");
            check(effects[0]==0&&cooldowns[0]==100&&first.session.failureDraws==1,"Failure releases no effect, charges paid burden only and settles full cooldown");
            check(NeoRuntime.data(risky).spellbookMasteryExpTen==0,"Failure grants no player experience");
            check(!second.session.failed&&effects[1]==1&&cooldowns[1]==100,"Lock reached while safe protects later payment and allows effect release");
            check(second.session.paid==300&&second.session.failureDraws==1,"Protected cast still pays the full quote without rerolling");
            System.out.println("NEO_FAILURE_WINDOW_AUDIT_PASS: safe ticks, projected 2N failure, actual paid burden, full cooldown and pre-lock protection");
        });
    }
}
