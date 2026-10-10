package com.aialchemy.core.model;

/**
 * Per tier geometry budgets (design document section 9.4). Tier 1 items are limited to a normal
 * 16 unit block footprint, higher tiers may spill a little outside it.
 */
public record ModelLimits(int maxParts, float extent, float maxEmissiveShare) {

	private static final ModelLimits[] BY_TIER = {
			new ModelLimits(12, 16f, 0f),
			new ModelLimits(20, 16f, 0.15f),
			new ModelLimits(32, 20f, 0.25f),
			new ModelLimits(48, 24f, 0.25f),
	};

	/** Smallest allowed edge length, so flat or degenerate boxes are rejected. */
	public static final float MIN_EDGE = 0.5f;
	/** Largest allowed single axis rotation, matching vanilla model limits. */
	public static final float MAX_ROTATION = 45f;
	/** Rotation granularity; anything finer is rejected rather than silently rounded. */
	public static final float ROTATION_STEP = 22.5f;
	/** How close two parts must be to count as connected. */
	public static final float CONNECT_TOLERANCE = 0.5f;
	/** Idle effect and pattern presets are the only ones allowed. */
	public static final float MIN_DISPLAY_SCALE = 0.5f;
	public static final float MAX_DISPLAY_SCALE = 1.5f;

	public static ModelLimits forTier(int tier) {
		int index = Math.min(Math.max(tier, 1), BY_TIER.length) - 1;
		return BY_TIER[index];
	}

	/** Applies the server wide {@code max_model_parts} cap on top of the tier limit. */
	public ModelLimits withGlobalPartCap(int globalCap) {
		if (globalCap <= 0) {
			return this;
		}

		return new ModelLimits(Math.min(this.maxParts, globalCap), this.extent, this.maxEmissiveShare);
	}

	/** Lowest allowed coordinate on any axis. */
	public float minCoordinate() {
		return -((this.extent - 16f) / 2f);
	}

	/** Highest allowed coordinate on any axis. */
	public float maxCoordinate() {
		return 16f + ((this.extent - 16f) / 2f);
	}
}