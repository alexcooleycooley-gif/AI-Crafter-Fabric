package com.aialchemy.core.script;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aialchemy.core.validation.Issue;

/**
 * The cheap half of script review: a banned token scan run before anything is compiled or executed
 * (design document section 7.4, step 6).
 *
 * <p>The scan is deliberately conservative - a token that merely appears inside a comment or string
 * literal is enough to reject the script. Retries are cheap and the prompt tells the model exactly
 * which names are unavailable, so false positives cost little while silent misses would be
 * dangerous.</p>
 */
public final class StaticScriptCheck {

	private StaticScriptCheck() {
	}

	/** Globals and library names a script may never mention. */
	private static final List<String> BANNED_TOKENS = List.of(
			"luajava",
			"loadstring",
			"dofile",
			"loadfile",
			"collectgarbage",
			"coroutine",
			"newproxy",
			"package",
			"getmetatable",
			"setmetatable",
			"rawset",
			"rawget",
			"debug",
			"require",
			"io.",
			"os.",
			"java.",
			"javax.",
			"sun.",
			"jdk.",
			"ProcessBuilder",
			"Runtime",
			"Class.forName",
			"_G");

	private static final Pattern HOOK_DEFINITION = Pattern.compile(
			"(?:function\\s+(on_[a-z]+)\\s*\\(|\\b(on_[a-z]+)\\s*=\\s*function\\s*\\()");

	public static void check(String source, Issue.Collector issues) {
		if (source == null) {
			issues.error("script.empty", "no script supplied");
			return;
		}

		String withoutComments = stripComments(source);

		for (String token : BANNED_TOKENS) {
			if (withoutComments.contains(token)) {
				issues.error("script.banned", "script mentions the unavailable name '" + token + "'");
			}
		}

		// A script that never defines a hook would sit in the store doing nothing.
		if (hooksIn(source).isEmpty()) {
			issues.warning("script.nohook", "script does not obviously define a supported hook");
		}
	}

	/** Hook names discovered textually; the sandbox confirms them by actually running the chunk. */
	public static Set<String> hooksIn(String source) {
		Set<String> hooks = new LinkedHashSet<>();
		Matcher matcher = HOOK_DEFINITION.matcher(source);

		while (matcher.find()) {
			String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

			if (name != null && ScriptHook.byLuaName(name).isPresent()) {
				hooks.add(name);
			}
		}

		return hooks;
	}

	/** Removes {@code --} line comments and long comments so token hits are easier to read. */
	private static String stripComments(String source) {
		StringBuilder builder = new StringBuilder(source.length());
		int index = 0;

		while (index < source.length()) {
			char character = source.charAt(index);

			if (character == '-' && index + 1 < source.length() && source.charAt(index + 1) == '-') {
				while (index < source.length() && source.charAt(index) != '\n') {
					index++;
				}

				continue;
			}

			builder.append(character);
			index++;
		}

		return builder.toString();
	}
}