package net.jackcooper.shapeShifterCurseAddon.ability.snowfox;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class SnowFoxMotion implements EntityComponentInitializer {
    public static final ComponentKey<SnowFoxState> STATE = ComponentRegistry.getOrCreate(
            new Identifier("ssc_addon", "snow_fox_motion"), SnowFoxState.class);
    public static final Identifier DIVE = new Identifier("my_addon", "snow_fox_super_dive");
    public static final Identifier ROLL = new Identifier("my_addon", "snow_fox_rolling_charge");
    public static final Identifier STEER = new Identifier("my_addon", "snow_fox_steer");
    public static final Identifier SPIKES = new Identifier("my_addon", "snow_fox_spike_front");

    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(STATE, SnowFoxState::new, RespawnCopyStrategy.NEVER_COPY);
    }

    public static void register() {
        SnowFoxSounds.init();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) ->
                !(victim instanceof ServerPlayerEntity player) || !STATE.get(player).absorb(player, amount));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATE.get(handler.player).interrupt(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) STATE.get(player).interrupt(player);
        });
    }
}
