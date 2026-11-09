package net.jackcooper.shapeShifterCurseAddon.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.ability.NineLivesManager;
import net.jackcooper.shapeShifterCurseAddon.mixin.entity.LivingEntityTotemInvoker;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.network.NeoNetwork;

/** Server-owned penalties; no potion immunity, damage conversion or random lingering cast locks. */
public final class CollapsePenalties {
    private static final UUID SLOW = UUID.fromString("587f5108-6c90-464f-9226-937f876c195b");
    private static final double[] SLOWS = {0, .15, .30, .40, .60, .80};
    private static final RegistryKey<DamageType> DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            new Identifier("ssc_addon", "collapse"));
    private static final Map<UUID, State> PLAYERS = new HashMap<>();
    private static final class State {
        final ServerPlayerEntity player;
        int stage, damagingTicks;
        State(ServerPlayerEntity player) { this.player = player; }
    }
    private CollapsePenalties() {}

    /** Exact boundaries belong to the lower band; C == N is always safe. */
    public static int stage(double collapse, double safety) {
        if (!(collapse > safety) || !(safety > 0) || !Double.isFinite(safety)) return 0;
        for (int i = 1; i < 5; i++) if (collapse <= safety * (1 + i * .2)) return i;
        return 5;
    }
    public static double slow(int stage) { return SLOWS[Math.max(0, Math.min(5, stage))]; }

    /** The vanilla squared-distance threshold must follow our synchronized slowdown on both sides. */
    public static float bodyYawMovementThreshold(LivingEntity entity,float threshold) {
        if (!(entity instanceof PlayerEntity)) return threshold;
        var speed=entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        var modifier=speed==null?null:speed.getModifier(SLOW);
        if (modifier==null||modifier.getOperation()!=EntityAttributeModifier.Operation.MULTIPLY_TOTAL) return threshold;
        double remaining=1+modifier.getValue();
        return remaining>0&&remaining<1?(float)(threshold*remaining*remaining):threshold;
    }

    public static void tick(ServerPlayerEntity player) {
        State state = PLAYERS.get(player.getUuid());
        if (state == null || state.player != player) {
            if (state != null) clear(state.player);
            state = new State(player);
            PLAYERS.put(player.getUuid(), state);
            // Migrate old saved penalties without touching ordinary blindness or nausea potions.
            player.removeStatusEffect(NeoMagic.DISORDER);
            player.removeStatusEffect(NeoMagic.DISINTEGRATION);
            player.removeStatusEffect(NeoMagic.BREAKDOWN);
        }
        double collapse = NeoRuntime.data(player).burden;
        int next = player.isAlive() && !player.isSpectator() && collapse > 0
                ? stage(collapse, NeoRuntime.profile(player).safety()) : 0;
        boolean changed = next != state.stage;
        var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null && (changed || next > 0 && speed.getModifier(SLOW) == null)) {
            speed.removeModifier(SLOW);
            if (next > 0) speed.addTemporaryModifier(new EntityAttributeModifier(SLOW, "SSCA collapse slowdown",
                    -slow(next), EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        state.stage = next;
        if (next < 3 || player.isCreative()) state.damagingTicks = 0;
        else {
            state.damagingTicks++;
            int interval = next == 3 ? 20 : 10;
            if (state.damagingTicks >= interval) {
                state.damagingTicks = 0;
                drain(player, next == 5 ? 2 : 1);
            }
        }
        if (changed) NeoNetwork.hud(player);
    }

    private static void drain(ServerPlayerEntity player, float amount) {
        // Code health loss deliberately skips armour, absorption and power damage modifiers.
        // Keep actual death, vanilla/virtual totems and the form's existing nine-lives revival.
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || NineLivesManager.isInvulnerable(player)) return;
        var source = player.getDamageSources().create(DAMAGE);
        if (player.getHealth() <= amount) {
            if (FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA) && NineLivesManager.tryRevive(player)) return;
            if (((LivingEntityTotemInvoker) player).sscAddon$tryUseTotem(source)) return;
        }
        player.setHealth(Math.max(0, player.getHealth() - amount));
        if (player.getHealth() <= 0) player.onDeath(source);
    }

    public static void clear(ServerPlayerEntity player) {
        State state = PLAYERS.get(player.getUuid());
        if (state != null && state.player == player) PLAYERS.remove(player.getUuid());
        var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW);
    }
}
