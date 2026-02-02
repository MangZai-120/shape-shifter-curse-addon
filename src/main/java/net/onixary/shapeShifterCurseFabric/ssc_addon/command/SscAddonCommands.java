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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;

//import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.Ability_AllayHeal;

public class SscAddonCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("SscAddon-Debug");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ssc_addon_action")
             /*
             .then(CommandManager.literal("hold_allay_heal")
                 .executes(context -> {
                     ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                     Ability_AllayHeal.onHold(player);
                     return 1;
                 })
             )
             */
        );

        dispatcher.register(CommandManager.literal("ssc-addon")
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
        debugInfo.append("===== SSC-ADDON FORM DEBUG =====\n");
        
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
