package com.aialchemy.core.script;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The only functions a generated script may define (design document section 8.3). The first
 * parameter of every hook is the entity the hook fired for, which the sandbox also exposes as the
 * {@code player} global.
 */
public enum ScriptHook {
	ON_USE("on_use", 2, "Right-click with the item"),
	ON_HIT("on_hit", 2, "Melee hit landed by the holder"),
	ON_MINE("on_mine", 3, "Block broken by the holder"),
	ON_TICK("on_tick", 2, "Throttled while held or worn"),
	ON_EQUIP("on_equip", 1, "Item equipped"),
	ON_UNEQUIP("on_unequip", 1, "Item unequipped"),
	ON_EAT("on_eat", 1, "Food consumed"),
	ON_DAMAGED("on_damaged", 3, "Holder took damage");

	private final String luaName;
	private final int arity;
	private final String description;

	ScriptHook(String luaName, int arity, String description) {
		this.luaName = luaName;
		this.arity = arity;
		this.description = description;
	}

	public String luaName() {
		return this.luaName;
	}

	public int arity() {
		return this.arity;
	}

	public String description() {
		return this.description;
	}

	public static Optional<ScriptHook> byLuaName(String name) {
		if (name == null) {
			return Optional.empty();
		}

		for (ScriptHook hook : values()) {
			if (hook.luaName.equals(name)) {
				return Optional.of(hook);
			}
		}

		return Optional.empty();
	}

	public static Set<String> luaNames() {
		Set<String> names = new LinkedHashSet<>();

		for (ScriptHook hook : values()) {
			names.add(hook.luaName);
		}

		return names;
	}

	/** Documentation block spliced into the system prompt, so the AI knows the exact names. */
	public static String promptReference() {
		StringBuilder builder = new StringBuilder();

		for (ScriptHook hook : values()) {
			builder.append("- ").append(hook.luaName).append('(').append(parameters(hook)).append("): ")
					.append(hook.description).append('\n');
		}

		return builder.toString();
	}

	private static String parameters(ScriptHook hook) {
		return switch (hook) {
			case ON_USE, ON_TICK -> "player, world";
			case ON_HIT -> "attacker, target";
			case ON_MINE -> "player, world, pos";
			case ON_DAMAGED -> "player, source, amount";
			default -> "player";
		};
	}
}