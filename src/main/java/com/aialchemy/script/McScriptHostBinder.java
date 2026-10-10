package com.aialchemy.script;

import com.aialchemy.core.script.CallBudget;
import com.aialchemy.core.script.ScriptAbort;
import com.aialchemy.core.script.ScriptCallContext;
import com.aialchemy.core.script.ScriptHostBinder;
import com.aialchemy.core.script.ScriptLimits;
import com.aialchemy.core.validation.EffectAllowlist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * The only bridge between a sandboxed script and the world (design document section 8.4).
 *
 * <p>Every function here validates its arguments, checks the allowlists, enforces the effect range
 * around the triggering entity and charges the per call {@link CallBudget}. A script that asks for
 * something outside the envelope gets a {@link ScriptAbort}, never a partial effect.</p>
 */
public final class McScriptHostBinder implements ScriptHostBinder {

	private final HookContext context;
	private final ScriptLimits limits;

	public McScriptHostBinder(HookContext context, ScriptLimits limits) {
		this.context = context;
		this.limits = limits;
	}

	@Override
	public void bind(Globals environment, CallBudget budget, ScriptCallContext callContext) {
		LuaTable player = new LuaTable();
		LuaTable world = new LuaTable();
		LuaTable util = new LuaTable();

		bindPlayer(player, budget);
		bindWorld(world, budget);
		bindUtil(util, budget);

		ScriptHostBinder.attach(environment, player, world, util);
		environment.set("target", this.context.target() == null ? LuaValue.NIL : entityTable(this.context.target()));
		environment.set("pos", this.context.position() == null ? LuaValue.NIL
				: vec(this.context.position().getX() + 0.5D, this.context.position().getY(), this.context.position().getZ() + 0.5D));
		environment.set("damage_source", this.context.damageSource() == null ? LuaValue.NIL
				: LuaValue.valueOf(this.context.damageSource().getMsgId()));
		environment.set("damage_amount", LuaValue.valueOf(this.context.amount()));
		environment.set("held_item", LuaValue.valueOf(this.context.stack().getHoverName().getString()));
	}

	// player ---------------------------------------------------------------

