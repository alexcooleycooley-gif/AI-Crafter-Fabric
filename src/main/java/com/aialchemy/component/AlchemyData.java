package com.aialchemy.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * The identity of a generated item, carried on the {@code ItemStack} itself (design document
 * section 5.3).
 *
 * <p>Minecraft freezes its registries at startup, so every AI generated item is really one of the
 * pre-registered shell items plus this component. The component stays deliberately small: the
 * script, the icon and the model live in world storage and are referenced by id, which keeps
 * stacks light on the network.</p>
 */
public record AlchemyData(
		String itemId,
		String scriptId,
		String iconId,
		String modelId,
		String name,
		int tier,
		String discoverer) {

	public static final Codec<AlchemyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("item_id").forGetter(AlchemyData::itemId),
			Codec.STRING.optionalFieldOf("script_id", "").forGetter(AlchemyData::scriptId),
			Codec.STRING.fieldOf("icon_id").forGetter(AlchemyData::iconId),
			Codec.STRING.optionalFieldOf("model_id", "").forGetter(AlchemyData::modelId),
			Codec.STRING.optionalFieldOf("name", "").forGetter(AlchemyData::name),
			Codec.INT.optionalFieldOf("tier", 1).forGetter(AlchemyData::tier),
			Codec.STRING.optionalFieldOf("discoverer", "").forGetter(AlchemyData::discoverer)
	).apply(instance, AlchemyData::new));

	public static final StreamCodec<ByteBuf, AlchemyData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, AlchemyData::itemId,
			ByteBufCodecs.STRING_UTF8, AlchemyData::scriptId,
			ByteBufCodecs.STRING_UTF8, AlchemyData::iconId,
			ByteBufCodecs.STRING_UTF8, AlchemyData::modelId,
			ByteBufCodecs.STRING_UTF8, AlchemyData::name,
			ByteBufCodecs.VAR_INT, AlchemyData::tier,
			ByteBufCodecs.STRING_UTF8, AlchemyData::discoverer,
			AlchemyData::new);

	public boolean hasScript() {
		return this.scriptId != null && !this.scriptId.isEmpty();
	}

	public boolean hasModel() {
		return this.modelId != null && !this.modelId.isEmpty();
	}

	public String discovererOrUnknown() {
		return this.discoverer == null || this.discoverer.isEmpty() ? "unknown" : this.discoverer;
	}

	/** Registry id of an icon texture, used by the client renderer. */
	public static Identifier iconTexture(String iconId) {
		return Identifier.fromNamespaceAndPath("ai_alchemy", "dynamic/icon/" + iconId);
	}

	/** Registry id of the shared one pixel white texture used for coloured model geometry. */
	public static Identifier whiteTexture() {
		return Identifier.fromNamespaceAndPath("ai_alchemy", "dynamic/white");
	}
}