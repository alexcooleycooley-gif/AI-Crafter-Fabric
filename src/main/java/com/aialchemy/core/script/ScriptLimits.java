package com.aialchemy.core.script;

/**
 * Resource limits applied to every script call (design document section 8.5). The defaults are the
 * shipped ones; server owners can tighten them through the mod config.
 */
public record ScriptLimits(
		int instructionsPerCall,
		long wallClockMillis,
		long memoryBytes,
		int entitySpawnsPerCall,
		int particlesPerCall,
		int apiCallsPerCall,
		double effectRange,
		int cooldownFloorTicks,
		int tickThrottleTicks,
		int maxSourceBytes) {

	public static final ScriptLimits DEFAULTS = new ScriptLimits(
			20_000,
			// A server tick is 50 ms, so 10 ms is a fifth of it: roomy enough that a GC pause cannot
			// kill an honest script, tight enough that a hostile one cannot bloat the tick.
			10L,
			2L * 1024L * 1024L,
			4,
			64,
			100,
			32.0D,
			10,
			10,
			8_192);

	public ScriptLimits {
		instructionsPerCall = Math.max(1_000, instructionsPerCall);
		wallClockMillis = Math.max(1L, wallClockMillis);
		memoryBytes = Math.max(256L * 1024L, memoryBytes);
		entitySpawnsPerCall = Math.max(0, entitySpawnsPerCall);
		particlesPerCall = Math.max(0, particlesPerCall);
		apiCallsPerCall = Math.max(1, apiCallsPerCall);
		effectRange = Math.max(1.0D, effectRange);
		cooldownFloorTicks = Math.max(0, cooldownFloorTicks);
		tickThrottleTicks = Math.max(1, tickThrottleTicks);
		maxSourceBytes = Math.max(512, maxSourceBytes);
	}
}
