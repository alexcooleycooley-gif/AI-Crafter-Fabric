package com.aialchemy.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.aialchemy.AiAlchemy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The mod's packets (design document section 12).
 *
 * <p>Two deliberate omissions from the design's packet list:</p>
 * <ul>
 *   <li>{@code TransmuteRequest} does <em>not</em> carry the grid contents. The server reads the
 *       real slots from the open menu, so a modified client cannot forge an ingredient list.</li>
 *   <li>Lists travel as newline separated strings, which keeps the codec surface tiny and removes a
 *       whole class of deserialisation edge cases.</li>
 * </ul>
 */
public final class AlchemyPayloads {

	private AlchemyPayloads() {
	}

	public static final int MAX_ASSET_BYTES = 1 << 20;

	/** C2S: the player pressed Transmute. */
	public record TransmuteRequest(boolean forceAi) implements CustomPacketPayload {

		public static final Identifier ID = AiAlchemy.id("transmute_request");
		public static final Type<TransmuteRequest> TYPE = new Type<>(ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, TransmuteRequest> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, TransmuteRequest::forceAi,
				TransmuteRequest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** S2C: the outcome of a transmute attempt. */
	public record TransmuteResult(Status status, String message, String itemId, String detail) implements CustomPacketPayload {

		public static final Identifier ID = AiAlchemy.id("transmute_result");
		public static final Type<TransmuteResult> TYPE = new Type<>(ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, TransmuteResult> CODEC = StreamCodec.composite(
					ByteBufCodecs.VAR_INT, payload -> payload.status().ordinal(),
					ByteBufCodecs.STRING_UTF8, TransmuteResult::message,
					ByteBufCodecs.STRING_UTF8, TransmuteResult::itemId,
					ByteBufCodecs.stringUtf8(512), TransmuteResult::detail,
					(status, message, itemId, detail) -> new TransmuteResult(Status.values()[status], message, itemId, detail));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The statuses shown on the crafting screen's status line. */
	public enum Status {
		PENDING,
		CACHE_HIT,
		SUCCESS,
		FAILED,
		RATE_LIMITED,
		OFFLINE
	}

	/** S2C: one 16x16 icon, roughly 300 bytes to 1 KB. */
	public record IconSync(String iconId, byte[] png) implements CustomPacketPayload {

		public static final Identifier ID = AiAlchemy.id("icon_sync");
		public static final Type<IconSync> TYPE = new Type<>(ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, IconSync> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, IconSync::iconId,
				ByteBufCodecs.byteArray(MAX_ASSET_BYTES), IconSync::png,
				IconSync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** S2C: one validated cuboid model, typically 1 to 4 KB. */
	public record ModelSync(String modelId, String json) implements CustomPacketPayload {

		public static final Identifier ID = AiAlchemy.id("model_sync");
		public static final Type<ModelSync> TYPE = new Type<>(ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, ModelSync> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, ModelSync::modelId,
				ByteBufCodecs.stringUtf8(MAX_ASSET_BYTES), ModelSync::json,
				ModelSync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** C2S: the client is missing assets and asks for them. */
	public record AssetRequest(String iconIds, String modelIds) implements CustomPacketPayload {

		public static final Identifier ID = AiAlchemy.id("asset_request");
		public static final Type<AssetRequest> TYPE = new Type<>(ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, AssetRequest> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(16_384), AssetRequest::iconIds,
				ByteBufCodecs.stringUtf8(16_384), AssetRequest::modelIds,
				AssetRequest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public List<String> icons() {
			return split(this.iconIds);
		}

		public List<String> models() {
			return split(this.modelIds);
		}
	}

	/** S2C: the player's discovery journal. */
	public record JournalSync(String entries) implements CustomPacketPayload {

		public static final Identifier ID = AiAlchemy.id("journal_sync");
		public static final Type<JournalSync> TYPE = new Type<>(ID);
		public static final StreamCodec<RegistryFriendlyByteBuf, JournalSync> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(65_536), JournalSync::entries,
				JournalSync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		/** Each entry is {@code recipeKey\tdisplayName}. */
		public List<String> lines() {
			return split(this.entries);
		}
	}

	public static String join(List<String> values) {
		return String.join("\n", values);
	}

	public static List<String> split(String value) {
		if (value == null || value.isEmpty()) {
			return List.of();
		}

		return new ArrayList<>(Arrays.asList(value.split("\n")));
	}
}
