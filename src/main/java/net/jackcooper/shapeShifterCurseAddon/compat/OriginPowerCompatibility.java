package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.MultiplePowerType;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeReference;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginRegistry;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.PerkUtils;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.ISubForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;

/** 仅为SSCA形态兼容同形态登录：只补缺失来源，不重建已有能力实例。 */
public final class OriginPowerCompatibility {
    private static final Set<UUID> REPAIRING = new HashSet<>();
    private static final Set<Identifier> RETIRED = Set.of(WindSpiritPerks.LONG_POUNCE, NovaPerks.LONG_POUNCE,
            new Identifier("shape-shifter-curse", "perks/ocelot_long_pounce"));
    private OriginPowerCompatibility() {}

    /** Scope by the actual form ID: a subform may share an Origin with a different owner. */
    public static boolean supportsForm(IForm form) {
        return form != null && "my_addon".equals(form.getFormID().getNamespace());
    }

    /** 在原Perk加载之前调用，随后仍由原有十四类适配处理增减、替换及高级节点覆盖。 */
    public static int beforePerks(PlayerEntity entity, Identifier tree) {
        if (!(entity instanceof ServerPlayerEntity player)) return 0;
        var form = FormUtils.getPlayerForm(player);
        if (!supportsForm(form) || !form.getPerkTreeID().equals(tree) || !REPAIRING.add(player.getUuid())) return 0;
        try {
            var layer = form.getFormLayer();
            if (layer == null || layer.getRight() == null) return 0;
            var source = layer.getRight();
            if (!(source.getNamespace().equals("my_addon") || source.getNamespace().equals("shape-shifter-curse"))) return 0;
            if (!OriginRegistry.contains(source)) return 0;
            var origin = OriginRegistry.get(source);
            var holder = PowerHolderComponent.KEY.get(player);
            // Subforms share their master's Origin, but deliberately replace some of its powers.
            // afterApplyLayer has already run by the time loadAllPerk invokes this repair.
            var modifier = form instanceof ISubForm subform ? subform.getLayerModifier() : null;
            Set<Identifier> subformRemoved = new HashSet<>();
            if (modifier != null) for (var id : modifier.getRight()) blockTree(id, subformRemoved);
            Set<Identifier> blocked = new HashSet<>(subformRemoved);
            var purchases = PlayerFormComponent.COMPONENT.get(player).formPerkMap.get(tree);
            if (purchases != null) for (var id : purchases) {
                if (RegPerks.getPerk(id) instanceof NormalPerk perk) {
                    for (var removed : perk.powerRemove) blockTree(removed, blocked);
                }
            }
            int changed = 0;
            Set<Identifier> visited = new HashSet<>();
            for (var type : origin.getPowerTypes()) changed += addMissing(holder, type, source, blocked, visited);
            if (modifier != null) for (var id : modifier.getLeft()) {
                if (PowerTypeRegistry.contains(id))
                    changed += addMissing(holder, PowerTypeRegistry.get(id), source, blocked, visited);
            }
            // Also repair contaminated saves. Adding a Multiple parent may implicitly add an
            // excluded child, so apply the subform removals after all missing powers are granted.
            // 停用技能和子形态排除项只清本形态来源，保留其它模组/饰品的独立来源。
            for (var type : Set.copyOf(holder.getPowersFromSource(source))) {
                if (subformRemoved.contains(type.getIdentifier()) || RETIRED.contains(type.getIdentifier())) {
                    holder.removePower(type, source);
                    changed++;
                }
            }
            if (changed > 0) PowerHolderComponent.KEY.sync(player);
            return changed;
        } finally { REPAIRING.remove(player.getUuid()); }
    }

    private static void blockTree(Identifier id, Set<Identifier> blocked) {
        if (!blocked.add(id) || !PowerTypeRegistry.contains(id)) return;
        if (PowerTypeRegistry.get(id) instanceof MultiplePowerType<?> multiple)
            for (var child : multiple.getSubPowers()) blockTree(child, blocked);
    }

    private static int addMissing(PowerHolderComponent holder, PowerType<?> type, Identifier source,
                                  Set<Identifier> blocked, Set<Identifier> visited) {
        if (type instanceof PowerTypeReference<?> reference) type = reference.getReferencedPowerType();
        if (type == null) return 0;
        var id = type.getIdentifier();
        if (!visited.add(id) || blocked.contains(id) || RETIRED.contains(id)) return 0;
        int added = 0;
        if (!holder.hasPower(type, source)) { holder.addPower(type, source); added++; }
        // Multiple父能力仍在、子能力从旧存档缺失时，也要补齐子能力。
        if (type instanceof MultiplePowerType<?> multiple) for (var child : multiple.getSubPowers()) {
            if (PowerTypeRegistry.contains(child)) added += addMissing(holder, PowerTypeRegistry.get(child), source, blocked, visited);
        }
        return added;
    }

    public static void restore(ServerPlayerEntity player) {
        var form = FormUtils.getPlayerForm(player);
        if (supportsForm(form)) {
            PerkUtils.loadAllPerk(player, form.getPerkTreeID());
            PowerHolderComponent.KEY.sync(player);
        }
    }

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            var server = player.getServer();
            server.execute(() -> {
                if (server.getPlayerManager().getPlayer(player.getUuid()) == player) restore(player);
            });
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success) for (var player : server.getPlayerManager().getPlayerList()) restore(player);
        });
    }
}
