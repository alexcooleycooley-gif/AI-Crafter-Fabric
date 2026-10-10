package com.aialchemy.core;

import java.util.Locale;
import java.util.Optional;

/**
 * The fixed set of pre-registered "shell" item types. Minecraft freezes its item registry at
 * startup, so every AI generated item is one of these shells carrying an {@code AlchemyData}
 * component that describes its real identity.
 *
 * <p>See design document section 5.1.</p>
 */
public enum Shell {
	WEAPON("weapon", "alchemy_weapon", true, false),
	TOOL("tool", "alchemy_tool", true, false),
	ARMOR_HEAD("armor_head", "alchemy_armor_head", true, true),
	ARMOR_CHEST("armor_chest", "alchemy_armor_chest", true, true),
	ARMOR_LEGS("armor_legs", "alchemy_armor_legs", true, true),
	ARMOR_FEET("armor_feet", "alchemy_armor_feet", true, true),
	FOOD("food", "alchemy_food", false, false),
	TRINKET("trinket", "alchemy_trinket", false, false),
	BLOCK("block", "alchemy_block", false, false);

	private final String jsonName;
	private final String itemPath;
	private final boolean damageable;
	private final boolean armor;

	Shell(String jsonName, String itemPath, boolean damageable, boolean armor) {
		this.jsonName = jsonName;
		this.itemPath = itemPath;
		this.damageable = damageable;
		this.armor = armor;
	}

	/** Name used inside the AI JSON contract, e.g. {@code "armor_head"}. */
	public String jsonName() {
		return this.jsonName;
	}

	/** Registry path of the shell item, e.g. {@code "alchemy_armor_head"}. */
	public String itemPath() {
		return this.itemPath;
	}

	/** Whether the shell item is registered with a max damage (durability) component. */
	public boolean damageable() {
		return this.damageable;
	}

	public boolean armor() {
		return this.armor;
	}

	/** True when the shell turns into a wearable item. */
	public boolean wearable() {
		return this.armor;
	}

	/** True when damage and attack speed make sense for this shell. */
	public boolean usesAttackStats() {
		return this == WEAPON || this == TOOL || this == TRINKET;
	}

	/** True when armour and armour toughness make sense for this shell. */
	public boolean usesArmourStats() {
		return this.armor;
	}

	/** Stable lowercase key used for asset paths, e.g. {@code armor_head}. */
	public String assetKey() {
		return this.jsonName;
	}

	public static Optional<Shell> byJsonName(String name) {
		if (name == null) {
			return Optional.empty();
		}
		String normalized = name.trim().toLowerCase(Locale.ROOT);
		for (Shell shell : values()) {
			if (shell.jsonName.equals(normalized) || shell.itemPath.equals(normalized) || shell.name().equalsIgnoreCase(normalized)) {
				return Optional.of(shell);
			}
		}
		return Optional.empty();
	}

	/** Comma separated list of the accepted contract names, used inside prompts. */
	public static String allowedNames() {
		StringBuilder builder = new StringBuilder();
		for (Shell shell : values()) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(shell.jsonName);
		}
		return builder.toString();
	}
}
