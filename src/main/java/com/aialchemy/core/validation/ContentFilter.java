package com.aialchemy.core.validation;

import java.util.List;
import java.util.Locale;

import com.aialchemy.core.ValidationContext;

/**
 * Name and lore moderation (design document sections 7.4 and 16).
 *
 * <p>The filter is intentionally boring: length caps, control character stripping and a blocklist
 * of slurs and of the awkward strings a language model produces when it tries to be edgy. A
 * {@code STRICT} server also rejects anything that looks like formatting abuse or a URL.</p>
 */
public final class ContentFilter {

	private ContentFilter() {
	}

	/** Kept deliberately small and configurable in spirit; operators add to this list. */
	public static final List<String> BLOCKLIST = List.of(
			"n1gger", "nigger", "faggot", "retard", "kys", "rape", "nazi", "hitler", "heil ",
			"child porn", "cp ", "loli", "anal ", "cum ", "penis", "vagina", "dildo",
			"asshole", "bitch", "whore", "slut");

	/**
	 * Strings a model produces when it echoes a prompt injection attempt instead of writing an item
	 * (design document section 16). Their presence means the response is malformed, so it is retried
	 * rather than shipped to a player.
	 */
	public static final List<String> INJECTION_MARKERS = List.of(
			"ignore previous", "ignore all previous", "ignore the above", "disregard previous",
			"forget your instructions", "as an ai language model", "system prompt", "you are now",
			"developer mode", "jailbreak", "prompt injection");

	public static Issue.Collector validate(String name, List<String> lore, ValidationContext context) {
		Issue.Collector collector = new Issue.Collector();

		if (name == null || name.isBlank()) {
			collector.error("text.name.missing", "item name is empty");
		} else {
			if (name.length() > context.maxNameLength()) {
				collector.error("text.name.length", "item name exceeds " + context.maxNameLength() + " characters");
			}

			if (containsIllegalCharacters(name, context.contentFilter())) {
				collector.error("text.name.characters", "item name contains control or formatting characters");
			}
		}

		if (lore != null) {
			if (lore.size() > context.maxLoreLines()) {
				collector.error("text.lore.lines", "lore exceeds " + context.maxLoreLines() + " lines");
			}

			for (int index = 0; index < lore.size(); index++) {
				String line = lore.get(index);

				if (line == null) {
					continue;
				}

				if (line.length() > context.maxLoreLength()) {
					collector.error("text.lore.length", "lore line " + index + " exceeds " + context.maxLoreLength() + " characters");
				}

				if (containsIllegalCharacters(line, context.contentFilter())) {
					collector.error("text.lore.characters", "lore line " + index + " contains control or formatting characters");
				}
			}
		}

		if (context.contentFilter() == ValidationContext.FilterLevel.OFF) {
			return withoutBlocklistErrors(collector, context);
		}

		String haystack = ((name == null ? "" : name) + " " + (lore == null ? "" : String.join(" ", lore)))
				.toLowerCase(Locale.ROOT);

		for (String banned : BLOCKLIST) {
			if (haystack.contains(banned)) {
				collector.error("text.blocklist", "generated text matched the content blocklist");
				break;
			}
		}

		for (String marker : INJECTION_MARKERS) {
			if (haystack.contains(marker)) {
				collector.error("text.injection", "generated text echoed an instruction from the prompt");
				break;
			}
		}

		if (context.contentFilter() == ValidationContext.FilterLevel.STRICT) {
			if (haystack.contains("http://") || haystack.contains("https://") || haystack.contains("www.")) {
				collector.error("text.url", "generated text contains a URL");
			}

			if (haystack.contains("§")) {
				collector.error("text.formatting", "generated text uses raw formatting codes");
			}
		}

		return withoutBlocklistErrors(collector, context);
	}

	private static boolean containsIllegalCharacters(String value, ValidationContext.FilterLevel level) {
		if (level == ValidationContext.FilterLevel.OFF) {
			return false;
		}

		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);

			if (character < 0x20 || character == 0x7F || character == 0xA7) {
				return true;
			}
		}

		return false;
	}

	private static Issue.Collector withoutBlocklistErrors(Issue.Collector collector, ValidationContext context) {
		if (context.contentFilter() != ValidationContext.FilterLevel.OFF) {
			return collector;
		}

		Issue.Collector filtered = new Issue.Collector();

		for (Issue issue : collector.issues()) {
			if (issue.code().equals("text.blocklist") || issue.code().equals("text.url")
					|| issue.code().equals("text.formatting") || issue.code().endsWith(".characters")) {
				continue;
			}

			if (issue.severity() == Issue.Severity.ERROR) {
				filtered.error(issue.code(), issue.message());
			} else {
				filtered.warning(issue.code(), issue.message());
			}
		}

		return filtered;
	}
}
