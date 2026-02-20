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
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;

import java.util.Collection;
import java.util.UUID;

public class SscAddonCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("SscAddon-Debug");

    private SscAddonCommands() {
        // This utility class should not be instantiated
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ssc_addon")
            .then(CommandManager.literal("set_mana")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("targets", EntityArgumentType.players())
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
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
                .then(CommandManager.literal("mana")
                    .executes(SscAddonCommands::debugMana)
                )
            )
            .then(CommandManager.literal("get_book")
                .requires(source -> source.hasPermissionLevel(2))
                // 使用字符串ID参数，支持任意书籍ID（不仅仅是数字）
                .then(CommandManager.argument("book_id", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        // 自动补全：显示所有可用的书籍ID
                        return CommandSource.suggestMatching(
                            net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookIds(),
                            builder
                        );
                    })
                    .executes(SscAddonCommands::giveStoryBookById)
                    .then(CommandManager.argument("language", StringArgumentType.string())
                        .suggests((context, builder) -> CommandSource.suggestMatching(new String[]{"zh_cn", "en_us"}, builder))
                        .executes(SscAddonCommands::giveStoryBookByIdWithLang)
                    )
                )
            )
            .then(CommandManager.literal("list_books")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(SscAddonCommands::listBooks)
                .then(CommandManager.argument("language", StringArgumentType.string())
                    .suggests((context, builder) -> CommandSource.suggestMatching(new String[]{"zh_cn", "en_us"}, builder))
                    .executes(SscAddonCommands::listBooksWithLang)
                )
            )
            .then(CommandManager.literal("reload_books")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(SscAddonCommands::reloadBooks)
            )
            .then(CommandManager.literal("allay_whitelist")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("allayPlayer", EntityArgumentType.player())
                        .then(CommandManager.argument("targetPlayer", EntityArgumentType.player())
                            .executes(SscAddonCommands::allayWhitelistAdd)
                        )
                    )
                )
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("allayPlayer", EntityArgumentType.player())
                        .then(CommandManager.argument("targetPlayer", EntityArgumentType.player())
                            .executes(SscAddonCommands::allayWhitelistRemove)
                        )
                    )
                )
                .then(CommandManager.literal("list")
                    .then(CommandManager.argument("allayPlayer", EntityArgumentType.player())
                        .executes(SscAddonCommands::allayWhitelistList)
                    )
                )
                .then(CommandManager.literal("clear")
                    .then(CommandManager.argument("allayPlayer", EntityArgumentType.player())
                        .executes(SscAddonCommands::allayWhitelistClear)
                    )
                )
            )
        );
    }

    private static int setMana(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets, int amount) {
        Identifier resourceId = new Identifier("my_addon", "form_snow_fox_sp_resource");
        Identifier allayResourceId = new Identifier("my_addon", "form_allay_sp_mana_resource");
        int count = 0;
        for (ServerPlayerEntity player : targets) {
            boolean updated = false;
            // 1. Try Apoli Resource (Snow Fox SP)
            PowerHolderComponent component = PowerHolderComponent.KEY.get(player);
            for (VariableIntPower power : component.getPowers(VariableIntPower.class)) {
                if (power.getType().getIdentifier().equals(resourceId) || power.getType().getIdentifier().equals(allayResourceId)) {
                    int newVal = amount;
                    if (newVal > power.getMax()) {
                        newVal = power.getMax();
                    }
                    power.setValue(newVal);
                    // Force sync immediately
                    component.sync();
                    updated = true;
                }
            }

            // 2. Try Global Mana (Familiar SP)
            try {
                ManaComponent manaComponent = ManaUtils.getManaComponent(player);
                if (manaComponent != null) {
                    double newVal = amount;
                    if (newVal > manaComponent.getMaxMana()) {
                         newVal = manaComponent.getMaxMana();
                    }
                    manaComponent.setMana(newVal);
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

        debugInfo.append("================================");
        // Check if info level is enabled before logging
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(debugInfo.toString());
        }
        
        // Send to player chat
        String[] lines = debugInfo.toString().split("\n");
        for (String line : lines) {
            player.sendMessage(Text.literal(line).formatted(Formatting.AQUA), false);
        }
        
        return 1;
    }

    private static int giveStoryBook(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return giveStoryBookInternal(context, null);
    }

    private static int giveStoryBookWithLang(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String lang = StringArgumentType.getString(context, "language");
        return giveStoryBookInternal(context, lang);
    }

    private static int giveStoryBookInternal(CommandContext<ServerCommandSource> context, String language) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int chapter = IntegerArgumentType.getInteger(context, "chapter");
        net.minecraft.item.ItemStack book = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getStoryBook(chapter, language);
        
        if (book.isEmpty()) {
            player.sendMessage(Text.literal("No book found for ID: " + chapter).formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!player.getInventory().insertStack(book)) {
            player.dropItem(book, false);
        }
        
        player.sendMessage(Text.literal("Received story book: Chapter " + chapter).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int debugMana(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        StringBuilder debugMsg = new StringBuilder();
        boolean foundMana = false;

        // 1. Check Apoli Resource (Snow Fox SP)
        Identifier resourceId = new Identifier("my_addon", "form_snow_fox_sp_resource");
        PowerHolderComponent component = PowerHolderComponent.KEY.get(player);
        for (VariableIntPower power : component.getPowers(VariableIntPower.class)) {
            if (power.getType().getIdentifier().equals(resourceId)) {
                debugMsg.append("Snow Fox SP: ").append(power.getValue()).append("/").append(power.getMax()).append("\n");
                foundMana = true;
            }
        }
        
        // 2. Check Global ManaComponent
        try {
            ManaComponent manaComponent = ManaUtils.getManaComponent(player);
            if (manaComponent != null) {
                // Check if it has any mana type associated or just valid mana
                if (manaComponent.getManaTypeID() != null) {
                     debugMsg.append("Mana Type: ").append(manaComponent.getManaTypeID()).append("\n");
                     debugMsg.append("Mana: ").append(manaComponent.getMana()).append("/").append(manaComponent.getMaxMana()).append("\n");
                     foundMana = true;
                } else if (manaComponent.getMaxMana() > 0) {
                     // Fallback if type is null but max mana is > 0
                     debugMsg.append("Mana (No Type): ").append(manaComponent.getMana()).append("/").append(manaComponent.getMaxMana()).append("\n");
                     foundMana = true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        if (!foundMana) {
            player.sendMessage(Text.literal("无能量条 (No Mana Bar)").formatted(Formatting.YELLOW), false);
        } else {
             player.sendMessage(Text.literal(debugMsg.toString().trim()).formatted(Formatting.AQUA), false);
        }
        return 1;
    }

    // ===== 新增：书籍命令方法 =====

    /**
     * 通过书籍ID获取书籍（使用配置的默认语言）
     */
    private static int giveStoryBookById(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return giveStoryBookByIdInternal(context, null);
    }

    /**
     * 通过书籍ID和指定语言获取书籍
     */
    private static int giveStoryBookByIdWithLang(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String lang = StringArgumentType.getString(context, "language");
        return giveStoryBookByIdInternal(context, lang);
    }

    /**
     * 内部方法：通过书籍ID获取书籍
     */
    private static int giveStoryBookByIdInternal(CommandContext<ServerCommandSource> context, String language) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String bookId = StringArgumentType.getString(context, "book_id");
        
        net.minecraft.item.ItemStack book = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getStoryBookById(bookId, language);
        
        if (book.isEmpty()) {
            player.sendMessage(Text.literal("未找到书籍 ID: " + bookId + " (Book not found)").formatted(Formatting.RED), false);
            return 0;
        }
        
        // 获取书籍信息用于显示
        net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.BookData bookData = 
            net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookDataById(bookId, language);
        
        if (!player.getInventory().insertStack(book)) {
            player.dropItem(book, false);
        }
        
        String bookTitle = bookData != null ? bookData.title : bookId;
        player.sendMessage(Text.literal("已获得书籍: " + bookTitle).formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * 列出所有可用书籍（使用配置的默认语言）
     */
    private static int listBooks(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return listBooksInternal(context, null);
    }

    /**
     * 列出所有可用书籍（指定语言）
     */
    private static int listBooksWithLang(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String lang = StringArgumentType.getString(context, "language");
        return listBooksInternal(context, lang);
    }

    /**
     * 内部方法：列出所有可用书籍
     */
    private static int listBooksInternal(CommandContext<ServerCommandSource> context, String language) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        java.util.List<String> bookIds;
        if (language != null && !language.isEmpty()) {
            bookIds = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookIds(language);
        } else {
            bookIds = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookIds();
        }
        
        if (bookIds.isEmpty()) {
            player.sendMessage(Text.literal("没有可用的书籍 (No books available)").formatted(Formatting.YELLOW), false);
            return 0;
        }
        
        player.sendMessage(Text.literal("===== 可用书籍列表 (Available Books) =====").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("共 " + bookIds.size() + " 本书籍:").formatted(Formatting.AQUA), false);
        
        for (String bookId : bookIds) {
            net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.BookData bookData = 
                net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookDataById(bookId, language);
            
            if (bookData != null) {
                // 截断过长的标题
                String displayTitle = bookData.title;
                if (displayTitle.length() > 30) {
                    displayTitle = displayTitle.substring(0, 27) + "...";
                }
                player.sendMessage(Text.literal("  [" + bookId + "] " + displayTitle + " - " + bookData.author).formatted(Formatting.WHITE), false);
            } else {
                player.sendMessage(Text.literal("  [" + bookId + "] (数据加载失败)").formatted(Formatting.RED), false);
            }
        }
        
        player.sendMessage(Text.literal("=========================================").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("使用 /ssc_addon get_book <ID> 获取书籍").formatted(Formatting.GRAY), false);
        
        return bookIds.size();
    }

    /**
     * 重新加载书籍配置
     */
    private static int reloadBooks(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.reloadBooks();
            int bookCount = net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookCount();
            player.sendMessage(Text.literal("书籍配置已重新加载！共加载 " + bookCount + " 本书籍。").formatted(Formatting.GREEN), false);
            player.sendMessage(Text.literal("Books reloaded! Loaded " + bookCount + " books.").formatted(Formatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("重新加载书籍失败: " + e.getMessage()).formatted(Formatting.RED), false);
            return 0;
        }
    }

    // ===== SP悦灵治疗白名单命令 =====

    /**
     * 添加玩家到SP悦灵的治疗白名单
     * /ssc_addon allay_whitelist add <allayPlayer> <targetPlayer>
     */
    private static int allayWhitelistAdd(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity allayPlayer = EntityArgumentType.getPlayer(context, "allayPlayer");
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "targetPlayer");

        boolean added = net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal.addToWhitelist(allayPlayer, targetPlayer);
        
        if (added) {
            context.getSource().sendFeedback(() -> Text.literal(
                "已将 " + targetPlayer.getName().getString() + " 添加到 " + allayPlayer.getName().getString() + " 的治疗白名单"
            ).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal(
                targetPlayer.getName().getString() + " 已在 " + allayPlayer.getName().getString() + " 的治疗白名单中"
            ).formatted(Formatting.YELLOW), false);
        }
        return 1;
    }

    /**
     * 从SP悦灵的治疗白名单中移除玩家
     * /ssc_addon allay_whitelist remove <allayPlayer> <targetPlayer>
     */
    private static int allayWhitelistRemove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity allayPlayer = EntityArgumentType.getPlayer(context, "allayPlayer");
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "targetPlayer");

        boolean removed = net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal.removeFromWhitelist(allayPlayer, targetPlayer);
        
        if (removed) {
            context.getSource().sendFeedback(() -> Text.literal(
                "已将 " + targetPlayer.getName().getString() + " 从 " + allayPlayer.getName().getString() + " 的治疗白名单中移除"
            ).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal(
                targetPlayer.getName().getString() + " 不在 " + allayPlayer.getName().getString() + " 的治疗白名单中"
            ).formatted(Formatting.YELLOW), false);
        }
        return 1;
    }

    /**
     * 列出SP悦灵的治疗白名单
     * /ssc_addon allay_whitelist list <allayPlayer>
     */
    private static int allayWhitelistList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity allayPlayer = EntityArgumentType.getPlayer(context, "allayPlayer");
        
        java.util.List<UUID> uuids = net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal.getWhitelistUuids(allayPlayer);
        
        if (uuids.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal(
                allayPlayer.getName().getString() + " 的治疗白名单为空"
            ).formatted(Formatting.YELLOW), false);
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal(
            "===== " + allayPlayer.getName().getString() + " 的治疗白名单 (" + uuids.size() + "人) ====="
        ).formatted(Formatting.GOLD), false);

        net.minecraft.server.MinecraftServer server = context.getSource().getServer();
        for (UUID uuid : uuids) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(uuid);
            String name = target != null ? target.getName().getString() : uuid.toString() + " (离线)";
            context.getSource().sendFeedback(() -> Text.literal("  - " + name).formatted(Formatting.WHITE), false);
        }

        return uuids.size();
    }

    /**
     * 清空SP悦灵的治疗白名单
     * /ssc_addon allay_whitelist clear <allayPlayer>
     */
    private static int allayWhitelistClear(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity allayPlayer = EntityArgumentType.getPlayer(context, "allayPlayer");
        
        int count = net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal.clearWhitelist(allayPlayer);
        
        context.getSource().sendFeedback(() -> Text.literal(
            "已清空 " + allayPlayer.getName().getString() + " 的治疗白名单 (移除了 " + count + " 名玩家)"
        ).formatted(Formatting.GREEN), true);

        return 1;
    }
}
