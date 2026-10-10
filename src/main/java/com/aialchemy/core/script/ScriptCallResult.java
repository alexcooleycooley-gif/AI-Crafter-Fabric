package com.aialchemy.core.script;

/**
 * Outcome of a single hook invocation. {@code ok} means the script ran inside every budget; any
 * other outcome carries the reason, which the runtime uses for logging and for the three-strikes
 * script disabling rule.
 */
public record ScriptCallResult(
		boolean ok,
		ScriptAbort.Reason reason,
		String error,
		int instructions,
		long allocatedBytes,
		long elapsedNanos,
		int apiCalls,
		boolean hookMissing) {

	public static ScriptCallResult ok(int instructions, long allocatedBytes, long elapsedNanos, int apiCalls) {
		return new ScriptCallResult(true, null, null, instructions, allocatedBytes, elapsedNanos, apiCalls, false);
	}

	public static ScriptCallResult missingHook() {
		return new ScriptCallResult(true, null, null, 0, 0L, 0L, 0, true);
	}

	public static ScriptCallResult aborted(ScriptAbort.Reason reason, String error, int instructions,
			long allocatedBytes, long elapsedNanos, int apiCalls) {
		return new ScriptCallResult(false, reason, error, instructions, allocatedBytes, elapsedNanos, apiCalls, false);
	}

	public double elapsedMillis() {
		return this.elapsedNanos / 1_000_000.0D;
	}

	public String describe() {
		if (this.hookMissing) {
			return "hook not defined";
		}

		if (this.ok) {
			return "ok (" + this.instructions + " instructions, " + Math.round(this.elapsedMillis() * 100.0D) / 100.0D + " ms)";
		}

		return "aborted: " + (this.reason == null ? "error" : this.reason.description())
				+ (this.error == null ? "" : " (" + this.error + ")");
	}
}
