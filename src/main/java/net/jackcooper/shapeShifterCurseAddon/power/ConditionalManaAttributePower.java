package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.mana.ManaRegistries;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import org.jetbrains.annotations.Nullable;

/** Condition-gated SSC mana modifiers, including cleanup of modifiers persisted by older powers. */
public final class ConditionalManaAttributePower extends Power {
    private final Identifier modifierId;
    private final @Nullable ManaUtils.Modifier maxManaModifier;
    private final @Nullable ManaUtils.Modifier regenManaModifier;
    private final boolean playerSide;

    public ConditionalManaAttributePower(PowerType<?> type, LivingEntity entity, Identifier modifierId,
                                        @Nullable ManaUtils.Modifier maxManaModifier,
                                        @Nullable ManaUtils.Modifier regenManaModifier, boolean playerSide) {
        super(type, entity);
        this.modifierId = modifierId;
        this.maxManaModifier = maxManaModifier;
        this.regenManaModifier = regenManaModifier;
        this.playerSide = playerSide;
        setTicking(true);
    }

    @Override
    public void onAdded() {
        reconcile(isActive());
    }

    @Override
    public void onRespawn() {
        reconcile(isActive());
    }

    @Override
    public void tick() {
        // Read the actual component instead of caching an "applied" flag: CCA can restore mana NBT
        // after power NBT, and a talent reset must also remove an old save's unconditional bonus.
        reconcile(isActive());
    }

    @Override
    public void onLost() {
        reconcile(false);
    }

    @Override
    public void onRemoved() {
        reconcile(false);
    }

    private void reconcile(boolean active) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        var mana = ManaUtils.getManaComponent(player);
        reconcileModifier(player, playerSide ? mana.MaxManaModifierPlayerSide : mana.MaxManaModifier,
                maxManaModifier, active, true);
        reconcileModifier(player, playerSide ? mana.ManaRegenModifierPlayerSide : mana.ManaRegenModifier,
                regenManaModifier, active, false);
    }

    private void reconcileModifier(ServerPlayerEntity player, ManaUtils.ModifierList modifiers,
                                   @Nullable ManaUtils.Modifier modifier, boolean active, boolean maximum) {
        if (modifier == null) return;
        var current = modifiers.getModifiers().get(modifierId);
        if (active) {
            if (current != null && ManaRegistries.MC_AlwaysTrue.equals(current.getLeft())
                    && modifier.equals(current.getRight())) return;
            if (maximum) ManaUtils.addMaxManaModifier(player, modifierId, modifier, playerSide);
            else ManaUtils.addRegenManaModifier(player, modifierId, modifier, playerSide);
        } else if (current != null) {
            if (maximum) ManaUtils.removeMaxManaModifier(player, modifierId, playerSide);
            else ManaUtils.removeRegenManaModifier(player, modifierId, playerSide);
        }
    }

    public static PowerFactory<Power> createFactory() {
        return new PowerFactory<Power>(new Identifier("ssc_addon", "conditional_mana_attribute"),
                new SerializableData()
                        .add("modifierID", SerializableDataTypes.IDENTIFIER)
                        .add("max_mana_modifier", ManaUtils.SDT_ManaModifier, null)
                        .add("regen_mana_modifier", ManaUtils.SDT_ManaModifier, null)
                        .add("player_side", SerializableDataTypes.BOOLEAN, false),
                data -> (type, entity) -> new ConditionalManaAttributePower(type, entity,
                        data.getId("modifierID"), data.get("max_mana_modifier"),
                        data.get("regen_mana_modifier"), data.getBoolean("player_side")))
                .allowCondition();
    }
}
