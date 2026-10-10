package com.aialchemy.server;

import java.util.List;

import com.aialchemy.AiAlchemy;
import com.aialchemy.network.AlchemyNetworking;
import com.aialchemy.network.AlchemyPayloads;
import com.aialchemy.storage.AlchemyStorage;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pushes icons and models to clients (design document section 12).
 *
 * <p>The pack carries no textures, so the only way an item can be drawn is this sync: the crafting
 * player gets the assets immediately, everyone else asks for what they are missing when they join or
 * when an unknown item shows up in the world.</p>
 */
public final class AssetSync {

	private final AlchemyStorage storage;
	private MinecraftServer server;

	public AssetSync(AlchemyStorage storage) {
		this.storage = storage;
	}

	public void attach(MinecraftServer server) {
		this.server = server;
	}

	/** Sends one item's icon and model to every connected player. */
	public void broadcastAssets(String iconId, String modelId) {
		if (this.server == null) {
			return;
		}

		for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
			sendIcon(player, iconId);
			sendModel(player, modelId);
		}
	}

	public void sendIcon(ServerPlayer player, String iconId) {
		if (iconId == null || iconId.isBlank()) {
			return;
		}

		byte[] png = this.storage.iconBytes(iconId);

		if (png == null || png.length == 0) {
			AiAlchemy.LOGGER.debug("No icon payload stored for {}", iconId);
			return;
		}

		AlchemyNetworking.send(player, new AlchemyPayloads.IconSync(iconId, png));
	}

	public void sendModel(ServerPlayer player, String modelId) {
		if (modelId == null || modelId.isBlank()) {
			return;
		}

		JsonObject model = this.storage.modelJson(modelId);

		if (model == null) {
			return;
		}

		AlchemyNetworking.send(player, new AlchemyPayloads.ModelSync(modelId, model.toString()));
	}

	/** Answers a client that is missing payloads, capped so a bad client cannot flood a server. */
	public void sendRequested(ServerPlayer player, List<String> iconIds, List<String> modelIds) {
		int sent = 0;

		for (String iconId : iconIds) {
			if (sent++ >= 64) {
				return;
			}

			sendIcon(player, iconId);
		}

		for (String modelId : modelIds) {
			if (sent++ >= 64) {
				return;
			}

			sendModel(player, modelId);
		}
	}
}