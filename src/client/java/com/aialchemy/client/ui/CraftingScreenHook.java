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
 * Makes the vanilla crafting result slot the transmute control. There is no extra button or panel:
 * the player clicks the empty output slot exactly where a normal recipe result would appear.
 */
public final class CraftingScreenHook {

	private static final int RESULT_X_OFFSET = 28;
	private static final int RESULT_Y_OFFSET = -58;
	private static final int RESULT_HITBOX = 32;

	private CraftingScreenHook() {
	}

	private static Button resultSlot;
	private static boolean pending;

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen) && !(screen instanceof CraftingScreen)) {
				return;
			}

			// In both vanilla 2x2 and 3x3 crafting screens, the result slot is 36 pixels right and
			// 48 pixels above the screen centre. The larger invisible hitbox makes it easy to click.
			int x = width / 2 + RESULT_X_OFFSET;
			int y = height / 2 + RESULT_Y_OFFSET;
			Button widget = Button.builder(Component.empty(), ignored -> transmute())
					.bounds(x, y, RESULT_HITBOX, RESULT_HITBOX)
					.tooltip(Tooltip.create(Component.translatable("button.ai_alchemy.result_slot.tooltip")))
					.build();
			widget.setAlpha(0.0F);
			widget.active = !pending;
			resultSlot = widget;
			attach(screen, widget);
		});
	}

	private static void attach(Screen screen, Button widget) {
		try {
			Screens.getButtons(screen).add(widget);
		} catch (RuntimeException exception) {
			AiAlchemy.LOGGER.warn("Could not attach the result-slot transmute control", exception);
		}
	}

	private static void transmute() {
		if (pending || !ClientPlayNetworking.canSend(AlchemyPayloads.TransmuteRequest.TYPE)) {
			return;
		}

		pending = true;
		ClientPlayNetworking.send(new AlchemyPayloads.TransmuteRequest(false));
		if (resultSlot != null) {
			resultSlot.active = false;
		}
	}

	/** Handles the server's answer for the current screen. */
	public static void onResult(AlchemyPayloads.TransmuteResult payload) {
		Component message = describe(payload);
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			client.player.displayClientMessage(message, true);
		}

		pending = payload.status() == AlchemyPayloads.Status.PENDING;
		if (resultSlot != null) {
			resultSlot.active = !pending;
		}
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

	/** Kept for screen tests and compatibility with callers that inspect the control. */
	public static Button button() {
		return resultSlot;
	}
}
