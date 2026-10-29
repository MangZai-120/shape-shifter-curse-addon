package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.jackcooper.shapeShifterCurseAddon.ability.FrostSpikeManager;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.entity.FrostThornEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.ssca.neomagic.NeoNetwork;
import net.ssca.neomagic.NeoRuntime;
import java.util.*;

/** Ice-only native-form collaboration. Quotes never consume a buff or a hovering thorn. */
public final class FrostSpellLink {
    private static final BalanceReader BAL=new BalanceReader("systems.form_casting");
    private record Ready(ServerPlayerEntity player,ServerWorld world,long until) {}
    private static final Map<UUID,Ready> READY=new HashMap<>();
    public static java.util.function.ToDoubleFunction<PlayerEntity> clientMultiplier=player->1;
    private FrostSpellLink() {}

    public static void onNativeHit(ServerPlayerEntity caster,LivingEntity target) {
        if(caster==null||target==null||!caster.isAlive()||caster.isRemoved()||target instanceof ArmorStandEntity
                ||target.isSpectator()||target.getWorld()!=caster.getWorld()
                ||!FormUtils.isForm(caster,FormIdentifiers.SNOW_FOX_SP)||WhitelistUtils.isProtected(caster,target))return;
        NeoRuntime.refreshForm(caster);
        long until=caster.getWorld().getTime()+BAL.i("snow_fox_ready_ticks",60);
        Ready previous=ready(caster);
        if(previous!=null&&previous.until==until)return; // An area hit refreshes once per server tick.
        READY.put(caster.getUuid(),new Ready(caster,caster.getServerWorld(),until));
        NeoNetwork.hud(caster);
    }
    private static Ready ready(ServerPlayerEntity player) {
        Ready value=READY.get(player.getUuid());
        if(value!=null&&value.player!=player)return null;
        if(value!=null&&(value.world!=player.getWorld()||!player.isAlive()
                ||!FormUtils.isForm(player,FormIdentifiers.SNOW_FOX_SP)||player.getWorld().getTime()>=value.until)) {
            READY.remove(player.getUuid(),value);return null;
        }
        return value;
    }
    public static long readyUntil(ServerPlayerEntity player){Ready value=ready(player);return value==null?0:value.until;}
    public static double multiplier(PlayerEntity player,boolean ice) {
        if(!ice||player==null)return 1;
        if(!(player instanceof ServerPlayerEntity server))return clientMultiplier.applyAsDouble(player);
        if(FormUtils.isForm(server,FormIdentifiers.SNOW_FOX_SP))return ready(server)==null?1:1-BAL.d("snow_fox_collapse_reduction",.15);
        if(FormUtils.isForm(server,FormIdentifiers.SNOW_FOX_FROSTSPINE))
            return Math.max(0,1-FrostSpikeManager.hoveringThorns(server).size()*BAL.d("frostspine_reduction_per_thorn",.05));
        return 1;
    }
    public static Payment plan(ServerPlayerEntity player,boolean ice) {
        if(!ice)return null;
        if(FormUtils.isForm(player,FormIdentifiers.SNOW_FOX_SP)) {
            Ready buff=ready(player);
            return buff==null?null:new Payment(player,buff,List.of(),1-BAL.d("snow_fox_collapse_reduction",.15),0);
        }
        if(!FormUtils.isForm(player,FormIdentifiers.SNOW_FOX_FROSTSPINE))return null;
        var thorns=FrostSpikeManager.hoveringThorns(player);
        return thorns.isEmpty()?null:new Payment(player,null,thorns,
                Math.max(0,1-thorns.size()*BAL.d("frostspine_reduction_per_thorn",.05)),BAL.i("frostspine_lifetime_cost_ticks",360));
    }
    public static final class Payment {
        private final ServerPlayerEntity player;
        private final Ready buff;
        private final List<FrostThornEntity> thorns;
        private final int lifetimeCost;
        public final double multiplier;
        private boolean paid;
        private int lifetimeSpent;
        private Payment(ServerPlayerEntity player,Ready buff,List<FrostThornEntity> thorns,double multiplier,int lifetimeCost) {
            this.player=player;this.buff=buff;this.thorns=thorns;this.multiplier=multiplier;this.lifetimeCost=lifetimeCost;
        }
        public boolean pay() {
            if(paid)return true;
            if(!player.isAlive()||player.isRemoved()||!thorns.isEmpty()&&!FormUtils.isForm(player,FormIdentifiers.SNOW_FOX_FROSTSPINE))return false;
            if(buff!=null&&ready(player)!=buff)return false;
            if(thorns.stream().anyMatch(t->t.isRemoved()||!t.isHover()||t.getWorld()!=player.getWorld()))return false;
            if(buff!=null)READY.remove(player.getUuid(),buff);
            for(var thorn:thorns)lifetimeSpent+=thorn.spendHoverLifetime(lifetimeCost);
            paid=true;return true;
        }
        public void record(net.ssca.neomagic.NeoState.Experiment record) {
            if(!paid)return;
            record.snowReadyUsed=buff!=null;record.frostThornsSpent=thorns.size();record.frostLifetimeTicksSpent=lifetimeSpent;
        }
    }
    public static void clear(ServerPlayerEntity player) {
        Ready value=READY.get(player.getUuid());if(value!=null&&value.player==player)READY.remove(player.getUuid(),value);
    }
    public static void register() {
        net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup.register((handler,server)->clear(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((old,player,alive)->clear(old));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->READY.clear());
    }
}
