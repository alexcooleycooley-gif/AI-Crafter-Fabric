package com.aialchemy.component;

import com.aialchemy.AiAlchemy;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registration point for the mod's data components. */
public final class ModComponents {

	private ModComponents() {
	}

	/**
	 * The only custom component: small enough to travel with every stack, and synchronised so the
	 * client can render an item the instant it arrives.
	 */
	public static final DataComponentType<AlchemyData> ALCHEMY_DATA = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			AiAlchemy.id("alchemy_data"),
			DataComponentType.<AlchemyData>builder()
					.persistent(AlchemyData.CODEC)
					.networkSynchronized(AlchemyData.STREAM_CODEC)
					.build());

	public static void initialize() {
		AiAlchemy.LOGGER.debug("Registered AI Alchemy components");
	}
}