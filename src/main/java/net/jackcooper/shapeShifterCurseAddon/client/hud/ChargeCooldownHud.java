package net.jackcooper.shapeShifterCurseAddon.client.hud;

/** Read-only client mirror for SSC charge powers whose vanilla NBT omits cooldowns. */
public interface ChargeCooldownHud {
    int ssca$remainingCooldown();
    int ssca$totalCooldown();
}
