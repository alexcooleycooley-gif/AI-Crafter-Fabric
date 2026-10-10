package com.aialchemy.core.script;

import java.util.Set;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.Prototype;

/**
 * A script compiled exactly once. Every call builds a fresh {@link Globals} from this shared
 * prototype, so scripts can never leak state between players while still reusing the parse/compile
 * work (design document section 8.2).
 */
public final class CompiledScript {

	private final String scriptId;
	private final String source;
	private final Prototype prototype;
	private final Set<ScriptHook> hooks;

	CompiledScript(String scriptId, String source, Prototype prototype, Set<ScriptHook> hooks) {
		this.scriptId = scriptId;
		this.source = source;
		this.prototype = prototype;
		this.hooks = Set.copyOf(hooks);
	}

	public String scriptId() {
		return this.scriptId;
	}

	public String source() {
		return this.source;
	}

	public Set<ScriptHook> hooks() {
		return this.hooks;
	}

	public boolean defines(ScriptHook hook) {
		return this.hooks.contains(hook);
	}

	/** Fresh closure bound to a fresh environment. */
	public LuaClosure newClosure(Globals environment) {
		return new LuaClosure(this.prototype, environment);
	}
}