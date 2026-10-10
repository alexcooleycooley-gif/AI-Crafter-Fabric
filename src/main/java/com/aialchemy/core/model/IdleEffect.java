package com.aialchemy.core.model;

import java.util.Locale;

/** Preset idle animation applied while the item is held. No skeletal or keyframe animation in v1. */
public enum IdleEffect {
	NONE,
	SPIN,
	BOB,
	PULSE;

	public String jsonName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static IdleEffect byJsonName(String name) {
		if (name == null || name.isBlank()) {
			return NONE;
		}

		String normalized = name.trim().toLowerCase(Locale.ROOT);
		for (IdleEffect effect : values()) {
			if (effect.jsonName().equals(normalized)) {
				return effect;
			}
		}

		return NONE;
	}

	public static String allowedNames() {
		return "none, spin, bob, pulse";
	}
}