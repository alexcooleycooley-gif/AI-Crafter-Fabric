package com.aialchemy.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aialchemy.core.prompt.PromptBuilder;

class PromptBuilderTest {

	@Test
	void fullPromptExpandsLiteralPercentagesWithoutFormattingErrors() {
		ValidationContext context = new ValidationContext(1, 48, true, true,
				ValidationContext.FilterLevel.STRICT, 40, 6, 160, 4_096, 1.0D);
		PromptBuilder.Prompt prompt = PromptBuilder.fullPrompt(
				"- 3x minecraft:cobblestone (rarity common)\n", 1, context);

		assertTrue(prompt.system().contains("25% of the canvas"));
	}
}
