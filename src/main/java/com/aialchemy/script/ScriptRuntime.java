package com.aialchemy.script;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.aialchemy.AiAlchemy;
import com.aialchemy.component.AlchemyData;
import com.aialchemy.config.AlchemyConfig;
import com.aialchemy.core.script.CompiledScript;
import com.aialchemy.core.script.ScriptAbort;
import com.aialchemy.core.script.ScriptCallContext;
import com.aialchemy.core.script.ScriptCallResult;
import com.aialchemy.core.script.ScriptHook;
import com.aialchemy.core.script.ScriptLimits;
import com.aialchemy.core.script.ScriptSandbox;
import com.aialchemy.storage.AlchemyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The Minecraft side of the script runtime (design document sections 8 and 17).
 *
 * <p>Responsibilities: compile each script once and cache it, dispatch hooks with a full budget,
 * count aborts and disable a script after the configured number of failures, and keep the global per
 * tick time budget so a hundred items with scripts cannot add up to a stalled server.</p>
 */
public final class ScriptRuntime {

	private final AlchemyStorage storage;
	private final AlchemyConfig config;
	private final ScriptLimits limits;

	private final Map<String, CompiledScript> compiled = new ConcurrentHashMap<>();
	private final Map<String, Integer> abortCounts = new ConcurrentHashMap<>();
	private final Set<String> disabled = ConcurrentHashMap.newKeySet();
	private final Map<String, Long> tickStamps = new ConcurrentHashMap<>();
	private final Map<String, ItemStack> lastEquipped = new ConcurrentHashMap<>();

	private final AtomicLong tickBudgetNanos = new AtomicLong();
	private long budgetWindowTick = -1L;

	public ScriptRuntime(AlchemyStorage storage, AlchemyConfig config) {
		this.storage = storage;
		this.config = config;
		this.limits = config.scriptLimits();
	}

	public ScriptLimits limits() {
		return this.limits;
	}

	public boolean isDisabled(String scriptId) {
		return this.disabled.contains(scriptId);
	}

	public Map<String, Integer> abortCounts() {
		return Map.copyOf(this.abortCounts);
	}

	public Set<String> disabledScripts() {
		return Set.copyOf(this.disabled);
	}

	public void enable(String scriptId) {
		this.disabled.remove(scriptId);
		this.abortCounts.remove(scriptId);
	}

	public void disable(String scriptId) {
		this.disabled.add(scriptId);
	}

	/** Runs a hook for the item in the context, if that item has a script and the budget allows. */
	public void run(ScriptHook hook, HookContext context) {
		ItemStack stack = context.stack();
		AlchemyData data = context.data();

		if (data == null || !data.hasScript() || isDisabled(data.scriptId())) {
			return;
		}

		CompiledScript script = this.compiled.get(data.scriptId());

		if (script == null) {
			script = compile(data.scriptId());

			if (script == null) {
				return;
			}
		}

		if (!script.defines(hook)) {
			return;
		}

		if (!claimTickBudget()) {
			return;
		}

		ScriptCallContext callContext = new ScriptCallContext(data.scriptId(), hook, data.name(), data.tier(),
				context.level().getGameTime(), context.level().getSeed());
		ScriptCallResult result = ScriptSandbox.run(script, hook, new McScriptHostBinder(context, this.limits),
				this.limits, callContext);

		if (!result.ok()) {
			recordAbort(data, hook, result);
		}
	}

