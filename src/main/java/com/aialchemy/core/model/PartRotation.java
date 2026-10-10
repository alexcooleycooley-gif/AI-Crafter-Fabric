package com.aialchemy.core.model;

import java.util.Locale;

/**
 * A single axis rotation applied to one cuboid. Vanilla allows {@code x}, {@code y} or {@code z}
 * with a maximum of 45 degrees, so anything else is rejected by the validator.
 */
public record PartRotation(char axis, float angle, float originX, float originY, float originZ) {

	public static PartRotation of(String axis, float angle, float originX, float originY, float originZ) {
		char normalized = axis == null || axis.isBlank() ? 'y' : axis.trim().toLowerCase(Locale.ROOT).charAt(0);
		return new PartRotation(normalized, angle, originX, originY, originZ);
	}

	public boolean validAxis() {
		return this.axis == 'x' || this.axis == 'y' || this.axis == 'z';
	}

	public boolean validAngle() {
		if (Math.abs(this.angle) > ModelLimits.MAX_ROTATION) {
			return false;
		}

		float steps = this.angle / ModelLimits.ROTATION_STEP;
		return Math.abs(steps - Math.round(steps)) < 1.0E-4f;
	}
}