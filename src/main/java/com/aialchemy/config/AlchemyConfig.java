package com.aialchemy.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.aialchemy.AiAlchemy;
import com.aialchemy.core.ValidationContext;
import com.aialchemy.core.script.ScriptLimits;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server side configuration (design document section 15).
 *
 * <p>Two files are involved: {@code config/ai_alchemy.json} holds the operator settings including
 * the API key, which never leaves the server, and a world may override the item value table with
 * {@code <world>/ai_alchemy/item_values.json}.</p>
 */
public final class AlchemyConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** hosted or local. */
	public String aiProvider = "hosted";
	/** Provider endpoint. Empty means "use the built in OpenAI compatible default". */
	public String endpoint = "";
	/** Server side only. Never sent to clients, never logged. */
	public String apiKey = "";
	/** Model name sent to OpenAI compatible endpoints. */
	public String model = "gpt-4o-mini";
	/** Maximum number of tokens the provider may use for one generated item. */
	public int maxTokens = 8_192;
	/** Request timeout in seconds. */
	public int requestTimeoutSeconds = 45;
	/** Number of retries before the fallback ladder gives up. */
	public int retries = 2;
	/** Global scaling of every tier. */
	public double powerCap = 1.0D;
	/** Use vanilla recipe results when a vanilla recipe matches. */
	public boolean vanillaPriority = true;
	/** Reuse cached results for the same recipe key. */
	public boolean cacheResults = true;
	/** Cache only, never call the provider. */
	public boolean offlineMode = false;
	/** Developer flag: answer every request from the deterministic mock provider. */
	public boolean mockProvider = false;
	/** If false, generated items are stats only. */
	public boolean allowScripts = true;
	/** If false, generated items use the stock template models. */
	public boolean generateModels = true;
	/** Upper bound on cuboids per model, applied on top of the tier limits. */
	public int maxModelParts = 48;
	/** Blocks beyond which dropped items draw as flat icons. */
	public int modelRenderDistance = 32;
	/** Ask for the concept first, then the model and script. */
	public boolean twoPassGeneration = false;
	/** Per player cooldown in seconds. */
	public int playerCooldownSeconds = 15;
	/** AI generations per player per day. */
	public int dailyLimitPerPlayer = 50;
	/** Global cap on concurrent provider requests. */
	public int maxConcurrentRequests = 4;
	/** off, basic or strict. */
	public String contentFilter = "strict";
	/** Include grid layout in the recipe key. */
	public boolean shapedMatters = false;
	/** Overrides for the sandbox budgets. */
	public int scriptInstructionsPerCall = ScriptLimits.DEFAULTS.instructionsPerCall();
	public long scriptWallClockMillis = ScriptLimits.DEFAULTS.wallClockMillis();
	public long scriptMemoryBytes = ScriptLimits.DEFAULTS.memoryBytes();
	public int scriptApiCallsPerCall = ScriptLimits.DEFAULTS.apiCallsPerCall();
	public int scriptEntitySpawnsPerCall = ScriptLimits.DEFAULTS.entitySpawnsPerCall();
	public int scriptParticlesPerCall = ScriptLimits.DEFAULTS.particlesPerCall();
	public double scriptEffectRange = ScriptLimits.DEFAULTS.effectRange();
	public int scriptRequiredCooldownTicks = ScriptLimits.DEFAULTS.cooldownFloorTicks();
	public int scriptTickThrottleTicks = ScriptLimits.DEFAULTS.tickThrottleTicks();
	public int scriptMaxBytes = ScriptLimits.DEFAULTS.maxSourceBytes();
	/** Aborts before a script is disabled. */
	public int scriptAbortsBeforeDisable = 3;
	/** Per tick global script time cap in microseconds. */
	public int scriptTickBudgetMicros = 3_000;

	public static AlchemyConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("ai_alchemy.json");
		AlchemyConfig config = new AlchemyConfig();

		if (!Files.exists(path)) {
			config.save(path);
			return config;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

			config.aiProvider = string(root, "ai_provider", config.aiProvider);
			config.endpoint = string(root, "endpoint", config.endpoint);
			config.apiKey = string(root, "api_key", config.apiKey);
			config.model = string(root, "model", config.model);
			config.maxTokens = integer(root, "max_tokens", config.maxTokens);
			config.requestTimeoutSeconds = integer(root, "request_timeout_s", config.requestTimeoutSeconds);
			config.retries = integer(root, "retries", config.retries);
			config.powerCap = decimal(root, "power_cap", config.powerCap);
			config.vanillaPriority = bool(root, "vanilla_priority", config.vanillaPriority);
			config.cacheResults = bool(root, "cache_results", config.cacheResults);
			config.offlineMode = bool(root, "offline_mode", config.offlineMode);
			config.mockProvider = bool(root, "mock_provider", config.mockProvider);
			config.allowScripts = bool(root, "allow_scripts", config.allowScripts);
			config.generateModels = bool(root, "generate_models", config.generateModels);
			config.maxModelParts = integer(root, "max_model_parts", config.maxModelParts);
			config.modelRenderDistance = integer(root, "model_render_distance", config.modelRenderDistance);
			config.twoPassGeneration = bool(root, "two_pass_generation", config.twoPassGeneration);
			config.playerCooldownSeconds = integer(root, "player_cooldown_s", config.playerCooldownSeconds);
			config.dailyLimitPerPlayer = integer(root, "daily_limit_per_player", config.dailyLimitPerPlayer);
			config.maxConcurrentRequests = integer(root, "max_concurrent_requests", config.maxConcurrentRequests);
			config.contentFilter = string(root, "content_filter", config.contentFilter);
			config.shapedMatters = bool(root, "shaped_matters", config.shapedMatters);
			config.scriptInstructionsPerCall = integer(root, "script_instructions_per_call", config.scriptInstructionsPerCall);
			config.scriptWallClockMillis = integer(root, "script_wall_clock_ms", (int) config.scriptWallClockMillis);
			config.scriptMemoryBytes = integer(root, "script_memory_bytes", (int) config.scriptMemoryBytes);
			config.scriptApiCallsPerCall = integer(root, "script_api_calls", config.scriptApiCallsPerCall);
			config.scriptEntitySpawnsPerCall = integer(root, "script_entity_spawns", config.scriptEntitySpawnsPerCall);
			config.scriptParticlesPerCall = integer(root, "script_particles", config.scriptParticlesPerCall);
			config.scriptEffectRange = decimal(root, "script_effect_range", config.scriptEffectRange);
			config.scriptRequiredCooldownTicks = integer(root, "script_cooldown_floor_ticks", config.scriptRequiredCooldownTicks);
			config.scriptTickThrottleTicks = integer(root, "script_tick_throttle_ticks", config.scriptTickThrottleTicks);
			config.scriptMaxBytes = integer(root, "script_max_bytes", config.scriptMaxBytes);
			config.scriptAbortsBeforeDisable = integer(root, "script_aborts_before_disable", config.scriptAbortsBeforeDisable);
			config.scriptTickBudgetMicros = integer(root, "script_tick_budget_us", config.scriptTickBudgetMicros);
		} catch (IOException | RuntimeException exception) {
			AiAlchemy.LOGGER.error("Could not read {}, using defaults", path, exception);
		}

		return config;
	}

	/** Creates the operator config before a world is opened or a transmute is pressed. */
	public static void ensureExists() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("ai_alchemy.json");

		if (!Files.exists(path)) {
			new AlchemyConfig().save(path);
		}
	}

	public void save(Path path) {
		JsonObject root = new JsonObject();
		root.addProperty("ai_provider", this.aiProvider);
		root.addProperty("endpoint", this.endpoint);
		root.addProperty("api_key", this.apiKey);
		root.addProperty("model", this.model);
		root.addProperty("max_tokens", this.maxTokens);
		root.addProperty("request_timeout_s", this.requestTimeoutSeconds);
		root.addProperty("retries", this.retries);
		root.addProperty("power_cap", this.powerCap);
		root.addProperty("vanilla_priority", this.vanillaPriority);
		root.addProperty("cache_results", this.cacheResults);
		root.addProperty("offline_mode", this.offlineMode);
		root.addProperty("mock_provider", this.mockProvider);
		root.addProperty("allow_scripts", this.allowScripts);
		root.addProperty("generate_models", this.generateModels);
		root.addProperty("max_model_parts", this.maxModelParts);
		root.addProperty("model_render_distance", this.modelRenderDistance);
		root.addProperty("two_pass_generation", this.twoPassGeneration);
		root.addProperty("player_cooldown_s", this.playerCooldownSeconds);
		root.addProperty("daily_limit_per_player", this.dailyLimitPerPlayer);
		root.addProperty("max_concurrent_requests", this.maxConcurrentRequests);
		root.addProperty("content_filter", this.contentFilter);
		root.addProperty("shaped_matters", this.shapedMatters);
		root.addProperty("script_instructions_per_call", this.scriptInstructionsPerCall);
		root.addProperty("script_wall_clock_ms", this.scriptWallClockMillis);
		root.addProperty("script_memory_bytes", this.scriptMemoryBytes);
		root.addProperty("script_api_calls", this.scriptApiCallsPerCall);
		root.addProperty("script_entity_spawns", this.scriptEntitySpawnsPerCall);
		root.addProperty("script_particles", this.scriptParticlesPerCall);
		root.addProperty("script_effect_range", this.scriptEffectRange);
		root.addProperty("script_cooldown_floor_ticks", this.scriptRequiredCooldownTicks);
		root.addProperty("script_tick_throttle_ticks", this.scriptTickThrottleTicks);
		root.addProperty("script_max_bytes", this.scriptMaxBytes);
		root.addProperty("script_aborts_before_disable", this.scriptAbortsBeforeDisable);
		root.addProperty("script_tick_budget_us", this.scriptTickBudgetMicros);

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException exception) {
			AiAlchemy.LOGGER.error("Could not write {}", path, exception);
		}
	}

	public ValidationContext.FilterLevel filterLevel() {
		return switch (this.contentFilter == null ? "strict" : this.contentFilter.toLowerCase(java.util.Locale.ROOT)) {
			case "off" -> ValidationContext.FilterLevel.OFF;
			case "basic" -> ValidationContext.FilterLevel.BASIC;
			default -> ValidationContext.FilterLevel.STRICT;
		};
	}

	public ValidationContext validationContext(int tier) {
		return new ValidationContext(tier, this.maxModelParts, this.allowScripts && !this.offlineMode,
				this.generateModels, this.filterLevel(), 40, 6, 160, this.scriptMaxBytes, this.powerCap);
	}

	public ScriptLimits scriptLimits() {
		return new ScriptLimits(this.scriptInstructionsPerCall, this.scriptWallClockMillis, this.scriptMemoryBytes,
				this.scriptEntitySpawnsPerCall, this.scriptParticlesPerCall, this.scriptApiCallsPerCall,
				this.scriptEffectRange, this.scriptRequiredCooldownTicks, this.scriptTickThrottleTicks, this.scriptMaxBytes);
	}

	/** True when there is no way to reach a model; the mod then behaves as cache only. */
	public boolean providerUnavailable() {
		return this.offlineMode || this.mockProvider
				? false
				: this.endpoint == null || this.endpoint.isBlank();
	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : fallback;
	}

	private static int integer(JsonObject root, String key, int fallback) {
		return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsInt() : fallback;
	}

	private static double decimal(JsonObject root, String key, double fallback) {
		return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsDouble() : fallback;
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsBoolean() : fallback;
	}
}
