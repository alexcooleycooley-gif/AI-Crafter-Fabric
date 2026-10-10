package com.aialchemy.storage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.aialchemy.AiAlchemy;
import com.aialchemy.core.GeneratedItem;
import com.aialchemy.core.json.ItemJson;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Everything AI Alchemy persists, under {@code <world>/ai_alchemy/} (design document section 11).
 *
 * <pre>
 * recipes.json    recipe key -> generated item metadata
 * scripts/&lt;id&gt;.lua  script text
 * icons/&lt;id&gt;.png    16x16 GUI sprites
 * models/&lt;id&gt;.json   validated cuboid models
 * journal.json    per player discovered recipes
 * bans.json       admin disabled recipes and item ids
 * </pre>
 *
 * <p>Writes go through a small dirty flag and are flushed on autosave, so a busy server does not
 * hammer the disk once per craft.</p>
 */
public final class AlchemyStorage {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String ROOT = "ai_alchemy";

	private final Path root;
	private final Path recipesFile;
	private final Path journalFile;
	private final Path bansFile;
	private final Path scriptsDir;
	private final Path iconsDir;
	private final Path modelsDir;

	private final Map<String, GeneratedItem> recipes = new HashMap<>();
	private final Map<String, Long> iconTimestamps = new HashMap<>();
	private final Map<String, Long> modelTimestamps = new HashMap<>();
	private final Set<String> bannedRecipes = new TreeSet<>();
	private final Set<String> bannedItems = new TreeSet<>();
	private final Map<String, Set<String>> journal = new HashMap<>();

	private boolean dirty;

	public AlchemyStorage(Path worldRoot) {
		this.root = worldRoot.resolve(ROOT);
		this.recipesFile = this.root.resolve("recipes.json");
		this.journalFile = this.root.resolve("journal.json");
		this.bansFile = this.root.resolve("bans.json");
		this.scriptsDir = this.root.resolve("scripts");
		this.iconsDir = this.root.resolve("icons");
		this.modelsDir = this.root.resolve("models");
	}

