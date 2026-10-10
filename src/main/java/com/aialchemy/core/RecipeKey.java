package com.aialchemy.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Recipe key normalization (design document section 6).
 *
 * <p>The key is a hash of the sorted multiset of ingredient signatures, so the arrangement inside
 * the crafting grid does not change the result. When {@code shapedMatters} is enabled the grid slot
 * index is folded into each signature instead, which makes two different layouts produce two
 * different items.</p>
 */
public final class RecipeKey {

	private RecipeKey() {
	}

	/**
	 * @param ingredients   normalized ingredients, in grid order
	 * @param shapedMatters when true, the position inside the grid is part of the key
	 * @return a stable hex key (24 characters) identifying this combination
	 */
	public static String compute(List<Ingredient> ingredients, boolean shapedMatters) {
		List<String> parts = new ArrayList<>(ingredients.size());

		for (int index = 0; index < ingredients.size(); index++) {
			Ingredient ingredient = ingredients.get(index);
			if (ingredient == null) {
				continue;
			}

			if (shapedMatters) {
				parts.add(index + ":" + ingredient.signature());
			} else {
				parts.add(ingredient.signature());
			}
		}

		if (!shapedMatters) {
			parts.sort(String::compareTo);
		}

		return hash(String.join("|", parts));
	}

	/** A truncated SHA-256 digest: short enough for cache files, wide enough to avoid collisions. */
	public static String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(24);

			for (int index = 0; index < 12; index++) {
				builder.append(Character.forDigit((bytes[index] >> 4) & 0xF, 16));
				builder.append(Character.forDigit(bytes[index] & 0xF, 16));
			}

			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	/**
	 * Stable item id for a generated item: the recipe key mixed with the world seed, so the same
	 * ingredients never collide across worlds while staying reproducible inside one world.
	 */
	public static String itemId(String recipeKey, long worldSeed) {
		return hash(recipeKey + ":" + worldSeed);
	}
}