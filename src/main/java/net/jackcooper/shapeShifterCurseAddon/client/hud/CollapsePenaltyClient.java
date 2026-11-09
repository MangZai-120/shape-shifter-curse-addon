package net.jackcooper.shapeShifterCurseAddon.client.hud;

import java.util.Map;
import net.jackcooper.shapeShifterCurseAddon.mixin.client.LivingEntityStatusEffectsAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.jackcooper.shapeShifterCurseAddon.client.NeoClient;

/** Visual carriers only: health loss is exclusively authoritative on the server. */
public final class CollapsePenaltyClient {
    private static Map<StatusEffect, StatusEffectInstance> effects;
    private static final StatusEffect[] TYPES = {StatusEffects.NAUSEA, StatusEffects.BLINDNESS, StatusEffects.WITHER};
    private static final StatusEffectInstance[] OWNED = new StatusEffectInstance[3];
    private CollapsePenaltyClient() {}

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null || !client.player.isAlive() || client.player.isSpectator()) {
            clear(); return;
        }
        var current = ((LivingEntityStatusEffectsAccessor) client.player).sscAddon$getActiveStatusEffects();
        if (effects != current) { clear(); effects = current; }
        var hud = NeoClient.hud;
        int stage = hud.getInt("PlayerId") == client.player.getId()
                && hud.getString("Dimension").equals(client.world.getRegistryKey().getValue().toString())
                ? hud.getInt("CollapseStage") : 0;
        for (int i = 0; i < TYPES.length; i++) {
            boolean active = stage >= (i == 0 ? 4 : 5);
            StatusEffectInstance effect = effects.get(TYPES[i]);
            if (!active) {
                if (effect != null && effect == OWNED[i]) effects.remove(TYPES[i]);
                OWNED[i] = null;
            } else if (effect == null || effect == OWNED[i] && effect.getDuration() < 40) {
                OWNED[i] = new StatusEffectInstance(TYPES[i], 200, i == 2 ? 2 : 0, false, false, false);
                effects.put(TYPES[i], OWNED[i]);
            } else if (effect != OWNED[i]) {
                OWNED[i] = null; // Real server potions and nightmare effects retain their ownership.
            }
        }
    }

    public static void clear() {
        for (int i = 0; i < TYPES.length; i++) {
            if (effects != null && OWNED[i] != null && effects.get(TYPES[i]) == OWNED[i]) effects.remove(TYPES[i]);
            OWNED[i] = null;
        }
        effects = null;
    }
}
