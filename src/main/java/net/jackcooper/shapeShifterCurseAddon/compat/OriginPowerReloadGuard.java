package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.MultiplePowerType;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeReference;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.Origin;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;

/** Preserve SSCA Perk/subform/accessory instances during SSC's same-form reconciliation. */
public final class OriginPowerReloadGuard {
    private static final ThreadLocal<Reload> CURRENT = new ThreadLocal<>();

    private OriginPowerReloadGuard() {}

    /** Only the synchronous, non-forced form load may defer origin cleanup. Never retained across ticks. */
    public static void duringReload(PlayerEntity player, IForm form, boolean forced, Runnable load) {
        var layer = form == null ? null : form.getFormLayer();
        Identifier source = layer == null ? null : layer.getRight();
        Reload previous = CURRENT.get();
        if (forced || !(player instanceof ServerPlayerEntity) || !OriginPowerCompatibility.supportsForm(form) || source == null
                || !(source.getNamespace().equals("my_addon") || source.getNamespace().equals("shape-shifter-curse"))) {
            if (previous == null) load.run();
            else {
                CURRENT.remove();
                try { load.run(); }
                finally { CURRENT.set(previous); }
            }
            return;
        }
        Reload reload = new Reload(player, PowerHolderComponent.KEY.get(player), source);
        CURRENT.set(reload);
        boolean succeeded = false;
        try {
            load.run();
            succeeded = true;
        } finally {
            try {
                if (succeeded && reload.finish()) PowerHolderComponent.KEY.sync(player);
            } finally {
                if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            }
        }
    }

    /** Keep the upstream stale-base cleanup, but allow normal declarations to identify valid additions first. */
    public static boolean deferCleanup(PlayerEntity player, Origin origin, PowerHolderComponent powers) {
        Reload reload = CURRENT.get();
        if (reload == null || reload.player != player || reload.powers != powers
                || !reload.source.equals(origin.getIdentifier())) return false;
        reload.origin = origin;
        for (PowerType<?> type : origin.getPowerTypes()) declareTree(type, reload.requested);
        for (PowerType<?> type : powers.getPowersFromSource(reload.source)) {
            if (!origin.hasPowerType(type)) reload.candidates.add(type);
        }
        return true;
    }

    /** Runs before addPower's already-equipped fast return, including direct accessory and Perk additions. */
    public static void declare(PowerHolderComponent powers, PowerType<?> type, Identifier source) {
        Reload reload = CURRENT.get();
        if (reload != null && !reload.cleaning && reload.powers == powers && reload.source.equals(source))
            declareTree(type, reload.requested);
    }

    /** Removing an obsolete Multiple parent must not remove a separately re-declared child from this source. */
    public static boolean keepDuringCleanup(PowerHolderComponent powers, PowerType<?> type, Identifier source) {
        Reload reload = CURRENT.get();
        if (reload == null || !reload.cleaning || reload.powers != powers || !reload.source.equals(source)) return false;
        type = resolve(type);
        return type != null && (reload.requested.contains(type.getIdentifier())
                || reload.origin != null && reload.origin.hasPowerType(type));
    }

    private static PowerType<?> resolve(PowerType<?> type) {
        return type instanceof PowerTypeReference<?> reference ? reference.getReferencedPowerType() : type;
    }

    private static void declareTree(PowerType<?> type, Set<Identifier> requested) {
        type = resolve(type);
        if (type == null || !requested.add(type.getIdentifier())) return;
        if (type instanceof MultiplePowerType<?> multiple) for (Identifier child : multiple.getSubPowers()) {
            if (PowerTypeRegistry.contains(child)) declareTree(PowerTypeRegistry.get(child), requested);
        }
    }

    private static final class Reload {
        final PlayerEntity player;
        final PowerHolderComponent powers;
        final Identifier source;
        final Set<PowerType<?>> candidates = new LinkedHashSet<>();
        final Set<Identifier> requested = new LinkedHashSet<>();
        Origin origin;
        boolean cleaning;

        Reload(PlayerEntity player, PowerHolderComponent powers, Identifier source) {
            this.player = player;
            this.powers = powers;
            this.source = source;
        }

        boolean finish() {
            boolean removed = false;
            cleaning = true;
            try {
                for (PowerType<?> type : candidates) {
                    if (!keepDuringCleanup(powers, type, source) && powers.hasPower(type, source)) {
                        powers.removePower(type, source);
                        removed = true;
                    }
                }
            } finally { cleaning = false; }
            return removed;
        }
    }
}
