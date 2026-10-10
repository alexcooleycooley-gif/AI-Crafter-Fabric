package com.aialchemy.core;

import java.util.Locale;
import java.util.Optional;

/**
 * Item rarity. Rarity is used both for the vanilla {@code rarity} component and as the fallback
 * power value for unknown (modded) ingredients, see design document section 10.
 */
public enum Rarity {
	COMMON("common", 1, 1),
	UNCOMMON("uncommon", 2, 2),
	RARE("rare", 4, 6),
	EPIC("epic", 8, 14);

	private final String jsonName;
	private final int tiersAhead;
	private final int defaultValue;

	Rarity(String jsonName, int tiersAhead, int defaultValue) {
		this.jsonName = jsonName;
		this.tiersAhead = tiersAhead;
		this.defaultValue = defaultValue;
	}

	public String jsonName() {
		return this.jsonName;
	}

	/** Ordinal lift applied when the generated item only uses cheap ingredients. */
	public int tiersAhead() {
		return this.tiersAhead;
	}

	/** Power value assumed for an unknown modded ingredient of this rarity. */
	public int defaultValue() {
		return this.defaultValue;
	}

	public static Rarity byJsonName(String name) {
		return parse(name).orElse(COMMON);
	}

	public static Optional<Rarity> parse(String name) {
		if (name == null) {
			return Optional.empty();
		}
		String normalized = name.trim().toLowerCase(Locale.ROOT);
		for (Rarity rarity : values()) {
			if (rarity.jsonName.equals(normalized) || rarity.name().equalsIgnoreCase(normalized)) {
				return Optional.of(rarity);
			}
		}
		return Optional.empty();
	}

	public static String allowedNames() {
		StringBuilder builder = new StringBuilder();
		for (Rarity rarity : values()) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(rarity.jsonName);
		}
		return builder.toString();
	}
}