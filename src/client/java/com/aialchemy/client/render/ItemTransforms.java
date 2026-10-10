package com.aialchemy.client.render;

import org.joml.Vector3f;

import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Display transforms for generated items, in the spirit of vanilla {@code item/handheld}.
 *
 * <p>The design doc asks for "shell specific transforms plus the clamped display values". Keeping
 * them in code rather than in a JSON model means a shell can be given a distinct pose from Java
 * while the display scales still come from the validated model, clamped to 0.5 - 1.5.</p>
 */
public final class ItemTransforms {

	private ItemTransforms() {
	}

	private static final ItemTransform GUI = new ItemTransform(new Vector3f(0f, 0f, 0f), new Vector3f(0f, 0f, 0f), new Vector3f(1f, 1f, 1f));
	private static final ItemTransform GROUND = new ItemTransform(new Vector3f(0f, 0f, 0f), new Vector3f(0f, 2f, 0f), new Vector3f(0.5f, 0.5f, 0.5f));
	private static final ItemTransform FIXED = new ItemTransform(new Vector3f(0f, 0f, 0f), new Vector3f(0f, 0f, 0f), new Vector3f(1f, 1f, 1f));
	private static final ItemTransform HEAD = new ItemTransform(new Vector3f(0f, 180f, 0f), new Vector3f(0f, 13f, 7f), new Vector3f(1f, 1f, 1f));
	private static final ItemTransform THIRD_RIGHT = new ItemTransform(new Vector3f(0f, -90f, 55f), new Vector3f(0f, 4f, 0.5f), new Vector3f(0.85f, 0.85f, 0.85f));
	private static final ItemTransform THIRD_LEFT = new ItemTransform(new Vector3f(0f, 90f, -55f), new Vector3f(0f, 4f, 0.5f), new Vector3f(0.85f, 0.85f, 0.85f));
	private static final ItemTransform FIRST_RIGHT = new ItemTransform(new Vector3f(0f, -90f, 25f), new Vector3f(1.13f, 3.2f, 1.13f), new Vector3f(0.68f, 0.68f, 0.68f));
	private static final ItemTransform FIRST_LEFT = new ItemTransform(new Vector3f(0f, 90f, -25f), new Vector3f(-1.13f, 3.2f, 1.13f), new Vector3f(0.68f, 0.68f, 0.68f));

	/** The vanilla transform for a context, with the model's own scale folded in. */
	public static ItemTransform forContext(ItemDisplayContext context, float scale) {
		ItemTransform base = switch (context) {
			case GUI, NONE -> GUI;
			case GROUND -> GROUND;
			case FIXED -> FIXED;
			case HEAD -> HEAD;
			case THIRD_PERSON_RIGHT_HAND -> THIRD_RIGHT;
			case THIRD_PERSON_LEFT_HAND -> THIRD_LEFT;
			case FIRST_PERSON_RIGHT_HAND -> FIRST_RIGHT;
			case FIRST_PERSON_LEFT_HAND -> FIRST_LEFT;
			default -> GUI;
		};

		if (Math.abs(scale - 1f) < 0.001f) {
			return base;
		}

		Vector3f scaled = new Vector3f(base.scale()).mul(scale);
		return new ItemTransform(new Vector3f(base.rotation()), new Vector3f(base.translation()), scaled);
	}

	/** Scale to use per display context, from the validated per item display block. */
	public static float scaleForContext(ItemDisplayContext context, float handScale, float groundScale, float frameScale) {
		return switch (context) {
			case GUI, NONE -> 1f;
			case GROUND -> groundScale;
			case FIXED -> frameScale;
			case HEAD -> 1f;
			default -> handScale;
		};
	}
}
