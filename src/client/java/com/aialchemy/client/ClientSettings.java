package com.aialchemy.client;

import com.aialchemy.AiAlchemy;
import com.aialchemy.config.AlchemyConfig;

/**
 * The handful of configuration values the client half cares about.
 *
 * <p>The same mod runs on both sides, so the client reads the same {@code config/ai-alchemy.json};
 * a player who wants a cheaper view of distant dropped items can lower {@code model_render_distance}
 * locally without touching the server.</p>
 */
public final class ClientSettings {

	private static int modelRenderDistance = -1;

	private ClientSettings() {
	}

	/** Blocks beyond which a dropped item draws as its flat icon instead of its cuboid model. */
	public static int modelRenderDistance() {
		if (modelRenderDistance < 0) {
			try {
				modelRenderDistance = Math.max(4, AlchemyConfig.load().modelRenderDistance);
			} catch (RuntimeException exception) {
				AiAlchemy.LOGGER.debug("Could not read the client config, using defaults: {}", exception.getMessage());
				modelRenderDistance = 32;
			}
		}

		return modelRenderDistance;
	}

	/** Used by tests and by a config reload. */
	public static void override(int distance) {
		modelRenderDistance = distance;
	}
}