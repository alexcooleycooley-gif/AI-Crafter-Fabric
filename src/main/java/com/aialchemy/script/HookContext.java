package com.aialchemy.script;

import com.aialchemy.component.AlchemyData;
import com.aialchemy.item.AlchemyItem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a hook invocation carries besides the script itself. One small immutable object keeps
 * the runtime, the binder and the vanilla hook methods in agreement about where the event came
 * from (design document section 8.3).
 */
public record HookContext(
		ServerPlayer player,
		ServerLevel level,
		ItemStack stack,
		InteractionHand hand,
		LivingEntity target,
		BlockPos position,
		DamageSource damageSource,
		float amount) {

	public static HookContext use(ServerPlayer player, ItemStack stack, ServerLevel level, InteractionHand hand) {
		return new HookContext(player, level, stack, hand, null, null, null, 0f);
	}

	public static HookContext hit(ServerPlayer player, ItemStack stack, ServerLevel level, LivingEntity target) {
		return new HookContext(player, level, stack, InteractionHand.MAIN_HAND, target, null, null, 0f);
	}

	public static HookContext mine(ServerPlayer player, ItemStack stack, ServerLevel level, BlockPos position) {
		return new HookContext(player, level, stack, InteractionHand.MAIN_HAND, null, position, null, 0f);
	}

	public static HookContext damaged(ServerPlayer player, ItemStack stack, ServerLevel level, DamageSource source, float amount) {
		return new HookContext(player, level, stack, InteractionHand.MAIN_HAND, null, null, source, amount);
	}

	public static HookContext tick(ServerPlayer player, ItemStack stack, ServerLevel level) {
		return new HookContext(player, level, stack, InteractionHand.MAIN_HAND, null, null, null, 0f);
	}

	public AlchemyData data() {
		return AlchemyItem.dataOf(this.stack);
	}
}