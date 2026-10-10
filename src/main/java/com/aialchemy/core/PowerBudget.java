package com.aialchemy.core;

import java.util.List;

/**
 * Turns an ingredient list into a power tier (design document section 10).
 *
 * <p>The score deliberately mixes the total value with the single most valuable ingredient:</p>
 * <ul>
 *   <li>many cheap items cannot inflate the tier above {@code 2 * max + SPLASH};</li>
 *   <li>a single expensive item cannot be diluted below its own weight.</li>
 * </ul>
 *
 * <p>{@code power_cap} scales the score globally, so a server owner can soften every tier at once.</p>
 */
public final class PowerBudget {

	/** How much a single standout ingredient can contribute on top of doubling its own value. */
	public static final int SPLASH = 12;

	private static final int[] THRESHOLDS = {8, 24, 60};

	private final ItemValueTable valueTable;
	private final double powerCap;

	public PowerBudget(ItemValueTable valueTable, double powerCap) {
		this.valueTable = valueTable;
		this.powerCap = Math.max(0.05D, powerCap);
	}

	public ItemValueTable valueTable() {
		return this.valueTable;
	}

	/** Raw score before tier thresholds, exposed for {@code /alchemy usage} style diagnostics. */
	public double score(List<ItemKeyed> ingredients) {
		int sum = 0;
		int max = 0;

		for (ItemKeyed entry : ingredients) {
			// Sixty four iron is not the same recipe as one iron, so the count is part of the weight.
			// The splash term below still keeps a single very expensive ingredient from dominating.
			int value = valueWeight(entry) * Math.max(1, entry.count());
			sum += value;
			max = Math.max(max, value);
		}

		if (max == 0) {
			return 0D;
		}

		double combined = Math.min(sum, 2.0D * max + SPLASH);
		return combined * this.powerCap;
	}

	public int tier(List<ItemKeyed> ingredients) {
		double score = score(ingredients);

		for (int index = 0; index < THRESHOLDS.length; index++) {
			if (score < THRESHOLDS[index]) {
				return index + 1;
			}
		}

		return THRESHOLDS.length + 1;
	}

	public StatRanges ranges(int tier) {
		return StatRanges.forTier(tier);
	}

	private int valueWeight(ItemKeyed entry) {
		return this.valueTable.valueFor(new ItemValueTable.ItemKey(entry.registryId(), entry.rarity()));
	}

	/** An ingredient plus the rarity of its base item, needed for the unknown-item fallback. */
	public record ItemKeyed(String registryId, int count, Rarity rarity) {

		public static ItemKeyed of(ItemKeyed source, int count) {
			return new ItemKeyed(source.registryId(), count, source.rarity());
		}
	}
}
