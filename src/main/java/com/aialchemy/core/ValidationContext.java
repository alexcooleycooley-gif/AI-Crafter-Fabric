package com.aialchemy.core;

/**
 * The small slice of server configuration the pure validation core needs. Keeping it as a record
 * lets the whole pipeline be unit tested without booting Minecraft.
 */
public record ValidationContext(
		int tier,
		int maxModelParts,
		boolean allowScripts,
		boolean generateModels,
		FilterLevel contentFilter,
		int maxNameLength,
		int maxLoreLines,
		int maxLoreLength,
		int maxScriptBytes,
		double powerCap) {

	public enum FilterLevel {
		OFF,
		BASIC,
		STRICT
	}

	public static ValidationContext defaults(int tier) {
		return new ValidationContext(tier, 48, true, true, FilterLevel.STRICT, 40, 6, 160, 8192, 1.0D);
	}

	public ValidationContext withTier(int newTier) {
		return new ValidationContext(newTier, this.maxModelParts, this.allowScripts, this.generateModels,
				this.contentFilter, this.maxNameLength, this.maxLoreLines, this.maxLoreLength, this.maxScriptBytes, this.powerCap);
	}
}