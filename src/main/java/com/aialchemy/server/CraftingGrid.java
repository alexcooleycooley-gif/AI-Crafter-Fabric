package com.aialchemy.server;

import java.util.ArrayList;
import java.util.List;

import com.aialchemy.component.ModComponents;
import com.aialchemy.core.Ingredient;
import com.aialchemy.core.Rarity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

/**
 * Reads the crafting grid straight out of the player's open menu (design document section 13).
 *
 * <p>The server never trusts the client's idea of what is in the grid: the transmute packet carries
 * no item data at all, the grid is read here, from the menu the server itself is holding. This works
 * for both the player's 2x2 grid and a crafting table's 3x3 grid.</p>
 */
public record CraftingGrid(List<Ingredient> ingredients, List<ItemStack> stacks, int width, int height,
		boolean containsGeneratedItem, boolean empty, CraftingContainer container) {

	/** Reads the grid, or returns null when the player has no crafting menu open. */
	public static CraftingGrid read(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;

		if (menu == null) {
			return null;
		}

		List<ItemStack> inputs = new ArrayList<>();
		CraftingContainer container = null;
		int gridWidth = 2;
		int gridHeight = 2;

		for (Slot slot : menu.slots) {
			if (slot.container instanceof CraftingContainer crafting) {
				container = crafting;
				gridWidth = crafting.getWidth();
				gridHeight = crafting.getHeight();
				break;
			}
		}

		if (container == null) {
			return null;
		}

		for (int index = 0; index < container.getContainerSize() && index < gridWidth * gridHeight; index++) {
			inputs.add(container.getItem(index));
		}

		List<Ingredient> ingredients = new ArrayList<>();
		List<ItemStack> stacks = new ArrayList<>();
		boolean generated = false;
		boolean empty = true;
		long total = 0;

		for (ItemStack stack : inputs) {
			if (stack.isEmpty()) {
				continue;
			}

			empty = false;
			total += stack.getCount();

			if (stack.has(ModComponents.ALCHEMY_DATA)) {
				generated = true;
			}

			String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			Rarity rarity = rarityOf(stack);
			ingredients.add(new Ingredient(registryId, stack.getCount(), raritySignature(rarity, stack)));
			stacks.add(stack);
		}

		if (total > 512) {
			return new CraftingGrid(List.of(), List.of(), gridWidth, gridHeight, generated, true, container);
		}

		return new CraftingGrid(ingredients, stacks, gridWidth, gridHeight, generated, empty, container);
	}

	/** The {@link CraftingInput} view of the grid, used for the vanilla recipe lookup. */
	public CraftingInput toCraftingInput() {
		List<ItemStack> items = new ArrayList<>(this.stacks);
		return CraftingInput.of(this.width, this.height, items);
	}

	/**
	 * Generation signature: two stacks of the same item with different AI identities must not hash to
	 * the same recipe.
	 */
	private static String raritySignature(Rarity rarity, ItemStack stack) {
		String signature = stack.getItem().getDescriptionId() + "/" + rarity.jsonName();

		if (stack.has(ModComponents.ALCHEMY_DATA)) {
			signature += "/ai:" + stack.get(ModComponents.ALCHEMY_DATA).itemId();
		}

		return signature;
	}

	private static Rarity rarityOf(ItemStack stack) {
		net.minecraft.world.item.Rarity vanilla = stack.get(net.minecraft.core.component.DataComponents.RARITY);

		if (vanilla == null) {
			return Rarity.COMMON;
		}

		return switch (vanilla) {
			case COMMON -> Rarity.COMMON;
			case UNCOMMON -> Rarity.UNCOMMON;
			case RARE -> Rarity.RARE;
			case EPIC -> Rarity.EPIC;
		};
	}
}
