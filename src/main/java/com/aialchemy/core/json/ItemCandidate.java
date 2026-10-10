package com.aialchemy.core.json;

import java.util.List;

import com.google.gson.JsonObject;

/**
 * The raw, untrusted AI response, after JSON parsing but before any validation. Everything in here
 * came from a language model and must be treated as hostile input.
 */
public record ItemCandidate(
		String name,
		List<String> lore,
		String shell,
		String rarity,
		StatBlock stats,
		List<String> palette,
		List<String> icon,
		JsonObject model,
		String lua,
		List<EffectSpec> effects,
		BlockSpec blockTraits) {

	/** Raw stats exactly as the model wrote them; clamping happens later. */
	public record StatBlock(
			float damage,
			float attackSpeed,
			int durability,
			float armor,
			float armorToughness,
			float miningSpeed,
			int food,
			float saturation,
			float abilityPower) {
	}

	/** A status effect the item applies, drawn from the allowlist. */
		public record EffectSpec(String id, int ticks, int amplifier) {
		}

		public record BlockSpec(String trait, int lightLevel, float friction, float bounce,
				float hardness, boolean emitsParticles) {
		}
}
