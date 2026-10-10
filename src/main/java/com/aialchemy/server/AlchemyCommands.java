package com.aialchemy.server;

import java.util.ArrayList;
import java.util.List;

import com.aialchemy.AiAlchemy;
import com.aialchemy.core.GeneratedItem;
import com.aialchemy.item.ItemFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The command surface from design document section 12.
 *
 * <pre>
 * /alchemy journal                 the player's discovery log
 * /alchemy inspect                 dump the held item's metadata
 * /alchemy export                  admin: write the cache to one JSON file
 * /alchemy import &lt;json&gt;           admin: merge a shared cache
 * /alchemy ban recipe &lt;key&gt;        admin: disable a recipe
 * /alchemy unban recipe &lt;key&gt;
 * /alchemy ban-item &lt;itemId&gt;
 * /alchemy enable-script &lt;itemId&gt;
 * /alchemy reset-cache             admin: drop every cached recipe
 * /alchemy status                  diagnostics
 * /alchemy usage                   provider usage counters
 * </pre>
 */
public final class AlchemyCommands {

	private AlchemyCommands() {
	}

	private static final SuggestionProvider<CommandSourceStack> RECIPE_KEYS = (context, builder) -> {
		WorldAlchemyState state = AiAlchemy.state();

		if (state != null) {
			for (String key : state.storage().recipes().keySet()) {
				builder.suggest(key);
			}
		}

		return builder.buildFuture();
	};

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("alchemy")
						.then(Commands.literal("journal").executes(AlchemyCommands::journal))
						.then(Commands.literal("inspect").executes(AlchemyCommands::inspect))
						.then(Commands.literal("status").executes(AlchemyCommands::status))
						.then(Commands.literal("usage").executes(AlchemyCommands::usage))
						.then(Commands.literal("export").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
								.executes(AlchemyCommands::export))
						.then(Commands.literal("import").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.argument("json", StringArgumentType.greedyString())
										.executes(AlchemyCommands::importCache)))
						.then(Commands.literal("reset-cache").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN))
								.executes(AlchemyCommands::resetCache))
						.then(Commands.literal("ban").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.literal("recipe")
										.then(Commands.argument("key", StringArgumentType.string())
												.suggests(RECIPE_KEYS)
												.executes(context -> ban(context, true)))))
						.then(Commands.literal("unban").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.literal("recipe")
										.then(Commands.argument("key", StringArgumentType.string())
												.suggests(RECIPE_KEYS)
												.executes(context -> ban(context, false)))))
						.then(Commands.literal("ban-item").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.argument("item_id", StringArgumentType.string())
										.executes(AlchemyCommands::banItem)))
						.then(Commands.literal("enable-script").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.argument("item_id", StringArgumentType.string())
										.executes(AlchemyCommands::enableScript)))));
	}

	private static int journal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		List<String> lines = state.transmute().journalLines(player);

		if (lines.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("command.ai_alchemy.journal_empty"), false);
			return 1;
		}

		context.getSource().sendSuccess(() -> Component.translatable("command.ai_alchemy.journal_header", lines.size()), false);

		for (String line : lines) {
			context.getSource().sendSuccess(() -> Component.literal(" - " + line.replace('\t', ' ')), false);
		}

		return lines.size();
	}

	private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		com.aialchemy.component.AlchemyData data = com.aialchemy.item.AlchemyItem.dataOf(player.getMainHandItem());

		if (data == null) {
			context.getSource().sendFailure(Component.translatable("command.ai_alchemy.not_generated"));
			return 0;
		}

		GeneratedItem item = state.storage().cachedByItemId(data.itemId());

		if (item == null) {
			context.getSource().sendFailure(Component.literal("No cached metadata for " + data.itemId()));
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.literal(com.aialchemy.generation.GenerationService.describe(item)), false);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.literal("recipes: " + state.storage().recipeCount()), false);
		context.getSource().sendSuccess(() -> Component.literal("provider: " + state.generation().provider().name()), false);
		context.getSource().sendSuccess(() -> Component.literal("transmute: " + state.transmute().describe()), false);
		context.getSource().sendSuccess(() -> Component.literal("scripts: " + state.scripts().describe()), false);
		return 1;
	}

	private static int usage(CommandContext<CommandSourceStack> context) {
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.literal("AI requests: " + state.generation().requestCount()
				+ ", failures: " + state.generation().failureCount()), false);

		if (context.getSource().getEntity() instanceof ServerPlayer player) {
			context.getSource().sendSuccess(() -> Component.literal("your quota today: "
					+ state.rateLimits().usedToday(player.getUUID()) + " used, "
					+ state.rateLimits().remainingToday(player.getUUID()) + " left"), false);
		}

		return 1;
	}

	private static int export(CommandContext<CommandSourceStack> context) {
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		java.nio.file.Path target = state.storage().exportToServerRoot();

		if (target == null) {
			context.getSource().sendFailure(Component.literal("Could not write the export file"));
			return 0;
		}

		context.getSource().sendSuccess(() -> Component.literal("Exported to " + target), true);
		return 1;
	}

	private static int importCache(CommandContext<CommandSourceStack> context) {
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		String json = StringArgumentType.getString(context, "json");

		try {
			int imported = state.storage().importCache(json);
			context.getSource().sendSuccess(() -> Component.literal("Imported " + imported + " recipes"), true);
			return imported;
		} catch (RuntimeException exception) {
			context.getSource().sendFailure(Component.literal("Import failed: " + exception.getMessage()));
			return 0;
		}
	}

	private static int resetCache(CommandContext<CommandSourceStack> context) {
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		state.storage().clearCache();
		context.getSource().sendSuccess(() -> Component.literal("Cache cleared"), true);
		return 1;
	}

	private static int ban(CommandContext<CommandSourceStack> context, boolean banned) {
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		String key = StringArgumentType.getString(context, "key");
		GeneratedItem item = state.storage().recipe(key).orElse(null);
		String itemId = item == null ? "" : item.itemId();

		if (banned) {
			state.storage().ban(key, itemId);
		} else {
			state.storage().unban(key, itemId);
		}

		context.getSource().sendSuccess(() -> Component.literal((banned ? "Banned " : "Unbanned ") + key), true);
		return 1;
	}

	private static int banItem(CommandContext<CommandSourceStack> context) {
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		String itemId = StringArgumentType.getString(context, "item_id");
		state.storage().ban("", itemId);
		context.getSource().sendSuccess(() -> Component.literal("Banned item " + itemId), true);
		return 1;
	}

	private static int enableScript(CommandContext<CommandSourceStack> context) {
		WorldAlchemyState state = require(context);

		if (state == null) {
			return 0;
		}

		String itemId = StringArgumentType.getString(context, "item_id");
		state.scripts().enable(itemId);
		context.getSource().sendSuccess(() -> Component.literal("Re-enabled the script for " + itemId), true);
		return 1;
	}

	private static WorldAlchemyState require(CommandContext<CommandSourceStack> context) {
		WorldAlchemyState state = AiAlchemy.state();

		if (state == null) {
			context.getSource().sendFailure(Component.literal("AI Alchemy is not loaded on this server"));
		}

		return state;
	}

	/** Used by the help text and tests. */
	public static List<String> commandNames() {
		return new ArrayList<>(List.of("journal", "inspect", "status", "usage", "export", "import", "reset-cache", "ban",
				"unban", "ban-item", "enable-script"));
	}
}