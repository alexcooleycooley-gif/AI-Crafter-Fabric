package com.aialchemy.core;

import java.util.List;

import com.aialchemy.core.icon.Palette;
import com.aialchemy.core.icon.PixelIcon;
import com.aialchemy.core.model.CuboidModel;
import com.aialchemy.core.validation.Issue;

/**
 * A fully validated generated item. This is what the recipe cache stores and what the item factory
 * later turns into an {@code ItemStack} of the matching shell.
 *
 * <p>Scripts, icons and models are referenced by id so that the item stacks themselves only carry
 * the small {@code AlchemyData} component.</p>
 */
public record GeneratedItem(
		String itemId,
		String recipeKey,
		String name,
		List<String> lore,
		Shell shell,
		Rarity rarity,
		StatSet stats,
		List<ItemEffect> effects,
		BlockTraits blockTraits,
		Palette palette,
		PixelIcon icon,
		CuboidModel model,
		String script,
		int tier,
		String discoverer,
		long createdAt,
		List<Issue> notes,
		boolean scriptDisabled) {

	public GeneratedItem {
		lore = List.copyOf(lore);
		effects = List.copyOf(effects);
		notes = List.copyOf(notes);
	}

	public String iconId() {
		return this.itemId;
	}

	public String modelId() {
		return this.model == null ? null : this.itemId;
	}

	public String scriptId() {
		return this.script == null || this.script.isBlank() ? null : this.itemId;
	}

	public boolean hasModel() {
		return this.model != null;
	}

	public boolean hasScript() {
		return !this.scriptDisabled && this.script != null && !this.script.isBlank();
	}

	/** Display fallback for the discoverer field, which can be null for legacy cache entries. */
	public String discovererOrUnknown() {
		return this.discoverer == null || this.discoverer.isBlank() ? "unknown" : this.discoverer;
	}

		public GeneratedItem withScriptDisabled() {
		return new GeneratedItem(this.itemId, this.recipeKey, this.name, this.lore, this.shell, this.rarity,
				this.stats, this.effects, this.blockTraits, this.palette, this.icon, this.model, this.script, this.tier, this.discoverer,
				this.createdAt, this.notes, true);
	}

	public GeneratedItem withDiscoverer(String playerId, long timestamp) {
		return new GeneratedItem(this.itemId, this.recipeKey, this.name, this.lore, this.shell, this.rarity,
				this.stats, this.effects, this.blockTraits, this.palette, this.icon, this.model, this.script, this.tier, playerId,
				timestamp == 0L ? this.createdAt : timestamp, this.notes, this.scriptDisabled);
	}

	/** Short human readable ability summary shown in tooltips (design document section 13). */
	public String abilitySummary() {
		StringBuilder builder = new StringBuilder();

		if (this.stats.damage() > 0f) {
			builder.append(String.format(java.util.Locale.ROOT, "%.1f damage", this.stats.damage()));
		}

		if (this.stats.armor() > 0f) {
			append(builder, String.format(java.util.Locale.ROOT, "%.1f armour", this.stats.armor()));
		}

		if (this.stats.miningSpeed() > 0f) {
			append(builder, String.format(java.util.Locale.ROOT, "%.1f mining speed", this.stats.miningSpeed()));
		}

		if (this.stats.food() > 0) {
			append(builder, this.stats.food() + " nutrition");
		}

		if (hasScript()) {
			append(builder, "scripted behaviour");
		}

		return builder.toString();
	}

	private static void append(StringBuilder builder, String text) {
		if (builder.length() > 0) {
			builder.append(", ");
		}

		builder.append(text);
	}
}
