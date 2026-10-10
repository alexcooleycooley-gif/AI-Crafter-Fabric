package com.aialchemy.item;

import java.util.ArrayList;
import java.util.List;

import com.aialchemy.AiAlchemy;
import com.aialchemy.component.AlchemyData;
import com.aialchemy.component.ModComponents;
import com.aialchemy.core.GeneratedItem;
import com.aialchemy.core.ItemEffect;
import com.aialchemy.core.Rarity;
import com.aialchemy.core.Shell;
import com.aialchemy.core.StatSet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;

/**
 * Turns a validated {@link GeneratedItem} into a real {@link ItemStack} (design document
 * section 10).
 *
 * <p>Stats are applied as components rather than as a class per item: damage, attack speed, armour
 * and toughness become attribute modifiers, mining speed becomes a {@code tool} component, food
 * becomes {@code food}, and wearable shells get an {@code equippable} component.</p>
 */
public final class ItemFactory {

	private ItemFactory() {
	}

	public static ItemStack create(GeneratedItem item) {
		Item base = ModItems.forShell(item.shell());
		ItemStack stack = new ItemStack(base);
		String scriptId = item.script() == null ? "" : item.itemId();
		String modelId = item.model() == null ? "" : item.itemId();

		stack.set(ModComponents.ALCHEMY_DATA, new AlchemyData(item.itemId(), scriptId, item.itemId(), modelId,
				item.name(), item.tier(), item.discovererOrUnknown()));

		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				Component.literal(item.name()).withStyle(ChatFormatting.WHITE));
		stack.set(net.minecraft.core.component.DataComponents.RARITY, vanillaRarity(item.rarity()));
		stack.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(loreComponents(item)));

		applyDurability(stack, item);
		applyAttributes(stack, item);
		applyTool(stack, item);
		applyFood(stack, item);
		applyEquippable(stack, item);

		return stack;
	}

	private static List<Component> loreComponents(GeneratedItem item) {
		List<Component> lore = new ArrayList<>();

		for (String line : item.lore()) {
			lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}

		if (!item.effects().isEmpty()) {
			for (ItemEffect effect : item.effects()) {
				lore.add(Component.literal(effectName(effect) + " " + roman(effect.amplifier() + 1)
						+ " (" + (effect.ticks() / 20) + "s)").withStyle(ChatFormatting.BLUE));
			}
		}

		return lore;
	}

	private static String effectName(ItemEffect effect) {
		net.minecraft.world.effect.MobEffect found = BuiltInRegistries.MOB_EFFECT
				.getOptional(Identifier.parse(effect.id()))
				.orElse(null);
		return found == null ? effect.id() : Component.translatable(found.getDescriptionId()).getString();
	}

	private static String roman(int value) {
		return switch (value) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> "V";
		};
	}

	private static void applyDurability(ItemStack stack, GeneratedItem item) {
		if (!item.shell().damageable() || item.stats().durability() <= 0) {
			return;
		}

		stack.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, Math.max(1, item.stats().durability()));
	}

	private static void applyAttributes(ItemStack stack, GeneratedItem item) {
		StatSet stats = item.stats();
		Shell shell = item.shell();
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		boolean any = false;

		if (shell.usesAttackStats() && stats.damage() > 0f) {
			builder.add(Attributes.ATTACK_DAMAGE, modifier("base_damage", stats.damage(),
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
			any = true;
		}

		if (shell.usesAttackStats()) {
			// The player's base attack speed is 4.0; the item stores the difference.
			float delta = stats.attackSpeed() - 4.0f;

			if (Math.abs(delta) > 0.001f) {
				builder.add(Attributes.ATTACK_SPEED, modifier("base_attack_speed", delta,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
				any = true;
			}
		}

		if (shell.usesArmourStats() && stats.armor() > 0f) {
			builder.add(Attributes.ARMOR, modifier("base_armor", stats.armor(),
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.bySlot(slotFor(shell)));
			any = true;
		}

		if (shell.usesArmourStats() && stats.armorToughness() > 0f) {
			builder.add(Attributes.ARMOR_TOUGHNESS, modifier("base_armor_toughness", stats.armorToughness(),
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.bySlot(slotFor(shell)));
			any = true;
		}

		if (shell == Shell.TRINKET && stats.abilityPower() > 0f) {
			// Ability power is not a vanilla attribute; it rides along as a luck-flavoured bonus so
			// that trinkets still feel like they do something without scripts.
			builder.add(Attributes.LUCK, modifier("ability_power", stats.abilityPower(),
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
			any = true;
		}

		if (any) {
			stack.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
		}
	}

	private static AttributeModifier modifier(String name, double amount, AttributeModifier.Operation operation) {
		return new AttributeModifier(AiAlchemy.id(name), amount, operation);
	}

	private static void applyTool(ItemStack stack, GeneratedItem item) {
		if (item.shell() != Shell.TOOL && item.shell() != Shell.WEAPON) {
			return;
		}

		float speed = Math.max(1.0f, item.stats().miningSpeed());
		List<Tool.Rule> rules = new ArrayList<>();

		if (item.shell() == Shell.TOOL) {
			for (TagKey<Block> tag : List.of(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_AXE,
					BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE)) {
				net.minecraft.core.HolderSet.Named<Block> blocks = BuiltInRegistries.BLOCK.getOrThrow(tag);
				rules.add(Tool.Rule.minesAndDrops(blocks, speed));
			}
		} else {
			// Weapons still cut plants, which keeps them from feeling like dead weight.
			rules.add(Tool.Rule.minesAndDrops(BuiltInRegistries.BLOCK.getOrThrow(BlockTags.SWORD_EFFICIENT), speed));
		}

		stack.set(net.minecraft.core.component.DataComponents.TOOL, new Tool(rules, speed, 1, true));
	}

	private static void applyFood(ItemStack stack, GeneratedItem item) {
		if (item.shell() != Shell.FOOD) {
			return;
		}

		int nutrition = Math.max(1, item.stats().food());
		float saturation = Math.max(0.1f, item.stats().saturation());
		stack.set(net.minecraft.core.component.DataComponents.FOOD, new FoodProperties(nutrition, saturation, false));
	}

	private static void applyEquippable(ItemStack stack, GeneratedItem item) {
		if (!item.shell().wearable()) {
			return;
		}

		EquipmentSlot slot = slotFor(item.shell());

		if (slot != null) {
			stack.set(net.minecraft.core.component.DataComponents.EQUIPPABLE, Equippable.builder(slot).build());
		}
	}

	/** Armour shells map onto the four vanilla armour slots. */
	public static EquipmentSlot slotFor(Shell shell) {
		return switch (shell) {
			case ARMOR_HEAD -> EquipmentSlot.HEAD;
			case ARMOR_CHEST -> EquipmentSlot.CHEST;
			case ARMOR_LEGS -> EquipmentSlot.LEGS;
			case ARMOR_FEET -> EquipmentSlot.FEET;
			default -> null;
		};
	}

	private static net.minecraft.world.item.Rarity vanillaRarity(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> net.minecraft.world.item.Rarity.COMMON;
			case UNCOMMON -> net.minecraft.world.item.Rarity.UNCOMMON;
			case RARE -> net.minecraft.world.item.Rarity.RARE;
			case EPIC -> net.minecraft.world.item.Rarity.EPIC;
		};
	}
}
