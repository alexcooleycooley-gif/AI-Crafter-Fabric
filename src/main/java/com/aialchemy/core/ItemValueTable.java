package com.aialchemy.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Data-driven ingredient values used by the power budget (design document section 10). The table is
 * shipped with the mod as {@code data/ai_alchemy/item_values.json} and may be overridden by a world
 * level file of the same name.
 *
 * <p>Unknown (mostly modded) items fall back to a value derived from their rarity.</p>
 */
public final class ItemValueTable {

	/** Values of well known vanilla ingredients; anything else is defaulted by rarity. */
	public static final Map<String, Integer> DEFAULTS = defaultValues();

	private final Map<String, Integer> values;
	private final int unknownDefault;

	public ItemValueTable(Map<String, Integer> values, int unknownDefault) {
		this.values = new TreeMap<>(values == null ? Map.of() : values);
		this.unknownDefault = unknownDefault;
	}

	public static ItemValueTable defaults() {
		return new ItemValueTable(DEFAULTS, 2);
	}

	public Map<String, Integer> values() {
		return Collections.unmodifiableMap(this.values);
	}

	/** Value for a recipe key / item id entry. */
	public int valueFor(ItemKey key) {
		Integer known = this.values.get(key.registryId());
		if (known != null) {
			return Math.max(1, known);
		}

		return Math.max(1, key.rarity() == null ? this.unknownDefault : key.rarity().defaultValue());
	}

	/** Minimal view of an ingredient needed to value it. */
	public record ItemKey(String registryId, Rarity rarity) {
	}

	private static Map<String, Integer> defaultValues() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("minecraft:cobblestone", 1);
		map.put("minecraft:stone", 1);
		map.put("minecraft:dirt", 1);
		map.put("minecraft:sand", 1);
		map.put("minecraft:oak_log", 1);
		map.put("minecraft:oak_planks", 1);
		map.put("minecraft:stick", 1);
		map.put("minecraft:coal", 1);
		map.put("minecraft:charcoal", 1);
		map.put("minecraft:flint", 1);
		map.put("minecraft:leather", 2);
		map.put("minecraft:copper_ingot", 2);
		map.put("minecraft:string", 1);
		map.put("minecraft:feather", 1);
		map.put("minecraft:bone", 1);
		map.put("minecraft:gunpowder", 2);
		map.put("minecraft:slime_ball", 2);
		map.put("minecraft:iron_ingot", 4);
		map.put("minecraft:iron_nugget", 1);
		map.put("minecraft:redstone", 2);
		map.put("minecraft:lapis_lazuli", 2);
		map.put("minecraft:quartz", 2);
		map.put("minecraft:gold_ingot", 6);
		map.put("minecraft:brick", 2);
		map.put("minecraft:paper", 1);
		map.put("minecraft:blaze_powder", 5);
		map.put("minecraft:blaze_rod", 8);
		map.put("minecraft:ender_pearl", 8);
		map.put("minecraft:ender_eye", 10);
		map.put("minecraft:glowstone_dust", 3);
		map.put("minecraft:amethyst_shard", 5);
		map.put("minecraft:obsidian", 5);
		map.put("minecraft:crying_obsidian", 8);
		map.put("minecraft:diamond", 12);
		map.put("minecraft:emerald", 10);
		map.put("minecraft:echo_shard", 14);
		map.put("minecraft:ghast_tear", 12);
		map.put("minecraft:shulker_shell", 12);
		map.put("minecraft:heart_of_the_sea", 20);
		map.put("minecraft:netherite_ingot", 24);
		map.put("minecraft:netherite_scrap", 8);
		map.put("minecraft:ancient_debris", 10);
		map.put("minecraft:enchanted_golden_apple", 30);
		map.put("minecraft:nether_star", 40);
		map.put("minecraft:dragon_breath", 16);
		map.put("minecraft:dragon_egg", 60);
		map.put("minecraft:totem_of_undying", 28);
		map.put("minecraft:trident", 22);
		map.put("minecraft:wither_skeleton_skull", 20);
		map.put("minecraft:experience_bottle", 3);
		map.put("minecraft:golden_apple", 8);
		return Collections.unmodifiableMap(map);
	}
}