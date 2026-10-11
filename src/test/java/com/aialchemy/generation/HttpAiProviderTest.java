package com.aialchemy.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HttpAiProviderTest {
	@Test
	void extractsContentFromACompletedChoice() {
		String response = """
				{"choices":[{"finish_reason":"stop","message":{"content":"{\\"name\\":\\"Test Item\\"}"}}]}
				""";

		assertEquals("{\"name\":\"Test Item\"}", HttpAiProvider.extractContent(response));
	}

	@Test
	void rejectsLengthTruncatedChoiceBeforeValidation() {
		String response = """
				{"choices":[{"finish_reason":"length","message":{"content":"partial JSON"}}]}
				""";

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> HttpAiProvider.extractContent(response));

		assertTrue(exception.getMessage().startsWith("provider truncated response"));
	}
}
