package net.jackcooper.shapeShifterCurseAddon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.action.SscAddonActions;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Cancels live casting state before a queued release can execute. Repeated calls are harmless. */
public final class ControlledAbilityInterruption {
    private ControlledAbilityInterruption() { }

    public static void interrupt(ServerPlayerEntity player) {
        if (!AbilityUseGuard.fullyControlled(player)) return;
        var manager = SkillCastManager.get(player.getServerWorld());
        var casts = manager.activeCasts().stream().filter(c -> c.playerId.equals(player.getUuid())
                && !SkillCastManager.isAutonomousEffect(c)
                // This instant skill applies the control effect before completing its own cast.
                && !SscAddonActions.PLAY_DEAD_SKILL_ID.equals(c.skillId)).toList();
        SpellChannelManager.onFullyControlled(player);
        VortexChargeManager.cancel(player);
        SpiderMoonWeaverWebManager.cancel(player);
        SpiderMoonWeaverSwingManager.interruptControl(player);
        WaterSpearLeapManager.cancel(player);
        VortexGuideManager.cancel(player);
        JumpKillManager.interruptControl(player);
        WindDashManager.clear(player);
        WindSpiritClawManager.clear(player);
        SnowFoxSpFrostStorm.cancelCharging(player);
        SnowFoxSpTeleportAttack.clearPlayer(player.getUuid());
        SnowFoxSpMeleeAbility.clearPlayer(player.getUuid());
        GoldenSandstormWitherSand.clearPlayer(player);
        NovaSkillManager.interruptControl(player);
        FluorescentLaserManager.interruptControl(player);
        FluorescentTidalManager.interruptControl(player);
        FrostSpikeManager.interruptControl(player);
        AllaySPGroupHeal.interruptControl(player);
        for (var power : PowerHolderComponent.getPowers(player,
                net.jackcooper.shapeShifterCurseAddon.power.MistFormAbilityPower.class)) power.interruptControl();
        for (var power : PowerHolderComponent.getPowers(player,
                net.jackcooper.shapeShifterCurseAddon.power.TrueInvisibilityAbilityPower.class)) power.interruptControl();
        for (var cast : casts) {
            clearCastResources(player, cast.skillId);
            manager.interrupt(cast.castId, SkillCastManager.now(player));
        }
        // SSC charge/grapple/dive powers expose their normal cleanup through onRemoved().
        for (var power : PowerHolderComponent.KEY.get(player).getPowers()) {
            if (power instanceof Active && power.getClass().getName().startsWith(
                    "net.onixary.shapeShifterCurseFabric.additional_power.")) power.onRemoved();
        }
    }

    private static void clearCastResources(ServerPlayerEntity player, String skill) {
        String root = skill.endsWith("_key_activation")
                ? skill.substring(0, skill.length() - "_key_activation".length()) : skill;
        for (String suffix : new String[]{"_charging_state", "_charge_timer", "_heal_execute", "_toggle_state"}) {
            Identifier id = Identifier.tryParse(root + suffix);
            if (id != null && PowerTypeRegistry.contains(id)
                    && PowerHolderComponent.KEY.get(player).hasPower(PowerTypeRegistry.get(id))
                    && PowerUtils.getResourceValue(player, id) != 0) PowerUtils.setResourceValueAndSync(player, id, 0);
        }
        if (root.startsWith("my_addon:form_familiar_fox_")
                && (root.endsWith("_blue_fire_ring") || root.endsWith("_blue_fire_ring_amulet"))) {
            player.removeStatusEffect(net.jackcooper.shapeShifterCurseAddon.SscAddon.BLUE_FIRE_RING);
        }
    }
}
