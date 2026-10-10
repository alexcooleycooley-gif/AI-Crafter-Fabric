package com.aialchemy.client;

import com.aialchemy.AiAlchemy;
import com.aialchemy.client.render.AlchemyModelPlugin;
import com.aialchemy.client.ui.CraftingScreenHook;
import com.aialchemy.network.AlchemyPayloads;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * Client entrypoint (design document section 12, client side).
 *
 * <p>The client is deliberately thin: it receives small icon and model payloads, caches them on
 * disk, owns the only rendering code in the mod, and asks the server for anything it is missing.</p>
 */
public class AiAlchemyClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		AlchemyModelPlugin.register();

		ClientPlayNetworking.registerGlobalReceiver(AlchemyPayloads.IconSync.TYPE, (payload, context) ->
				context.client().execute(() -> ClientAssets.get().receiveIcon(payload.iconId(), payload.png())));

		ClientPlayNetworking.registerGlobalReceiver(AlchemyPayloads.ModelSync.TYPE, (payload, context) ->
				context.client().execute(() -> ClientAssets.get().receiveModel(payload.modelId(), payload.json())));

		ClientPlayNetworking.registerGlobalReceiver(AlchemyPayloads.TransmuteResult.TYPE, (payload, context) ->
				context.client().execute(() -> CraftingScreenHook.onResult(payload)));

		ClientPlayNetworking.registerGlobalReceiver(AlchemyPayloads.JournalSync.TYPE, (payload, context) ->
				context.client().execute(() -> {
					if (context.client().player != null && !payload.lines().isEmpty()) {
						context.client().player.displayClientMessage(
								Component.translatable("status.ai_alchemy.journal_loaded", payload.lines().size()), true);
					}
				}));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientAssets.get().loadDiskCache());

		// Texture uploads and asset re-requests happen here, on the client thread, never in a packet
		// handler and never on the render hot path.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientAssets assets = ClientAssets.get();
			assets.flush();

			java.util.List<String> missingIcons = assets.drainAssetRequests();

			if (!missingIcons.isEmpty() && ClientPlayNetworking.canSend(AlchemyPayloads.AssetRequest.TYPE)) {
				ClientPlayNetworking.send(new AlchemyPayloads.AssetRequest(
						AlchemyPayloads.join(missingIcons), ""));
			}
		});

		CraftingScreenHook.register();
		AiAlchemy.LOGGER.info("AI Alchemy client ready");
	}
}