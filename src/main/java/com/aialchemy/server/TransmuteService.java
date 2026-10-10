package com.aialchemy.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.aialchemy.AiAlchemy;
import com.aialchemy.config.AlchemyConfig;
import com.aialchemy.core.GeneratedItem;
import com.aialchemy.core.Ingredient;
import com.aialchemy.core.PowerBudget;
import com.aialchemy.core.RecipeKey;
import com.aialchemy.core.icon.ProceduralIcon;
import com.aialchemy.generation.GenerationService;
import com.aialchemy.item.ItemFactory;
import com.aialchemy.network.AlchemyNetworking;
import com.aialchemy.network.AlchemyPayloads;
import com.aialchemy.script.ScriptRuntime;
import com.aialchemy.storage.AlchemyStorage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The crafting flow of design document section 7.3, in order: read the real grid, canonicalise the
 * ingredients, consult the cache, fall back to a deterministic offline item, then ask the model in
 * the background.
 *
 * <p>Players are never left waiting on the server thread: the request returns a status line
 * immediately and the finished item arrives as a result message plus the item itself.</p>
 */
public final class TransmuteService {

	private final AlchemyStorage storage;
	private final AlchemyConfig config;
	private final GenerationService generation;
	private final AssetSync assets;
	private final ScriptRuntime scripts;
	private final RateLimits limits;
	private final com.aialchemy.core.ItemValueTable valueTable;
	private final Set<UUID> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private MinecraftServer server;

	public TransmuteService(AlchemyStorage storage, AlchemyConfig config, GenerationService generation,
			AssetSync assets, ScriptRuntime scripts, RateLimits limits, com.aialchemy.core.ItemValueTable valueTable) {
		this.storage = storage;
		this.config = config;
		this.generation = generation;
		this.assets = assets;
		this.scripts = scripts;
		this.limits = limits;
		this.valueTable = valueTable;
	}

	public void attach(MinecraftServer server) {
		this.server = server;
	}

	public AlchemyConfig config() {
		return this.config;
	}

	public boolean isPending(UUID playerId) {
		return this.pending.contains(playerId);
	}

	/** Entry point for the Transmute button. Always called on the server thread. */
	public void handleRequest(ServerPlayer player, boolean forceAi) {
		CraftingGrid grid = CraftingGrid.read(player);

		if (grid == null || grid.empty()) {
			fail(player, "status.ai_alchemy.empty_grid");
			return;
		}

		if (grid.containsGeneratedItem()) {
			fail(player, "status.ai_alchemy.generated_ingredient");
			return;
		}

		if (grid.ingredients().size() > 9) {
			fail(player, "status.ai_alchemy.too_many");
			return;
		}

		String recipeKey = RecipeKey.compute(grid.ingredients(), this.config.shapedMatters);
		String itemId = RecipeKey.itemId(recipeKey, player.level().getSeed());

		if (this.storage.isBanned(recipeKey, itemId)) {
			fail(player, "status.ai_alchemy.banned");
			return;
		}

		// 1. A vanilla recipe always wins unless the player explicitly overrides it, so the mod never
		//    hijacks a combination the game already understands.
		if (this.config.vanillaPriority && !forceAi && hasVanillaRecipe(player, grid)) {
			fail(player, "status.ai_alchemy.vanilla_recipe");
			return;
		}

		// 2. Cache first: free, instant and identical for the same ingredient set.
		if (this.config.cacheResults) {
			GeneratedItem cached = this.storage.recipe(recipeKey)
					.orElseGet(() -> this.storage.cachedByItemId(itemId));

			if (cached != null) {
				deliver(player, grid, cached, AlchemyPayloads.Status.CACHE_HIT);
				return;
			}
		}

		// 3. Offline mode: use the validated template item instead of failing.
		if (this.generation.offline()) {
			GeneratedItem offline = this.storage.recipe(recipeKey).orElse(null);

			if (offline != null) {
				deliver(player, grid, offline, AlchemyPayloads.Status.OFFLINE);
				return;
			}

			GeneratedItem deterministic = buildOfflineItem(player, grid, recipeKey, itemId);
			this.storage.store(deterministic);
			deliver(player, grid, deterministic, AlchemyPayloads.Status.OFFLINE);
			return;
		}

		// 4. Rate limits only apply to real generations.
		long now = System.currentTimeMillis();
		RateLimits.Verdict verdict = this.limits.check(player.getUUID(), now);

		if (verdict != RateLimits.Verdict.ALLOWED) {
			respond(player, new AlchemyPayloads.TransmuteResult(AlchemyPayloads.Status.RATE_LIMITED,
					verdict == RateLimits.Verdict.COOLDOWN
							? "status.ai_alchemy.cooldown"
							: "status.ai_alchemy.daily_limit", "", ""));
			return;
		}

		if (!this.pending.add(player.getUUID())) {
			respond(player, new AlchemyPayloads.TransmuteResult(AlchemyPayloads.Status.PENDING,
					"status.ai_alchemy.already_pending", "", ""));
			return;
		}

		int tier = tierFor(grid.ingredients());
		List<Integer> colors = ingredientColors(grid.stacks());
		respond(player, new AlchemyPayloads.TransmuteResult(AlchemyPayloads.Status.PENDING,
				"status.ai_alchemy.generating", itemId, ""));

		GenerationService.Request request = new GenerationService.Request(recipeKey, itemId,
				promptIngredientList(grid.ingredients()), colors, tier, player.getGameProfile().name(),
				System.currentTimeMillis(), player.level().getSeed());

		this.generation.generate(request).whenComplete((outcome, throwable) -> {
			if (this.server == null) {
				return;
			}

			this.server.execute(() -> {
				this.pending.remove(player.getUUID());

				if (outcome != null && outcome.ok()) {
					this.limits.record(player.getUUID(), System.currentTimeMillis());
					this.storage.store(outcome.item());
					this.storage.saveIfDirty();
					deliver(player, grid, outcome.item(), AlchemyPayloads.Status.SUCCESS);
				} else {
					String reason = outcome != null ? outcome.error()
							: (throwable == null ? "generation failed" : throwable.getMessage());
					AiAlchemy.LOGGER.warn("Transmute failed for {}: {}", player.getGameProfile().name(), reason);
						respond(player, new AlchemyPayloads.TransmuteResult(AlchemyPayloads.Status.FAILED,
								"status.ai_alchemy.failed", "", diagnostic(reason)));
				}
			});
		});
	}

