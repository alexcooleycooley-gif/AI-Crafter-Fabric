package com.aialchemy.core.model;

import java.util.List;
import java.util.Map;

import com.aialchemy.core.Shell;
import com.aialchemy.core.icon.Palette;

/**
 * The stock template model of every shell, recoloured with the item's palette. This is the first
 * rung of the fallback ladder: a model that fails validation is replaced by this, so a bad AI
 * response can never produce an invisible or unrenderable item.
 *
 * <p>Every template is hand built to satisfy the tier 1 constraints (at most 12 parts, coordinates
 * inside the 0..16 grid, one connected cluster and a grip inside the bounding box), which means it
 * also passes for every higher tier.</p>
 */
public final class StockModels {

	private StockModels() {
	}

	public static CuboidModel forShell(Shell shell, Palette palette) {
		return switch (shell) {
			case WEAPON -> weapon(palette);
			case TOOL -> tool(palette);
			case ARMOR_HEAD -> armorHead(palette);
			case ARMOR_CHEST -> armorChest(palette);
			case ARMOR_LEGS -> armorLegs(palette);
			case ARMOR_FEET -> armorFeet(palette);
			case FOOD -> food(palette);
			case TRINKET -> trinket(palette);
		};
	}

	private static CuboidModel weapon(Palette palette) {
		List<ModelPart> parts = List.of(
				part(7.5f, 0f, 7.5f, 8.5f, 3f, 8.5f, 1, palette),
				part(6f, 3f, 6.5f, 10f, 4f, 9.5f, 2, palette),
				part(7f, 4f, 7f, 9f, 13f, 9f, 3, palette),
				part(7f, 13f, 7f, 9f, 15f, 9f, 2, palette));
		return model(8f, 1f, 8f, parts, IdleEffect.NONE);
	}

	private static CuboidModel tool(Palette palette) {
		List<ModelPart> parts = List.of(
				part(7.5f, 0f, 7.5f, 8.5f, 11f, 8.5f, 1, palette),
				part(3f, 10f, 7f, 13f, 12f, 9f, 2, palette),
				part(3f, 11f, 7f, 4f, 13f, 9f, 3, palette),
				part(12f, 11f, 7f, 13f, 13f, 9f, 3, palette));
		return model(7.5f, 1f, 8f, parts, IdleEffect.NONE);
	}

	private static CuboidModel armorHead(Palette palette) {
		List<ModelPart> parts = List.of(
				part(3f, 9f, 3f, 13f, 15f, 13f, 1, palette),
				part(4f, 11f, 2.5f, 12f, 14f, 3.5f, 2, palette),
				part(7f, 15f, 7f, 9f, 16f, 9f, 3, palette));
		return model(8f, 10f, 8f, parts, IdleEffect.NONE);
	}

	private static CuboidModel armorChest(Palette palette) {
		List<ModelPart> parts = List.of(
				part(3f, 5f, 6f, 13f, 11f, 10f, 1, palette),
				part(1f, 10f, 6f, 15f, 12f, 10f, 2, palette),
				part(5f, 5f, 5.5f, 11f, 8f, 6.5f, 3, palette));
		return model(8f, 6f, 8f, parts, IdleEffect.NONE);
	}

	private static CuboidModel armorLegs(Palette palette) {
		List<ModelPart> parts = List.of(
				part(3f, 8f, 6f, 13f, 11f, 10f, 1, palette),
				part(3f, 2f, 6.5f, 6.5f, 8.5f, 9.5f, 2, palette),
				part(9.5f, 2f, 6.5f, 13f, 8.5f, 9.5f, 2, palette));
		return model(6f, 4f, 8f, parts, IdleEffect.NONE);
	}

	private static CuboidModel armorFeet(Palette palette) {
		List<ModelPart> parts = List.of(
				part(3f, 0f, 5.5f, 7f, 4f, 10.5f, 1, palette),
				part(9f, 0f, 5.5f, 13f, 4f, 10.5f, 1, palette),
				part(7f, 1f, 6.5f, 9f, 4f, 9.5f, 2, palette));
		return model(8f, 1.5f, 8f, parts, IdleEffect.NONE);
	}

	private static CuboidModel food(Palette palette) {
		List<ModelPart> parts = List.of(
				part(5f, 3f, 4f, 11f, 11f, 12f, 1, palette),
				part(7f, 11f, 7f, 9f, 14f, 9f, 2, palette),
				part(9f, 12f, 6f, 12f, 13f, 9f, 3, palette));
		return model(8f, 4f, 8f, parts, IdleEffect.NONE);
	}

	private static CuboidModel trinket(Palette palette) {
		List<ModelPart> parts = List.of(
				part(5f, 6f, 7f, 11f, 10f, 9f, 1, palette),
				part(6.5f, 9f, 6f, 9.5f, 13f, 10f, 2, palette),
				part(7f, 13f, 7f, 9f, 15f, 9f, 3, palette));
		return model(8f, 7f, 8f, parts, IdleEffect.NONE);
	}

	private static ModelPart part(float fromX, float fromY, float fromZ, float toX, float toY, float toZ, int color, Palette palette) {
		return new ModelPart(fromX, fromY, fromZ, toX, toY, toZ, clamp(color, palette), Pattern.SOLID, false, null);
	}

	private static int clamp(int color, Palette palette) {
		if (palette == null || palette.size() == 0) {
			return 0;
		}

		return Math.min(color, palette.size() - 1);
	}

	private static CuboidModel model(float gripX, float gripY, float gripZ, List<ModelPart> parts, IdleEffect effect) {
		return new CuboidModel(gripX, gripY, gripZ, parts,
				Map.of(CuboidModel.DISPLAY_HAND, 1.0f, CuboidModel.DISPLAY_GROUND, 0.8f, CuboidModel.DISPLAY_FRAME, 1.0f),
				effect);
	}
}