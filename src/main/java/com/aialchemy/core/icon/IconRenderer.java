package com.aialchemy.core.icon;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Turns a validated {@link PixelIcon} into the PNG bytes that are stored on disk and shipped to
 * clients. The server re-encodes every icon itself, so only vetted pixels ever reach a client.
 */
public final class IconRenderer {

	private IconRenderer() {
	}

	public static BufferedImage toImage(PixelIcon icon, Palette palette) {
		BufferedImage image = new BufferedImage(PixelIcon.SIZE, PixelIcon.SIZE, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = icon.toArgb(palette);
		image.setRGB(0, 0, PixelIcon.SIZE, PixelIcon.SIZE, pixels, 0, PixelIcon.SIZE);
		return image;
	}

	public static byte[] toPngBytes(PixelIcon icon, Palette palette) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
			ImageIO.write(toImage(icon, palette), "png", out);
			return out.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("failed to encode icon", exception);
		}
	}

	/** Reads back previously stored PNG bytes; used by the client side cache. */
	public static PixelIcon fromPngBytes(byte[] pngBytes, Palette palette) {
		try {
			BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(pngBytes));

			if (image == null) {
				throw new IllegalStateException("not a readable PNG");
			}

			String[] rows = new String[PixelIcon.SIZE];

			for (int y = 0; y < PixelIcon.SIZE; y++) {
				StringBuilder row = new StringBuilder(PixelIcon.SIZE);

				for (int x = 0; x < PixelIcon.SIZE; x++) {
					int argb = image.getRGB(x, y);
					row.append(Character.forDigit(nearestIndex(argb, palette), 16));
				}

				rows[y] = row.toString();
			}

			return new PixelIcon(rows);
		} catch (IOException exception) {
			throw new IllegalStateException("failed to decode icon", exception);
		}
	}

	private static int nearestIndex(int argb, Palette palette) {
		int best = 0;
		long bestDistance = Long.MAX_VALUE;

		for (int index = 0; index < palette.size(); index++) {
			int candidate = palette.argb(index);
			long distance = distance(argb, candidate);

			if (distance < bestDistance) {
				bestDistance = distance;
				best = index;
			}
		}

		return best;
	}

	private static long distance(int left, int right) {
		long da = ((left >>> 24) & 0xFF) - ((right >>> 24) & 0xFF);
		long dr = ((left >>> 16) & 0xFF) - ((right >>> 16) & 0xFF);
		long dg = ((left >>> 8) & 0xFF) - ((right >>> 8) & 0xFF);
		long db = (left & 0xFF) - (right & 0xFF);
		return da * da + dr * dr + dg * dg + db * db;
	}
}