	/** Free, instant, deterministic items for offline servers: the shell template plus the palette. */
	private GeneratedItem buildOfflineItem(ServerPlayer player, CraftingGrid grid, String recipeKey, String itemId) {
		List<Integer> colors = ingredientColors(grid.stacks());
		com.aialchemy.core.icon.Palette palette = com.aialchemy.core.icon.Palette.fromColors(colors, 4);
		com.aialchemy.core.Shell shell = shellForGrid(grid.ingredients());
		com.aialchemy.core.StatSet stats = com.aialchemy.core.StatRanges.forTier(tierFor(grid.ingredients()))
				.clamp(new com.aialchemy.core.StatSet(2f, 4f, 120, 1f, 0f, 2f, 2, 1f, 1f), shell);

		return new GeneratedItem(itemId, recipeKey, "Unnamed " + shell.jsonName(), List.of(
				"Crafted offline from " + grid.ingredients().size() + " ingredients."),
				shell, com.aialchemy.core.Rarity.COMMON, stats, List.of(), palette,
				ProceduralIcon.build(palette, shell, itemId.hashCode()), null, null, tierFor(grid.ingredients()),
				player.getGameProfile().name(), System.currentTimeMillis(), List.of(), true);
	}

	/** Hands the item over, empties the grid and pushes the assets to clients. */
	private void deliver(ServerPlayer player, CraftingGrid grid, GeneratedItem item, AlchemyPayloads.Status status) {
		ItemStack stack = ItemFactory.create(item);
		consumeGrid(player, grid);

		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}

		boolean first = this.storage.recordDiscovery(player.getUUID().toString(), item.recipeKey());
		this.assets.sendIcon(player, item.itemId());
		this.assets.sendModel(player, item.itemId());

		if (first) {
			this.assets.broadcastAssets(item.itemId(), item.model() == null ? "" : item.itemId());
		}

