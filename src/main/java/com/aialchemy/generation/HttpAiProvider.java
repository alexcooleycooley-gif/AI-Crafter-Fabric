package com.aialchemy.generation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.aialchemy.AiAlchemy;
import com.aialchemy.config.AlchemyConfig;
import com.aialchemy.core.prompt.PromptBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The hosted provider: any OpenAI compatible chat completions endpoint, including a local model
 * server (design document section 7.1).
 *
 * <p>The API key lives only here, on the server, and is never put into a packet or a log line.</p>
 */
public final class HttpAiProvider implements AiProvider {

	private final HttpClient client;
	private final String endpoint;
	private final String apiKey;
	private final String model;
	private final Duration timeout;

	public HttpAiProvider(AlchemyConfig config) {
		this.endpoint = config.endpoint;
		this.apiKey = config.apiKey;
		this.model = config.model;
		this.timeout = Duration.ofSeconds(Math.max(5, config.requestTimeoutSeconds));
		this.client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	@Override
	public String name() {
		return "hosted (" + this.endpoint + ", " + this.model + ")";
	}

	@Override
	public boolean available() {
		return this.endpoint != null && !this.endpoint.isBlank();
	}

	@Override
	public CompletableFuture<String> complete(PromptBuilder.Prompt prompt) {
		JsonObject body = new JsonObject();
		body.addProperty("model", this.model);
		body.addProperty("temperature", 0.9D);

		JsonArray messages = new JsonArray();
		messages.add(message("system", prompt.system()));
		messages.add(message("user", prompt.user()));
		body.add("messages", messages);

		JsonObject responseFormat = new JsonObject();
		responseFormat.addProperty("type", "json_object");
		body.add("response_format", responseFormat);

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(this.endpoint))
				.timeout(this.timeout)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()));

		if (this.apiKey != null && !this.apiKey.isBlank()) {
			builder.header("Authorization", "Bearer " + this.apiKey);
		}

		return this.client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					if (response.statusCode() / 100 != 2) {
						// The response body may echo the key in an error message, so only the status
						// code is logged.
						throw new IllegalStateException("provider returned HTTP " + response.statusCode());
					}

					return extractContent(response.body());
				});
	}

	static String extractContent(String responseBody) {
		JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();

		if (!root.has("choices")) {
			throw new IllegalStateException("provider response had no choices");
		}

		JsonArray choices = root.getAsJsonArray("choices");

		if (choices.isEmpty()) {
			throw new IllegalStateException("provider returned an empty choice list");
		}

		JsonObject first = choices.get(0).getAsJsonObject();

		if (!first.has("message")) {
			throw new IllegalStateException("provider response had no message");
		}

		JsonObject message = first.getAsJsonObject("message");

		if (!message.has("content")) {
			throw new IllegalStateException("provider message had no content");
		}

		return message.get("content").getAsString();
	}

	private static JsonObject message(String role, String content) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);
		message.addProperty("content", content);
		return message;
	}
}