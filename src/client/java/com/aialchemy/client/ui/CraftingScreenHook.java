package com.aialchemy.client.ui;

import com.aialchemy.AiAlchemy;
import com.aialchemy.network.AlchemyPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds the Transmute button to the player's crafting grid and to crafting tables, and reports the
 * outcome (design document section 12).
 *
 * <p>Rather than drawing a bespoke panel, the button doubles as the status line: it reads
 * "Transmute", then "Working...", then the name of the item that came back. The detailed outcome also
 * goes to the action bar, which keeps this class free of rendering code that would be fragile across
 * game versions.</p>
 */
public final class CraftingScreenHook {

	private static final int BUTTON_WIDTH = 128;
	private static final int BUTTON_HEIGHT = 20;

	private CraftingScreenHook() {
	}

	private static Button lastButton;
	private static boolean pending;

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen) && !(screen instanceof CraftingScreen)) {
				return;
			}

			// Placed relative to the screen centre, so it lands beside both the 2x2 inventory grid and
			// the 3x3 crafting table without reaching into container screen internals.
				// The old placement was relative to the top edge and could produce a button floating at
				// y=2. Put it under the output slot instead: it is visible in both the 2x2 and 3x3 screens.
				int x = width / 2 + 22;
				int y = height / 2 + 20;
				Button button = Button.builder(Component.translatable("button.ai_alchemy.transmute"), widget -> transmute())
						.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
						.tooltip(Tooltip.create(Component.translatable("button.ai_alchemy.transmute.tooltip")))
						.build();
				lastButton = button;
				button.active = !pending;
			attach(screen, button);
		});
	}

	private static void attach(Screen screen, Button button) {
		try {
			// Fabric's screen API exposes the screen's widget list; Screen.addRenderableWidget itself
			// is protected, which is exactly what this API exists for.
			Screens.getButtons(screen).add(button);
		} catch (RuntimeException exception) {
			AiAlchemy.LOGGER.warn("Could not attach the transmute button", exception);
		}
	}

		private static void transmute() {
			if (pending || !ClientPlayNetworking.canSend(AlchemyPayloads.TransmuteRequest.TYPE)) {
				return;
			}

			pending = true;
			ClientPlayNetworking.send(new AlchemyPayloads.TransmuteRequest(false));
			setButtonLabel(Component.translatable("button.ai_alchemy.working"));
			if (lastButton != null) lastButton.active = false;
	}

	/** Handles the server's answer for the current screen. */
	public static void onResult(AlchemyPayloads.TransmuteResult payload) {
		Component message = describe(payload);
		Minecraft client = Minecraft.getInstance();

			if (client.player != null) {
				client.player.displayClientMessage(message, true);
			}

			pending = payload.status() == AlchemyPayloads.Status.PENDING;
			setButtonLabel(message);
			if (lastButton != null) lastButton.active = !pending;
	}

	private static Component describe(AlchemyPayloads.TransmuteResult payload) {
		return switch (payload.status()) {
			case SUCCESS, CACHE_HIT -> Component.translatable("status.ai_alchemy.success", payload.message());
			case OFFLINE -> Component.translatable("status.ai_alchemy.offline", payload.message());
				case PENDING, RATE_LIMITED -> Component.translatable(payload.message());
				case FAILED -> payload.detail() == null || payload.detail().isBlank()
						? Component.translatable(payload.message())
						: Component.translatable("status.ai_alchemy.failed_detail", payload.detail());
		};
	}

	private static void setButtonLabel(Component label) {
		if (lastButton != null) {
			lastButton.setMessage(label);
		}
	}

	/** Used by the screen tests. */
	public static Button button() {
		return lastButton;
	}
}
