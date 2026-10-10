package com.aialchemy.core.prompt;

import java.util.List;
import java.util.StringJoiner;

import com.aialchemy.core.Shell;
import com.aialchemy.core.StatRanges;
import com.aialchemy.core.ValidationContext;
import com.aialchemy.core.validation.EffectAllowlist;
import com.aialchemy.core.model.IdleEffect;
import com.aialchemy.core.model.ModelLimits;
import com.aialchemy.core.model.Pattern;
import com.aialchemy.core.script.ScriptHook;
import com.aialchemy.core.script.ScriptLimits;

/**
 * Builds the two messages sent to the provider (design document section 7.2).
 *
 * <p>The system prompt carries the whole contract: the JSON schema, the Lua API the script may call,
 * the cuboid model spec and its per tier limits, the stat ranges for the tier, the allowlists, and
 * a couple of complete worked examples. The user prompt carries the ingredient list, and nothing
 * but ids: ingredient display names never travel to the model, which is the prompt injection
 * defence from section 16.</p>
 */
public final class PromptBuilder {

	private PromptBuilder() {
	}

	/** The pair of messages handed to a provider. */
	public record Prompt(String system, String user) {

		public Prompt withRetryContext(String validationError) {
			return new Prompt(this.system, this.user + "\n\nYour previous answer was rejected: " + validationError
					+ "\nAnswer again with a corrected JSON object only.");
		}
	}

	/** Concept-only prompt used by two pass mode; the model and script come in the second pass. */
	public static Prompt conceptPrompt(String ingredientList, int tier, ValidationContext context) {
		return new Prompt(conceptSystemPrompt(tier), ingredientUserPrompt(ingredientList, tier));
	}

	/** Second pass of two pass mode, given the concept the model already committed to. */
	public static Prompt detailPrompt(String conceptJson, int tier, ValidationContext context) {
		return detailPrompt(conceptJson, tier, context, ScriptLimits.DEFAULTS);
	}

	/** Second pass, quoting the script budgets the server will actually enforce. */
	public static Prompt detailPrompt(String conceptJson, int tier, ValidationContext context, ScriptLimits limits) {
		StringBuilder system = new StringBuilder(fullSystemPrompt(tier, context, limits));
		system.append("\n## Second pass\n\nThe concept below is already fixed. Produce the model, icon and Lua script for it ")
				.append("and repeat the concept fields unchanged.\n");
		return new Prompt(system.toString(), conceptJson);
	}

	public static Prompt fullPrompt(String ingredientList, int tier, ValidationContext context) {
		return fullPrompt(ingredientList, tier, context, ScriptLimits.DEFAULTS);
	}

	/** The single pass prompt, quoting the script budgets the server will actually enforce. */
	public static Prompt fullPrompt(String ingredientList, int tier, ValidationContext context, ScriptLimits limits) {
		return new Prompt(fullSystemPrompt(tier, context, limits), ingredientUserPrompt(ingredientList, tier));
	}

	private static String conceptSystemPrompt(int tier) {
		return """
				You are an item designer for a Minecraft mod called AI Alchemy. Given a set of crafting
				ingredients you invent exactly one new item and describe its concept.

				Answer with a single JSON object and nothing else. Schema:

				{
				  "name": "<= 40 characters, no formatting codes",
				  "lore": ["<= 6 short lines"],
				  "shell": "one of: %s",
				  "rarity": "one of: common, uncommon, rare, epic",
				  "stats": {
				    "damage": number, "attack_speed": number, "durability": integer,
				    "armor": number, "armor_toughness": number, "mining_speed": number,
				    "food": integer, "saturation": number, "ability_power": number
				  },
				  "palette": ["<= 16 colours, #RRGGBB or #AARRGGBB, index 0 is transparent"],
				  "effects": [{ "id": "minecraft:effect_name", "ticks": integer, "amplifier": integer }]
				}

				Rules:
				- The item must feel like it was made from the ingredients: thematically and by power.
				- This recipe is in power tier %d. %s
				- The palette is shared by the inventory icon and the 3D model, so choose colours that
				  read well at 16x16 pixels.
				- Answer with JSON only. No prose, no code fences.
				""".formatted(Shell.allowedNames(), tier, tierGuidance(tier));
	}

