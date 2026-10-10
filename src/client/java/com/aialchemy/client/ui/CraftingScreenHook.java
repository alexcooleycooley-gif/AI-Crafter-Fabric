package com.aialchemy.client.ui;

import com.aialchemy.AiAlchemy;
import com.aialchemy.network.AlchemyPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Makes the vanilla crafting result slot itself the transmute interaction. */
public final class CraftingScreenHook {

	private CraftingScreenHook() {
	}

	private static boolean pending;

	public static void register() {
		// The actual click is intercepted in AbstractContainerScreenMixin before vanilla handles the
		// slot. This lifecycle hook only exists so the client initializer remains explicit and future
		// screen-specific status work has one registration point.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen || screen instanceof CraftingScreen) {
				AiAlchemy.LOGGER.debug("AI Alchemy result-slot control active on {}", screen.getClass().getSimpleName());
				ScreenEvents.remove(screen).register(removed -> {
					if (pending && client.player != null) {
						client.player.displayClientMessage(
								Component.translatable("status.ai_alchemy.left_table"), true);
					}
				});
			}
		});
	}

	/** Called by the client mixin before vanilla's result-slot click handling. */
	public static boolean handleResultSlotClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY,
			int button) {
		if (!(screen instanceof InventoryScreen) && !(screen instanceof CraftingScreen)) {
			return false;
		}

		// Vanilla's 2x2 and 3x3 crafting menus both place the output slot at leftPos+124, topPos+35.
		// Use a one-slot hitbox, not a floating widget, so normal inventory behaviour is untouched.
		int resultX = screen.width / 2 + 36;
		int resultY = screen.height / 2 - 48;
		boolean inside = mouseX >= resultX && mouseX < resultX + 16
				&& mouseY >= resultY && mouseY < resultY + 16;

		if (!inside || button != 0) {
			return false;
		}

		transmute();
		return true;
	}

	private static void transmute() {
		if (pending || !ClientPlayNetworking.canSend(AlchemyPayloads.TransmuteRequest.TYPE)) {
			return;
		}

		pending = true;
		ClientPlayNetworking.send(new AlchemyPayloads.TransmuteRequest(false));
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("status.ai_alchemy.started"), true);
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
}
