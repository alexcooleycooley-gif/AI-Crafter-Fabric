package com.aialchemy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aialchemy.component.ModComponents;
import com.aialchemy.item.ModItems;
import com.aialchemy.network.AlchemyNetworking;
import com.aialchemy.server.AlchemyCommands;
import com.aialchemy.server.ServerHooks;
import com.aialchemy.server.WorldAlchemyState;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;

/**
 * AI Alchemy: items invented by an AI model, crafted from any combination of ingredients.
 *
 * <p>The mod is server authoritative. The server owns the API key, the recipe cache and the
 * sandboxed script runtime; clients only receive the small icon and model payloads needed to draw
 * an item (design document section 14).</p>
 */
public class AiAlchemy implements ModInitializer {

	public static final String MOD_ID = "ai_alchemy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile WorldAlchemyState worldState;

	@Override
	public void onInitialize() {
		ModComponents.initialize();
		ModItems.initialize();
		AlchemyNetworking.registerPayloads();
		AlchemyNetworking.registerServerReceivers();
		AlchemyCommands.register();
		ServerHooks.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			worldState = WorldAlchemyState.load(server);
			LOGGER.info("AI Alchemy ready: {} cached recipes, {} disabled scripts",
					worldState.storage().recipeCount(), worldState.storage().bannedCount());
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WorldAlchemyState state = worldState;

			if (state != null) {
				state.shutdown();
			}

			worldState = null;
		});

		LOGGER.info("AI Alchemy initialised");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** The per world state, or null when no server is running (client side, or during startup). */
	public static WorldAlchemyState state() {
		return worldState;
	}
}