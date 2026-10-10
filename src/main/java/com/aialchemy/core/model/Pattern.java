package com.aialchemy.core.model;

import java.util.Locale;

/** Preset face detail for a generated cuboid. Only these four are allowed. */
public enum Pattern {
	SOLID,
	GRADIENT,
	STRIPES,
	SPECKLE;

	public String jsonName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Pattern byJsonName(String name) {
		if (name == null || name.isBlank()) {
			return SOLID;
		}

		String normalized = name.trim().toLowerCase(Locale.ROOT);
		for (Pattern pattern : values()) {
			if (pattern.jsonName().equals(normalized)) {
				return pattern;
			}
		}

		return SOLID;
	}

	public static String allowedNames() {
		return "solid, gradient, stripes, speckle";
	}
}