package net.onixary.shapeShifterCurseFabric.ssc_addon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
						.requires(source -> source.hasPermissionLevel(2))
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
				// 玩家自助白名单 GUI（无 OP 限制，仅作用于调用者自己）
				.then(CommandManager.literal("my_whitelist")
						.executes(SscAddonCommands::openWhitelistGui)
				)
				.then(CommandManager.literal("skill")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("form", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										Arrays.asList("snow_fox", "anubis_wolf", "allay", "axolotl", "wild_cat", "familiar_fox", "familiar_fox_red"), builder))
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
				.then(CommandManager.literal("resistance")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("get")
								.executes(ctx -> resistanceGet(ctx, ctx.getSource().getPlayer()))
								.then(CommandManager.argument("player", EntityArgumentType.player())
										.executes(ctx -> resistanceGet(ctx, EntityArgumentType.getPlayer(ctx, "player")))
								)
						)
						.then(CommandManager.literal("set")
								.then(CommandManager.argument("value", IntegerArgumentType.integer(0))
										.executes(ctx -> resistanceSet(ctx, ctx.getSource().getPlayer(), IntegerArgumentType.getInteger(ctx, "value")))
										.then(CommandManager.argument("player", EntityArgumentType.player())
												.executes(ctx -> resistanceSet(ctx, EntityArgumentType.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "value")))
										)
								)
						)
						.then(CommandManager.literal("add")
								.then(CommandManager.argument("delta", IntegerArgumentType.integer())
										.executes(ctx -> resistanceAdd(ctx, ctx.getSource().getPlayer(), IntegerArgumentType.getInteger(ctx, "delta")))
										.then(CommandManager.argument("player", EntityArgumentType.player())
												.executes(ctx -> resistanceAdd(ctx, EntityArgumentType.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "delta")))
										)
								)
						)
				)
				// ============== /ssc_addon mancianima_assault ==============
				// 控制玩家"今日是否可触发契灵敲钟袭击"的每日冷却（持久化）
				.then(CommandManager.literal("mancianima_assault")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("reset")
								.executes(ctx -> mancianimaAssaultReset(ctx, ctx.getSource().getPlayer()))
								.then(CommandManager.argument("player", EntityArgumentType.player())
										.executes(ctx -> mancianimaAssaultReset(ctx, EntityArgumentType.getPlayer(ctx, "player")))
								)
						)
						.then(CommandManager.literal("lock")
								.executes(ctx -> mancianimaAssaultLock(ctx, ctx.getSource().getPlayer()))
								.then(CommandManager.argument("player", EntityArgumentType.player())
										.executes(ctx -> mancianimaAssaultLock(ctx, EntityArgumentType.getPlayer(ctx, "player")))
								)
						)
						.then(CommandManager.literal("status")
								.executes(ctx -> mancianimaAssaultStatus(ctx, ctx.getSource().getPlayer()))
								.then(CommandManager.argument("player", EntityArgumentType.player())
										.executes(ctx -> mancianimaAssaultStatus(ctx, EntityArgumentType.getPlayer(ctx, "player")))
								)
						)
				)
		);
	}

	// ============== /ssc_addon mancianima_assault ==============
	private static final long MANCIANIMA_ASSAULT_COOLDOWN_TICKS = 24000L;

	private static int mancianimaAssaultReset(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
		if (player == null) { ctx.getSource().sendError(Text.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		net.minecraft.server.MinecraftServer srv = ctx.getSource().getServer();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState state =
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState.get(srv);
		if (state.lastRoll.remove(player.getUuid()) != null) {
			state.markDirty();
		}
		ctx.getSource().sendFeedback(() -> Text.translatable("command.ssc_addon.assault.reset", player.getName().getString()), true);
		return 1;
	}

	private static int mancianimaAssaultLock(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
		if (player == null) { ctx.getSource().sendError(Text.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		net.minecraft.server.MinecraftServer srv = ctx.getSource().getServer();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState state =
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState.get(srv);
		state.lastRoll.put(player.getUuid(), srv.getOverworld().getTime());
		state.markDirty();
		ctx.getSource().sendFeedback(() -> Text.translatable("command.ssc_addon.assault.lock", player.getName().getString()), true);
		return 1;
	}

	private static int mancianimaAssaultStatus(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
		if (player == null) { ctx.getSource().sendError(Text.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		net.minecraft.server.MinecraftServer srv = ctx.getSource().getServer();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState state =
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAssaultState.get(srv);
		Long last = state.lastRoll.get(player.getUuid());
		long now = srv.getOverworld().getTime();
		if (last == null || now - last >= MANCIANIMA_ASSAULT_COOLDOWN_TICKS) {
			ctx.getSource().sendFeedback(() -> Text.translatable("command.ssc_addon.assault.status.available", player.getName().getString()), false);
		} else {
			long remain = MANCIANIMA_ASSAULT_COOLDOWN_TICKS - (now - last);
			ctx.getSource().sendFeedback(() -> Text.translatable("command.ssc_addon.assault.status.cooldown", player.getName().getString(), remain, remain / 20), false);
		}
		return 1;
	}

	// ============== /ssc_addon resistance ==============
	private static int resistanceGet(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
		if (player == null) { ctx.getSource().sendError(Text.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		int cur = PowerUtils.getResourceValue(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		int max = PowerUtils.getResourceMax(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		ctx.getSource().sendFeedback(() -> Text.translatable("command.ssc_addon.resistance.get", player.getName().getString(), cur, max), false);
		return 1;
	}

	private static int resistanceSet(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player, int value) {
		if (player == null) { ctx.getSource().sendError(Text.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		int max = PowerUtils.getResourceMax(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		if (max <= 0) { ctx.getSource().sendError(Text.translatable("command.ssc_addon.resistance.no_power")); return 0; }
		int clamped = Math.max(0, Math.min(value, max));
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.MANCIANIMA_RESISTANCE, clamped);
		ctx.getSource().sendFeedback(() -> Text.translatable("command.ssc_addon.resistance.set", player.getName().getString(), clamped, max), true);
		return 1;
	}

	private static int resistanceAdd(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player, int delta) {
		if (player == null) { ctx.getSource().sendError(Text.translatable("command.ssc_addon.common.no_target_player")); return 0; }
		int max = PowerUtils.getResourceMax(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		if (max <= 0) { ctx.getSource().sendError(Text.translatable("command.ssc_addon.resistance.no_power")); return 0; }
		int cur = PowerUtils.getResourceValue(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		int next = Math.max(0, Math.min(cur + delta, max));
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.MANCIANIMA_RESISTANCE, next);
		ctx.getSource().sendFeedback(() -> Text.translatable("command.ssc_addon.resistance.add", player.getName().getString(), cur, next, max), true);
		return 1;
	}

	private static int setMana(CommandContext<ServerCommandSource> context, Collection<ServerPlayerEntity> targets, int amount) {
		int count = 0;
		for (ServerPlayerEntity player : targets) {
			boolean updated = false;

			int snowFoxMax = PowerUtils.getResourceMax(player, FormIdentifiers.SNOW_FOX_RESOURCE);
			if (snowFoxMax > 0) {
				int clamped = Math.min(amount, snowFoxMax);
				PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RESOURCE, clamped);
				updated = true;
			}

			int allayMax = PowerUtils.getResourceMax(player, FormIdentifiers.ALLAY_MANA_RESOURCE);
			if (allayMax > 0) {
				int clamped = Math.min(amount, allayMax);
				PowerUtils.setResourceValueAndSync(player, FormIdentifiers.ALLAY_MANA_RESOURCE, clamped);
				updated = true;
			}

			int soulMax = PowerUtils.getResourceMax(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY);
			if (soulMax > 0) {
				int clamped = Math.min(amount, soulMax);
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSoulEnergy.setEnergy(player, clamped);
				updated = true;
			}

			try {
				ManaComponent manaComponent = ManaUtils.getManaComponent(player);
				if (manaComponent != null) {
					double newVal = Math.min(amount, manaComponent.getMaxMana());
					manaComponent.setMana(newVal);
					updated = true;
				}
			} catch (Exception e) {
				LOGGER.debug("ManaComponent not available for {}", player.getName().getString(), e);
			}

			if (updated) {
				count++;
			}
		}
		final int finalCount = count;
		context.getSource().sendFeedback(() -> Text.translatable("command.ssc_addon.set_mana.result", finalCount, amount), true);
		return count;
	}

	private static int markOwner(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Collection<? extends Entity> targets = EntityArgumentType.getEntities(context, "targets");
		ServerCommandSource source = context.getSource();
		Entity attacker = source.getEntity();

		if (attacker instanceof ServerPlayerEntity player) {
			UUID playerUUID = player.getUuid();
			for (Entity target : targets) {
				if (target instanceof LivingEntity livingTarget) {
					// Update ownership tag: remove old owner, set new owner
					livingTarget.getCommandTags().removeIf(tag -> tag.startsWith("ssc_owner:"));
					livingTarget.addCommandTag("ssc_owner:" + playerUUID.toString());
				}
			}
			return targets.size();
		}
		return 0;
	}

	private static int debugFormInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

		// 获取玩家形态组件
		PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);

		// 准备调试信息（服务器日志保留原始英文，玩家聘天用可翻译版本）
		StringBuilder debugInfo = new StringBuilder();
		debugInfo.append("===== SSC_ADDON FORM DEBUG =====\n");
		player.sendMessage(Text.translatable("command.ssc_addon.debug_form.header").formatted(Formatting.AQUA), false);

		if (component == null) {
			debugInfo.append("PlayerFormComponent: NULL\n");
			player.sendMessage(Text.translatable("command.ssc_addon.debug_form.no_component").formatted(Formatting.AQUA), false);
		} else {
			PlayerFormBase currentForm = component.getCurrentForm();
			if (currentForm == null) {
				debugInfo.append("Current Form: NULL (no form active)\n");
				player.sendMessage(Text.translatable("command.ssc_addon.debug_form.no_form").formatted(Formatting.AQUA), false);
			} else {
				debugInfo.append("Form ID: ").append(currentForm.FormID).append("\n");
				player.sendMessage(Text.translatable("command.ssc_addon.debug_form.form_id", String.valueOf(currentForm.FormID)).formatted(Formatting.AQUA), false);
				debugInfo.append("Form Class: ").append(currentForm.getClass().getName()).append("\n");
				player.sendMessage(Text.translatable("command.ssc_addon.debug_form.form_class", currentForm.getClass().getName()).formatted(Formatting.AQUA), false);
				debugInfo.append("Phase: ").append(currentForm.getPhase()).append("\n");
				player.sendMessage(Text.translatable("command.ssc_addon.debug_form.phase", String.valueOf(currentForm.getPhase())).formatted(Formatting.AQUA), false);
				debugInfo.append("Body Type: ").append(currentForm.getBodyType()).append("\n");
				player.sendMessage(Text.translatable("command.ssc_addon.debug_form.body_type", String.valueOf(currentForm.getBodyType())).formatted(Formatting.AQUA), false);
			}
		}
		debugInfo.append("================================");
		player.sendMessage(Text.translatable("command.ssc_addon.debug_form.footer").formatted(Formatting.AQUA), false);

		// 记录到服务器日志
		if (LOGGER.isInfoEnabled()) {
			LOGGER.info(debugInfo.toString());
		}

		return 1;
	}

	private static int debugMana(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		boolean foundMana = false;

		int snowFoxVal = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RESOURCE);
		int snowFoxMax = PowerUtils.getResourceMax(player, FormIdentifiers.SNOW_FOX_RESOURCE);
		if (snowFoxMax > 0) {
			player.sendMessage(Text.translatable("command.ssc_addon.debug_mana.snow_fox", snowFoxVal, snowFoxMax).formatted(Formatting.AQUA), false);
			foundMana = true;
		}

		int allayVal = PowerUtils.getResourceValue(player, FormIdentifiers.ALLAY_MANA_RESOURCE);
		int allayMax = PowerUtils.getResourceMax(player, FormIdentifiers.ALLAY_MANA_RESOURCE);
		if (allayMax > 0) {
			player.sendMessage(Text.translatable("command.ssc_addon.debug_mana.allay", allayVal, allayMax).formatted(Formatting.AQUA), false);
			foundMana = true;
		}

		int soulVal = PowerUtils.getResourceValue(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY);
		int soulMax = PowerUtils.getResourceMax(player, FormIdentifiers.ANUBIS_WOLF_SP_SOUL_ENERGY);
		if (soulMax > 0) {
			player.sendMessage(Text.translatable("command.ssc_addon.debug_mana.anubis_wolf", soulVal, soulMax).formatted(Formatting.AQUA), false);
			foundMana = true;
		}

		try {
			ManaComponent manaComponent = ManaUtils.getManaComponent(player);
			if (manaComponent != null) {
				if (manaComponent.getManaTypeID() != null) {
					player.sendMessage(Text.translatable("command.ssc_addon.debug_mana.mana_type", manaComponent.getManaTypeID().toString()).formatted(Formatting.AQUA), false);
					player.sendMessage(Text.translatable("command.ssc_addon.debug_mana.mana", manaComponent.getMana(), manaComponent.getMaxMana()).formatted(Formatting.AQUA), false);
					foundMana = true;
				} else if (manaComponent.getMaxMana() > 0) {
					player.sendMessage(Text.translatable("command.ssc_addon.debug_mana.mana_notype", manaComponent.getMana(), manaComponent.getMaxMana()).formatted(Formatting.AQUA), false);
					foundMana = true;
				}
			}
		} catch (Exception e) {
			LOGGER.debug("ManaComponent not available for {}", player.getName().getString(), e);
		}

		if (!foundMana) {
			player.sendMessage(Text.translatable("command.ssc_addon.debug_mana.no_mana").formatted(Formatting.YELLOW), false);
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
			player.sendMessage(Text.translatable("command.ssc_addon.book.not_found", bookId).formatted(Formatting.RED), false);
			return 0;
		}

		// 获取书籍信息用于显示
		net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.BookData bookData =
				net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookDataById(bookId, language);

		if (!player.getInventory().insertStack(book)) {
			player.dropItem(book, false);
		}

		String bookTitle = bookData != null ? bookData.title : bookId;
		player.sendMessage(Text.translatable("command.ssc_addon.book.obtained", bookTitle).formatted(Formatting.GREEN), false);
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
			player.sendMessage(Text.translatable("command.ssc_addon.book.list.empty").formatted(Formatting.YELLOW), false);
			return 0;
		}

		player.sendMessage(Text.translatable("command.ssc_addon.book.list.header").formatted(Formatting.GOLD), false);
		player.sendMessage(Text.translatable("command.ssc_addon.book.list.count", bookIds.size()).formatted(Formatting.AQUA), false);

		for (String bookId : bookIds) {
			net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.BookData bookData =
					net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.getBookDataById(bookId, language);

			if (bookData != null) {
				// 截断过长的标题
				String displayTitle = bookData.title;
				if (displayTitle.length() > 30) {
					displayTitle = displayTitle.substring(0, 27) + "...";
				}
				player.sendMessage(Text.translatable("command.ssc_addon.book.list.entry", bookId, displayTitle, bookData.author).formatted(Formatting.WHITE), false);
			} else {
				player.sendMessage(Text.translatable("command.ssc_addon.book.list.entry_failed", bookId).formatted(Formatting.RED), false);
			}
		}

		player.sendMessage(Text.translatable("command.ssc_addon.book.list.footer").formatted(Formatting.GOLD), false);
		player.sendMessage(Text.translatable("command.ssc_addon.book.list.hint").formatted(Formatting.GRAY), false);

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
			player.sendMessage(Text.translatable("command.ssc_addon.reload.books.success", bookCount).formatted(Formatting.GREEN), false);
			return 1;
		} catch (Exception e) {
			player.sendMessage(Text.translatable("command.ssc_addon.reload.books.fail", e.getMessage()).formatted(Formatting.RED), false);
			LOGGER.error("Failed to reload books", e);
			return 0;
		}
	}

	/**
	 * 重新加载模组配置
	 */
	private static int reloadConfig(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

		try {
			ConfigChangeManager.notifyChange();
			player.sendMessage(Text.translatable("command.ssc_addon.reload.config.success").formatted(Formatting.GREEN), true);
			return 1;
		} catch (Exception e) {
			player.sendMessage(Text.translatable("command.ssc_addon.reload.config.fail", e.getMessage()).formatted(Formatting.RED), true);
			LOGGER.error("Failed to reload config", e);
			return 0;
		}
	}

	// ===== SP悦灵治疗白名单命令 =====
	// 旧的 /ssc_addon whitelist add/remove/list/clear 系列指令已被 my_whitelist GUI 取代，
	// 相关私有方法已删除以减少代码维护面。

	/**
	 * 打开玩家自助白名单 GUI（无 OP 限制，仅作用于调用者本人）。
	 * 服务端通过 S2C 包推送当前白名单数据给客户端，由客户端打开 WhitelistManageScreen。
	 * 控制台 / 命令方块调用会返回错误。
	 */
	private static int openWhitelistGui(CommandContext<ServerCommandSource> context) {
		ServerPlayerEntity player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendError(Text.translatable("command.ssc_addon.whitelist.gui.console_only"));
			return 0;
		}
		net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.sendWhitelistSync(player);
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
			context.getSource().sendFeedback(() -> Text.translatable(
					"command.ssc_addon.block_skill.already", form, skill, target.getName().getString()
			).formatted(Formatting.YELLOW), false);
			return 0;
		}

		blockSkill(target, form, skill);
		LOGGER.info("[SSC] Blocked " + form + "/" + skill + " for " + target.getName().getString());
		context.getSource().sendFeedback(() -> Text.translatable(
				"command.ssc_addon.block_skill.success", target.getName().getString(), form, skill
		).formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int unblockSkill(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		String form = StringArgumentType.getString(context, "form");
		String skill = StringArgumentType.getString(context, "skill");

		if (!isSkillBlocked(target, form, skill)) {
			context.getSource().sendFeedback(() -> Text.translatable(
					"command.ssc_addon.unblock_skill.not_blocked", form, skill, target.getName().getString()
			).formatted(Formatting.YELLOW), false);
			return 0;
		}

		unblockSkill(target, form, skill);
		LOGGER.info("[SSC] Unblocked " + form + "/" + skill + " for " + target.getName().getString());
		context.getSource().sendFeedback(() -> Text.translatable(
				"command.ssc_addon.unblock_skill.success", target.getName().getString(), form, skill
		).formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int listBlockedSkills(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		List<String> blockedSkills = getBlockedSkills(target);

		if (blockedSkills.isEmpty()) {
			context.getSource().sendFeedback(() -> Text.translatable(
					"command.ssc_addon.list_blocked.empty", target.getName().getString()
			).formatted(Formatting.YELLOW), false);
			return 0;
		}

		context.getSource().sendFeedback(() -> Text.translatable(
				"command.ssc_addon.list_blocked.header", target.getName().getString()
		).formatted(Formatting.GOLD), false);

		for (String blocked : blockedSkills) {
			context.getSource().sendFeedback(() -> Text.translatable(
					"command.ssc_addon.list_blocked.entry", blocked
			).formatted(Formatting.WHITE), false);
		}

		return blockedSkills.size();
	}

	private static int invokeSkill(CommandContext<ServerCommandSource> context, ServerPlayerEntity target, String form, String skill) {
		ServerCommandSource source = context.getSource();
		String executorName = source.getName();

		if (isSkillBlocked(target, form, skill)) {
			LOGGER.warn("[SSC] Blocked skill invocation: " + form + "/" + skill + " on " + target.getName());
			source.sendError(Text.translatable("command.ssc_addon.ssc_test.skill_blocked", form, skill));
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
		source.sendError(Text.translatable("command.ssc_addon.ssc_test.unknown_form", form));
		return 0;
	}

	private static int invokeSnowFoxSkill(ServerCommandSource source, ServerPlayerEntity target, String skill, String executorName) {
		return switch (skill) {
			case "melee_primary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/melee_primary on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpMeleeAbility.execute(target);
				if (!success) {
					source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.snow_fox.melee_primary.fail").formatted(Formatting.YELLOW), false);
				} else {
					source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.snow_fox.melee_primary.success").formatted(Formatting.GREEN), false);
				}
				yield 1;
			}
			case "melee_secondary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/melee_secondary on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpTeleportAttack.execute(target);
				if (!success) {
					source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.snow_fox.melee_secondary.fail").formatted(Formatting.YELLOW), false);
				} else {
					source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.snow_fox.melee_secondary.success").formatted(Formatting.GREEN), false);
				}
				yield 1;
			}
			case "ranged_primary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/ranged_primary (frost_ball) on " + target.getName().getString() + " by " + executorName);
				boolean success = invokeSnowFoxFrostBall(target);
				if (!success) {
					source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.snow_fox.ranged_primary.fail").formatted(Formatting.YELLOW), false);
				} else {
					source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.snow_fox.ranged_primary.success").formatted(Formatting.GREEN), false);
				}
				yield success ? 1 : 0;
			}
			case "ranged_secondary" -> {
				LOGGER.info("[SSC] Invoking snow_fox/ranged_secondary (frost_storm) on " + target.getName().getString() + " by " + executorName);
				boolean success = SnowFoxSpFrostStorm.startCharging(target);
				if (!success) {
					source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.snow_fox.ranged_secondary.fail").formatted(Formatting.YELLOW), false);
				} else {
					source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.snow_fox.ranged_secondary.success").formatted(Formatting.GREEN), false);
				}
				yield 1;
			}
default -> {
                LOGGER.warn("[SSC] Unknown snow_fox skill: " + skill);
                source.sendError(Text.translatable("command.ssc_addon.ssc_test.unknown_skill", skill, "snow_fox"));
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
                    source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.anubis_wolf.summon_wolves.fail").formatted(Formatting.YELLOW), false);
                } else {
                    source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.anubis_wolf.summon_wolves.success").formatted(Formatting.GREEN), false);
                }
                yield 1;
            }
            case "death_domain" -> {
                LOGGER.info("[SSC] Invoking anubis_wolf/death_domain on " + target.getName().getString() + " by " + executorName);
                boolean success = AnubisWolfSpDeathDomain.execute(target);
                if (!success) {
                    source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.anubis_wolf.death_domain.fail").formatted(Formatting.YELLOW), false);
                } else {
                    source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.anubis_wolf.death_domain.success").formatted(Formatting.GREEN), false);
                }
                yield 1;
            }
            default -> {
                LOGGER.warn("[SSC] Unknown anubis_wolf skill: " + skill);
                source.sendError(Text.translatable("command.ssc_addon.ssc_test.unknown_skill", skill, "anubis_wolf"));
                yield 0;
            }
        };
    }

	private static int invokeAllaySkill(ServerCommandSource source, ServerPlayerEntity target, String skill, String executorName) {
		return switch (skill) {
			case "jukebox_charge" -> {
				LOGGER.info("[SSC] Invoking allay/jukebox_charge on " + target.getName().getString() + " by " + executorName);
				AllaySPJukebox.tick(target);
				source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.allay.jukebox_charge.triggered").formatted(Formatting.AQUA), false);
				yield 1;
			}
			case "group_heal" -> {
				LOGGER.info("[SSC] Invoking allay/group_heal on " + target.getName().getString() + " by " + executorName);
				AllaySPGroupHeal.tick(target);
				source.sendFeedback(() -> Text.translatable("command.ssc_addon.ssc_test.allay.group_heal.triggered").formatted(Formatting.AQUA), false);
				yield 1;
			}
			default -> {
				LOGGER.warn("[SSC] Unknown allay skill: " + skill);
				source.sendError(Text.translatable("command.ssc_addon.ssc_test.unknown_skill", skill, "allay"));
				yield 0;
			}
		};
	}

	private static boolean invokeSnowFoxFrostBall(ServerPlayerEntity player) {
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD) > 0) {
			return false;
		}

		int manaCost = 10;
		int currentMana = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RESOURCE);
		if (currentMana >= manaCost) {
			PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RESOURCE, -manaCost);
		} else {
			return false;
		}
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD, 100);

		FrostBallEntity frostBall = new FrostBallEntity(player.getWorld(), player);
		Vec3d lookDir = player.getRotationVector().normalize();
		Vec3d startPos = player.getPos().add(lookDir.multiply(0.5));
		frostBall.setPosition(startPos.x, startPos.y, startPos.z);
		frostBall.setVelocity(lookDir.multiply(3.0));
		player.getWorld().spawnEntity(frostBall);
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.5f, 1.2f);
		return true;
	}
}
