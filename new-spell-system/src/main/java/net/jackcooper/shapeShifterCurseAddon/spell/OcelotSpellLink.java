package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.ssca.neomagic.NeoNetwork;
import net.ssca.neomagic.NeoRuntime;
import java.util.*;

/** Wind movement and Nova's temporary post-nine-lives preparation shortcut. */
public final class OcelotSpellLink {
    private static final BalanceReader BAL=new BalanceReader("systems.form_casting");
    private record Window(ServerPlayerEntity player,ServerWorld world,long until) {}
    private static final Map<UUID,Window> WINDOWS=new HashMap<>();
    public static java.util.function.ToLongFunction<PlayerEntity> clientQuickUntil=player->0;
    public static java.util.function.Predicate<PlayerEntity> clientReleaseLocked=player->false;
    private OcelotSpellLink() {}
    public static boolean wind(PlayerEntity player) {
        return player!=null&&FormUtils.isForm(player,FormIdentifiers.OCELOT_SP);
    }
    public static double walkingMultiplier(){return BAL.d("wind_cast_walking_multiplier",.3);}
    /** Match SSC's actual dry, fed sneak-rush condition rather than treating every crouch as running. */
    public static boolean sneakRush(PlayerEntity player) {
        return wind(player)&&player.isSneaking()&&player.getFluidHeight(net.minecraft.registry.tag.FluidTags.WATER)<=0
                &&player.getHungerManager().getFoodLevel()>=6;
    }
    public static boolean rushing(PlayerEntity player){return wind(player)&&(player.isSprinting()||sneakRush(player));}
    public static boolean windLocked(PlayerEntity player) {
        return wind(player)&&(player instanceof ServerPlayerEntity server
                ?SpellChannelManager.releaseLocked(server):clientReleaseLocked.test(player));
    }
    /** Called only by the successful nine-lives revival path, including its self-detonation use. */
    public static void onNineLivesRevive(ServerPlayerEntity player) {
        if(!player.isAlive()||!FormUtils.isForm(player,FormIdentifiers.OCELOT_NOVA))return;
        NeoRuntime.refreshForm(player);
        WINDOWS.put(player.getUuid(),new Window(player,player.getServerWorld(),player.getWorld().getTime()+BAL.i("nova_quick_cast_ticks",100)));
        NeoNetwork.hud(player);
    }
    public static long quickUntil(PlayerEntity player) {
        if(player==null||!player.isAlive()||!FormUtils.isForm(player,FormIdentifiers.OCELOT_NOVA))return 0;
        if(!(player instanceof ServerPlayerEntity server))return clientQuickUntil.applyAsLong(player);
        Window w=WINDOWS.get(player.getUuid());if(w==null||w.player!=player)return 0;
        if(w.world!=player.getWorld()||player.getWorld().getTime()>=w.until){WINDOWS.remove(player.getUuid(),w);return 0;}
        return w.until;
    }
    public static boolean quickCast(PlayerEntity player){return quickUntil(player)>0;}
    public static void clear(ServerPlayerEntity player){Window w=WINDOWS.get(player.getUuid());if(w!=null&&w.player==player)WINDOWS.remove(player.getUuid(),w);}
    public static void register() {
        // A replacement connection must not retain the old player's world/network references.
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            Window old=WINDOWS.get(handler.player.getUuid());
            if(old!=null&&old.player!=handler.player)WINDOWS.remove(handler.player.getUuid(),old);
        });
        net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup.register((handler,server)->clear(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof ServerPlayerEntity player)clear(player);});
        ServerPlayerEvents.AFTER_RESPAWN.register((old,player,alive)->{clear(old);clear(player);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->WINDOWS.clear());
    }
}
