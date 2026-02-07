package net.onixary.shapeShifterCurseFabric.ssc_addon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.VariableIntPower;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class SscAddonCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("SscAddon-Debug");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ssc_addon")
            .then(CommandManager.literal("set_mana")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("targets", EntityArgumentType.players())
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 100))
                        .executes(context -> setMana(context, EntityArgumentType.getPlayers(context, "targets"), IntegerArgumentType.getInteger(context, "amount")))
                    )
                )
            )
            .then(CommandManager.literal("mark_owner")
                .then(CommandManager.argument("targets", EntityArgumentType.entities())
                    .executes(SscAddonCommands::markOwner)
                )
            )
            .then(CommandManager.literal("debug")
                .then(CommandManager.literal("form")
                    .executes(SscAddonCommands::debugFormInfo)
                )
            )
        );

        /*
        dispatcher.register(CommandManager.literal("my_addon_allay_treatment")
                .then(CommandManager.argument("allayPlayer", EntityArgumentType.player())
                        .then(CommandManager.argument("targetPlayer", EntityArgumentType.player())
                                .executes(SscAddonCommands::registerTreatmentWhitelist)
                        )
                )
        );
        */
    }

    private static int setMana(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets, int amount) {
        Identifier resourceId = new Identifier("my_addon", "form_snow_fox_sp_resource");
        int count = 0;
        for (ServerPlayerEntity player : targets) {
            boolean updated = false;
            // 1. Try Apoli Resource (Snow Fox SP)
            PowerHolderComponent component = PowerHolderComponent.KEY.get(player);
            for (VariableIntPower power : component.getPowers(VariableIntPower.class)) {
                if (power.getType().getIdentifier().equals(resourceId)) {
                    power.setValue(amount);
                    PowerHolderComponent.syncPower(player, power.getType());
                    updated = true;
                }
            }

            // 2. Try Global Mana (Familiar SP)
            try {
                ManaComponent manaComponent = ManaUtils.getManaComponent(player);
                if (manaComponent != null) {
                    manaComponent.setMana((double) amount);
                    updated = true;
                }
            } catch (Exception e) {
                // Ignore
            }

            if (updated) {
                count++;
            }
        }
        final int finalCount = count;
        context.getSource().sendFeedback(() -> Text.literal("Set mana to " + amount + " for " + finalCount + " players."), true);
        return count;
    }

    /*
    private static int registerTreatmentWhitelist(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity allayPlayer = EntityArgumentType.getPlayer(context, "allayPlayer");
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "targetPlayer");

        // Format: "ssc_allay_whitelist:<TargetUUID>"
        // Stored on the Allay Player
        allayPlayer.addCommandTag("ssc_allay_whitelist:" + targetPlayer.getUuidAsString());
        
        context.getSource().sendFeedback(() -> Text.literal("Added " + targetPlayer.getName().getString() + " to " + allayPlayer.getName().getString() + "'s treatment whitelist."), false);

        return 1;
    }
    */

    private static int markOwner(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgumentType.getEntities(context, "targets");
        ServerCommandSource source = context.getSource();
        Entity attacker = source.getEntity();
        
        if (attacker instanceof ServerPlayerEntity player) {
            UUID playerUUID = player.getUuid();
            for (Entity target : targets) {
                if (target instanceof LivingEntity livingTarget) {
                    NbtCompound nbt = new NbtCompound();
                    // We can't safely modify the entity NBT directly while it's alive without using specific methods or writing to custom data if available.
                    // However, standard entity NBT modification is restricted. 
                    // But we can use persistent data if we are using Fabric API or similar, or just manage a map.
                    // But simplest is to reuse the 'killed_by' logic? No.
                    
                    // Actually, modifying `target.getNbt()` directly and setting it back is dangerous.
                    // But we can use a custom tag or scoreboard.
                    // Let's write to a custom field used by our effect.
                    // Since we can't add fields to vanilla entities, checking 'FireOwner' mapping is safer?
                    // No, a global map leaks memory.
                    
                    // Let's use the Scoreboard Tags!
                    // Tag format: "ssc_owner:<UUID>"
                    
                    // Remove old tags
                    livingTarget.getCommandTags().removeIf(tag -> tag.startsWith("ssc_owner:"));
                    // Add new tag
                    livingTarget.addCommandTag("ssc_owner:" + playerUUID.toString());
                }
            }
            return targets.size();
        }
        return 0;
    }

    private static int debugFormInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        // Get player form component
        PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
        
        // Prepare debug info
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("===== SSC_ADDON FORM DEBUG =====\n");
        
        if (component == null) {
            debugInfo.append("PlayerFormComponent: NULL\n");
        } else {
            PlayerFormBase currentForm = component.getCurrentForm();
            if (currentForm == null) {
                debugInfo.append("Current Form: NULL (no form active)\n");
            } else {
                debugInfo.append("Form ID: ").append(currentForm.FormID).append("\n");
                debugInfo.append("Form Class: ").append(currentForm.getClass().getName()).append("\n");
                debugInfo.append("Phase: ").append(currentForm.getPhase()).append("\n");
                debugInfo.append("Body Type: ").append(currentForm.getBodyType()).append("\n");
            }
        }
        debugInfo.append("================================");
        
        // Log to console
        LOGGER.info("\n" + debugInfo.toString());
        
        // Send to player chat
        String[] lines = debugInfo.toString().split("\n");
        for (String line : lines) {
            player.sendMessage(Text.literal(line).formatted(Formatting.AQUA), false);
        }
        
        return 1;
    }
}
