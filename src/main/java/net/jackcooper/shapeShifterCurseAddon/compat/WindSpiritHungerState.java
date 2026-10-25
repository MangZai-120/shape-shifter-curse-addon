package net.jackcooper.shapeShifterCurseAddon.compat;

import net.minecraft.entity.player.PlayerEntity;

/** Owner binding for the server-authoritative hunger hooks. */
public interface WindSpiritHungerState {
    void ssca$setOwner(PlayerEntity player);
}
