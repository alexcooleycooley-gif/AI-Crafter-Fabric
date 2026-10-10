package com.aialchemy.core;

/**
 * A status effect a generated item applies on use. The identifier must come from the allowlisted
 * vanilla effect list, so a model cannot invent arbitrary registry names.
 */
public record ItemEffect(String id, int ticks, int amplifier) {

	public ItemEffect {
		String normalized = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);

		if (!normalized.contains(":")) {
			normalized = "minecraft:" + normalized;
		}

		id = normalized;
		ticks = Math.max(1, Math.min(ticks, 20 * 60));
		amplifier = Math.max(0, Math.min(amplifier, 4));
	}
}