package com.aialchemy.server;

import com.aialchemy.AiAlchemy;
import com.aialchemy.component.AlchemyData;
import com.aialchemy.component.ModComponents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric event wiring for the hooks vanilla does not call on {@code Item} (design document
 * section 8.3): taking damage, joining, leaving and periodic persistence.
 */
public final class ServerHooks {

	private ServerHooks() {
	}

	private static int tickCounter;

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			WorldAlchemyState state = AiAlchemy.state();

			if (state != null) {
				state.transmute().sendJournal(handler.player);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WorldAlchemyState state = AiAlchemy.state();

			if (state != null) {
				state.scripts().forget(handler.player.getUUID());
				state.rateLimits().forget(handler.player.getUUID());
			}
		});

		// Autosave every five minutes, matching vanilla's autosave rhythm.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter % 6_000 == 0) {
				WorldAlchemyState state = AiAlchemy.state();

				if (state != null) {
					state.tick();
				}
			}
		});

		// on_damaged fires for the stack in the main hand first, then for worn armour pieces.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || damageTaken <= 0f) {
				return;
			}

			WorldAlchemyState state = AiAlchemy.state();

			if (state == null) {
				return;
			}

			dispatchDamage(state, player, player.getMainHandItem(), source, damageTaken);
			dispatchDamage(state, player, player.getItemBySlot(EquipmentSlot.HEAD), source, damageTaken);
			dispatchDamage(state, player, player.getItemBySlot(EquipmentSlot.CHEST), source, damageTaken);
			dispatchDamage(state, player, player.getItemBySlot(EquipmentSlot.LEGS), source, damageTaken);
			dispatchDamage(state, player, player.getItemBySlot(EquipmentSlot.FEET), source, damageTaken);
		});
	}

	private static void dispatchDamage(WorldAlchemyState state, ServerPlayer player, ItemStack stack,
			net.minecraft.world.damagesource.DamageSource source, float damage) {
		AlchemyData data = stack.get(ModComponents.ALCHEMY_DATA);

		if (data == null || !data.hasScript()) {
			return;
		}

		state.scripts().onDamaged(player, stack, player.level(), source, damage);
	}
}
