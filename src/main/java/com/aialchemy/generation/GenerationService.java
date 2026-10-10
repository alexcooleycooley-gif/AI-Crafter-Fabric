package com.aialchemy.generation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import com.aialchemy.AiAlchemy;
import com.aialchemy.config.AlchemyConfig;
import com.aialchemy.core.ValidationContext;
import com.aialchemy.core.json.ItemJson;
import com.aialchemy.core.prompt.PromptBuilder;
import com.aialchemy.core.validation.ValidationPipeline;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Builds prompts, calls the provider off the server thread, and pushes the answer through the
 * validation pipeline (design document sections 7 and 17).
 *
 * <p>Failure handling mirrors the design: retry with the validation error appended to the prompt,
 * then give up and let the caller decide whether the player gets a refund message or an offline
 * response. A global semaphore keeps the number of in flight requests bounded.</p>
 */
public final class GenerationService {

	private final AlchemyConfig config;
	private final AiProvider provider;
	private final Executor executor;
	private final Semaphore concurrency;
	private final AtomicInteger requests = new AtomicInteger();
	private final AtomicInteger failures = new AtomicInteger();

	public GenerationService(AlchemyConfig config, AiProvider provider, Executor executor) {
		this.config = config;
		this.provider = provider;
		this.executor = executor;
		this.concurrency = new Semaphore(Math.max(1, config.maxConcurrentRequests));
	}

	public AiProvider provider() {
		return this.provider;
	}

	public int requestCount() {
		return this.requests.get();
	}

	public int failureCount() {
		return this.failures.get();
	}

	public boolean offline() {
		return this.config.offlineMode || !this.provider.available();
	}

	/** Everything the pipeline needs that comes from the crafting grid rather than the model. */
	public record Request(
			String recipeKey,
			String itemId,
			String ingredientList,
			List<Integer> ingredientColors,
			int tier,
			String discoverer,
			long createdAt,
			long seed) {
	}

	public record Outcome(com.aialchemy.core.GeneratedItem item, String error, boolean offline) {

		public boolean ok() {
			return this.item != null;
		}
	}

	public CompletableFuture<Outcome> generate(Request request) {
		if (offline()) {
			return CompletableFuture.completedFuture(
					new Outcome(null, "offline mode: only cached recipes can be crafted", true));
		}

		return CompletableFuture.supplyAsync(() -> {
			try {
				this.concurrency.acquire();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return new Outcome(null, "request interrupted", false);
			}

			try {
				return attempt(request);
			} finally {
				this.concurrency.release();
			}
		}, this.executor);
	}

	private Outcome attempt(Request request) {
		this.requests.incrementAndGet();
		ValidationContext context = this.config.validationContext(request.tier());
		PromptBuilder.Prompt prompt = PromptBuilder.fullPrompt(request.ingredientList(), request.tier(), context, this.config.scriptLimits());
		String validationError = null;
		int attempts = Math.max(0, this.config.retries) + 1;

		for (int attempt = 0; attempt < attempts; attempt++) {
			PromptBuilder.Prompt currentPrompt = validationError == null ? prompt : prompt.withRetryContext(validationError);

			try {
				String raw = this.provider.complete(currentPrompt).join();
				ValidationPipeline.Input input = new ValidationPipeline.Input(
						request.recipeKey(), request.itemId(), request.discoverer(), request.createdAt(),
						request.seed(), request.ingredientColors(), context, this.config.scriptLimits());
				ValidationPipeline.Result result = ValidationPipeline.process(raw, input);

				if (result.ok()) {
					logNoteworthy(result);
					return new Outcome(result.item(), null, false);
				}

				validationError = result.fatalError();
				AiAlchemy.LOGGER.warn("Rejected AI response for {} (attempt {}): {}", request.itemId(), attempt + 1, validationError);
			} catch (RuntimeException exception) {
				// CompletableFuture.join wraps provider failures; unwrap for a readable message.
				Throwable cause = exception.getCause() == null ? exception : exception.getCause();
				validationError = cause.getClass().getSimpleName() + ": " + cause.getMessage();
				AiAlchemy.LOGGER.warn("AI provider call failed for {} (attempt {}): {}", request.itemId(), attempt + 1, validationError);
			}

			if (attempt + 1 < attempts) {
				sleepBackoff(attempt);
			}
		}

		this.failures.incrementAndGet();
		return new Outcome(null, validationError == null ? "generation failed" : validationError, false);
	}

	/** Two pass mode: ask for the concept first, then for the model and script. */
	public CompletableFuture<String> conceptPass(String ingredientList, int tier) {
		ValidationContext context = this.config.validationContext(tier);
		return this.provider.complete(PromptBuilder.conceptPrompt(ingredientList, tier, context));
	}

	public CompletableFuture<String> detailPass(String conceptJson, int tier) {
		ValidationContext context = this.config.validationContext(tier);

		try {
			JsonObject concept = JsonParser.parseString(conceptJson).getAsJsonObject();
			concept.addProperty("lua", "");
			JsonObject model = new JsonObject();
			concept.add("model", model);
			return this.provider.complete(PromptBuilder.detailPrompt(concept.toString(), tier, context, this.config.scriptLimits()));
		} catch (RuntimeException exception) {
			return CompletableFuture.failedFuture(exception);
		}
	}

	private void logNoteworthy(ValidationPipeline.Result result) {
		if (result.fallbackIcon() || result.fallbackModel() || result.fallbackScript()) {
			AiAlchemy.LOGGER.info("Item {} used fallbacks (icon={}, model={}, script={})",
					result.item().itemId(), result.fallbackIcon(), result.fallbackModel(), result.fallbackScript());
		}

		for (com.aialchemy.core.validation.Issue issue : result.issues().issues()) {
			if (issue.severity() == com.aialchemy.core.validation.Issue.Severity.WARNING) {
				AiAlchemy.LOGGER.debug("Validation note for {}: {} - {}", result.item().itemId(), issue.code(), issue.message());
			}
		}
	}

	private static void sleepBackoff(int attempt) {
		try {
			Thread.sleep(250L * (attempt + 1));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	/** Serialised item, used by tests and by the {@code /alchemy inspect} command. */
	public static String describe(com.aialchemy.core.GeneratedItem item) {
		return ItemJson.toJson(item).toString();
	}
}