	/**
	 * Called from the item's server side inventory tick. It drives {@code on_tick} (throttled and
	 * only for the held or worn stacks) and synthesises {@code on_equip} and {@code on_unequip} by
	 * diffing what each player had last tick.
	 */
	public void onInventoryTick(ServerPlayer player, ItemStack stack, ServerLevel level, EquipmentSlot slot) {
		AlchemyData data = com.aialchemy.item.AlchemyItem.dataOf(stack);

		if (data == null || !data.hasScript()) {
			return;
		}

		boolean held = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
		boolean worn = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS
				|| slot == EquipmentSlot.FEET;

		if (!held && !worn) {
			return;
		}

		String key = player.getUUID() + ":" + slot.name();
		ItemStack previous = this.lastEquipped.get(key);

		if (previous == null || !ItemStack.matches(previous, stack)) {
			if (previous != null && !previous.isEmpty()) {
				run(ScriptHook.ON_UNEQUIP, HookContext.tick(player, previous, level));
			}

			this.lastEquipped.put(key, stack.copy());
			run(ScriptHook.ON_EQUIP, HookContext.tick(player, stack, level));
		}

		long now = level.getGameTime();
		Long last = this.tickStamps.get(key);

		if (last != null && now - last < this.limits.tickThrottleTicks()) {
			return;
		}

		this.tickStamps.put(key, now);
		run(ScriptHook.ON_TICK, HookContext.tick(player, stack, level));
	}

	/** Also used by the damage hook from {@code ServerHooks}. */
	public void onDamaged(ServerPlayer player, ItemStack stack, ServerLevel level,
			net.minecraft.world.damagesource.DamageSource source, float amount) {
		run(ScriptHook.ON_DAMAGED, HookContext.damaged(player, stack, level, source, amount));
	}

	/** Compiles a stored script once and caches it. */
	private CompiledScript compile(String scriptId) {
		String source = this.storage.scriptText(scriptId).orElse(null);

		if (source == null) {
			return null;
		}

		ScriptSandbox.CompileResult result = ScriptSandbox.compile(scriptId, source, this.limits);

		if (!result.ok()) {
			AiAlchemy.LOGGER.warn("Stored script {} no longer compiles: {}", scriptId, result.error());
			this.disabled.add(scriptId);
			return null;
		}

		this.compiled.put(scriptId, result.script());
		return result.script();
	}

	private void recordAbort(AlchemyData data, ScriptHook hook, ScriptCallResult result) {
		ScriptAbort.Reason reason = result.reason();

		if (reason != null && reason.countsTowardDisable()) {
			int count = this.abortCounts.merge(data.scriptId(), 1, Integer::sum);
			AiAlchemy.LOGGER.warn("Script {} aborted on {} ({} of {}): {}", data.scriptId(), hook.luaName(), count,
					this.config.scriptAbortsBeforeDisable, result.describe());

			if (count >= this.config.scriptAbortsBeforeDisable) {
				this.disabled.add(data.scriptId());
				AiAlchemy.LOGGER.warn("Script {} disabled after {} aborts; the item keeps its stats",
						data.scriptId(), count);
			}
		} else {
			AiAlchemy.LOGGER.debug("Script {} reported {} on {}", data.scriptId(), result.describe(), hook.luaName());
		}
	}

	/**
	 * Global per tick script time guard. Scripts share one budget per tick, so the worst case per
	 * tick is bounded no matter how many scripted items are in play.
	 */
	private boolean claimTickBudget() {
		long tick = System.nanoTime() / 50_000_000L;

		if (tick != this.budgetWindowTick) {
			this.budgetWindowTick = tick;
			this.tickBudgetNanos.set(0L);
		}

		long used = this.tickBudgetNanos.addAndGet(this.limits.wallClockMillis() * 100_000L);
		return used <= this.config.scriptTickBudgetMicros * 1_000L + this.limits.wallClockMillis() * 100_000L;
	}

	/** Clears per player state when they disconnect, so the maps cannot grow without bound. */
	public void forget(java.util.UUID playerId) {
		String prefix = playerId.toString() + ":";
		this.tickStamps.keySet().removeIf(key -> key.startsWith(prefix));
		this.lastEquipped.keySet().removeIf(key -> key.startsWith(prefix));
	}

	public int cachedScriptCount() {
		return this.compiled.size();
	}

	/** Position helper used by the commands. */
	public static BlockPos blockPos(HookContext context) {
		return context.position();
	}

	/** Diagnostics. */
	public String describe() {
		return "scripts=" + this.compiled.size() + ", disabled=" + this.disabled.size()
				+ ", aborts=" + new AtomicInteger(this.abortCounts.values().stream().mapToInt(Integer::intValue).sum()).get();
	}
}
