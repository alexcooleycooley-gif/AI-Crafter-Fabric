package com.aialchemy.core.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aialchemy.core.Shell;
import com.aialchemy.core.ValidationContext;
import com.aialchemy.core.validation.Issue;

/**
 * Icon tests: parsing, validation, the procedural fallback and the PNG encoder (design document
 * section 6.1).
 */
class IconTest {

	private static final Palette PALETTE = Palette.parse(List.of("#00000000", "#ff0000", "#00ff00", "#0000ff"));

	private static List<String> rows(String fill) {
		List<String> list = new ArrayList<>(16);

		for (int index = 0; index < 16; index++) {
			list.add(fill.repeat(16));
		}

		return list;
	}

	@Test
	@DisplayName("palettes round trip through hex and reject malformed colours")
	void paletteParsing() {
		assertEquals(4, PALETTE.size());
		assertEquals(0xFF0000, PALETTE.argb(1) & 0xFFFFFF);
		assertEquals("#00000000", PALETTE.toHexList().get(0));
		assertTrue(Palette.isValidHex("#ABCDEF"));
		assertTrue(Palette.isValidHex("ABCDEF"), "the leading hash is optional, the AI forgets it often");
		assertFalse(Palette.isValidHex("#ABCDE"), "five digits is not a colour");
		assertFalse(Palette.isValidHex("#ABCDEF0"), "seven digits is not a colour");
		assertFalse(Palette.isValidHex("#GGGGGG"));
	}

	@Test
	@DisplayName("a well formed icon passes and produces a decodable PNG")
	void goodIconPasses() {
		List<String> raw = rows("1");
		Issue.Collector issues = IconValidator.validate(raw, PALETTE);
		assertFalse(issues.hasErrors(), issues::errorSummary);

		PixelIcon icon = PixelIcon.parse(raw);
		byte[] png = IconRenderer.toPngBytes(icon, PALETTE);
		assertNotNull(png);
		assertTrue(png.length > 60);
		assertEquals((byte) 0x89, png[0]);
		assertEquals('P', png[1]);
		assertEquals('N', png[2]);
		assertEquals('G', png[3]);

		// And the encoder is its own inverse for an opaque palette entry.
		PixelIcon restored = IconRenderer.fromPngBytes(png, PALETTE);
		assertEquals(icon.asList(), restored.asList());
	}

	@Test
	@DisplayName("wrong row count, wrong row length, out of range indices and empty icons are rejected")
	void badIconsAreRejected() {
		assertTrue(IconValidator.validate(rows("1").subList(0, 15), PALETTE).hasErrors(), "too few rows");
		assertTrue(IconValidator.validate(List.of("111", "111"), PALETTE).hasErrors(), "wrong row length");

		List<String> outOfRange = rows("0");
		outOfRange.set(0, "9999999999999999");
		assertTrue(IconValidator.validate(outOfRange, PALETTE).hasErrors(), "palette index out of range");

		assertTrue(IconValidator.validate(rows("0"), PALETTE).hasErrors(), "an empty icon is not an icon");
	}

	@Test
	@DisplayName("the procedural fallback is deterministic, valid and shell aware")
	void proceduralIconIsDeterministic() {
		PixelIcon first = ProceduralIcon.build(PALETTE, Shell.WEAPON, 1234L);
		PixelIcon second = ProceduralIcon.build(PALETTE, Shell.WEAPON, 1234L);
		PixelIcon other = ProceduralIcon.build(PALETTE, Shell.FOOD, 1234L);

		assertEquals(first.asList(), second.asList(), "same seed means the same sprite");
		assertFalse(first.asList().equals(other.asList()), "different shells look different");

		Issue.Collector issues = IconValidator.validate(first.asList(), PALETTE);
		assertFalse(issues.hasErrors(), issues::errorSummary);
		// The generated sprite must not be a flat block of one colour.
		assertTrue(first.asList().stream().distinct().count() > 1);
	}

	@Test
	@DisplayName("a single colour palette is widened rather than rejected")
	void paletteWidening() {
		List<Integer> ingredientColors = List.of(0xFF112233);
		Palette widened = Palette.fromColors(ingredientColors, 4);

		assertTrue(widened.size() >= 2, "at least one visible colour plus transparency");
		Issue.Collector issues = IconValidator.validate(ProceduralIcon.build(widened, Shell.TRINKET, 7L).asList(), widened);
		assertFalse(issues.hasErrors(), issues::errorSummary);
	}

	@Test
	@DisplayName("off mode skips the blocklist but keeps the structural checks")
	void filterLevels() {
		ValidationContext off = new ValidationContext(1, 48, true, true, ValidationContext.FilterLevel.OFF, 40, 6, 160, 8192, 1.0D);
		Issue.Collector issues = com.aialchemy.core.validation.ContentFilter.validate("whatever", List.of(), off);
		assertFalse(issues.hasErrors());
	}
}
