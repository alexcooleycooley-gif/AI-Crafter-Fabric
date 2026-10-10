package com.aialchemy.client.render;

import java.util.List;

import com.aialchemy.core.model.CuboidModel;
import com.aialchemy.core.model.IdleEffect;
import com.aialchemy.core.model.ModelPart;

import org.joml.Vector3fc;

/**
 * Immutable snapshot handed from the item model to the renderer for one frame. Building it once per
 * update keeps the hot path free of lookups and of any work that could touch the client cache while
 * the GPU is busy.
 */
public record RenderArgument(
		String itemId,
		String iconId,
		int[] palette,
		List<PartGeometry> parts,
		float handScale,
		float groundScale,
		float frameScale,
		Vector3fc[] extents,
		IdleEffect idleEffect,
		long animationSeed) {

	/** One cuboid, pre-transformed into item space with its per face colours resolved. */
	public record PartGeometry(
			float minX, float minY, float minZ,
			float maxX, float maxY, float maxZ,
			int rgb,
			int alpha,
			boolean emissive,
			float rotationDegrees,
			char rotationAxis,
			float rotationOriginX,
			float rotationOriginY,
			float rotationOriginZ) {

		public static PartGeometry of(ModelPart part, int[] palette) {
			int color = part.color() >= 0 && part.color() < palette.length ? palette[part.color()] : 0xFFFFFFFF;
			return new PartGeometry(
					Math.min(part.fromX(), part.toX()), Math.min(part.fromY(), part.toY()), Math.min(part.fromZ(), part.toZ()),
					Math.max(part.fromX(), part.toX()), Math.max(part.fromY(), part.toY()), Math.max(part.fromZ(), part.toZ()),
					color & 0x00FFFFFF, (color >>> 24) & 0xFF, part.emissive(),
					part.rotation() == null ? 0f : part.rotation().angle(),
					part.rotation() == null ? ' ' : part.rotation().axis(),
					part.rotation() == null ? 0f : part.rotation().originX(),
					part.rotation() == null ? 0f : part.rotation().originY(),
					part.rotation() == null ? 0f : part.rotation().originZ());
		}
	}
}
