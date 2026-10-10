package com.aialchemy.generation;

import java.util.concurrent.CompletableFuture;

import com.aialchemy.core.prompt.PromptBuilder;

/**
 * Provider abstraction (design document section 7.1). Implementations must never touch the server
 * thread: the generation service always calls them from its own executor.
 */
public interface AiProvider {

	CompletableFuture<String> complete(PromptBuilder.Prompt prompt);

	/** Human readable name used in logs and in {@code /alchemy usage}. */
	String name();

	/** True when this provider can actually answer; used for the offline fallback message. */
	default boolean available() {
		return true;
	}
}