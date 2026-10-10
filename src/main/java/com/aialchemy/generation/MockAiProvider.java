package com.aialchemy.generation;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.aialchemy.core.Shell;
import com.aialchemy.core.prompt.PromptBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A deterministic provider used by unit and game tests and by {@code mock_provider = true}. It
 * exercises the whole pipeline - icon, model and script included - without any network access.
 */
public final class MockAiProvider implements AiProvider {

	private static final String[] ADJECTIVES = {"Glimmering", "Hollow", "Wandering", "Sunken", "Feral", "Quiet"};
	private static final String[] NOUNS = {"Cog", "Sigil", "Ward", "Lens", "Anvil", "Charm"};

	private final AtomicInteger calls = new AtomicInteger();

	@Override
	public String name() {
		return "mock";
	}

	@Override
	public CompletableFuture<String> complete(PromptBuilder.Prompt prompt) {
		int call = this.calls.incrementAndGet();
		Random random = new Random(prompt.user().hashCode() * 31L + call);
		return CompletableFuture.completedFuture(respond(prompt, random));
	}

	public int callCount() {
		return this.calls.get();
	}

	static String respond(PromptBuilder.Prompt prompt, Random random) {
		String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
		String noun = NOUNS[random.nextInt(NOUNS.length)];
		Shell shell = Shell.values()[random.nextInt(Shell.values().length)];

		JsonObject root = new JsonObject();
		root.addProperty("name", adjective + " " + noun);
		JsonArray lore = new JsonArray();
		lore.add("Mocked by the test provider.");
		root.add("lore", lore);
		root.addProperty("shell", shell.jsonName());
		root.addProperty("rarity", "uncommon");

		JsonObject stats = new JsonObject();
		stats.addProperty("damage", 4 + random.nextInt(5));
		stats.addProperty("attack_speed", 3.5D + random.nextInt(10) / 10.0D);
		stats.addProperty("durability", 150 + random.nextInt(400));
		stats.addProperty("armor", random.nextInt(5));
		stats.addProperty("armor_toughness", 0);
		stats.addProperty("mining_speed", 2 + random.nextInt(4));
		stats.addProperty("food", 2 + random.nextInt(4));
		stats.addProperty("saturation", 1.0D + random.nextInt(3));
		stats.addProperty("ability_power", 1 + random.nextInt(2));
		root.add("stats", stats);

		JsonArray palette = new JsonArray();
		palette.add("#00000000");
		palette.add(String.format("#ff%06x", random.nextInt(0xFFFFFF)));
		palette.add(String.format("#ff%06x", random.nextInt(0xFFFFFF)));
		root.add("palette", palette);

		JsonArray icon = new JsonArray();

		for (int y = 0; y < 16; y++) {
			StringBuilder row = new StringBuilder(16);

			for (int x = 0; x < 16; x++) {
				boolean inside = x >= 4 && x <= 11 && y >= 3 && y <= 12;
				row.append(inside ? (y < 8 ? '1' : '2') : '0');
			}

			icon.add(row.toString());
		}

		root.add("icon", icon);

		JsonObject model = new JsonObject();
		JsonArray grip = new JsonArray();
		grip.add(8);
		grip.add(1);
		grip.add(8);
		model.add("grip", grip);
		JsonArray parts = new JsonArray();

		for (int index = 0; index < 3; index++) {
			JsonObject part = new JsonObject();
			JsonArray from = new JsonArray();
			from.add(7.5D - index * 0.5D);
			from.add(index * 4.0D);
			from.add(7.5D);
			JsonArray to = new JsonArray();
			to.add(8.5D + index * 0.5D);
			to.add(index * 4.0D + 4.0D);
			to.add(8.5D);
			part.add("from", from);
			part.add("to", to);
			part.addProperty("color", 1 + (index % 2));
			parts.add(part);
		}

		model.add("parts", parts);
		JsonObject display = new JsonObject();
		display.addProperty("hand", 1.0D);
		display.addProperty("ground", 0.8D);
		model.add("display", display);
		model.addProperty("idle_effect", "bob");
		root.add("model", model);

		root.addProperty("lua", "function on_use(player, world)\n"
				+ "  world.play_sound('minecraft:block.note_block.pling', player.pos())\n"
				+ "  world.spawn_particles('minecraft:glow', player.pos(), 8)\n"
				+ "  player.send_message('The " + adjective + " " + noun + " hums.')\n"
				+ "end\n");

		return root.toString();
	}
}