package com.aialchemy.core.icon;

import java.util.List;
import java.util.Random;

import com.aialchemy.core.Shell;

/**
 * Fallback icon generator (design document section 7.4). When the AI icon fails validation we build
 * a sprite procedurally from the ingredients' average colours and the item's shell silhouette, so a
 * broken response still yields something recognisable instead of a blank slot.
 */
public final class ProceduralIcon {

	private ProceduralIcon() {
	}

	public static PixelIcon build(Palette palette, Shell shell, long seed) {
		Random random = new Random(seed);
		String[] rows = new String[PixelIcon.SIZE];

		for (int y = 0; y < PixelIcon.SIZE; y++) {
			StringBuilder row = new StringBuilder(PixelIcon.SIZE);

			for (int x = 0; x < PixelIcon.SIZE; x++) {
				row.append(Character.forDigit(pixel(palette, shell, x, y, random), 16));
			}

			rows[y] = row.toString();
		}

		return new PixelIcon(rows);
	}

	private static int pixel(Palette palette, Shell shell, int x, int y, Random random) {
		if (!inside(shell, x, y)) {
			return 0;
		}

		int shade = Math.min(palette.size() - 1, 1 + (y * (palette.size() - 1)) / PixelIcon.SIZE);

		if (random.nextInt(100) < 8) {
			shade = Math.max(1, shade - 1);
		}

		return shade;
	}

	private static boolean inside(Shell shell, int x, int y) {
		return switch (shell) {
			case WEAPON -> (y >= 11 && x >= 6 && x <= 9) || (x >= 5 && x <= 10 && y >= 5 && y <= 10) || (y <= 5 && x >= 6 && x <= 9);
			case TOOL -> (y >= 11 && x >= 6 && x <= 9) || (y <= 6 && x >= 3 && x <= 12);
			case FOOD -> x >= 3 && x <= 12 && y >= 3 && y <= 12;
			case ARMOR_HEAD -> x >= 4 && x <= 11 && y >= 3 && y <= 10;
			case ARMOR_CHEST -> x >= 3 && x <= 12 && y >= 3 && y <= 11;
			case ARMOR_LEGS -> (x >= 2 && x <= 6 && y >= 3 && y <= 12) || (x >= 9 && x <= 13 && y >= 3 && y <= 12);
			case ARMOR_FEET -> (x >= 2 && x <= 6 && y >= 7 && y <= 12) || (x >= 9 && x <= 13 && y >= 7 && y <= 12);
			case TRINKET -> {
				int dx = x - 8;
				int dy = y - 8;
				yield dx * dx + dy * dy <= 30;
			}
		};
	}

	/** Mean colour of a set of ingredient average colours, used to seed a fallback palette. */
	public static int averageColor(List<Integer> colors) {
		if (colors.isEmpty()) {
			return 0xFF9AA0A6;
		}

		long alpha = 0;
		long red = 0;
		long green = 0;
		long blue = 0;

		for (int color : colors) {
			alpha += (color >>> 24) & 0xFF;
			red += (color >>> 16) & 0xFF;
			green += (color >>> 8) & 0xFF;
			blue += color & 0xFF;
		}

		int size = colors.size();
		int a = (int) (alpha / size);
		int r = (int) (red / size);
		int g = (int) (green / size);
		int b = (int) (blue / size);
		return ((a == 0 ? 0xFF : a) << 24) | (r << 16) | (g << 8) | b;
	}
}