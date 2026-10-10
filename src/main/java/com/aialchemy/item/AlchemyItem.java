package com.aialchemy.item;

import java.util.List;
import java.util.function.Consumer;

import com.aialchemy.AiAlchemy;
import com.aialchemy.component.AlchemyData;
import com.aialchemy.component.ModComponents;
import com.aialchemy.core.Shell;
import com.aialchemy.core.script.ScriptHook;
import com.aialchemy.script.HookContext;
import com.aialchemy.server.WorldAlchemyState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shell item: one class for all eight shells.
 *
 * <p>Everything that makes an item unique lives in the {@link AlchemyData} component or in world
 * storage, so this class never needs to know what the AI invented. Its only job is to dispatch
 * vanilla hooks into the script runtime and to draw the tooltip.</p>
 */
public class AlchemyItem extends Item {

	private final Shell shell;

	public AlchemyItem(Properties properties, Shell shell) {
		super(properties);
		this.shell = shell;
	}

	public Shell shell() {
		return this.shell;
	}

	public static AlchemyData dataOf(ItemStack stack) {
		return stack.get(ModComponents.ALCHEMY_DATA);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			dispatch(ScriptHook.ON_USE, HookContext.use(serverPlayer, stack, serverLevel, hand));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (this.shell != Shell.BLOCK) return super.useOn(context);
		BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
		if (!context.getLevel().getBlockState(placePos).canBeReplaced()) return InteractionResult.FAIL;
		AlchemyData data = dataOf(context.getItemInHand());
		int choice = Math.floorMod(data == null ? 0 : data.itemId().hashCode(), 5);
		var block = switch (choice) {
			case 0 -> Blocks.SEA_LANTERN;
			case 1 -> Blocks.SLIME_BLOCK;
			case 2 -> Blocks.HONEY_BLOCK;
			case 3 -> Blocks.AMETHYST_BLOCK;
			default -> Blocks.OBSIDIAN;
		};
		if (!context.getLevel().setBlock(placePos, block.defaultBlockState(), 3)) return InteractionResult.FAIL;
		if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (attacker instanceof ServerPlayer serverPlayer && target.level() instanceof ServerLevel serverLevel) {
			dispatch(ScriptHook.ON_HIT, HookContext.hit(serverPlayer, stack, serverLevel, target));
		}
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
		if (level instanceof ServerLevel serverLevel && miner instanceof ServerPlayer serverPlayer) {
			dispatch(ScriptHook.ON_MINE, HookContext.mine(serverPlayer, stack, serverLevel, pos));
		}

		return true;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer serverPlayer) {
			dispatch(ScriptHook.ON_EAT, HookContext.use(serverPlayer, stack, serverLevel, InteractionHand.MAIN_HAND));
		}

		return super.finishUsingItem(stack, level, entity);
	}

	/**
	 * Server side inventory tick, which 1.21.11 provides on the server only. It powers {@code on_tick}
	 * and the equip and unequip transitions, throttled by the configured tick interval.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		WorldAlchemyState state = AiAlchemy.state();

		if (state == null) {
			return;
		}

		state.scripts().onInventoryTick(player, stack, level, slot);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		AlchemyData data = dataOf(stack);

		if (data == null) {
			tooltip.accept(Component.translatable("tooltip.ai_alchemy.unidentified").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}

		WorldAlchemyState state = AiAlchemy.state();
		com.aialchemy.core.GeneratedItem item = state == null ? null : state.storage().cachedByItemId(data.itemId());

		if (item != null) {
			for (String line : item.lore()) {
				tooltip.accept(Component.literal(line).withStyle(ChatFormatting.GRAY));
			}

			tooltip.accept(Component.empty());
				tooltip.accept(Component.translatable("tooltip.ai_alchemy.tier", data.tier()).withStyle(ChatFormatting.DARK_AQUA));
				if (this.shell == Shell.BLOCK) {
					tooltip.accept(Component.literal("Trait: " + blockTrait(data.itemId())).withStyle(ChatFormatting.AQUA));
				}

			if (data.hasScript()) {
				tooltip.accept(Component.translatable(state.scripts().isDisabled(data.scriptId())
						? "tooltip.ai_alchemy.script_disabled"
						: "tooltip.ai_alchemy.script_active").withStyle(ChatFormatting.DARK_PURPLE));
			}
		}

		tooltip.accept(Component.translatable("tooltip.ai_alchemy.discoverer", data.discovererOrUnknown())
				.withStyle(ChatFormatting.DARK_GRAY));
		}

		private static String blockTrait(String itemId) {
			return switch (Math.floorMod(itemId.hashCode(), 5)) {
				case 0 -> "luminous";
				case 1 -> "bouncy";
				case 2 -> "slippery";
				case 3 -> "resonant";
				default -> "fortified";
			};
		}

	private void dispatch(ScriptHook hook, HookContext context) {
		WorldAlchemyState state = AiAlchemy.state();

		if (state != null) {
			state.scripts().run(hook, context);
		}
	}

	/** Convenience used by the tick code and the commands. */
	public static List<String> shellNames() {
		return java.util.Arrays.stream(Shell.values()).map(Shell::itemPath).toList();
	}
}
