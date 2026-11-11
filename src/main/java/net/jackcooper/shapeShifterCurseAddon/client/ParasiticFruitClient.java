package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.fruit.ParasiticFruitAbility;
import net.jackcooper.shapeShifterCurseAddon.client.sound.ParasiticFruitChargeSoundInstance;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.state.RegParasiticFruitStateComponent;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.client.MinecraftClient;

/** Hold to throw, two short taps to select. UI/control transitions cancel instead of releasing. */
@Environment(EnvType.CLIENT)
public final class ParasiticFruitClient {
    private static Object playerIdentity, worldIdentity;
    private static boolean wasPressed, armed;
    private static long pressedTick, lastTap = -1;
    private static int chargeCues;
    private ParasiticFruitClient() { }
    public static void register() { ClientTickEvents.END_CLIENT_TICK.register(ParasiticFruitClient::tick); }
    private static void tick(MinecraftClient client) {
        var player = client.player;
        var key = SscAddonKeybindings.getPrimaryKey();
        boolean valid = player != null && client.world != null && key != null && player.isAlive()
                && client.currentScreen == null && FormUtils.isForm(player, FormIdentifiers.BAT_PARASITIC_FRUIT)
                && !AbilityUseGuard.blocked(player) && !player.hasStatusEffect(SscAddon.PURIFIED);
        if (!valid || playerIdentity != player || worldIdentity != client.world) {
            chargeCues = 0;
            if (wasPressed && client.getNetworkHandler() != null) SscAddonNetworking.sendEmpty(ParasiticFruitAbility.CANCEL);
            wasPressed = false; armed = false; lastTap = -1;
            playerIdentity = player; worldIdentity = client.world;
            return;
        }
        boolean pressed = key.isPressed();
        if (!armed) { chargeCues = 0; if (!pressed) armed = true; return; }
        long now = System.nanoTime() / 1_000_000;
        if (pressed && !wasPressed) {
            pressedTick = client.world.getTime();
            chargeCues = 0;
            SscAddonNetworking.sendEmpty(ParasiticFruitAbility.START);
        } else if (!pressed && wasPressed) {
            if (client.world.getTime() - pressedTick >= 10) {
                SscAddonNetworking.sendEmpty(ParasiticFruitAbility.RELEASE); lastTap = -1;
            } else {
                SscAddonNetworking.sendEmpty(ParasiticFruitAbility.CANCEL);
                if (lastTap >= 0 && now - lastTap <= 300) {
                    SscAddonNetworking.sendEmpty(ParasiticFruitAbility.CYCLE); lastTap = -1;
                } else lastTap = now;
            }
        }
        // Exactly two one-shot cues per accepted hold: minimum charge, then full charge.
        // Include the release tick so releasing exactly at a threshold still gives its cue.
        if ((pressed || wasPressed) && RegParasiticFruitStateComponent.STATE.get(player).charging()) {
            long heldTicks = client.world.getTime() - pressedTick;
            if (chargeCues == 0 && heldTicks >= 10) {
                client.getSoundManager().play(new ParasiticFruitChargeSoundInstance(false));
                chargeCues = 1;
            }
            if (chargeCues == 1 && heldTicks >= 30) {
                client.getSoundManager().play(new ParasiticFruitChargeSoundInstance(true));
                chargeCues = 2;
            }
        }
        if (!pressed) chargeCues = 0;
        wasPressed = pressed;
    }
}
