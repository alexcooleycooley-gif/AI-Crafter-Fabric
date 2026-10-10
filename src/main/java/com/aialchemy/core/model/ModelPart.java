package com.aialchemy.core.model;

/**
 * One cuboid of a generated model, expressed on the 16 unit item grid. The AI never emits vertices,
 * UVs or textures: only corners, a palette index and optional presets.
 */
public record ModelPart(
		float fromX,
		float fromY,
		float fromZ,
		float toX,
		float toY,
		float toZ,
		int color,
		Pattern pattern,
		boolean emissive,
		PartRotation rotation) {

	public ModelPart {
		// Corner order is normalized once, here, so the rest of the pipeline can rely on it.
		float minX = Math.min(fromX, toX);
		float minY = Math.min(fromY, toY);
		float minZ = Math.min(fromZ, toZ);
		float maxX = Math.max(fromX, toX);
		float maxY = Math.max(fromY, toY);
		float maxZ = Math.max(fromZ, toZ);
		fromX = minX;
		fromY = minY;
		fromZ = minZ;
		toX = maxX;
		toY = maxY;
		toZ = maxZ;

		if (pattern == null) {
			pattern = Pattern.SOLID;
		}
	}

	public float sizeX() {
		return this.toX - this.fromX;
	}

	public float sizeY() {
		return this.toY - this.fromY;
	}

	public float sizeZ() {
		return this.toZ - this.fromZ;
	}

	public boolean degenerate() {
		return sizeX() < ModelLimits.MIN_EDGE || sizeY() < ModelLimits.MIN_EDGE || sizeZ() < ModelLimits.MIN_EDGE;
	}

	/** True when the two boxes overlap once both are grown by {@code tolerance} on every axis. */
	public boolean touches(ModelPart other, float tolerance) {
		return this.fromX - tolerance <= other.toX && this.toX + tolerance >= other.fromX
				&& this.fromY - tolerance <= other.toY && this.toY + tolerance >= other.fromY
				&& this.fromZ - tolerance <= other.toZ && this.toZ + tolerance >= other.fromZ;
	}

	/** A copy with a 0.5 unit minimum edge, used by the stock template fallback. */
	public ModelPart inflated() {
		float growX = Math.max(0f, (ModelLimits.MIN_EDGE - sizeX()) / 2f);
		float growY = Math.max(0f, (ModelLimits.MIN_EDGE - sizeY()) / 2f);
		float growZ = Math.max(0f, (ModelLimits.MIN_EDGE - sizeZ()) / 2f);
		return new ModelPart(this.fromX - growX, this.fromY - growY, this.fromZ - growZ,
				this.toX + growX, this.toY + growY, this.toZ + growZ,
				this.color, this.pattern, this.emissive, this.rotation);
	}
}