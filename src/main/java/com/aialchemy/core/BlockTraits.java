package com.aialchemy.core;

import java.util.Locale;

/** Safe, bounded behavior properties for the generated block shell. */
public record BlockTraits(String trait, int lightLevel, float friction, float bounce, float hardness,
		boolean emitsParticles) {

	public static final BlockTraits DEFAULT = new BlockTraits("fortified", 0, 0.6f, 0f, 5f, false);

	public BlockTraits {
		trait = normalizeTrait(trait);
		lightLevel = Math.max(0, Math.min(15, lightLevel));
		friction = clamp(friction, 0.2f, 1.0f);
		bounce = clamp(bounce, 0f, 1.0f);
		hardness = clamp(hardness, 0.1f, 50f);
	}

	public static BlockTraits from(String trait, int lightLevel, float friction, float bounce,
			float hardness, boolean emitsParticles) {
		return new BlockTraits(trait, lightLevel, friction, bounce, hardness, emitsParticles);
	}

	private static String normalizeTrait(String value) {
		String normalized = value == null ? "fortified" : value.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "luminous", "bouncy", "slippery", "resonant", "fortified" -> normalized;
			default -> "fortified";
		};
	}

	private static float clamp(float value, float min, float max) {
		return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
	}
}
