package com.aialchemy.client.render;

import java.util.ArrayList;
import java.util.List;

import com.aialchemy.client.ClientAssets;
import com.aialchemy.component.AlchemyData;
import com.aialchemy.core.model.CuboidModel;
import com.aialchemy.core.model.ModelPart;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The item model installed over every AI Alchemy shell (design document section 6.2).
 *
 * <p>It replaces the baked JSON model for the shell with a layer that points at
 * {@link AlchemySpecialModelRenderer}, so an item can draw an icon in the inventory and a palette
 * coloured cuboid model in the world without the resource pack ever containing either.</p>
 *
 * <p>The version specific parts of the queue API live here and in
 * {@link AlchemyModelPlugin}; everything else in the mod talks to plain data.</p>
 */
public final class AlchemyItemModel implements ItemModel {

	private final ItemModel fallback;

	/**
	 * @param fallback the model vanilla would have used, drawn when an item has no payload yet, so a
	 *                 freshly crafted item is never invisible.
	 */
	public AlchemyItemModel(ItemModel fallback) {
		this.fallback = fallback;
	}

	@Override
	public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
			ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
		AlchemyData data = stack.get(com.aialchemy.component.ModComponents.ALCHEMY_DATA);

		if (data == null) {
			if (this.fallback != null) {
				this.fallback.update(state, stack, resolver, context, level, owner, seed);
			}

			return;
		}

		ClientAssets assets = ClientAssets.get();
		state.appendModelIdentityElement(data.itemId());
		ItemStackRenderState.LayerRenderState layer = state.newLayer();

		// The layer applies the display transform, then hands off to the special renderer, which is
		// exactly how vanilla's own special item models are structured.
		layer.setupSpecialModel(AlchemySpecialModelRenderer.INSTANCE, argument(assets, data, context));
		layer.setTransform(transform(assets, data, context));

		if (stack.hasFoil()) {
			layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
		}

		CuboidModel model = data.hasModel() ? assets.model(data.modelId()) : null;

		if (model != null) {
			Vector3fc[] extents = extents(model);
			layer.setExtents(() -> extents);
		}
	}

	private static RenderArgument argument(ClientAssets assets, AlchemyData data, ItemDisplayContext context) {
		CuboidModel model = data.hasModel() ? assets.model(data.modelId()) : null;
		int[] palette = paletteOf(model, data);
		List<RenderArgument.PartGeometry> parts = new ArrayList<>();

		if (model != null && context != ItemDisplayContext.GUI) {
			for (ModelPart part : model.parts()) {
				parts.add(RenderArgument.PartGeometry.of(part, palette));
			}
		}

		return new RenderArgument(
				data.itemId(),
				data.iconId(),
				palette,
				parts,
				model == null ? 1f : model.displayScale(CuboidModel.DISPLAY_HAND, 1f),
				model == null ? 0.8f : model.displayScale(CuboidModel.DISPLAY_GROUND, 0.8f),
				model == null ? 1f : model.displayScale(CuboidModel.DISPLAY_FRAME, 1f),
				model == null ? defaultExtents() : extents(model),
				model == null ? com.aialchemy.core.model.IdleEffect.NONE : model.idleEffect(),
				data.itemId().hashCode());
	}

	/**
	 * The palette normally arrives with the icon payload; until then the model's own colours are
	 * used, so a model can still draw without its icon.
	 */
	private static int[] paletteOf(CuboidModel model, AlchemyData data) {
		if (model != null && !model.parts().isEmpty()) {
			int highest = 0;

			for (ModelPart part : model.parts()) {
				highest = Math.max(highest, part.color());
			}

			int[] palette = new int[Math.max(4, highest + 1)];

			for (int index = 0; index < palette.length; index++) {
				palette[index] = index == 0 ? 0x00000000 : 0xFF808080 + index * 0x00070707;
			}

			return palette;
		}

		return new int[] {0x00000000, 0xFFAAAAAA, 0xFF666666, 0xFFFFFFFF};
	}

	private static ItemTransform transform(ClientAssets assets, AlchemyData data, ItemDisplayContext context) {
		CuboidModel model = data.hasModel() ? assets.model(data.modelId()) : null;
		float hand = model == null ? 1f : model.displayScale(CuboidModel.DISPLAY_HAND, 1f);
		float ground = model == null ? 0.8f : model.displayScale(CuboidModel.DISPLAY_GROUND, 0.8f);
		float frame = model == null ? 1f : model.displayScale(CuboidModel.DISPLAY_FRAME, 1f);
		return ItemTransforms.forContext(context, ItemTransforms.scaleForContext(context, hand, ground, frame));
	}

	private static Vector3fc[] extents(CuboidModel model) {
		CuboidModel.Bounds bounds = model.bounds();
		List<Vector3fc> points = new ArrayList<>(2);
		points.add(new Vector3f(bounds.minX(), bounds.minY(), bounds.minZ()));
		points.add(new Vector3f(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
		return points.toArray(new Vector3fc[0]);
	}

	private static Vector3fc[] defaultExtents() {
		return new Vector3fc[] {new Vector3f(0f, 0f, 0f), new Vector3f(16f, 16f, 16f)};
	}
}