	public void load() {
		try {
			Files.createDirectories(this.root);
			Files.createDirectories(this.scriptsDir);
			Files.createDirectories(this.iconsDir);
			Files.createDirectories(this.modelsDir);
		} catch (IOException exception) {
			AiAlchemy.LOGGER.error("Could not create the ai_alchemy world folder", exception);
			return;
		}

		readJson(this.recipesFile, root -> {
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}

				try {
					this.recipes.put(entry.getKey(), ItemJson.fromJson(entry.getValue().getAsJsonObject()));
				} catch (RuntimeException exception) {
					AiAlchemy.LOGGER.warn("Skipping unreadable cache entry {}", entry.getKey(), exception);
				}
			}
		});

		readJson(this.bansFile, root -> {
			if (root.has("recipes") && root.get("recipes").isJsonArray()) {
				root.getAsJsonArray("recipes").forEach(element -> this.bannedRecipes.add(element.getAsString()));
			}

			if (root.has("items") && root.get("items").isJsonArray()) {
				root.getAsJsonArray("items").forEach(element -> this.bannedItems.add(element.getAsString()));
			}
		});

		readJson(this.journalFile, root -> {
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				Set<String> keys = new TreeSet<>();

				if (entry.getValue().isJsonArray()) {
					entry.getValue().getAsJsonArray().forEach(element -> keys.add(element.getAsString()));
				}

				this.journal.put(entry.getKey(), keys);
			}
		});
	}

	public int recipeCount() {
		return this.recipes.size();
	}

	public int bannedCount() {
		return this.bannedRecipes.size() + this.bannedItems.size();
	}

	public Optional<GeneratedItem> recipe(String recipeKey) {
		return Optional.ofNullable(this.recipes.get(recipeKey));
	}

	public GeneratedItem cachedByItemId(String itemId) {
		for (GeneratedItem item : this.recipes.values()) {
			if (item.itemId().equals(itemId)) {
				return item;
			}
		}

		return null;
	}

	public boolean isBanned(String recipeKey, String itemId) {
		return this.bannedRecipes.contains(recipeKey) || this.bannedItems.contains(itemId);
	}

	public Map<String, GeneratedItem> recipes() {
		return Map.copyOf(this.recipes);
	}

	/** Stores a validated item and writes its icon, model and script payloads. */
	public void store(GeneratedItem item) {
		this.recipes.put(item.recipeKey(), item);
		writeIcon(item.itemId(), item.icon(), item);
		writeModel(item.itemId(), item);
		writeScript(item);
		this.dirty = true;
	}

	public void removeRecipe(String recipeKey) {
		GeneratedItem removed = this.recipes.remove(recipeKey);

		if (removed != null) {
			deleteQuietly(this.scriptsDir.resolve(removed.itemId() + ".lua"));
			deleteQuietly(this.iconsDir.resolve(removed.itemId() + ".png"));
			deleteQuietly(this.modelsDir.resolve(removed.itemId() + ".json"));
		}

		this.dirty = true;
	}

	public void ban(String recipeKey, String itemId) {
		if (recipeKey != null && !recipeKey.isBlank()) {
			this.bannedRecipes.add(recipeKey);
		}

		if (itemId != null && !itemId.isBlank()) {
			this.bannedItems.add(itemId);
		}

		this.dirty = true;
	}

	public void unban(String recipeKey, String itemId) {
		this.bannedRecipes.remove(recipeKey);
		this.bannedItems.remove(itemId);
		this.dirty = true;
	}

	public Set<String> bannedRecipes() {
		return Set.copyOf(this.bannedRecipes);
	}

	public Set<String> bannedItems() {
		return Set.copyOf(this.bannedItems);
	}

	// Icon and model payloads --------------------------------------------------

	public byte[] iconBytes(String iconId) {
		return readBytes(this.iconsDir.resolve(iconId + ".png"));
	}

	public JsonObject modelJson(String modelId) {
		byte[] bytes = readBytes(this.modelsDir.resolve(modelId + ".json"));

		if (bytes == null) {
			return null;
		}

		try {
			return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (RuntimeException exception) {
			AiAlchemy.LOGGER.warn("Unreadable model payload {}: {}", modelId, exception.getMessage());
			return null;
		}
	}

	public Optional<String> scriptText(String scriptId) {
		byte[] bytes = readBytes(this.scriptsDir.resolve(scriptId + ".lua"));
		return bytes == null ? Optional.empty() : Optional.of(new String(bytes, StandardCharsets.UTF_8));
	}

	public void writeScriptText(String scriptId, String script) {
		writeBytes(this.scriptsDir.resolve(scriptId + ".lua"), script.getBytes(StandardCharsets.UTF_8));
	}

	public Map<String, Long> iconTimestamps() {
		return Map.copyOf(this.iconTimestamps);
	}

	public Map<String, Long> modelTimestamps() {
		return Map.copyOf(this.modelTimestamps);
	}

	// Journal ------------------------------------------------------------------

	public boolean hasDiscovered(String playerId, String recipeKey) {
		return this.journal.getOrDefault(playerId, Set.of()).contains(recipeKey);
	}

	/** @return true when this call was the first discovery for the player */
	public boolean recordDiscovery(String playerId, String recipeKey) {
		Set<String> discovered = this.journal.computeIfAbsent(playerId, key -> new TreeSet<>());
		boolean added = discovered.add(recipeKey);

		if (added) {
			this.dirty = true;
		}

		return added;
	}

	public Set<String> journal(String playerId) {
		return Set.copyOf(this.journal.getOrDefault(playerId, Set.of()));
	}

	public Map<String, Set<String>> fullJournal() {
		Map<String, Set<String>> copy = new LinkedHashMap<>();
		this.journal.forEach((player, keys) -> copy.put(player, Set.copyOf(keys)));
		return copy;
	}

	// Persistence ---------------------------------------------------------------

	public void markDirty() {
		this.dirty = true;
	}

	public void saveIfDirty() {
		if (!this.dirty) {
			return;
		}

		this.dirty = false;
		save();
	}

	public void save() {
		try {
			Files.createDirectories(this.root);
			JsonObject recipesJson = new JsonObject();
			this.recipes.forEach((key, item) -> recipesJson.add(key, ItemJson.toJson(item)));

			try (Writer writer = Files.newBufferedWriter(this.recipesFile, StandardCharsets.UTF_8)) {
				GSON.toJson(recipesJson, writer);
			}

			JsonObject bans = new JsonObject();
			bans.add("recipes", toJsonArray(this.bannedRecipes));
			bans.add("items", toJsonArray(this.bannedItems));

			try (Writer writer = Files.newBufferedWriter(this.bansFile, StandardCharsets.UTF_8)) {
				GSON.toJson(bans, writer);
			}

			JsonObject journalJson = new JsonObject();
			this.journal.forEach((player, keys) -> journalJson.add(player, toJsonArray(keys)));

			try (Writer writer = Files.newBufferedWriter(this.journalFile, StandardCharsets.UTF_8)) {
				GSON.toJson(journalJson, writer);
			}
		} catch (IOException exception) {
			AiAlchemy.LOGGER.error("Could not save AI Alchemy data", exception);
		}
	}

	/** Exports the whole cache as one shareable file (design document section 11). */
	public String exportCache() {
		JsonObject root = new JsonObject();
		JsonObject items = new JsonObject();
		this.recipes.forEach((key, item) -> items.add(key, ItemJson.toJson(item)));
		root.add("recipes", items);
		root.add("scripts", new JsonObject());

		for (GeneratedItem item : this.recipes.values()) {
			if (item.script() != null) {
				root.getAsJsonObject("scripts").addProperty(item.itemId(), item.script());
			}
		}

		return GSON.toJson(root);
	}

	/** Writes the export next to the world data and returns the file, or null on failure. */
	public Path exportToServerRoot() {
		Path target = this.root.resolve("export-" + System.currentTimeMillis() + ".json");

		try {
			Files.createDirectories(this.root);
			Files.writeString(target, exportCache(), StandardCharsets.UTF_8);
			return target;
		} catch (IOException exception) {
			AiAlchemy.LOGGER.error("Could not write the export file", exception);
			return null;
		}
	}

	/** Imports a previously exported cache, replacing any entry with the same recipe key. */
	public int importCache(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		int imported = 0;

		if (!root.has("recipes")) {
			return 0;
		}

		JsonObject items = root.getAsJsonObject("recipes");
		JsonObject scripts = root.has("scripts") ? root.getAsJsonObject("scripts") : new JsonObject();

		for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
			try {
				GeneratedItem item = ItemJson.fromJson(entry.getValue().getAsJsonObject());

				if (scripts.has(item.itemId())) {
					item = new GeneratedItem(item.itemId(), item.recipeKey(), item.name(), item.lore(), item.shell(),
							item.rarity(), item.stats(), item.effects(), item.palette(), item.icon(), item.model(),
							scripts.get(item.itemId()).getAsString(), item.tier(), item.discoverer(), item.createdAt(),
							item.notes(), false);
				}

				store(item);
				imported++;
			} catch (RuntimeException exception) {
				AiAlchemy.LOGGER.warn("Skipping unreadable import entry {}", entry.getKey(), exception);
			}
		}

		return imported;
	}

	public void clearCache() {
		this.recipes.clear();
		deleteQuietly(this.recipesFile);
		this.dirty = false;
	}

	// Helpers -------------------------------------------------------------------

	private void writeIcon(String iconId, com.aialchemy.core.icon.PixelIcon icon, GeneratedItem item) {
		byte[] png = com.aialchemy.core.icon.IconRenderer.toPngBytes(icon, item.palette());
		writeBytes(this.iconsDir.resolve(iconId + ".png"), png);
		this.iconTimestamps.put(iconId, System.currentTimeMillis());
	}

	private void writeModel(String modelId, GeneratedItem item) {
		if (item.model() == null) {
			return;
		}

		writeBytes(this.modelsDir.resolve(modelId + ".json"),
				GSON.toJson(item.model().toJson()).getBytes(StandardCharsets.UTF_8));
		this.modelTimestamps.put(modelId, System.currentTimeMillis());
	}

	private void writeScript(GeneratedItem item) {
		if (item.script() == null) {
			return;
		}

		writeBytes(this.scriptsDir.resolve(item.itemId() + ".lua"), item.script().getBytes(StandardCharsets.UTF_8));
	}

	private void readJson(Path path, java.util.function.Consumer<JsonObject> consumer) {
		if (!Files.exists(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement element = JsonParser.parseReader(reader);

			if (element.isJsonObject()) {
				consumer.accept(element.getAsJsonObject());
			}
		} catch (IOException | RuntimeException exception) {
			AiAlchemy.LOGGER.warn("Could not read {}", path, exception);
		}
	}

	private static byte[] readBytes(Path path) {
		try {
			return Files.exists(path) ? Files.readAllBytes(path) : null;
		} catch (IOException exception) {
			return null;
		}
	}

	private static void writeBytes(Path path, byte[] bytes) {
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, bytes);
		} catch (IOException exception) {
			AiAlchemy.LOGGER.error("Could not write {}", path, exception);
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// best effort
		}
	}

	private static com.google.gson.JsonArray toJsonArray(Iterable<String> values) {
		com.google.gson.JsonArray array = new com.google.gson.JsonArray();
		values.forEach(array::add);
		return array;
	}

}
