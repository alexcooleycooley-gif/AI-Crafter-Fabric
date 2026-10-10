package com.aialchemy.generation;

import java.util.concurrent.CompletableFuture;

import com.aialchemy.core.prompt.PromptBuilder;

/**
 * Used when {@code offline_mode} is on or no endpoint is configured. It always fails, which makes
 * the generation service behave exactly like a provider outage: cache hits still work, new items
 * report a clear message instead of hanging (design document section 17).
 */
public final class OfflineAiProvider implements AiProvider {

	public static final OfflineAiProvider INSTANCE = new OfflineAiProvider();

	@Override
	public String name() {
		return "offline";
	}

	@Override
	public boolean available() {
		return false;
	}

	@Override
	public CompletableFuture<String> complete(PromptBuilder.Prompt prompt) {
		return CompletableFuture.failedFuture(new IllegalStateException("no AI provider is configured"));
	}
}