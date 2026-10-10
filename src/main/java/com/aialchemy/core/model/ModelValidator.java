package com.aialchemy.core.model;

import java.util.ArrayDeque;
import java.util.Deque;

import com.aialchemy.core.ValidationContext;
import com.aialchemy.core.icon.Palette;
import com.aialchemy.core.validation.Issue;

/**
 * Server side model validation (design document section 9.4).
 *
 * <p>Everything the client will later bake passes through here first: part caps, bounds, minimum
 * edges, single axis rotations, connectivity, quad budget, grip placement and the emissive share.
 * Only models that survive are re-serialized and stored.</p>
 */
public final class ModelValidator {

	private ModelValidator() {
	}

	public static Issue.Collector validate(CuboidModel model, int tier, int globalPartCap, Palette palette) {
		Issue.Collector collector = new Issue.Collector();

		if (model == null) {
			collector.error("model.missing", "no model supplied");
			return collector;
		}

		ModelLimits limits = ModelLimits.forTier(tier).withGlobalPartCap(globalPartCap);

		if (model.parts().isEmpty()) {
			collector.error("model.empty", "model contains no parts");
			return collector;
		}

		if (model.parts().size() > limits.maxParts()) {
			collector.error("model.parts", "model has " + model.parts().size() + " parts, the tier allows " + limits.maxParts());
		}

		float min = limits.minCoordinate();
		float max = limits.maxCoordinate();

		for (int index = 0; index < model.parts().size(); index++) {
			ModelPart part = model.parts().get(index);

			if (part.degenerate()) {
				collector.error("model.part.degenerate", "part " + index + " has an edge shorter than " + ModelLimits.MIN_EDGE + " units");
			}

			if (outside(part, min, max)) {
				collector.error("model.part.bounds", "part " + index + " leaves the " + limits.extent() + " unit extent");
			}

			if (palette != null && !palette.isValidIndex(part.color())) {
				collector.error("model.part.color", "part " + index + " references palette index " + part.color() + " outside the palette");
			}

			if (part.rotation() != null) {
				if (!part.rotation().validAxis()) {
					collector.error("model.part.rotation.axis", "part " + index + " rotates around an unsupported axis");
				}

				if (!part.rotation().validAngle()) {
					collector.error("model.part.rotation.angle", "part " + index + " rotates by " + part.rotation().angle()
							+ " degrees, only steps of " + ModelLimits.ROTATION_STEP + " up to " + ModelLimits.MAX_ROTATION + " are allowed");
				}
			}
		}

		if (collector.hasErrors()) {
			return collector;
		}

		if (!connected(model)) {
			collector.error("model.connectivity", "parts do not form a single connected cluster");
		}

		if (model.quadCount() > model.parts().size() * 6) {
			collector.error("model.quads", "quad budget exceeded");
		}

		if (!model.bounds().contains(model.gripX(), model.gripY(), model.gripZ(), ModelLimits.CONNECT_TOLERANCE)) {
			collector.error("model.grip", "grip point lies outside the model bounding box");
		}

		if (model.emissiveShare() > limits.maxEmissiveShare() + 1.0E-4f) {
			collector.error("model.emissive", "emissive share " + Math.round(model.emissiveShare() * 100)
					+ "% exceeds the tier allowance of " + Math.round(limits.maxEmissiveShare() * 100) + "%");
		}

		float height = model.bounds().maxY() - model.bounds().minY();

		if (height < 2f) {
			collector.warning("model.height", "model is very flat (" + height + " units tall)");
		}

		if (model.parts().size() < 2) {
			collector.warning("model.simple", "model is a single cuboid");
		}

		return collector;
	}

	private static boolean outside(ModelPart part, float min, float max) {
		return part.fromX() < min || part.fromY() < min || part.fromZ() < min
				|| part.toX() > max || part.toY() > max || part.toZ() > max;
	}

	/** Flood fill over the "touches within 0.5 units" relation; every part must be reachable. */
	private static boolean connected(CuboidModel model) {
		int size = model.parts().size();
		boolean[] visited = new boolean[size];
		Deque<Integer> queue = new ArrayDeque<>();
		queue.add(0);
		visited[0] = true;
		int seen = 1;

		while (!queue.isEmpty()) {
			int current = queue.poll();

			for (int candidate = 0; candidate < size; candidate++) {
				if (visited[candidate]) {
					continue;
				}

				if (model.parts().get(current).touches(model.parts().get(candidate), ModelLimits.CONNECT_TOLERANCE)) {
					visited[candidate] = true;
					seen++;
					queue.add(candidate);
				}
			}
		}

		return seen == size;
	}

	/** Convenience overload used by tests and by the fallback ladder. */
	public static boolean isValid(CuboidModel model, int tier, int globalPartCap, Palette palette) {
		return !validate(model, tier, globalPartCap, palette).hasErrors();
	}

	/** Tier aware variant that also honours {@link ValidationContext}. */
	public static Issue.Collector validate(CuboidModel model, ValidationContext context, Palette palette) {
		return validate(model, context.tier(), context.maxModelParts(), palette);
	}
}