	private static String fullSystemPrompt(int tier, ValidationContext context, ScriptLimits scriptLimits) {
		ModelLimits limits = ModelLimits.forTier(tier).withGlobalPartCap(context.maxModelParts());
		StatRanges ranges = StatRanges.forTier(tier);

		return """
				You are an item designer for a Minecraft mod called AI Alchemy. Given a set of crafting
				ingredients you invent exactly one new item: its name, lore, stats, a palette, a 16x16
				icon, a cuboid 3D model and a short behaviour script.

				## Output

				Answer with a single JSON object and nothing else. No prose, no code fences.

				{
				  "name": "Frostfire Wand",
				  "lore": ["Two elements, one temper."],
				  "shell": "weapon",
				  "rarity": "rare",
				  "stats": { "damage": 6, "attack_speed": 1.4, "durability": 400 },
				  "palette": ["#00000000", "#2b2b3a", "#7fd4ff", "#ff7a2f"],
				  "icon": ["0000000000002200", "16 rows of 16 hex digits, each digit is a palette index"],
				  "model": {
				    "grip": [8, 1, 8],
				    "parts": [
				      { "from": [7, 0, 7], "to": [9, 8, 9], "color": 1 },
				      { "from": [6, 8, 6], "to": [10, 11, 10], "color": 2, "pattern": "gradient" }
				    ],
				    "display": { "hand": 1.0, "ground": 0.8 },
				    "idle_effect": "pulse"
				  },
					  "block_traits": {
					    "trait": "luminous", "light_level": 12, "friction": 0.6,
					    "bounce": 0.0, "hardness": 5.0, "emits_particles": true
					  },
					  "lua": "function on_use(player, world)\\n  ...\\nend",
				  "effects": [{ "id": "minecraft:speed", "ticks": 100, "amplifier": 0 }]
				}

					## Shells

					%s

					## Archetype choices

					Choose the shell that best fits the material story; do not default to weapon:
					- weapon: blade, mace, hammer, wand, staff, spear, gauntlet or an unusual handheld arm.
					- tool: pick, axe, shovel, hoe, sickle, chisel, compass or a specialist harvesting tool.
					- armor_head: helmet, hood, mask, crown, goggles, circlet or creature-themed headpiece.
					- armor_chest: cuirass, coat, robe, harness, mantle, shell or protective chest rig.
					- armor_legs: greaves, leggings, trousers, kilt, skirt or articulated leg guards.
					- armor_feet: boots, shoes, sandals, greaves or movement-focused footgear.
					- food: stew, bread, fruit, confection, soup, ration or a strange edible preparation.
					- trinket: ring, amulet, charm, mask, idol, lantern, compass, instrument or pocket relic.
					- block: a placeable cube or rune block with one clear trait: luminous, bouncy, slippery,
					  resonant or fortified. Give it a block-like silhouette and explain the trait in the lore.
					If ingredients suggest protection (iron, diamond, netherite, leather, chain, wool, turtle,
					gold or obsidian), strongly consider one of the four armour shells. If they suggest food,
					choose food. If they suggest navigation, magic or redstone, consider a trinket. A wearable
					item is a first-class result, not a fallback.

				## Power tier %d

				This item may not exceed:
				- damage %s, attack speed %s
				- durability %d
				- armour %s, armour toughness %s
				- mining speed %s
				- food %d, saturation %s
				- ability power %s, number of effects %d

				Stats above these values are clamped downwards automatically. Values for parts the shell
				does not use are ignored.

				## Palette and icon

				- The palette holds at most 16 colours, written as #RRGGBB or #AARRGGBB.
				- Index 0 should be transparent (#00000000) and is the background of the icon.
					- The icon is exactly 16 rows of exactly 16 characters. Each character is one hex digit:
					  the palette index of that pixel. Draw a readable silhouette, not noise.
					- Use a transparent background and a bold 3-5 colour silhouette with a clear focal detail.
					  Fill at least 25%% of the canvas, use deliberate symmetry or a deliberate asymmetry, and
					  never make a random checkerboard or a plain coloured square.
					- The same palette colours the icon and the model, so the item looks coherent in the
					  inventory and in the world.

				## Cuboid model

				- Coordinates are on the standard 16 unit item grid. Allowed range on every axis: %s to %s.
				- At most %d parts. Every edge must be at least %s units long.
				- One connected cluster: every part must touch or overlap another within %s units. No
				  floating boxes.
				- Rotation is optional, one axis only (x, y or z), in steps of %s degrees up to %s degrees.
				- Optional "pattern": %s.
				- Optional "emissive": true. At most %s%% of the parts may be emissive in this tier.
				- Optional "idle_effect": %s.
					- "display" scales are clamped to %s .. %s.
					- For the block shell only, include "block_traits": trait luminous, bouncy, slippery,
					  resonant or fortified; light_level 0-15; friction 0.2-1.0; bounce 0-1; hardness 0.1-50;
					  and emits_particles true or false. These values are bounded and mapped to safe vanilla
					  behavior when the block is placed.
					- The grip point must lie inside the model's bounding box.
					- Design rules: one recognisable shape, grip near the bottom, silhouette readable at a
					  glance. Use 4-10 purposeful parts with a main body, a distinctive feature and a small
					  accent where appropriate. A pile of unrelated boxes is a failure, not a style.

				## Lua script

				The script is optional. It runs in a sandboxed Lua 5.1 interpreter with only base, table,
				string and math available. There is no io, os, require, debug, coroutine or Java access.
				It must not use loops that run long, and it cannot store data between calls.

				Define any of these hooks:

				%s
				Globals available inside every hook:
				- player: pos(), look_direction(), health(), heal(n), damage(n),
				  add_effect(id, ticks, amplifier), knockback(dir, strength), set_cooldown(ticks),
				  send_message(text), is_sneaking(), held_item_name()
				- world: tick(), time_until_day(), spawn_projectile(id, dir, speed),
				  spawn_particles(id, pos, count), play_sound(id, pos),
				  entities_near(pos, radius), block_at(pos), light_level(pos), ignite(pos), is_raining()
				- util: vec(x, y, z), random(), clamp(value, min, max), distance(a, b)
				- log(text)

				Allowlisted ids:
				- effects: %s
				- sounds: %s
				- particles: %s
				- projectiles: %s

				Budget per call: %d instructions, %d ms, %d API calls, %d entities, %d particles,
				or a %s block range from the caster. Exceeding a budget aborts the call and repeated
				aborts disable the script.

				## Worked examples

				### Example 1: cheap ingredients, tier 1

				Ingredients: 3x minecraft:cobblestone, 2x minecraft:stick

				{
				  "name": "Pebble Knocker",
				  "lore": ["Heavy enough to matter.", "Barely."],
				  "shell": "weapon",
				  "rarity": "common",
				  "stats": { "damage": 3, "attack_speed": 3.8, "durability": 180 },
				  "palette": ["#00000000", "#4a4a4a", "#8a8a8a", "#5b3a1e"],
				  "icon": ["0000000000000000", "0000000666600000", "0000006888860000",
				           "0000068822886000", "0000068822886000", "0000068888860000",
				           "0000006888860000", "0000000666600000", "0000000333300000",
				           "0000000333300000", "0000000333300000", "0000000333300000",
				           "0000000333300000", "0000000222200000", "0000000000000000",
				           "0000000000000000"],
				  "model": {
				    "grip": [8, 1, 8],
				    "parts": [
				      { "from": [7.5, 0, 7.5], "to": [8.5, 3, 8.5], "color": 3 },
				      { "from": [6, 3, 6.5], "to": [10, 4, 9.5], "color": 2 },
				      { "from": [7, 4, 7], "to": [9, 13, 9], "color": 1 },
				      { "from": [7, 13, 7], "to": [9, 15, 9], "color": 2 }
				    ],
				    "display": { "hand": 1.0, "ground": 0.8 },
				    "idle_effect": "none"
				  },
				  "lua": "function on_hit(attacker, target)\\n  world.play_sound('minecraft:block.glass.break', attacker.pos())\\nend"
				}

				### Example 2: rare ingredients, tier 3

				Ingredients: 1x minecraft:nether_star, 4x minecraft:blaze_rod

				{
				  "name": "Starforge Brand",
				  "lore": ["Forged where the sky burns."],
				  "shell": "weapon",
				  "rarity": "epic",
				  "stats": { "damage": 9, "attack_speed": 4.6, "durability": 1500 },
				  "palette": ["#00000000", "#1b1b2e", "#6fe3ff", "#ffd166"],
				  "icon": ["0000000333300000", "0000003666630000", "0000036666663000",
				           "0000036222263000", "0000032222223000", "0000003222230000",
				           "0000000322300000", "0000000033000000", "0000000222200000",
				           "0000000222200000", "0000000222200000", "0000000222200000",
				           "0000000222200000", "0000000111100000", "0000000000000000",
				           "0000000000000000"],
				  "model": {
				    "grip": [8, 1, 8],
				    "parts": [
				      { "from": [7.5, 0, 7.5], "to": [8.5, 3, 8.5], "color": 1 },
				      { "from": [6, 3, 6.5], "to": [10, 4, 9.5], "color": 2 },
				      { "from": [7, 4, 7], "to": [9, 12, 9], "color": 2, "pattern": "gradient" },
				      { "from": [6.5, 12, 6.5], "to": [9.5, 15, 9.5], "color": 3, "emissive": true }
				    ],
				    "display": { "hand": 1.0, "ground": 0.8 },
				    "idle_effect": "pulse"
				  },
				  "lua": "function on_hit(attacker, target)\\n  world.spawn_particles('minecraft:electric_spark', target.pos(), 12)\\n  target.add_effect('minecraft:glowing', 100, 0)\\nend"
				}

					## Final rules

					- Answer with JSON only, no commentary.
					- Do not copy the worked examples, their names, their icon layouts, or their lore.
					- Avoid generic names such as "Mystic Blade", "Ancient Staff", "Elemental Tool",
					  "Enchanted Weapon", "Power Amulet" or "Alchemised <item>". Use a concrete, memorable
					  two-to-four word name tied to the ingredients and the item's one signature mechanic.
					- The item must have one clear signature mechanic expressed consistently in its lore, model,
					  effects and Lua hook. Prefer an unusual interaction over simply adding damage.
					- Never invent registry names: use only the ids listed above for effects, sounds,
				  particles and projectiles.
				- Never write Java, never write file or network code, never use a name that is not in the
				  Lua API above.
				- %s
				""".formatted(
				shellList(),
				tier,
				ranges.damage(), ranges.attackSpeed(), ranges.durability(), ranges.armor(), ranges.armorToughness(),
				ranges.miningSpeed(), ranges.food(), ranges.saturation(), ranges.abilityPower(), ranges.effects(),
				limits.minCoordinate(), limits.maxCoordinate(), limits.maxParts(), ModelLimits.MIN_EDGE,
				ModelLimits.CONNECT_TOLERANCE, ModelLimits.ROTATION_STEP, ModelLimits.MAX_ROTATION,
				Pattern.allowedNames(), Math.round(limits.maxEmissiveShare() * 100), IdleEffect.allowedNames(),
				ModelLimits.MIN_DISPLAY_SCALE, ModelLimits.MAX_DISPLAY_SCALE,
				ScriptHook.promptReference(),
				join(EffectAllowlist.sorted(EffectAllowlist.EFFECTS)),
				join(EffectAllowlist.sorted(EffectAllowlist.SOUNDS)),
				join(EffectAllowlist.sorted(EffectAllowlist.PARTICLES)),
				join(EffectAllowlist.sorted(EffectAllowlist.PROJECTILES)),
				scriptLimits.instructionsPerCall(), scriptLimits.wallClockMillis(), scriptLimits.apiCallsPerCall(),
scriptLimits.entitySpawnsPerCall(), scriptLimits.particlesPerCall(),
Math.round(scriptLimits.effectRange()) + " block",
				context.allowScripts() ? "Scripts are allowed for this world."
						: "Scripts are disabled in this world: omit the lua field.");
	}