		this.storage.saveIfDirty();
		respond(player, new AlchemyPayloads.TransmuteResult(status, item.name(), item.itemId(), ""));
	}

	private void consumeGrid(ServerPlayer player, CraftingGrid grid) {
		if (grid.container() != null) {
			grid.container().clearContent();
			player.containerMenu.broadcastChanges();
			return;
		}

		java.util.Set<net.minecraft.world.Container> cleared = java.util.Collections.newSetFromMap(
				new java.util.IdentityHashMap<>());

		for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
			if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer container && cleared.add(container)) {
				container.clearContent();
			}
		}

		player.containerMenu.broadcastChanges();
	}

	private void fail(ServerPlayer player, String messageKey) {
		respond(player, new AlchemyPayloads.TransmuteResult(AlchemyPayloads.Status.FAILED, messageKey, "", ""));
	}

	/** Keep provider diagnostics useful without sending a giant response or a secret to the client. */
	private static String diagnostic(String reason) {
		if (reason == null || reason.isBlank()) return "unknown server error";
		String safe = reason.replaceAll("(?i)(api[_ -]?key|authorization|bearer)\\s*[:=]?\\s*\\S+", "$1=[redacted]")
				.replaceAll("\\s+", " ").trim();
		return safe.length() > 480 ? safe.substring(0, 480) + "..." : safe;
	}

	private void respond(ServerPlayer player, AlchemyPayloads.TransmuteResult result) {
		AlchemyNetworking.send(player, result);
	}

	private boolean hasVanillaRecipe(ServerPlayer player, CraftingGrid grid) {
		try {
			var input = grid.toCraftingInput();
			var level = player.level();
			var found = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
			return found.isPresent();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public List<String> journalLines(ServerPlayer player) {
		List<String> lines = new ArrayList<>();

		for (String recipeKey : this.storage.journal(player.getUUID().toString())) {
			GeneratedItem item = this.storage.recipe(recipeKey).orElse(null);

			if (item != null) {
				lines.add(recipeKey + "\t" + item.name() + "\t" + item.tier());
			}
		}

		return lines;
	}

	public void sendJournal(ServerPlayer player) {
		AlchemyNetworking.send(player, new AlchemyPayloads.JournalSync(AlchemyPayloads.join(journalLines(player))));
	}

	int tierFor(List<Ingredient> ingredients) {
		return new PowerBudget(this.valueTable, this.config.powerCap).tier(ingredients.stream()
				.map(ingredient -> new PowerBudget.ItemKeyed(ingredient.itemId(), ingredient.count(),
						com.aialchemy.core.Rarity.COMMON))
				.toList());
	}

	/** Ingredient list as sent to the model: ids, counts and rarity, never display names. */
	static String promptIngredientList(List<Ingredient> ingredients) {
		StringBuilder builder = new StringBuilder();

		for (Ingredient ingredient : ingredients) {
			builder.append("- ").append(ingredient.count()).append("x ").append(ingredient.itemId())
					.append(" (rarity ").append(ingredient.dataSignature() == null ? "common" : "mixed")
					.append(")\n");
		}

		return builder.toString();
	}

	static List<Integer> ingredientColors(List<ItemStack> stacks) {
		List<Integer> colors = new ArrayList<>();

		for (ItemStack stack : stacks) {
			colors.add(averageIconColor(stack));
		}

		return colors;
	}

	/** Cheap deterministic palette seed: the item's own average icon colour, cached per item. */
	private static final java.util.Map<net.minecraft.world.item.Item, Integer> COLOR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	private static int averageIconColor(ItemStack stack) {
		return COLOR_CACHE.computeIfAbsent(stack.getItem(), item -> {
			String path = BuiltInRegistries.ITEM.getKey(item).getPath();
			int hash = path.hashCode();
			int red = 64 + Math.abs(hash % 160);
			int green = 64 + Math.abs((hash / 7) % 160);
			int blue = 64 + Math.abs((hash / 13) % 160);
			return 0xFF000000 | (red << 16) | (green << 8) | blue;
		});
	}

	private com.aialchemy.core.Shell shellForGrid(List<Ingredient> ingredients) {
		int index = Math.abs(ingredients.hashCode()) % com.aialchemy.core.Shell.values().length;
		return com.aialchemy.core.Shell.values()[index];
	}

	/** Collected for diagnostics by {@code /alchemy status}. */
	public String describe() {
		return "pending=" + this.pending.size() + ", generation=" + this.generation.provider().name()
				+ ", requests=" + this.generation.requestCount() + ", failures=" + this.generation.failureCount()
				+ ", " + this.scripts.describe();
	}
}