	private void bindPlayer(LuaTable player, CallBudget budget) {
		ServerPlayer entity = this.context.player();

		player.set("pos", guarded(budget, args -> vec(entity.getX(), entity.getY(), entity.getZ())));
		player.set("look_direction", guarded(budget, args -> {
			Vec3 look = entity.getLookAngle();
			return vec(look.x, look.y, look.z);
		}));
		player.set("health", guarded(budget, args -> LuaValue.valueOf(entity.getHealth())));
		player.set("heal", guarded(budget, args -> {
			float amount = (float) Math.min(Math.max(args.checkdouble(1), 0.0D), 20.0D);
			entity.heal(amount);
			return LuaValue.NIL;
		}));
		player.set("damage", guarded(budget, args -> {
			float amount = (float) Math.min(Math.max(args.checkdouble(1), 0.0D), 10.0D);
			entity.hurtServer(this.context.level(), this.context.level().damageSources().magic(), amount);
			return LuaValue.NIL;
		}));
		player.set("add_effect", guarded(budget, args -> {
			String id = args.checkjstring(1);
			int ticks = Math.min(Math.max(args.checkint(2), 1), 20 * 30);
			int amplifier = Math.min(Math.max(args.checkint(3), 0), 4);

			if (!EffectAllowlist.EFFECTS.contains(id)) {
				throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
			}

			net.minecraft.core.Holder.Reference<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id)).orElse(null);

			if (effect == null) {
				throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
			}

			entity.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true));
			return LuaValue.NIL;
		}));
		player.set("knockback", guarded(budget, args -> {
			Vec3 direction = toVec3(args.arg(1)).normalize();
			double strength = Math.min(Math.max(args.checkdouble(2), 0.0D), 2.0D);
			entity.push(direction.x * strength, 0.3D * strength, direction.z * strength);
			entity.hurtMarked = true;
			return LuaValue.NIL;
		}));
		player.set("set_cooldown", guarded(budget, args -> {
			// The configured floor stops a script from turning an item into a machine gun.
			int ticks = Math.max(args.checkint(1), this.limits.cooldownFloorTicks());
			entity.getCooldowns().addCooldown(this.context.stack(), Math.min(ticks, 20 * 60));
			return LuaValue.NIL;
		}));
		player.set("send_message", guarded(budget, args -> {
			String text = args.checkjstring(1);

			if (text.length() > 200) {
				text = text.substring(0, 200);
			}

			entity.sendSystemMessage(Component.literal(text));
			return LuaValue.NIL;
		}));
		player.set("is_sneaking", guarded(budget, args -> LuaValue.valueOf(entity.isShiftKeyDown())));
		player.set("held_item_name", guarded(budget, args -> LuaValue.valueOf(this.context.stack().getHoverName().getString())));
		player.set("name", guarded(budget, args -> LuaValue.valueOf(entity.getGameProfile().name())));
	}

	// world ----------------------------------------------------------------

	private void bindWorld(LuaTable world, CallBudget budget) {
		ServerLevel level = this.context.level();

		world.set("tick", guarded(budget, args -> LuaValue.valueOf(level.getGameTime())));
		world.set("time_until_day", guarded(budget, args -> LuaValue.valueOf(level.getDayTime() % 24000L)));
		world.set("is_raining", guarded(budget, args -> LuaValue.valueOf(level.isRaining())));
		world.set("spawn_particles", guarded(budget, args -> {
			String id = args.checkjstring(1);
			Vec3 position = toVec3(args.arg(2));
			int count = Math.min(Math.max(args.checkint(3), 1), 64);

			if (!EffectAllowlist.PARTICLES.contains(id)) {
				throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
			}

			requireInRange(position);
			budget.consumeParticles(count);
			ParticleOptions options = particleOptions(id);
			level.sendParticles(options, position.x, position.y, position.z, count, 0.25D, 0.25D, 0.25D, 0.02D);
			return LuaValue.NIL;
		}));
		world.set("play_sound", guarded(budget, args -> {
			String id = args.checkjstring(1);
			Vec3 position = toVec3(args.arg(2));

			if (!EffectAllowlist.SOUNDS.contains(id)) {
				throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
			}

			requireInRange(position);
			SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(id)).orElse(null);

			if (event == null) {
				throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
			}

			level.playSound(null, position.x, position.y, position.z, event, SoundSource.PLAYERS, 0.8f, 1.0f);
			return LuaValue.NIL;
		}));
		world.set("spawn_projectile", guarded(budget, args -> {
			String id = args.checkjstring(1);
			Vec3 direction = toVec3(args.arg(2)).normalize();
			double speed = Math.min(Math.max(args.checkdouble(3), 0.1D), 3.0D);

			if (!EffectAllowlist.PROJECTILES.contains(id)) {
				throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
			}

			budget.consumeEntitySpawn();
			return spawnProjectile(id, direction, speed);
		}));
		world.set("entities_near", guarded(budget, args -> {
			Vec3 position = toVec3(args.arg(1));
			double radius = Math.min(Math.max(args.checkdouble(2), 0.5D), this.context.level() == null ? 8.0D : 16.0D);
			requireInRange(position);
			AABB box = new AABB(position, position).inflate(radius);
			LuaTable result = new LuaTable();
			int index = 1;

			for (Entity entity : level.getEntities(this.context.player(), box)) {
				if (entity instanceof LivingEntity living) {
					result.set(index++, entityTable(living));
				}
			}

			return result;
		}));
		world.set("block_at", guarded(budget, args -> {
			Vec3 position = toVec3(args.arg(1));
			requireInRange(position);
			BlockPos pos = BlockPos.containing(position.x, position.y, position.z);
			BlockState state = level.getBlockState(pos);
			return LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		}));
		world.set("light_level", guarded(budget, args -> {
			Vec3 position = toVec3(args.arg(1));
			requireInRange(position);
			return LuaValue.valueOf(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
					BlockPos.containing(position.x, position.y, position.z)));
		}));
		world.set("ignite", guarded(budget, args -> {
			Vec3 position = toVec3(args.arg(1));
			requireInRange(position);
			BlockPos pos = BlockPos.containing(position.x, position.y, position.z);

			if (level.getBlockState(pos).isAir()) {
				level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
			}

			return LuaValue.NIL;
		}));
	}

	// util -----------------------------------------------------------------

	private void bindUtil(LuaTable util, CallBudget budget) {
		util.set("log", guarded(budget, args -> {
			// A capped debug line: the only output a script can produce, and it goes to the server log
			// where an operator can see what a misbehaving script was trying to do.
			String text = args.checkjstring(1);

			if (text.length() > 200) {
				text = text.substring(0, 200);
			}

			com.aialchemy.component.AlchemyData data = this.context.data();
			com.aialchemy.AiAlchemy.LOGGER.debug("scripted item {} says: {}", data == null ? "?" : data.itemId(), text);
			return LuaValue.NIL;
		}));
		util.set("vec", guarded(budget, args -> vec(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3))));
		util.set("random", guarded(budget, args -> LuaValue.valueOf(this.context.player().getRandom().nextDouble())));
		util.set("clamp", guarded(budget, args -> {
			double value = args.checkdouble(1);
			double min = args.checkdouble(2);
			double max = args.checkdouble(3);
			return LuaValue.valueOf(Math.min(Math.max(value, min), max));
		}));
		util.set("distance", guarded(budget, args -> {
			Vec3 first = toVec3(args.arg(1));
			Vec3 second = toVec3(args.arg(2));
			return LuaValue.valueOf(first.distanceTo(second));
		}));
	}

	// helpers ---------------------------------------------------------------

	/** Wraps an exposed function so it charges the budget and converts Java failures to aborts. */
	private static LuaValue guarded(CallBudget budget, ExposedFunction function) {
		return new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				budget.consumeApiCall();

				try {
					return function.apply(args);
				} catch (LuaError error) {
					throw error;
				} catch (ScriptAbort abort) {
					throw new LuaError(abort);
				} catch (RuntimeException exception) {
					throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
				}
			}
		};
	}

	private interface ExposedFunction {
		LuaValue apply(Varargs args);
	}

	private void requireInRange(Vec3 position) {
		double range = 32.0D;
		Vec3 origin = this.context.player().position();

		if (origin.distanceTo(position) > range) {
			throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RANGE));
		}
	}

	private LuaValue spawnProjectile(String id, Vec3 direction, double speed) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).orElse(null);

		if (type == null) {
			throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
		}

		Entity created = type.create(this.context.level(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);

		if (!(created instanceof Projectile projectile)) {
			if (created != null) {
				created.discard();
			}

			throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
		}

		Vec3 origin = this.context.player().getEyePosition().add(direction.scale(0.6D));
		projectile.setOwner(this.context.player());
		projectile.setPos(origin.x, origin.y, origin.z);
		projectile.shoot(direction.x, direction.y, direction.z, (float) speed, 1.0f);
		this.context.level().addFreshEntity(projectile);
		return LuaValue.NIL;
	}

	private static ParticleOptions particleOptions(String id) {
		Holder<ParticleType<?>> type = BuiltInRegistries.PARTICLE_TYPE.get(Identifier.parse(id)).orElse(null);

		if (type != null && type.value() instanceof ParticleOptions options) {
			return options;
		}

		return ParticleTypes.ENCHANT;
	}

	private static Vec3 toVec3(LuaValue value) {
		if (value != null && value.istable()) {
			LuaTable table = value.checktable();
			return new Vec3(table.get("x").checkdouble(), table.get("y").checkdouble(), table.get("z").checkdouble());
		}

		throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
	}

	private LuaValue vec(double x, double y, double z) {
		LuaTable table = new LuaTable();
		table.set("x", LuaValue.valueOf(x));
		table.set("y", LuaValue.valueOf(y));
		table.set("z", LuaValue.valueOf(z));
		return table;
	}

	private LuaValue entityTable(LivingEntity living) {
		LuaTable table = new LuaTable();
		table.set("name", LuaValue.valueOf(living.getName().getString()));
		table.set("health", LuaValue.valueOf(living.getHealth()));
		table.set("type", LuaValue.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString()));
		table.set("pos", vec(living.getX(), living.getY(), living.getZ()));
		table.set("uuid", LuaValue.valueOf(living.getUUID().toString()));
		table.set("add_effect", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				String id = args.checkjstring(1);

				if (!EffectAllowlist.EFFECTS.contains(id)) {
					throw new LuaError(new ScriptAbort(ScriptAbort.Reason.RUNTIME));
				}

				net.minecraft.core.Holder.Reference<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id)).orElse(null);

				if (effect != null) {
					living.addEffect(new MobEffectInstance(effect, Math.min(Math.max(args.checkint(2), 1), 20 * 30),
							Math.min(Math.max(args.checkint(3), 0), 4), false, true));
				}

				return LuaValue.NIL;
			}
		});
		return table;
	}

}
