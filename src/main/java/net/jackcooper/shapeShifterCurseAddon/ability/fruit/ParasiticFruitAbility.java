package net.jackcooper.shapeShifterCurseAddon.ability.fruit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.jackcooper.shapeShifterCurseAddon.power.ParasiticFruitSeedPower;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager;
import net.jackcooper.shapeShifterCurseAddon.state.RegParasiticFruitStateComponent;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.function.Consumer;

/** Empty gesture packets; elapsed time, costs and selection are owned by the server. */
public final class ParasiticFruitAbility {
    public static final Identifier START = new Identifier("my_addon", "fruit_charge_start");
    public static final Identifier RELEASE = new Identifier("my_addon", "fruit_charge_release");
    public static final Identifier CANCEL = new Identifier("my_addon", "fruit_charge_cancel");
    public static final Identifier CYCLE = new Identifier("my_addon", "fruit_cycle");
    public static final String SKILL_ID = "my_addon:form_bat_parasitic_fruit_parasitic_seed";
    private record Charge(ServerPlayerEntity player, ServerWorld world, long start) { }
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static final Map<UUID, Integer> LAST_CYCLE = new HashMap<>();
    private ParasiticFruitAbility() { }

    public static void init() {
        receiver(START, ParasiticFruitAbility::start);
        receiver(RELEASE, ParasiticFruitAbility::release);
        receiver(CANCEL, ParasiticFruitAbility::cancel);
        receiver(CYCLE, ParasiticFruitAbility::cycle);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) tick(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            cancel(handler.player); LAST_CYCLE.remove(handler.player.getUuid());
            ParasiticFruitLayers.removeOwner(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { CHARGES.clear(); LAST_CYCLE.clear(); });
    }
    private static void receiver(Identifier id, Consumer<ServerPlayerEntity> action) {
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buf, sender) -> {
            if (buf.isReadable()) return;
            server.execute(() -> {
                if (server.getPlayerManager().getPlayer(player.getUuid()) == player && player.networkHandler == handler)
                    action.accept(player);
            });
        });
    }
    public static boolean allowed(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && !player.isRemoved()
                && FormUtils.isForm(player, FormIdentifiers.BAT_PARASITIC_FRUIT)
                && !AbilityUseGuard.blocked(player) && !player.hasStatusEffect(SscAddon.PURIFIED)
                && BalanceIntegration.isPlayerReady(player);
    }
    public static void start(ServerPlayerEntity player) {
        if (!allowed(player) || CHARGES.containsKey(player.getUuid())) return;
        var power = ParasiticFruitSeedPower.get(player);
        if (power == null || !power.isActive() || !SkillCooldowns.ready(player, power.powerIdentifier())) return;
        if (!SpellChannelManager.onSkillAction(player)) return;
        CHARGES.put(player.getUuid(), new Charge(player, player.getServerWorld(), player.getWorld().getTime()));
        chargeState(player, true, 0);
    }
    public static void release(ServerPlayerEntity player) {
        Charge charge = CHARGES.get(player.getUuid());
        if (charge == null || charge.player() != player) return;
        CHARGES.remove(player.getUuid()); chargeState(player, false, 0);
        if (charge.world() != player.getWorld() || !allowed(player)) return;
        int ticks = (int) Math.min(30, Math.max(0, player.getWorld().getTime() - charge.start()));
        if (!FruitSelection.canRelease(ticks) || !SpellChannelManager.onSkillAction(player)) return;
        var power = ParasiticFruitSeedPower.get(player);
        if (power != null) power.throwChargedSeed(player, ticks, ParasiticFruitSeedPower.selection(player));
    }
    public static void cancel(ServerPlayerEntity player) {
        Charge charge = CHARGES.get(player.getUuid());
        if (charge != null && charge.player() == player) {
            CHARGES.remove(player.getUuid()); chargeState(player, false, 0);
        }
    }
    public static void cycle(ServerPlayerEntity player) {
        if (!allowed(player) || CHARGES.containsKey(player.getUuid()) || ParasiticFruitSeedPower.get(player) == null) return;
        int now = player.getServer().getTicks();
        Integer last = LAST_CYCLE.get(player.getUuid());
        if (last != null && now - last < 4) return;
        LAST_CYCLE.put(player.getUuid(), now);
        var state = RegParasiticFruitStateComponent.STATE.get(player);
        state.selection(state.selection().next()); RegParasiticFruitStateComponent.STATE.sync(player);
        player.sendMessage(Text.translatable("message.my_addon.fruit_selection",
                Text.translatable("fruit.my_addon." + state.selection().key())), true);
    }
    private static void tick(ServerPlayerEntity player) {
        var state = RegParasiticFruitStateComponent.STATE.get(player);
        if (player.isAlive()) {
            var form = FormUtils.getCurrentForm(player);
            if (state.observeForm(form == null ? null : form.getFormID())) {
                cancel(player); state.selection(FruitSelection.AUTO);
                RegParasiticFruitStateComponent.STATE.sync(player);
            }
        }
        Charge charge = CHARGES.get(player.getUuid());
        if (charge == null) return;
        if (charge.player() != player || charge.world() != player.getWorld() || !allowed(player)
                || ParasiticFruitSeedPower.get(player) == null) { cancel(charge.player()); return; }
        int ticks = (int) Math.min(30, Math.max(0, player.getWorld().getTime() - charge.start()));
        if (ticks <= 30 && ticks % 5 == 0 && player.getWorld().getTime() - charge.start() <= 30)
            chargeState(player, true, ticks);
    }
    private static void chargeState(ServerPlayerEntity player, boolean active, int ticks) {
        var state = RegParasiticFruitStateComponent.STATE.get(player);
        state.charge(active, ticks); RegParasiticFruitStateComponent.STATE.sync(player);
    }
}
