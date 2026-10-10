package com.aialchemy.core.icon;

import java.util.ArrayList;
import java.util.List;

/**
 * A 16x16 GUI sprite stored as palette indices, one hex character per pixel. A palette capped at 16
 * colours is exactly what makes a single hex digit per pixel possible, and it makes the icon trivial
 * to validate and to re-encode on the server.
 */
public record PixelIcon(String[] rows) {

	public static final int SIZE = 16;

	public PixelIcon {
		if (rows == null || rows.length != SIZE) {
			throw new IllegalArgumentException("icon must have exactly " + SIZE + " rows");
		}

		rows = rows.clone();

		for (String row : rows) {
			if (row == null || row.length() != SIZE) {
				throw new IllegalArgumentException("every icon row must have exactly " + SIZE + " characters");
			}
		}
	}

	public int indexAt(int x, int y) {
		char character = this.rows[y].charAt(x);
		return Character.digit(character, 16);
	}

	/** Flattens the grid into ARGB pixels, ready to be written as a PNG. */
	public int[] toArgb(Palette palette) {
		int[] pixels = new int[SIZE * SIZE];

		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int index = indexAt(x, y);
				pixels[y * SIZE + x] = palette.isValidIndex(index) ? palette.argb(index) : 0;
			}
		}

		return pixels;
	}

	public List<String> asList() {
		return new ArrayList<>(List.of(this.rows));
	}

	public static PixelIcon parse(List<String> rows) {
		if (rows == null || rows.size() != SIZE) {
			throw new IllegalArgumentException("icon must have exactly " + SIZE + " rows");
		}

		return new PixelIcon(rows.toArray(new String[0]));
	}
}