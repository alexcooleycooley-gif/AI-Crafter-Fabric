package com.aialchemy.core.icon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shared colour palette of a generated item. Both the 16x16 inventory icon and the 3D model
 * reference colours by index, which is what keeps a freshly generated item looking coherent in the
 * inventory and in the world.
 *
 * <p>At most 16 entries, each written as {@code #RRGGBB} or {@code #AARRGGBB}.</p>
 */
public record Palette(List<Integer> colors) {

	public static final int MAX_COLORS = 16;

	public Palette {
		if (colors == null || colors.isEmpty()) {
			throw new IllegalArgumentException("palette must not be empty");
		}

		if (colors.size() > MAX_COLORS) {
			throw new IllegalArgumentException("palette may hold at most " + MAX_COLORS + " colours");
		}

		colors = List.copyOf(colors);
	}

	public int size() {
		return this.colors.size();
	}

	public boolean isValidIndex(int index) {
		return index >= 0 && index < this.colors.size();
	}

	public int argb(int index) {
		int safe = Math.min(Math.max(index, 0), this.colors.size() - 1);
		return this.colors.get(safe);
	}

	/** Widens a 6 digit colour to fully opaque ARGB. */
	public static int parseHex(String value) {
		if (value == null) {
			throw new IllegalArgumentException("colour must not be null");
		}

		String normalized = value.trim().toLowerCase(Locale.ROOT);

		if (normalized.startsWith("#")) {
			normalized = normalized.substring(1);
		}

		if (normalized.startsWith("0x")) {
			normalized = normalized.substring(2);
		}

		if (!normalized.matches("[0-9a-f]{6}|[0-9a-f]{8}")) {
			throw new IllegalArgumentException("invalid colour: " + value);
		}

		long parsed = Long.parseLong(normalized, 16);

		if (normalized.length() == 6) {
			return (int) (0xFF000000L | parsed);
		}

		return (int) parsed;
	}

	public static boolean isValidHex(String value) {
		try {
			parseHex(value);
			return true;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	public static Palette parse(List<String> raw) {
		List<Integer> parsed = new ArrayList<>();
		int remaining = MAX_COLORS;

		for (String entry : raw) {
			if (remaining == 0) {
				break;
			}

			parsed.add(parseHex(entry));
			remaining--;
		}

		return new Palette(parsed);
	}

	public List<String> toHexList() {
		List<String> hex = new ArrayList<>(this.colors.size());

		for (int color : this.colors) {
			hex.add(String.format(Locale.ROOT, "#%08x", color));
		}

		return hex;
	}

	/**
	 * Builds a palette from the average colours of the ingredients, used when the AI palette is
	 * rejected (fallback ladder, design document section 7.4).
	 */
	public static Palette fromColors(List<Integer> candidateColors, int targetSize) {
		List<Integer> unique = new ArrayList<>();

		for (int color : candidateColors) {
			int opaque = color | 0xFF000000;

			if (!unique.contains(opaque)) {
				unique.add(opaque);
			}

			if (unique.size() >= Math.min(Math.max(targetSize, 2), MAX_COLORS)) {
				break;
			}
		}

		if (unique.isEmpty()) {
			unique.add(0xFF9AA0A6);
		}

		// A single ingredient often supplies only one colour. Add deterministic dark and light
		// variants so fallback icons are not rendered as flat one-colour blobs.
		int source = unique.get(0);
		while (unique.size() < Math.min(Math.max(targetSize, 3), MAX_COLORS)) {
			int step = unique.size();
			int r = Math.min(255, Math.max(0, ((source >>> 16) & 0xFF) + (step % 2 == 0 ? 42 : -42)));
			int g = Math.min(255, Math.max(0, ((source >>> 8) & 0xFF) + (step % 2 == 0 ? 42 : -42)));
			int b = Math.min(255, Math.max(0, (source & 0xFF) + (step % 2 == 0 ? 42 : -42)));
			int variant = 0xFF000000 | (r << 16) | (g << 8) | b;
			if (!unique.contains(variant)) unique.add(variant);
			else break;
		}

		unique.add(0, 0x00000000);

		if (unique.size() < 2) {
			unique.add(0xFF404040);
		}

		return new Palette(unique);
	}
}
