package com.aialchemy.client.mixin;

import com.aialchemy.client.ui.CraftingScreenHook;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures the empty crafting result slot before vanilla tries to click a menu slot. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void aiAlchemy$transmuteResultSlot(MouseButtonEvent event, boolean doubleClick,
			CallbackInfoReturnable<Boolean> callback) {
		if (CraftingScreenHook.handleResultSlotClick((AbstractContainerScreen<?>) (Object) this,
				event.x(), event.y(), event.button())) {
			callback.setReturnValue(true);
		}
	}
}
