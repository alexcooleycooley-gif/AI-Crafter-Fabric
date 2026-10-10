package com.aialchemy.network;

import com.aialchemy.AiAlchemy;
import com.aialchemy.server.WorldAlchemyState;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Registers the payload types and the server side receivers. */
public final class AlchemyNetworking {

	private AlchemyNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.playC2S().register(AlchemyPayloads.TransmuteRequest.TYPE, AlchemyPayloads.TransmuteRequest.CODEC);
		PayloadTypeRegistry.playC2S().register(AlchemyPayloads.AssetRequest.TYPE, AlchemyPayloads.AssetRequest.CODEC);

		PayloadTypeRegistry.playS2C().register(AlchemyPayloads.TransmuteResult.TYPE, AlchemyPayloads.TransmuteResult.CODEC);
		PayloadTypeRegistry.playS2C().register(AlchemyPayloads.IconSync.TYPE, AlchemyPayloads.IconSync.CODEC);
		PayloadTypeRegistry.playS2C().register(AlchemyPayloads.ModelSync.TYPE, AlchemyPayloads.ModelSync.CODEC);
		PayloadTypeRegistry.playS2C().register(AlchemyPayloads.JournalSync.TYPE, AlchemyPayloads.JournalSync.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(AlchemyPayloads.TransmuteRequest.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			// Handlers may arrive on the network thread; every world mutation belongs on the server
			// thread, which also keeps the script runtime single threaded.
			context.server().execute(() -> {
				WorldAlchemyState state = AiAlchemy.state();

				if (state != null) {
					state.transmute().handleRequest(player, payload.forceAi());
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(AlchemyPayloads.AssetRequest.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				WorldAlchemyState state = AiAlchemy.state();

				if (state != null) {
					state.assets().sendRequested(player, payload.icons(), payload.models());
				}
			});
		});
	}

	public static void send(ServerPlayer player, AlchemyPayloads.JournalSync payload) {
		ServerPlayNetworking.send(player, payload);
	}

	public static void send(ServerPlayer player, AlchemyPayloads.TransmuteResult payload) {
		ServerPlayNetworking.send(player, payload);
	}

	public static void send(ServerPlayer player, AlchemyPayloads.IconSync payload) {
		ServerPlayNetworking.send(player, payload);
	}

	public static void send(ServerPlayer player, AlchemyPayloads.ModelSync payload) {
		ServerPlayNetworking.send(player, payload);
	}
}