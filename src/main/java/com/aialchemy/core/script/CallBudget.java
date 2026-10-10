package com.aialchemy.core.script;

/**
 * Per call counters shared by the sandbox and the host API. Every exposed function goes through
 * here, so a script can never spawn more entities, emit more particles or make more API calls than
 * its budget allows, no matter what it tries.
 */
public final class CallBudget {

	private final ScriptLimits limits;
	private final ScriptAbort.Reason[] firstAbort = new ScriptAbort.Reason[1];

	private int apiCalls;
	private int entitySpawns;
	private int particles;

	public CallBudget(ScriptLimits limits) {
		this.limits = limits;
	}

	public ScriptLimits limits() {
		return this.limits;
	}

	/** Records an API call, aborting the script when the budget is exhausted. */
	public void consumeApiCall() {
		if (++this.apiCalls > this.limits.apiCallsPerCall()) {
			throw abort(ScriptAbort.Reason.API_CALLS);
		}
	}

	public void consumeEntitySpawn() {
		if (++this.entitySpawns > this.limits.entitySpawnsPerCall()) {
			throw abort(ScriptAbort.Reason.ENTITY_SPAWNS);
		}
	}

	public void consumeParticles(int count) {
		this.particles += Math.max(0, count);

		if (this.particles > this.limits.particlesPerCall()) {
			throw abort(ScriptAbort.Reason.PARTICLES);
		}
	}

	public int apiCalls() {
		return this.apiCalls;
	}

	public int entitySpawns() {
		return this.entitySpawns;
	}

	public int particles() {
		return this.particles;
	}

	/** The first budget the script blew, kept so the caller can log a precise reason. */
	public ScriptAbort.Reason firstAbort() {
		return this.firstAbort[0];
	}

	private ScriptAbort abort(ScriptAbort.Reason reason) {
		if (this.firstAbort[0] == null) {
			this.firstAbort[0] = reason;
		}

		return new ScriptAbort(reason);
	}
}