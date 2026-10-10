package com.aialchemy.core;

/**
 * Hard ceilings per power tier (design document section 10). The AI is told about these ranges in
 * the prompt, and every returned value is clamped into them before it reaches an item stack.
 */
public record StatRanges(
		float damage,
		float attackSpeed,
		int durability,
		float armor,
		float armorToughness,
		float miningSpeed,
		int food,
		float saturation,
		float abilityPower,
		int effects) {

	private static final StatRanges[] BY_TIER = {
			// tier 1 - roughly a stone tool with a single minor effect
			new StatRanges(4f, 4.4f, 250, 3f, 0f, 4f, 4, 2f, 1f, 1),
			// tier 2 - iron
			new StatRanges(7f, 4.8f, 800, 6f, 1f, 7f, 8, 4f, 2f, 2),
			// tier 3 - diamond
			new StatRanges(10f, 5.2f, 1800, 10f, 2f, 9f, 12, 6f, 3f, 3),
			// tier 4 - netherite and beyond
			new StatRanges(14f, 5.6f, 3000, 16f, 3f, 12f, 20, 10f, 4f, 4),
	};

	public static StatRanges forTier(int tier) {
		int index = Math.min(Math.max(tier, 1), BY_TIER.length) - 1;
		return BY_TIER[index];
	}

	/** Clamps the supplied stats into this range, dropping values the shell cannot use. */
	public StatSet clamp(StatSet stats, Shell shell) {
		float damage = shell == Shell.WEAPON || shell == Shell.TOOL ? clampRange(stats.damage(), 0f, this.damage) : 0f;
		float attackSpeed = shell == Shell.WEAPON || shell == Shell.TOOL ? clampRange(stats.attackSpeed(), 3.0f, this.attackSpeed) : 0f;
		int durability = shell.damageable() ? clampInt(stats.durability(), 0, this.durability) : 0;
		float armor = shell.armor() ? clampRange(stats.armor(), 0f, this.armor) : 0f;
		float toughness = shell.armor() ? clampRange(stats.armorToughness(), 0f, this.armorToughness) : 0f;
		float miningSpeed = shell == Shell.TOOL ? clampRange(stats.miningSpeed(), 1f, this.miningSpeed) : 0f;
		int food = shell == Shell.FOOD ? clampInt(stats.food(), 0, this.food) : 0;
		float saturation = shell == Shell.FOOD ? clampRange(stats.saturation(), 0f, this.saturation) : 0f;

		return new StatSet(damage, attackSpeed, durability, armor, toughness, miningSpeed, food, saturation,
				clampRange(stats.abilityPower(), 0f, this.abilityPower));
	}

	public static float clampRange(float value, float min, float max) {
		if (Float.isNaN(value)) {
			return min;
		}

		return Math.min(Math.max(value, min), max);
	}

	public static int clampInt(int value, int min, int max) {
		return Math.min(Math.max(value, min), max);
	}
}