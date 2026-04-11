package net.onixary.shapeShifterCurseFabric.ssc_addon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.VariableIntPower;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.ConfigChangeManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSummonWolves;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpDeathDomain;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPJukebox;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostBallEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SscAddonCommands {
	private static final Logger LOGGER = LoggerFactory.getLogger("SscAddon-Debug");
	private static final String SKILL_BLOCKED_PREFIX = "ssc_skill_blocked:";

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
				.then(CommandManager.literal("reload")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(SscAddonCommands::reloadConfig)
				)
				.then(CommandManager.literal("whitelist")
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
				.then(CommandManager.literal("skill")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("form", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										Arrays.asList("snow_fox", "anubis_wolf", "allay", "axolotl", "wild_cat", "familiar_fox", "familiar_fox_red", "shape-shifter-curse"), builder))
								.then(CommandManager.argument("skill", StringArgumentType.word())
										.suggests((context, builder) -> {
											String form = StringArgumentType.getString(context, "form");
											return CommandSource.suggestMatching(getSkillsForForm(form), builder);
										})
										.then(CommandManager.argument("player", EntityArgumentType.player())
												.executes(SscAddonCommands::invokeSkillOnPlayer)
										)
										.executes(SscAddonCommands::invokeSkillOnSelf)
								)
						)
				)
				.then(CommandManager.literal("block")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.then(CommandManager.argument("form", StringArgumentType.word())
										.suggests((context, builder) -> CommandSource.suggestMatching(
												Arrays.asList("snow_fox", "anubis_wolf", "allay", "axolotl", "wild_cat", "familiar_fox", "familiar_fox_red"), builder))
										.then(CommandManager.argument("skill", StringArgumentType.word())
												.suggests((context, builder) -> {
													String form = StringArgumentType.getString(context, "form");
													return CommandSource.suggestMatching(getSkillsForForm(form), builder);
												})
												.executes(SscAddonCommands::blockSkill)
										)
								)
						)
				)
				.then(CommandManager.literal("unblock")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.then(CommandManager.argument("form", StringArgumentType.word())
										.suggests((context, builder) -> CommandSource.suggestMatching(
												Arrays.asList("snow_fox", "anubis_wolf", "allay", "axolotl", "wild_cat", "familiar_fox", "familiar_fox_red"), builder))
										.then(CommandManager.argument("skill", StringArgumentType.word())
												.suggests((context, builder) -> {
													String form = StringArgumentType.getString(context, "form");
													return CommandSource.suggestMatching(getSkillsForForm(form), builder);
												})
												.executes(SscAddonCommands::unblockSkill)
										)
								)
						)
				)
				.then(CommandManager.literal("list_blocks")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("player", EntityArgumentType.player())
								.executes(SscAddonCommands::listBlockedSkills)
						)
				)
		);
	}

	private static int setMana(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets, int amount) {
		Identifier resourceId = new Identifier("my_addon", "form_snow_fox_sp_resource");
		Identifier allayResourceId = new Identifier("my_addon", "form_allay_sp_mana_resource");
		Identifier soulEnergyId = new Identifier("my_addon", "form_anubis_wolf_sp_soul_energy");
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			boolean updated = false;
			// 1. Try Apoli Resource (Snow Fox SP / Allay SP / Anubis Wolf SP Soul Energy)
			PowerHolderComponent component = PowerHolderComponent.KEY.get(player);
			for (VariableIntPower power : component.getPowers(VariableIntPower.class)) {
				Identifier powerId = power.getType().getIdentifier();
				if (powerId.equals(resourceId) || powerId.equals(allayResourceId)) {
					int newVal = amount;
					if (newVal > power.getMax()) {
						newVal = power.getMax();
					}
					power.setValue(newVal);
					component.sync();
					updated = true;
				} else if (powerId.equals(soulEnergyId)) {
// 使用PowerUtils设置灵魂能量
					net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSoulEnergy.setEnergy(player, amount);
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

	/**
	 * 重新加载模组配置
	 */
	private static int reloadConfig(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

		try {
			SSCAddonConfig config = AutoConfig.getConfigHolder(SSCAddonConfig.class).getConfig();
			ConfigChangeManager.notifyChange(config);
			player.sendMessage(Text.literal("模组配置已重新加载！").formatted(Formatting.GREEN), true);
			player.sendMessage(Text.literal("Mod config reloaded!").formatted(Formatting.GREEN), true);
			return 1;
		} catch (Exception e) {
			player.sendMessage(Text.literal("重新加载配置失败: " + e.getMessage()).formatted(Formatting.RED), true);
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
					"已将 " + targetPlayer.getName().getString() + " 添加到 " + allayPlayer.getName().getString() + " 的白名单"
			).formatted(Formatting.GREEN), true);
		} else {
			context.getSource().sendFeedback(() -> Text.literal(
					targetPlayer.getName().getString() + " 已在 " + allayPlayer.getName().getString() + " 的白名单中"
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
					"已将 " + targetPlayer.getName().getString() + " 从 " + allayPlayer.getName().getString() + " 的白名单中移除"
			).formatted(Formatting.GREEN), true);
		} else {
			context.getSource().sendFeedback(() -> Text.literal(
					targetPlayer.getName().getString() + " 不在 " + allayPlayer.getName().getString() + " 的白名单中"
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
					allayPlayer.getName().getString() + " 的白名单为空"
			).formatted(Formatting.YELLOW), false);
			return 0;
		}

		context.getSource().sendFeedback(() -> Text.literal(
				"===== " + allayPlayer.getName().getString() + " 的白名单 (" + uuids.size() + "人) ====="
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
				"已清空 " + allayPlayer.getName().getString() + " 的白名单 (移除了 " + count + " 名玩家)"
		).formatted(Formatting.GREEN), true);

		return 1;
	}

	private static List<String> getSkillsForForm(String form) {
		return switch (form) {
			case "snow_fox" -> Arrays.asList("melee_primary", "melee_secondary", "ranged_primary", "ranged_secondary", "frost_regen");
			case "anubis_wolf" -> Arrays.asList("summon_wolves", "death_domain", "soul_sand_heal", "wither_hunt", "soul_scrutiny");
			case "allay" -> Arrays.asList("jukebox_charge", "group_heal", "mana_regen");
			case "axolotl" -> Arrays.asList("natural_regen_boost", "rain_wetness");
			case "wild_cat" -> Arrays.asList("night_speed", "day_slow");
			case "familiar_fox" -> Arrays.asList("mana_regen");
			case "familiar_fox_red" -> Arrays.asList("red_mana_regen");
			default -> Collections.emptyList();
		};
	}

	private static boolean isSkillBlocked(ServerPlayerEntity player, String form, String skill) {
		String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
		return player.getCommandTags().contains(tag);
	}

	private static void blockSkill(ServerPlayerEntity player, String form, String skill) {
		String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
		player.addCommandTag(tag);
	}

	private static void unblockSkill(ServerPlayerEntity player, String form, String skill) {
		String tag = SKILL_BLOCKED_PREFIX + form + ":" + skill;
		player.getCommandTags().remove(tag);
	}

	private static List<String> getBlockedSkills(ServerPlayerEntity player) {
		return player.getCommandTags().stream()
				.filter(tag -> tag.startsWith(SKILL_BLOCKED_PREFIX))
				.map(tag -> tag.substring(SKILL_BLOCKED_PREFIX.length()))
				.toList();
	}

	private static int invokeSkillOnSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");
		return invokeSkill(context, player, form, skill);
	}

	private static int invokeSkillOnPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");
		return invokeSkill(context, target, form, skill);
	}

	private static int blockSkill(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");

		if (isSkillBlocked(target, form, skill)) {
			context.getSource().sendFeedback(() -> Text.literal(
					"[SSC] " + form + "/" + skill + " is already blocked for " + target.getName().getString()
			).formatted(Formatting.YELLOW), false);
			return 0;
		}

		blockSkill(target, form, skill);
		LOGGER.info("[SSC] Blocked " + form + "/" + skill + " for " + target.getName().getString());
		context.getSource().sendFeedback(() -> Text.literal(
				"[SSC] Blocked " + form + "/" + skill + " for " + target.getName().getString()
		).formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int unblockSkill(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");

		if (!isSkillBlocked(target, form, skill)) {
			context.getSource().sendFeedback(() -> Text.literal(
					"[SSC] " + form + "/" + skill + " is not blocked for " + target.getName().getString()
			).formatted(Formatting.YELLOW), false);
			return 0;
		}

		unblockSkill(target, form, skill);
		LOGGER.info("[SSC] Unblocked " + form + "/" + skill + " for " + target.getName().getString());
		context.getSource().sendFeedback(() -> Text.literal(
				"[SSC] Unblocked " + form + "/" + skill + " for " + target.getName().getString()
		).formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int listBlockedSkills(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		List<String> blockedSkills = getBlockedSkills(target);

		if (blockedSkills.isEmpty()) {
			context.getSource().sendFeedback(() -> Text.literal(
					"[SSC] No skills blocked for " + target.getName().getString()
			).formatted(Formatting.YELLOW), false);
			return 0;
		}

		context.getSource().sendFeedback(() -> Text.literal(
				"[SSC] Blocked skills for " + target.getName().getString() + ":"
		).formatted(Formatting.GOLD), false);

		for (String blocked : blockedSkills) {
			context.getSource().sendFeedback(() -> Text.literal(
					"  - " + blocked
			).formatted(Formatting.WHITE), false);
		}

		return blockedSkills.size();
	}

	private static int invokeSkill(CommandContext<ServerCommandSource> context, ServerPlayerEntity target, String form, String skill) {
		ServerCommandSource source = context.getSource();
		String executorName = source.getName();

		if (isSkillBlocked(target, form, skill)) {
			LOGGER.warn("[SSC] Blocked skill invocation: " + form + "/" + skill + " on " + target.getName());
			source.sendError(Text.literal("[SSC] Skill is blocked: " + form + "/" + skill));
			return 0;
		}

	if ("snow_fox".equals(form)) {
            return invokeSnowFoxSkill(source, target, skill, executorName);
        } else if ("anubis_wolf".equals(form)) {
            return invokeAnubisWolfSkill(source, target, skill, executorName);
        } else if ("allay".equals(form)) {
            return invokeAllaySkill(source, target, skill, executorName);
        }

        LOGGER.warn("[SSC] Unknown form: " + form);
		source.sendError(Text.literal("[SSC] Unknown form: " + form));
		return 0;
	}

	private static int invokeSnowFoxSkill(ServerCommandSource source, ServerPlayerEntity target, String skill, String executorName) {
		return switch (skill) {
			case "melee_primary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/melee_primary on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpMeleeAbility.execute(target);
				if (!success) {
					source.sendFeedback(() -> Text.literal("[SSC] snow_fox/melee_primary failed (no mana or already dashing)").formatted(Formatting.YELLOW), false);
				} else {
					source.sendFeedback(() -> Text.literal("[SSC] snow_fox/melee_primary invoked").formatted(Formatting.GREEN), false);
				}
				yield 1;
			}
			case "melee_secondary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/melee_secondary on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpTeleportAttack.execute(target);
				if (!success) {
					source.sendFeedback(() -> Text.literal("[SSC] snow_fox/melee_secondary failed (no targets, mana, or already attacking)").formatted(Formatting.YELLOW), false);
				} else {
					source.sendFeedback(() -> Text.literal("[SSC] snow_fox/melee_secondary invoked").formatted(Formatting.GREEN), false);
				}
				yield 1;
			}
			case "ranged_primary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/ranged_primary (frost_ball) on " + target.getName().getString() + " by " + executorName);
				invokeSnowFoxFrostBall(target, executorName);
				source.sendFeedback(() -> Text.literal("[SSC] snow_fox/ranged_primary invoked").formatted(Formatting.GREEN), false);
				yield 1;
			}
			case "ranged_secondary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/ranged_secondary (frost_storm) on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpFrostStorm.startCharging(target);
				if (!success) {
					source.sendFeedback(() -> Text.literal("[SSC] snow_fox/ranged_secondary failed (already charging, on CD, or no mana)").formatted(Formatting.YELLOW), false);
				} else {
					source.sendFeedback(() -> Text.literal("[SSC] snow_fox/ranged_secondary invoked").formatted(Formatting.GREEN), false);
				}
				yield 1;
			}
default -> {
                LOGGER.warn("[SSC] Unknown snow_fox skill: " + skill);
                source.sendError(Text.literal("[SSC] Unknown skill: " + skill + " for form snow_fox"));
                yield 0;
            }
        };
    }

    private static int invokeAnubisWolfSkill(ServerCommandSource source, ServerPlayerEntity target, String skill, String executorName) {
        return switch (skill) {
            case "summon_wolves" -> {
                LOGGER.info("[SSC] Invoking anubis_wolf/summon_wolves on " + target.getName().getString() + " by " + executorName);
                boolean success = AnubisWolfSpSummonWolves.execute(target);
                if (!success) {
                    source.sendFeedback(() -> Text.literal("[SSC] anubis_wolf/summon_wolves failed (on CD, already summoning, or max wolves)").formatted(Formatting.YELLOW), false);
                } else {
                    source.sendFeedback(() -> Text.literal("[SSC] anubis_wolf/summon_wolves invoked").formatted(Formatting.GREEN), false);
                }
                yield 1;
            }
            case "death_domain" -> {
                LOGGER.info("[SSC] Invoking anubis_wolf/death_domain on " + target.getName().getString() + " by " + executorName);
                boolean success = AnubisWolfSpDeathDomain.execute(target);
                if (!success) {
                    source.sendFeedback(() -> Text.literal("[SSC] anubis_wolf/death_domain failed (on CD or already active)").formatted(Formatting.YELLOW), false);
                } else {
                    source.sendFeedback(() -> Text.literal("[SSC] anubis_wolf/death_domain invoked").formatted(Formatting.GREEN), false);
                }
                yield 1;
            }
            default -> {
                LOGGER.warn("[SSC] Unknown anubis_wolf skill: " + skill);
                source.sendError(Text.literal("[SSC] Unknown skill: " + skill + " for form anubis_wolf"));
                yield 0;
            }
        };
    }

    private static int invokeAllaySkill(ServerCommandSource source, ServerPlayerEntity target, String skill, String executorName) {
        return switch (skill) {
            case "jukebox_charge" -> {
                LOGGER.info("[SSC] Invoking allay/jukebox_charge on " + target.getName().getString() + " by " + executorName);
                AllaySPJukebox.tick(target);
                source.sendFeedback(() -> Text.literal("[SSC] allay/jukebox_charge invoked").formatted(Formatting.GREEN), false);
                yield 1;
            }
            case "group_heal" -> {
                LOGGER.info("[SSC] Invoking allay/group_heal on " + target.getName().getString() + " by " + executorName);
                AllaySPGroupHeal.tick(target);
                source.sendFeedback(() -> Text.literal("[SSC] allay/group_heal invoked").formatted(Formatting.GREEN), false);
                yield 1;
            }
            default -> {
                LOGGER.warn("[SSC] Unknown allay skill: " + skill);
                source.sendError(Text.literal("[SSC] Unknown skill: " + skill + " for form allay"));
                yield 0;
            }
        };
    }

    private static void invokeSnowFoxFrostBall(ServerPlayerEntity player, String ownerName) {
		double manaCost = 10.0;
		int currentMana = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RESOURCE);
		if (currentMana >= manaCost) {
			PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RESOURCE, -(int)manaCost);
		}
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD, 100);

		FrostBallEntity frostBall = new FrostBallEntity(player.getWorld(), player);
		Vec3d lookDir = player.getRotationVector().normalize();
		Vec3d startPos = player.getPos().add(lookDir.multiply(0.5));
		frostBall.setPosition(startPos.x, startPos.y, startPos.z);
		frostBall.setVelocity(lookDir.multiply(3.0));
		player.getWorld().spawnEntity(frostBall);
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.ENTITY_SNOWBALL_THROW,
			SoundCategory.PLAYERS, 0.5f, 1.2f);
	}

	private static int sendError(ServerCommandSource source, String message) {
		LOGGER.info("[SSC] Skill command error: " + message);
		source.sendError(Text.literal("[SSC] " + message));
		return 0;
	}
}
