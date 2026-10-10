package com.aialchemy.core.icon;

import java.util.List;

import com.aialchemy.core.validation.Issue;

/** Validates AI supplied icons: exact dimensions, valid palette indices, palette cap. */
public final class IconValidator {

	private IconValidator() {
	}

	public static Issue.Collector validate(List<String> rawIcon, Palette palette) {
		Issue.Collector collector = new Issue.Collector();

		if (palette == null || palette.size() < 2) {
			collector.error("icon.palette.too_small", "palette must contain at least 2 colours");
			return collector;
		}

		if (palette.size() > Palette.MAX_COLORS) {
			collector.error("icon.palette.too_large", "palette must not exceed " + Palette.MAX_COLORS + " colours");
		}

		if (rawIcon == null || rawIcon.size() != PixelIcon.SIZE) {
			collector.error("icon.rows", "icon must contain exactly " + PixelIcon.SIZE + " rows");
			return collector;
		}

		for (int row = 0; row < rawIcon.size(); row++) {
			String line = rawIcon.get(row);

			if (line == null || line.length() != PixelIcon.SIZE) {
				collector.error("icon.row.length", "row " + row + " must contain exactly " + PixelIcon.SIZE + " characters");
				continue;
			}

			for (int column = 0; column < line.length(); column++) {
				char character = line.charAt(column);

				if (Character.digit(character, 16) < 0) {
					collector.error("icon.pixel", "row " + row + " column " + column + " is not a palette index");
					break;
				}
			}
		}

		if (collector.hasErrors()) {
			return collector;
		}

		boolean anyOpaque = false;
		PixelIcon icon = new PixelIcon(rawIcon.toArray(new String[0]));

		for (int y = 0; y < PixelIcon.SIZE; y++) {
			for (int x = 0; x < PixelIcon.SIZE; x++) {
				int index = icon.indexAt(x, y);

				if (!palette.isValidIndex(index)) {
					collector.error("icon.pixel.range", "pixel (" + x + "," + y + ") uses index " + index + " outside the palette");
					return collector;
				}

				if ((palette.argb(index) >>> 24) != 0) {
					anyOpaque = true;
				}
			}
		}

		if (!anyOpaque) {
			collector.error("icon.empty", "icon is fully transparent");
		}

		return collector;
	}
}