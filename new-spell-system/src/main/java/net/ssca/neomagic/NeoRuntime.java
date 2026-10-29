package net.ssca.neomagic;

import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.minecraft.entity.effect.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

public final class NeoRuntime {
    public static final Map<UUID,Active> ACTIVE = new HashMap<>();
    public static final class Active {
        public final Session session;
        public final NeoState.Experiment record;
        public final ItemStack wand;
        public final Hand hand;
        public final NeoEffects.Anchor anchor;
        private final Random random;
        public int fullCooldown;
        public double powerMultiplier=1;
        public double experienceMultiplier=1;
        public double rangeMultiplier=1;
        public boolean legacyBook,legacyReleased;
        public UUID refundCastId;
        public float curseDurationMultiplier=1;
        public net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle style;
        public int age, beganTick;
        public boolean settled;
        Active(Session s,NeoState.Experiment r,ItemStack wand,Hand hand,NeoEffects.Anchor anchor,Random random) {
            session=s;record=r;this.wand=wand;this.hand=hand;this.anchor=anchor;fullCooldown=NeoRules.cooldown(s.blueprint);
            this.random=random;record.checks=session.checkpoints;
        }
    }
    public static NeoState.PlayerData data(ServerPlayerEntity p) { return NeoState.get(p.getServer()).player(p.getUuid()); }
    public static Profile profile(ServerPlayerEntity p) {return NeoLegacyCasting.profile(p);}
    public static String form(ServerPlayerEntity p) {
        var f=FormUtils.getCurrentForm(p);
        return f==null || f.getFormID()==null?"shape-shifter-curse:original_shifter":f.getFormID().toString();
    }
    public static void refreshForm(ServerPlayerEntity p) {
        var d=data(p);String current=form(p);
        if(!current.equals(d.form)) {
            net.jackcooper.shapeShifterCurseAddon.spell.FrostSpellLink.clear(p);
            net.jackcooper.shapeShifterCurseAddon.spell.OcelotSpellLink.clear(p);
            if(ACTIVE.containsKey(p.getUuid())) cancel(p,"form_changed");
            NeoEffects.clearStored(p.getUuid());
            d.burden=0;d.recovery=0;d.form=current;dirty(p);
        }
    }
    public static boolean blocked(ServerPlayerEntity p) {
        return p.hasStatusEffect(NeoMagic.DISORDER)||p.hasStatusEffect(NeoMagic.DISINTEGRATION)||p.hasStatusEffect(NeoMagic.BREAKDOWN)
                ||p.hasStatusEffect(SscAddon.PURIFIED)||AbilityUseGuard.blocked(p)
                ||p.isSpectator()||!p.isAlive();
    }
    public static void start(ServerPlayerEntity p,Hand hand) {
        refreshForm(p);var d=data(p);Blueprint b=NeoNetwork.bound(p.getStackInHand(hand));
        if(b==null){message(p,"need_blueprint");return;}
        if(!p.getStackInHand(hand).isOf(NeoMagic.WAND)||blocked(p)||NeoLegacyCasting.isCasting(p)||NeoEffects.hasStored(p.getUuid())) {message(p,"blocked");return;}
        if(!errors(b).isEmpty()||!d.knows(b)) {message(p,"invalid");return;}
        if(d.cooldowns.getOrDefault(b.identity(),0)>0) {message(p,"cooldown");return;}
        NeoEffects.Anchor anchor=NeoEffects.capture(p,b);
        if(!NeoEffects.preflight(p,b,anchor)) {message(p,"target_invalid");return;}
        Quote quote=NeoLegacyCasting.blueprintQuote(p,b);
        var style=net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle.plan(p,b.carrier()==Carrier.PROJECTILE,
                b.range()*NeoLegacyCasting.blueprintRangeMultiplier(p,b),b.prep(),b.prep(),-1,b.output()==Core.FROST);
        if(!style.pay())return;
        var e=new NeoState.Experiment();e.blueprint=b;e.form=d.form;e.profile=NeoRules.profile(d.form);e.initial=d.burden;
        e.normal=quote.normal();e.actual=quote.actual();e.profile=profile(p);e.preparation=style.timing.duration();e.dimension=p.getWorld().getRegistryKey().getValue().toString();
        e.target=anchor.target()==null?anchor.position().toString():anchor.target().toString();
        d.add(e);var active=new Active(new Session(b,quote,style.timing.duration()),e,p.getStackInHand(hand),hand,anchor,new Random(p.getRandom().nextLong()));
        active.style=style;
        active.record.novaQuickCast=style.novaQuickCast;
        active.record.moistureSpent=style.paidMoisture();
        if(style.frost!=null)style.frost.record(active.record);
        if(style.healing!=null)style.healing.onPayment(amount->active.record.moistureSpent+=amount);
        active.fullCooldown=NeoLegacyCasting.blueprintCooldown(p,b);active.powerMultiplier=NeoLegacyCasting.blueprintPower(p,b);
        NeoLegacyCasting.snapshotBlueprintAffinity(p,active);
        active.experienceMultiplier=NeoLegacyCasting.blueprintExperienceMultiplier(p);
        active.rangeMultiplier=NeoLegacyCasting.blueprintRangeMultiplier(p,b)*style.projectileRange;
        active.beganTick=p.getServer().getTicks();ACTIVE.put(p.getUuid(),active);
        if(net.jackcooper.shapeShifterCurseAddon.spell.OcelotSpellLink.wind(p))
            net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager.applyMovement(p,net.jackcooper.shapeShifterCurseAddon.spell.OcelotSpellLink.walkingMultiplier());
        advance(p,active);NeoNetwork.send(p,false);
    }
    private static void advance(ServerPlayerEntity p,Active a) {
        if(a.settled)return;
        var d=data(p);var s=a.session;
        Profile live=s.needsRisk(a.age)?profile(p):null;
        double delta=s.advance(a.age,d.burden,live,a.random);
        d.burden+=delta;if(delta>0){d.recovery=NeoRules.recoveryDelay;d.lastPaymentTick=p.getServer().getTicks();}
        a.record.paid=s.paid;a.record.yaw=s.yaw;a.record.pitch=s.pitch;a.record.reversed=s.reversed;
        a.record.failureBasis=s.failureBasis;a.record.failureProbability=s.failureProbability;a.record.failureDraws=s.failureDraws;a.record.failureBoundary=s.failureBoundary;
        if(s.failed) fail(p,a);
        else if(s.complete) {
            ACTIVE.remove(p.getUuid());
            net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager.clearSlow(p);
            // Effects receive one immutable outcome; no later collapse checks run on it.
            if(NeoEffects.release(p,a)) {
                a.record.result=s.quote.plan().stored()?"stored":"released";
                setCooldown(p,s.blueprint.identity(),a.fullCooldown);a.settled=true;
                if(!s.quote.plan().stored()){
                    d.successful(s.blueprint);
                    grantExperience(p,a);
                }
            } else if(s.hasShifted) fail(p,a);
            else {
                a.record.result="target_lost";
                setCooldown(p,s.blueprint.identity(),Math.max(10,a.fullCooldown/2));a.settled=true;
                message(p,"target_invalid");
            }
            NeoNetwork.send(p,false);
        }
        dirty(p);
    }
    public static void fail(ServerPlayerEntity p,Active a) {
        if(a.settled)return;a.settled=true;
        ACTIVE.remove(p.getUuid());a.record.result="failed";
        net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager.clearSlow(p);
        var d=data(p);setCooldown(p,a.session.blueprint.identity(),a.fullCooldown);
        backlash(p,a.session.quote.actual());
    }
    static void grantExperience(ServerPlayerEntity p,Active a) {
        double amount=a.session.quote.normal()*a.session.blueprint.tier()*a.experienceMultiplier;
        net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.grant(p,null,(int)Math.min(Integer.MAX_VALUE,Math.round(amount)));
    }
    public static void backlash(ServerPlayerEntity p,double actualCost) {
        var d=data(p);
        double n=profile(p).safety();
        double excess=60*Math.max(0,d.burden-n)/Math.max(1,n);
        if(excess>0) {
            p.damage(p.getDamageSources().magic(),(float)(1.5+.05*excess+.02*actualCost));
            double serious=clamp((excess-20)/80,0,1);
            if(excess>60 && p.getRandom().nextDouble()<clamp((excess-60)/40,0,1)) {
                p.removeStatusEffect(NeoMagic.DISORDER);p.removeStatusEffect(NeoMagic.DISINTEGRATION);
                p.addStatusEffect(new StatusEffectInstance(NeoMagic.BREAKDOWN,300,(int)Math.min(4,(excess-60)/40)));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,300));
            } else if(!p.hasStatusEffect(NeoMagic.BREAKDOWN)&&p.getRandom().nextDouble()<serious) {
                p.removeStatusEffect(NeoMagic.DISORDER);
                p.addStatusEffect(new StatusEffectInstance(NeoMagic.DISINTEGRATION,300));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,300));
            } else if(!p.hasStatusEffect(NeoMagic.BREAKDOWN)&&!p.hasStatusEffect(NeoMagic.DISINTEGRATION)&&p.getRandom().nextDouble()<.45) p.addStatusEffect(new StatusEffectInstance(NeoMagic.DISORDER,100));
        }
        message(p,"failed");dirty(p);
    }
    public static void cancel(ServerPlayerEntity p,String reason) {
        Active a=ACTIVE.remove(p.getUuid());if(a==null)return;
        net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager.clearSlow(p);
        a.record.result=reason;a.settled=true;setCooldown(p,a.session.blueprint.identity(),Math.max(10,a.fullCooldown/2));
        dirty(p);
    }
    public static void releaseOrCancel(ServerPlayerEntity p) {
        if(NeoEffects.hasStored(p.getUuid()))NeoEffects.trigger(p);else cancel(p,"cancelled");
        NeoNetwork.send(p,false);
    }
    public static void relieve(ServerPlayerEntity p,double amount) {
        refreshForm(p);data(p).burden=Math.max(0,data(p).burden-amount);dirty(p);NeoNetwork.send(p,false);
    }
    public static void dirty(ServerPlayerEntity p) { NeoState.get(p.getServer()).markDirty(); }
    public static void message(ServerPlayerEntity p,String message) { p.sendMessage(NeoMagic.text(message),true); }
    private static void setCooldown(ServerPlayerEntity p,String identity,int ticks) {
        var d=data(p);d.cooldowns.put(identity,ticks);d.cooldownStarted.put(identity,p.getServer().getTicks());
    }
    private static void tick(MinecraftServer server) {
        for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList()) {
            refreshForm(p);var d=data(p);double prior=d.burden;int priorRecovery=d.recovery;boolean hadCooldowns=!d.cooldowns.isEmpty();var a=ACTIVE.get(p.getUuid());
            d.cooldowns.replaceAll((key,ticks)->d.cooldownStarted.getOrDefault(key,Integer.MIN_VALUE)==server.getTicks()?ticks:Math.max(0,ticks-1));
            d.cooldowns.values().removeIf(ticks->ticks==0);d.cooldownStarted.keySet().retainAll(d.cooldowns.keySet());
            if(a!=null) {
                if(blocked(p)||net.jackcooper.shapeShifterCurseAddon.spell.OcelotSpellLink.wind(p)&&p.isSprinting()
                        ||p.getStackInHand(a.hand)!=a.wand||!a.record.dimension.equals(p.getWorld().getRegistryKey().getValue().toString()))cancel(p,"interrupted");
                else if(server.getTicks()>a.beganTick){a.age++;advance(p,a);}
            } else if(p.isAlive()&&!NeoLegacyCasting.isCasting(p)) {
                if(server.getTicks()>d.lastPaymentTick){if(d.recovery>0)d.recovery--;else if(d.burden>0)d.burden=Math.max(0,d.burden-NeoRules.recoveryRate*NeoLegacyCasting.recoveryMultiplier(net.jackcooper.shapeShifterCurseAddon.spell.SpellCastManager.getEquippedBook(p)));}
            }
            if(server.getTicks()%5==0)NeoNetwork.hud(p);
            if(prior!=d.burden||priorRecovery!=d.recovery||hadCooldowns)dirty(p);
        }
        NeoEffects.tick(server);
    }
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(NeoRuntime::tick);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success)->{if(success)server.getPlayerManager().getPlayerList().forEach(p->NeoNetwork.send(p,false));});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->server.execute(()->{
            var online=server.getPlayerManager().getPlayer(handler.player.getUuid());
            if(online!=null&&online.networkHandler!=handler)return;
            cancel(handler.player,"disconnected");NeoEffects.clearStored(handler.player.getUuid());dirty(handler.player);NeoNetwork.close(handler.player);
        }));
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->{cancel(old,"death");var d=data(p);d.burden=0;d.recovery=0;NeoEffects.clearStored(p.getUuid());dirty(p);});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            for(var p:server.getPlayerManager().getPlayerList())cancel(p,"server_stopped");
            NeoEffects.clear();for(var p:server.getPlayerManager().getPlayerList())dirty(p);NeoNetwork.clear();ACTIVE.clear();
        });
    }
}
