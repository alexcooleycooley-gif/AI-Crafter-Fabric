package com.aialchemy.core.script;

/**
 * Thrown (usually wrapped in a {@code LuaError}) when a generated script exceeds one of its
 * budgets. Aborts are counted per item; three of them disable the script and the item falls back to
 * its stats (design document section 8.5).
 */
public class ScriptAbort extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final Reason reason;

	public ScriptAbort(Reason reason) {
		super(reason.name().toLowerCase(java.util.Locale.ROOT), null, false, false);
		this.reason = reason;
	}

	public Reason reason() {
		return this.reason;
	}

	public enum Reason {
		INSTRUCTIONS("instruction budget exhausted"),
		WALL_CLOCK("wall clock budget exhausted"),
		MEMORY("memory budget exhausted"),
		API_CALLS("too many API calls"),
		ENTITY_SPAWNS("too many entity spawns"),
		PARTICLES("too many particles"),
		RANGE("target outside the allowed range"),
		PROTECTION("blocked by world protection"),
		UNKNOWN_HOOK("hook named an unknown function"),
		RUNTIME("script raised an error");

		private final String description;

		Reason(String description) {
			this.description = description;
		}

		public String description() {
			return this.description;
		}

		/** True when the abort should count towards disabling the script. */
		public boolean countsTowardDisable() {
			return this != UNKNOWN_HOOK;
		}
	}
}