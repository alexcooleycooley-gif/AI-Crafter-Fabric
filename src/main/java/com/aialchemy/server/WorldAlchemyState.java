package com.aialchemy.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.aialchemy.AiAlchemy;
import com.aialchemy.config.AlchemyConfig;
import com.aialchemy.core.ItemValueTable;
import com.aialchemy.generation.AiProvider;
import com.aialchemy.generation.GenerationService;
import com.aialchemy.generation.HttpAiProvider;
import com.aialchemy.generation.MockAiProvider;
import com.aialchemy.generation.OfflineAiProvider;
import com.aialchemy.script.ScriptRuntime;
import com.aialchemy.storage.AlchemyStorage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * All per world AI Alchemy state, created when the server starts and torn down when it stops.
 *
 * <p>The provider calls run on a dedicated small thread pool: never on the server thread, and never
 * on the common pool, so a slow endpoint cannot starve other work.</p>
 */
public final class WorldAlchemyState {

	private final AlchemyConfig config;
	private final AlchemyStorage storage;
	private final ExecutorService executor;
	private final GenerationService generation;
	private final ScriptRuntime scripts;
	private final AssetSync assets;
	private final RateLimits rateLimits;
	private final TransmuteService transmute;
	private final ItemValueTable valueTable;

	private WorldAlchemyState(MinecraftServer server, AlchemyConfig config, AlchemyStorage storage,
			ExecutorService executor, GenerationService generation, ScriptRuntime scripts, AssetSync assets,
			RateLimits rateLimits, TransmuteService transmute, ItemValueTable valueTable) {
		this.config = config;
		this.storage = storage;
		this.executor = executor;
		this.generation = generation;
		this.scripts = scripts;
		this.assets = assets;
		this.rateLimits = rateLimits;
		this.transmute = transmute;
		this.valueTable = valueTable;
	}

	public static WorldAlchemyState load(MinecraftServer server) {
		AlchemyConfig config = AlchemyConfig.load();
		java.nio.file.Path worldPath = server.getWorldPath(LevelResource.ROOT);
		AlchemyStorage storage = new AlchemyStorage(worldPath);
		storage.load();
		ItemValueTable valueTable = loadValueTable(worldPath);
		ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, config.maxConcurrentRequests),
				runnable -> {
					Thread thread = new Thread(runnable, "ai-alchemy-provider");
					thread.setDaemon(true);
					return thread;
				});
		AiProvider provider = buildProvider(config);
		GenerationService generation = new GenerationService(config, provider, executor);
		ScriptRuntime scripts = new ScriptRuntime(storage, config);
		AssetSync assets = new AssetSync(storage);
		RateLimits rateLimits = new RateLimits(config);
		TransmuteService transmute = new TransmuteService(storage, config, generation, assets, scripts, rateLimits,
				valueTable);
		assets.attach(server);
		transmute.attach(server);

		AiAlchemy.LOGGER.info("AI Alchemy provider: {}{}", provider.name(), provider.available() ? "" : " (unavailable)");
		return new WorldAlchemyState(server, config, storage, executor, generation, scripts, assets, rateLimits,
				transmute, valueTable);
	}

	private static AiProvider buildProvider(AlchemyConfig config) {
		if (config.mockProvider) {
			return new MockAiProvider();
		}

		if (config.providerUnavailable()) {
			return OfflineAiProvider.INSTANCE;
		}

		return new HttpAiProvider(config);
	}

	/** A world may override the built in ingredient values with {@code ai_alchemy/item_values.json}. */
	private static ItemValueTable loadValueTable(java.nio.file.Path worldPath) {
		java.nio.file.Path path = worldPath.resolve("ai_alchemy").resolve("item_values.json");

		if (!java.nio.file.Files.exists(path)) {
			return ItemValueTable.defaults();
		}

		try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
			com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
			java.util.Map<String, Integer> values = new java.util.HashMap<>();
			int unknown = root.has("unknown_default") ? root.get("unknown_default").getAsInt() : 2;

			if (root.has("values") && root.get("values").isJsonObject()) {
				for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("values").entrySet()) {
					values.put(entry.getKey(), entry.getValue().getAsInt());
				}
			}

			return new ItemValueTable(values, unknown);
		} catch (java.io.IOException | RuntimeException exception) {
			AiAlchemy.LOGGER.warn("Could not read the world item value table, using defaults", exception);
			return ItemValueTable.defaults();
		}
	}

	public void shutdown() {
		this.storage.save();
		this.executor.shutdown();

		try {
			if (!this.executor.awaitTermination(3, TimeUnit.SECONDS)) {
				this.executor.shutdownNow();
			}
		} catch (InterruptedException exception) {
			this.executor.shutdownNow();
			Thread.currentThread().interrupt();
		}

		AiAlchemy.LOGGER.info("AI Alchemy stopped: {} cached recipes", this.storage.recipeCount());
	}

	public void tick() {
		this.storage.saveIfDirty();
	}

	public AlchemyConfig config() {
		return this.config;
	}

	public AlchemyStorage storage() {
		return this.storage;
	}

	public GenerationService generation() {
		return this.generation;
	}

	public ScriptRuntime scripts() {
		return this.scripts;
	}

	public AssetSync assets() {
		return this.assets;
	}

	public RateLimits rateLimits() {
		return this.rateLimits;
	}

	public TransmuteService transmute() {
		return this.transmute;
	}

	public ItemValueTable valueTable() {
		return this.valueTable;
	}
}