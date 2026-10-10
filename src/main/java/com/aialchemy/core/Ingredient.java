package com.aialchemy.core;

/**
 * A normalized crafting ingredient. Only the parts of an {@code ItemStack} that may influence the
 * generated result are recorded: the item registry id, the count, and a short signature of the
 * data that matters (enchantments, potion contents, ...).
 *
 * <p>Display names are deliberately <em>not</em> part of an ingredient: prompt injection protection
 * requires that only ids travel to the AI (design document section 16).</p>
 */
public record Ingredient(String itemId, int count, String dataSignature) {

	public Ingredient {
		if (itemId == null || itemId.isBlank()) {
			throw new IllegalArgumentException("itemId must not be blank");
		}
		count = Math.max(1, count);
		if (dataSignature == null) {
			dataSignature = "";
		}
	}

	public static Ingredient of(String itemId, int count) {
		return new Ingredient(itemId, count, "");
	}

	/** Stable textual form used when computing the recipe key. */
	public String signature() {
		return this.itemId + "@" + this.count + (this.dataSignature.isEmpty() ? "" : "#" + this.dataSignature);
	}
}