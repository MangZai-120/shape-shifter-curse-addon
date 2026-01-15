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

import java.util.Collection;
import java.util.UUID;

public class SscAddonCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ssc-addon")
            .then(CommandManager.literal("mark_owner")
                .then(CommandManager.argument("targets", EntityArgumentType.entities())
                    .executes(SscAddonCommands::markOwner)
                )
            )
        );
    }

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
}
