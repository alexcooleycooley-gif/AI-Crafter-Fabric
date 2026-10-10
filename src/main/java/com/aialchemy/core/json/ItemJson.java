package com.aialchemy.core.json;

import java.util.ArrayList;
import java.util.List;

import com.aialchemy.core.GeneratedItem;
import com.aialchemy.core.ItemEffect;
import com.aialchemy.core.Rarity;
import com.aialchemy.core.Shell;
import com.aialchemy.core.StatSet;
import com.aialchemy.core.icon.Palette;
import com.aialchemy.core.icon.PixelIcon;
import com.aialchemy.core.model.CuboidModel;
import com.aialchemy.core.validation.Issue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * JSON handling for both directions: parsing the untrusted AI response into an {@link ItemCandidate}
 * and re-serializing a validated {@link GeneratedItem} for the world cache.
 */
public final class ItemJson {

	private ItemJson() {
	}

	public record ParseResult(ItemCandidate candidate, String error) {

		public boolean ok() {
			return this.candidate != null;
		}
	}

	/** Parses the AI response. Unknown fields are ignored; missing required fields are errors. */
	public static ParseResult parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return new ParseResult(null, "empty response");
		}

		String json = extractJsonObject(raw);

		try {
			JsonElement element = JsonParser.parseString(json);

			if (!element.isJsonObject()) {
				return new ParseResult(null, "response is not a JSON object");
			}

			JsonObject root = element.getAsJsonObject();

			if (!root.has("name") || !root.get("name").isJsonPrimitive()) {
				return new ParseResult(null, "missing 'name'");
			}

			if (!root.has("shell") || !root.get("shell").isJsonPrimitive()) {
				return new ParseResult(null, "missing 'shell'");
			}

			List<String> lore = stringList(root, "lore");
			List<String> palette = stringList(root, "palette");
			List<String> icon = stringList(root, "icon");

			if (palette.size() < 2) {
				return new ParseResult(null, "palette must contain at least 2 colours");
			}

			if (palette.size() > Palette.MAX_COLORS) {
				return new ParseResult(null, "palette must not exceed " + Palette.MAX_COLORS + " colours");
			}

			JsonObject stats = root.has("stats") && root.get("stats").isJsonObject()
					? root.getAsJsonObject("stats")
					: new JsonObject();

			ItemCandidate.StatBlock statBlock = new ItemCandidate.StatBlock(
					floatOf(stats, "damage"),
					floatOf(stats, "attack_speed"),
					intOf(stats, "durability"),
					floatOf(stats, "armor"),
					floatOf(stats, "armor_toughness"),
					floatOf(stats, "mining_speed"),
					intOf(stats, "food"),
					floatOf(stats, "saturation"),
					floatOf(stats, "ability_power"));

			JsonObject model = root.has("model") && root.get("model").isJsonObject() ? root.getAsJsonObject("model") : null;

			if (model != null && (!model.has("parts") || !model.get("parts").isJsonArray())) {
				model = null;
			}

			String lua = root.has("lua") && root.get("lua").isJsonPrimitive() ? root.get("lua").getAsString() : null;

			List<ItemCandidate.EffectSpec> effects = new ArrayList<>();

			if (root.has("effects") && root.get("effects").isJsonArray()) {
				for (JsonElement entry : root.getAsJsonArray("effects")) {
					if (!entry.isJsonObject()) {
						continue;
					}

					JsonObject effect = entry.getAsJsonObject();

					if (!effect.has("id") || !effect.get("id").isJsonPrimitive()) {
						continue;
					}

					effects.add(new ItemCandidate.EffectSpec(
							effect.get("id").getAsString(),
							intOf(effect, "ticks"),
							intOf(effect, "amplifier")));
				}
			}

			return new ParseResult(new ItemCandidate(
					root.get("name").getAsString(),
					lore,
					root.get("shell").getAsString(),
					root.has("rarity") && root.get("rarity").isJsonPrimitive() ? root.get("rarity").getAsString() : "common",
					statBlock,
					palette,
					icon,
					model,
					lua,
					effects), null);
		} catch (JsonSyntaxException | IllegalStateException | UnsupportedOperationException exception) {
			return new ParseResult(null, "invalid JSON: " + exception.getMessage());
		}
	}

	/**
	 * Models like to wrap JSON in prose or code fences even when told not to. Slicing from the first
	 * brace to the last one recovers the object instead of burning a retry.
	 */
	static String extractJsonObject(String raw) {
		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');

		if (start >= 0 && end > start) {
			return raw.substring(start, end + 1);
		}

		return raw;
	}

	public static JsonObject toJson(GeneratedItem item) {
		JsonObject root = new JsonObject();
		root.addProperty("item_id", item.itemId());
		root.addProperty("recipe_key", item.recipeKey());
		root.addProperty("name", item.name());

		JsonArray lore = new JsonArray();
		item.lore().forEach(lore::add);
		root.add("lore", lore);

		root.addProperty("shell", item.shell().jsonName());
		root.addProperty("rarity", item.rarity().jsonName());
		root.addProperty("tier", item.tier());
		root.addProperty("created_at", item.createdAt());

		if (item.discoverer() != null) {
			root.addProperty("discoverer", item.discoverer());
		}

		root.add("stats", statsToJson(item.stats()));

		JsonArray effects = new JsonArray();

		for (ItemEffect effect : item.effects()) {
			JsonObject jsonEffect = new JsonObject();
			jsonEffect.addProperty("id", effect.id());
			jsonEffect.addProperty("ticks", effect.ticks());
			jsonEffect.addProperty("amplifier", effect.amplifier());
			effects.add(jsonEffect);
		}

		root.add("effects", effects);
		root.add("palette", toArray(item.palette().toHexList()));
		root.add("icon", toArray(item.icon().asList()));

		if (item.model() != null) {
			root.add("model", item.model().toJson());
		}

		if (item.script() != null) {
			root.addProperty("lua", item.script());
		}

		root.addProperty("script_disabled", item.scriptDisabled());

		JsonArray notes = new JsonArray();

		for (Issue issue : item.notes()) {
			JsonObject note = new JsonObject();
			note.addProperty("severity", issue.severity().name());
			note.addProperty("code", issue.code());
			note.addProperty("message", issue.message());
			notes.add(note);
		}

		root.add("notes", notes);
		return root;
	}

	public static GeneratedItem fromJson(JsonObject root) {
		Shell shell = Shell.byJsonName(root.get("shell").getAsString()).orElse(Shell.TRINKET);
		Rarity rarity = Rarity.byJsonName(root.get("rarity").getAsString());
		Palette palette = Palette.parse(stringList(root, "palette"));
		PixelIcon icon = PixelIcon.parse(stringList(root, "icon"));
		CuboidModel model = root.has("model") && root.get("model").isJsonObject()
				? CuboidModel.fromJson(root.getAsJsonObject("model"))
				: null;

		List<Issue> notes = new ArrayList<>();

		if (root.has("notes") && root.get("notes").isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray("notes")) {
				JsonObject note = element.getAsJsonObject();
				String message = note.has("message") ? note.get("message").getAsString() : "";
				String code = note.has("code") ? note.get("code").getAsString() : "note";
				boolean error = note.has("severity") && "ERROR".equals(note.get("severity").getAsString());
				notes.add(error ? Issue.error(code, message) : Issue.warning(code, message));
			}
		}

		JsonObject stats = root.has("stats") && root.get("stats").isJsonObject() ? root.getAsJsonObject("stats") : new JsonObject();
		List<ItemEffect> effects = new ArrayList<>();

		if (root.has("effects") && root.get("effects").isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray("effects")) {
				if (!element.isJsonObject()) {
					continue;
				}

				JsonObject effect = element.getAsJsonObject();

				if (effect.has("id")) {
					effects.add(new ItemEffect(effect.get("id").getAsString(), intOf(effect, "ticks"), intOf(effect, "amplifier")));
				}
			}
		}

		return new GeneratedItem(
				root.get("item_id").getAsString(),
				root.get("recipe_key").getAsString(),
				root.get("name").getAsString(),
				stringList(root, "lore"),
				shell,
				rarity,
				new StatSet(floatOf(stats, "damage"), floatOf(stats, "attack_speed"), intOf(stats, "durability"),
						floatOf(stats, "armor"), floatOf(stats, "armor_toughness"), floatOf(stats, "mining_speed"),
						intOf(stats, "food"), floatOf(stats, "saturation"), floatOf(stats, "ability_power")),
				effects,
				palette,
				icon,
				model,
				root.has("lua") && root.get("lua").isJsonPrimitive() ? root.get("lua").getAsString() : null,
				intOf(root, "tier"),
				root.has("discoverer") && root.get("discoverer").isJsonPrimitive() ? root.get("discoverer").getAsString() : null,
				root.has("created_at") ? root.get("created_at").getAsLong() : 0L,
				notes,
				root.has("script_disabled") && root.get("script_disabled").getAsBoolean());
	}

	private static JsonObject statsToJson(StatSet stats) {
		JsonObject json = new JsonObject();
		json.addProperty("damage", stats.damage());
		json.addProperty("attack_speed", stats.attackSpeed());
		json.addProperty("durability", stats.durability());
		json.addProperty("armor", stats.armor());
		json.addProperty("armor_toughness", stats.armorToughness());
		json.addProperty("mining_speed", stats.miningSpeed());
		json.addProperty("food", stats.food());
		json.addProperty("saturation", stats.saturation());
		json.addProperty("ability_power", stats.abilityPower());
		return json;
	}

	private static JsonArray toArray(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}

	private static List<String> stringList(JsonObject root, String key) {
		List<String> values = new ArrayList<>();

		if (root == null || !root.has(key)) {
			return values;
		}

		JsonElement element = root.get(key);

		if (element.isJsonArray()) {
			for (JsonElement entry : element.getAsJsonArray()) {
				if (entry.isJsonPrimitive()) {
					values.add(entry.getAsString());
				}
			}
		} else if (element.isJsonPrimitive()) {
			values.add(element.getAsString());
		}

		return values;
	}

	private static float floatOf(JsonObject root, String key) {
		if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) {
			return 0f;
		}

		try {
			return root.get(key).getAsFloat();
		} catch (NumberFormatException exception) {
			return 0f;
		}
	}

	private static int intOf(JsonObject root, String key) {
		if (root == null || !root.has(key) || !root.get(key).isJsonPrimitive()) {
			return 0;
		}

		try {
			return root.get(key).getAsInt();
		} catch (NumberFormatException exception) {
			return 0;
		}
	}
}
