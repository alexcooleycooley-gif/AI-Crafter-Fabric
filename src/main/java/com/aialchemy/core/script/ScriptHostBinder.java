package com.aialchemy.core.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;

/**
 * Installs the exposed API into a fresh script environment. The Minecraft side implements this with
 * thin validating wrappers around the live player, world and item; the test suite uses a mock.
 *
 * <p>Language level sandboxing (which libraries exist, which globals are removed, the instruction,
 * wall clock and memory budgets) is handled by {@link ScriptSandbox} and cannot be influenced by an
 * implementation of this interface.</p>
 */
public interface ScriptHostBinder {

	/**
	 * @param environment the sandboxed globals for this single call
	 * @param budget      per call counters; every API call must consume from it
	 * @param context     read-only information about the triggering hook
	 */
	void bind(Globals environment, CallBudget budget, ScriptCallContext context);

	/**
	 * A binder with no world access at all, used by the validation dry run: a script that loops
	 * forever or blows its memory budget is caught there, without any effect reaching a world.
	 */
	ScriptHostBinder NONE = (environment, budget, context) ->
			attach(environment, new LuaTable(), new LuaTable(), new LuaTable());

	/** Convenience helper for binders: attaches the three standard API tables. */
	static void attach(Globals environment, LuaTable player, LuaTable world, LuaTable util) {
		environment.set("player", player);
		environment.set("world", world);
		environment.set("util", util);
	}
}
