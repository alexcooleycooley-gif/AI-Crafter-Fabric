package com.aialchemy.core.script;

/** Read-only data handed to the host binder for one script call. */
public record ScriptCallContext(
		String scriptId,
		ScriptHook hook,
		String itemName,
		int tier,
		long worldTick,
		long seed) {

	public ScriptCallContext {
		if (itemName == null) {
			itemName = "unknown";
		}
	}
}