	private static String tierGuidance(int tier) {
		return switch (tier) {
			case 1 -> "Cheap ingredients: modest numbers, at most one small effect, keep the model simple.";
			case 2 -> "Mid tier ingredients: a solid tool or weapon, one or two effects.";
			case 3 -> "Valuable ingredients: a genuinely strong item with a distinctive shape.";
			default -> "Legendary ingredients: the strongest tier available, but still within the ranges below.";
		};
	}

	private static String shellList() {
		StringJoiner joiner = new StringJoiner("\n");

		for (Shell shell : Shell.values()) {
			joiner.add("- " + shell.jsonName() + ": " + describe(shell));
		}

		return joiner.toString();
	}

	private static String describe(Shell shell) {
		return switch (shell) {
			case WEAPON -> "swords, wands, staves, throwables (damage, attack speed, durability)";
			case TOOL -> "pickaxe, axe, shovel or hoe style behaviour (mining speed, durability)";
			case ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET -> "wearable gear (armour, toughness, durability)";
				case FOOD -> "consumables (food, saturation, effects)";
				case TRINKET -> "right-click curios and passive items (ability power)";
				case BLOCK -> "placeable trait blocks (luminous, bouncy, slippery, resonant or fortified)";
		};
	}

	private static String join(List<String> values) {
		return String.join(", ", values);
	}

