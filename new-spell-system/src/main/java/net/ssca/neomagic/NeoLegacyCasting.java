package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Book/scroll adapters retain their spells, formations, runes and cooldown ownership.
 * Only one resource exists: NeoRuntime's per-player burden. No book mana is paid. */
public final class NeoLegacyCasting {
    private static final Map<UUID,NeoLegacyCasting> ACTIVE=new HashMap<>();
    private static final ThreadLocal<NeoLegacyCasting> EFFECT=new ThreadLocal<>();
    public final Session session;
    public final NeoState.Experiment record;
    public final String key,form;
    public final int duration;
    private final ServerPlayerEntity player;
    private final int band;
    private boolean settled, hadShift;
    public boolean effectFailed;
    public int age;

    public static NeoLegacyCasting active(ServerPlayerEntity p){return ACTIVE.get(p.getUuid());}
    public static boolean isCasting(ServerPlayerEntity p){return SpellChannelManager.isCasting(p)||active(p)!=null||NeoRuntime.ACTIVE.containsKey(p.getUuid());}
    public static Profile profile(ServerPlayerEntity p) {
        Profile base=NeoRules.profile(NeoRuntime.form(p));ItemStack book=SpellCastManager.getEquippedBook(p);
        double bonus=book==null?0:safetyBonus(book);
        return new Profile(base.safety()+bonus,base.failure(),base.offset(),base.merge(),base.split(),base.reference());
    }
    public static double safetyBonus(ItemStack book) {
        return Math.max(0,SpellbookData.getMaxMana(book)-SpellbookData.getMaxMana(new ItemStack(SscAddon.MOON_DUST_SPELLBOOK)));
    }
    public static FormationElement element(Core core) {return switch(core){case THERMAL->FormationElement.FIRE;case FROST->FormationElement.ICE;case LUNAR->FormationElement.LUNAR;case CURSE->FormationElement.CURSE;case SUMMON->FormationElement.SUMMON;case VOID->FormationElement.VOID;case SPACE->FormationElement.SPACE;};}
    public static Quote blueprintQuote(ServerPlayerEntity p,Blueprint b) {
        Quote q=NeoRules.quote(b,NeoRuntime.data(p).mastery(b));
        double multiplier=blueprintCostMultiplier(p,b.core());
        return new Quote(q.normal()*multiplier,q.actual()*multiplier,q.floor(),q.plan());
    }
    public static double blueprintCostMultiplier(ServerPlayerEntity p,Core core) {
        ItemStack book=SpellCastManager.getEquippedBook(p);return book==null?1:FormationData.sumManaCostMultiplier(book,element(core))*FormAffinity.manaCostMultiplier(p);
    }
    public static double blueprintPower(ServerPlayerEntity p,Blueprint b) {
        return blueprintPower(p,b.output());
    }
    public static double blueprintPower(ServerPlayerEntity p,Core core) {ItemStack book=SpellCastManager.getEquippedBook(p);return book==null?1:FormationData.sumDamageMultiplier(book,element(core))*FormAffinity.damageMultiplier(p,element(core));}
    public static int blueprintCooldown(ServerPlayerEntity p,Blueprint b) {
        int base=NeoRules.cooldown(b);ItemStack book=SpellCastManager.getEquippedBook(p);
        return book==null?base:Math.max(Math.max(10,(int)Math.ceil(base*.2)),Math.round(base*FormationData.sumCooldownMultiplier(book,element(b.core()))*FormAffinity.cooldownMultiplier(p,element(b.core()))));
    }
    public static double recoveryMultiplier(ItemStack book) {
        if(book==null||book.isEmpty())return 1;
        double base=FormCastingStyle.naturalRegenRate(new ItemStack(SscAddon.MOON_DUST_SPELLBOOK));
        return Math.max(0,FormCastingStyle.naturalRegenRate(book)/Math.max(1,base));
    }
    /** Old refund/energy-conversion effects now relieve C, retaining their original trigger and cap. */
    public static int restore(ServerPlayerEntity p,double amount) {
        double before=NeoRuntime.data(p).burden;
        if(amount>0&&before>0)NeoRuntime.relieve(p,amount);
        return (int)Math.ceil(before-NeoRuntime.data(p).burden);
    }
    public static Blueprint descriptor(Spell spell,int level,int duration) {
        var element=spell.getElement();
        Core core=element==null?Core.VOID:switch(element){
            case FIRE->Core.THERMAL;case ICE->Core.FROST;case LUNAR->Core.LUNAR;case CURSE->Core.CURSE;
            case SUMMON->Core.SUMMON;case SPACE->Core.SPACE;default->Core.VOID;};
        String path=spell.getId().getPath();
        Carrier carrier=switch(core){
            case SPACE->Carrier.LOCK;case SUMMON->Carrier.AREA;
            case LUNAR->path.equals("lunar_mend")||path.equals("lunar_veil")?Carrier.SELF:spell.getAimMaxRange(level)>0?Carrier.LOCK:Carrier.PROJECTILE;
            case CURSE->Carrier.LOCK;default->spell.getAimMaxRange(level)>0?Carrier.AREA:Carrier.PROJECTILE;};
        return new Blueprint(path.length()>32?path.substring(0,32):path,core,carrier,List.of(),Core.FROST,
                Math.max(.5,Math.min(4,level)),Math.min(80,Math.max(0,duration)),Math.max(1,Math.min(32,spell.getAimMaxRange(level)>0?spell.getAimMaxRange(level):32)));
    }
    public NeoLegacyCasting(ServerPlayerEntity p,Spell spell,int level,int duration,int normalCost) {
        player=p;this.duration=duration;form=NeoRuntime.form(p);key="spell:"+spell.getId();
        var d=NeoRuntime.data(p);var b=descriptor(spell,level,duration);band=b.band();
        Quote quote=quote(p,spell,level,duration,normalCost);double normal=quote.normal();
        session=new Session(b,quote,duration,spell.getLockInTick());
        record=new NeoState.Experiment();record.blueprint=b;record.spell=spell.getId().toString();record.preparation=duration;
        record.form=form;record.profile=profile(p);record.initial=d.burden;record.normal=normal;record.actual=session.quote.actual();
        record.dimension=p.getWorld().getRegistryKey().getValue().toString();d.add(record);
        ACTIVE.put(p.getUuid(),this);NeoRuntime.dirty(p);
    }
    public static Quote quote(ServerPlayerEntity p,Spell spell,int level,int duration,int normalCost) {
        var b=descriptor(spell,level,duration);String key="spell:"+spell.getId();
        int tier=Math.max(1,spell.getRarity(level).ordinal()+1);
        Tuning t=NeoRules.tunings().getOrDefault(key,Tuning.defaults());
        double needed=t.mastery()>0?t.mastery():20.0*tier*tier;
        var progress=NeoRuntime.data(p).progress.get(key);double mastery=progress==null?0:clamp(progress[b.band()]/needed,0,1);
        double floor=t.floor()>0?t.floor():tier<=2?.5:tier<=4?.4:.35;
        double normal=Math.max(0,normalCost)*t.cost();
        return new Quote(normal,normal*(1-(1-floor)*mastery),floor,plan(b));
    }
    /** One call per actual server tick; holding a prepared spell keeps rolling but never repays. */
    public boolean advance(int elapsed) {
        if(settled)return false;
        age=elapsed;var d=NeoRuntime.data(player);
        double paid=session.advance(elapsed,d.burden,profile(player),new Random(player.getRandom().nextLong()),false);
        d.burden+=paid;if(paid>0){d.recovery=NeoRules.recoveryDelay;d.lastPaymentTick=player.getServer().getTicks();}
        hadShift|=session.checkpoints.stream().anyMatch(Checkpoint::shifted);
        // Keep start plus the latest checkpoints for an indefinitely held legacy key.
        while(session.checkpoints.size()>512){session.checkpoints.remove(1);record.omittedChecks++;}
        record.paid=session.paid;record.checks=new ArrayList<>(session.checkpoints);
        record.failureBasis=session.failureBasis;record.failureProbability=session.failureProbability;record.failureDraws=session.failureDraws;record.failureBoundary=session.failureBoundary;
        record.yaw=session.yaw;record.pitch=session.pitch;record.reversed=session.reversed;
        NeoRuntime.dirty(player);
        if(session.failed){finish(false,"failed");return false;}
        return true;
    }
    public void finish(boolean success,String result) {
        if(settled)return;settled=true;ACTIVE.remove(player.getUuid(),this);record.result=result;
        if(success){var values=NeoRuntime.data(player).progress.computeIfAbsent(key,k->new double[4]);
            for(int i=0;i<=band;i++)values[i]=Math.min(100000,values[i]+1);}
        NeoRuntime.dirty(player);NeoNetwork.hud(player);NeoNetwork.send(player,false);
    }
    public Vec3d target(Vec3d target) {
        if(target==null)return null;
        Vec3d origin=player.getEyePos(),v=target.subtract(origin);
        v=v.rotateX((float)Math.toRadians(session.pitch)).rotateY((float)Math.toRadians(-session.yaw));
        if(session.blueprint.output()==Core.SPACE)v=v.multiply(session.attenuation);
        return origin.add(v);
    }
    /** Rotation is scoped to constructing the released effect; it never moves the client's camera. */
    public void effect(Runnable action) {
        // Anchor/dimension spells cannot safely reuse their original guaranteed destination after a bad lock.
        if(hadShift&&(record.spell.endsWith(":space_recall")||record.spell.endsWith(":pocket_space"))){effectFailed=true;return;}
        float yaw=player.getYaw(),pitch=player.getPitch();var previous=EFFECT.get();
        EFFECT.set(this);
        // Teleport packets include the current camera angles. Position deviation is already in target().
        if(session.blueprint.output()!=Core.SPACE){player.setYaw(yaw+(float)session.yaw);player.setPitch((float)clamp(pitch+session.pitch,-90,90));}
        try{action.run();}finally{player.setYaw(yaw);player.setPitch(pitch);if(previous==null)EFFECT.remove();else EFFECT.set(previous);}
    }
    public static float power(float power) {var a=EFFECT.get();return a==null?power:(float)(power*a.session.attenuation);}
    public static int actualCost(int fallback) {var a=EFFECT.get();return a==null?fallback:(int)Math.ceil(a.session.quote.actual());}
    public static boolean reversed() {var a=EFFECT.get();return a!=null&&a.session.reversed;}
    public static int summonCount(int count) {var a=EFFECT.get();return a==null?count:(int)Math.floor(count*a.session.attenuation+1e-9);}
    public static boolean shifted() {var a=EFFECT.get();return a!=null&&a.hadShift;}
    public static boolean wrongTarget(){var a=EFFECT.get();return a!=null&&a.session.wrongTarget;}
    public static void miss(){var a=EFFECT.get();if(a!=null)a.effectFailed=true;}
    public static boolean missed(){var a=EFFECT.get();return a!=null&&a.effectFailed;}
    private NeoLegacyCasting(){throw new AssertionError();}
}
