package com.aialchemy.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.aialchemy.config.AlchemyConfig;

/**
 * Cooldown and daily quota handling (design document section 13). State is in memory only: a server
 * restart resetting the daily counters is the friendlier behaviour, and it keeps the world files
 * free of per player bookkeeping.
 */
public final class RateLimits {

	private final Map<UUID, Long> lastRequest = new HashMap<>();
	private final Map<UUID, Integer> dailyCount = new HashMap<>();
	private final Map<UUID, Long> dailyWindowStart = new HashMap<>();

	private final AlchemyConfig config;

	public RateLimits(AlchemyConfig config) {
		this.config = config;
	}

	public enum Verdict {
		ALLOWED,
		COOLDOWN,
		DAILY_LIMIT
	}

	public Verdict check(UUID playerId, long nowMillis) {
		Long last = this.lastRequest.get(playerId);

		if (last != null && nowMillis - last < this.config.playerCooldownSeconds * 1000L) {
			return Verdict.COOLDOWN;
		}

		rollWindow(playerId, nowMillis);

		if (this.dailyCount.getOrDefault(playerId, 0) >= this.config.dailyLimitPerPlayer) {
			return Verdict.DAILY_LIMIT;
		}

		return Verdict.ALLOWED;
	}

	/** Records a successful generation; cache hits deliberately do not count against the quota. */
	public void record(UUID playerId, long nowMillis) {
		rollWindow(playerId, nowMillis);
		this.lastRequest.put(playerId, nowMillis);
		this.dailyCount.merge(playerId, 1, Integer::sum);
	}

	public int remainingToday(UUID playerId) {
		return Math.max(0, this.config.dailyLimitPerPlayer - this.dailyCount.getOrDefault(playerId, 0));
	}

	public int usedToday(UUID playerId) {
		return this.dailyCount.getOrDefault(playerId, 0);
	}

	public void forget(UUID playerId) {
		this.lastRequest.remove(playerId);
	}

	private void rollWindow(UUID playerId, long nowMillis) {
		long day = nowMillis / 86_400_000L;
		Long start = this.dailyWindowStart.get(playerId);

		if (start == null || start != day) {
			this.dailyWindowStart.put(playerId, day);
			this.dailyCount.put(playerId, 0);
		}
	}
}