	/** The user message: ids, rarity and the computed tier, never display names. */
	public static String ingredientUserPrompt(String ingredientList, int tier) {
		return "Ingredients (id, count, rarity):\n" + ingredientList
					+ "\n\nComputed power tier: " + tier
					+ "\nInvent one item that could be made from exactly these ingredients."
					+ "\nFirst identify the material story and one signature mechanic; then make the name,"
					+ " lore, icon silhouette, model silhouette and script all reinforce that same idea."
					+ " Do not return a generic sword, pickaxe, armour piece or trinket unless the ingredients"
					+ " clearly demand it, and do not copy the examples in the system prompt."
					+ "\n\nChoose deliberately from the archetype list: " + archetypeDirection(ingredientList) + "."
					+ "\nRequired visual direction for this recipe: " + visualDirection(ingredientList) + ".";
	}

	private static String archetypeDirection(String ingredientList) {
		String lower = ingredientList.toLowerCase(java.util.Locale.ROOT);
		if (containsAny(lower, "iron", "diamond", "netherite", "leather", "chain", "wool", "turtle", "obsidian")) {
			return switch (Math.floorMod(ingredientList.hashCode(), 4)) {
				case 0 -> "armor_head, especially a mask, crown or visor";
				case 1 -> "armor_chest, especially a coat, mantle or cuirass";
				case 2 -> "armor_legs, especially articulated greaves or leggings";
				default -> "armor_feet, especially boots with a movement theme";
			};
		}
		if (containsAny(lower, "apple", "bread", "beef", "pork", "fish", "potato", "carrot", "sugar", "melon")) {
			return "food, with a memorable preparation and one sensory detail";
		}
		if (containsAny(lower, "ender", "amethyst", "echo", "redstone", "prismarine", "quartz")) {
			return "trinket, especially a charm, instrument, lantern or pocket relic";
		}
		return switch (Math.floorMod(ingredientList.hashCode(), 4)) {
			case 0 -> "weapon, but not a default sword";
			case 1 -> "tool, preferably a specialist tool rather than a generic pickaxe";
			case 2 -> "trinket with a visible carried identity";
			default -> "one of the four armor shells if the material story supports protection";
		};
	}

	private static boolean containsAny(String value, String... terms) {
		for (String term : terms) {
			if (value.contains(term)) return true;
		}
		return false;
	}

	private static String visualDirection(String ingredientList) {
		String[] directions = {
				"use a crescent or hook silhouette with one asymmetrical focal detail",
				"use a compact core with a ring, halo or orbiting accent",
				"use a long segmented silhouette with a visibly different tip",
				"use a forked, branching or split silhouette rather than a straight blade",
				"use a broad central body with a small suspended or inset gem-like detail",
				"use a coiled, layered or wrapped silhouette with strong diagonal movement"
		};
		return directions[Math.floorMod(ingredientList.hashCode(), directions.length)];
	}
}
