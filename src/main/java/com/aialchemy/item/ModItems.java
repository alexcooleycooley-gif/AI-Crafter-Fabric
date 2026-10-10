package com.aialchemy.item;

import java.util.EnumMap;
import java.util.Map;

import com.aialchemy.AiAlchemy;
import com.aialchemy.core.Shell;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * The shell items (design document section 5.1). These are the only item types the mod ever
 * registers; everything the AI invents is one of these plus an {@code AlchemyData} component.
 */
public final class ModItems {

	private ModItems() {
	}

	private static final Map<Shell, Item> SHELLS = new EnumMap<>(Shell.class);

	public static final Item ALCHEMY_WEAPON = register(Shell.WEAPON);
	public static final Item ALCHEMY_TOOL = register(Shell.TOOL);
	public static final Item ALCHEMY_ARMOR_HEAD = register(Shell.ARMOR_HEAD);
	public static final Item ALCHEMY_ARMOR_CHEST = register(Shell.ARMOR_CHEST);
	public static final Item ALCHEMY_ARMOR_LEGS = register(Shell.ARMOR_LEGS);
	public static final Item ALCHEMY_ARMOR_FEET = register(Shell.ARMOR_FEET);
	public static final Item ALCHEMY_FOOD = register(Shell.FOOD);
	public static final Item ALCHEMY_TRINKET = register(Shell.TRINKET);
	public static final Item ALCHEMY_BLOCK = register(Shell.BLOCK);
	public static final Item ALCHEMY_UNKNOWN = registerUnknown();

	public static void initialize() {
		AiAlchemy.LOGGER.info("Registered {} AI Alchemy shell items", SHELLS.size());
	}

	public static Item forShell(Shell shell) {
		return SHELLS.get(shell);
	}

	private static Item register(Shell shell) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AiAlchemy.id(shell.itemPath()));
		Item item = new AlchemyItem(new Item.Properties().setId(key).stacksTo(shell.damageable() ? 1 : 64), shell);
		Item registered = Registry.register(BuiltInRegistries.ITEM, key, item);
		SHELLS.put(shell, registered);
		return registered;
	}

	private static Item registerUnknown() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AiAlchemy.id("alchemy_unknown"));
		Item item = new Item(new Item.Properties().setId(key).stacksTo(1));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
