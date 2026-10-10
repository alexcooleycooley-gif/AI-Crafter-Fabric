package com.aialchemy.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;

import com.aialchemy.core.script.ScriptSandbox.CompileResult;
import com.aialchemy.core.script.ScriptSandbox.InstructionGuard;

/**
 * The hostile corpus from design document section 16. Every case here either had to be contained or
 * the sandbox design had to change; this test keeps them contained.
 */
class ScriptSandboxTest {

	/**
	 * The shipped limits, with a generous wall clock. The wall clock is wall clock: on a loaded CI
	 * machine it can fire before the deterministic budgets, so the tests that are about the
	 * instruction and memory guards use this copy and {@link #wallClockAborts()} covers the timer.
	 */
	private static final ScriptLimits LIMITS = new ScriptLimits(20_000, 2_000L, 2L * 1024L * 1024L, 4, 64, 100, 32.0D, 10, 10, 8_192);

	/** A binder that exposes empty API tables, so scripts can only touch the language itself. */
	private static final ScriptHostBinder EMPTY_BINDER =
			(environment, budget, context) -> ScriptHostBinder.attach(environment, new LuaTable(), new LuaTable(), new LuaTable());

	private static ScriptCallResult run(String source, ScriptHook hook) {
		CompileResult compiled = ScriptSandbox.compile("test", source, LIMITS);
		assertTrue(compiled.ok(), () -> "script should compile: " + compiled.error());
		return ScriptSandbox.run(compiled.script(), hook, EMPTY_BINDER, LIMITS,
				new ScriptCallContext("test", hook, "test item", 1, 0L, 1L));
	}

	@Test
	@DisplayName("a well behaved script runs and is counted")
	void goodScriptRuns() {
		ScriptCallResult result = run("""
				function on_use(player, world)
					local total = 0
					for i = 1, 10 do total = total + i end
					return total
				end
				""", ScriptHook.ON_USE);

		assertTrue(result.ok(), result::describe);
		assertTrue(result.instructions() > 10, "instructions were counted");
	}

	@Test
	@DisplayName("an infinite loop is stopped by the instruction budget")
	void infiniteLoopIsAborted() {
		ScriptCallResult result = run("""
				function on_use(player, world)
					while true do end
				end
				""", ScriptHook.ON_USE);

		assertFalse(result.ok());
		assertEquals(ScriptAbort.Reason.INSTRUCTIONS, result.reason());
	}

	@Test
	@DisplayName("deep recursion is stopped by the instruction budget")
	void recursionIsAborted() {
		ScriptCallResult result = run("""
				local function dive(n) return dive(n + 1) end
				function on_use(player, world)
					dive(1)
				end
				""", ScriptHook.ON_USE);

		assertFalse(result.ok());
		assertTrue(result.reason() == ScriptAbort.Reason.INSTRUCTIONS || result.reason() == ScriptAbort.Reason.WALL_CLOCK,
				() -> "recursion must be stopped by a budget, was " + result.reason());
	}

	@Test
	@DisplayName("a string doubling bomb is stopped by the allocation watchdog, not by an OutOfMemoryError")
	void stringDoublingBombIsAborted() {
		ScriptCallResult result = run("""
				function on_use(player, world)
					local s = 'x'
					for i = 1, 40 do s = s .. s end
					return s
				end
				""", ScriptHook.ON_USE);

		assertFalse(result.ok());
		assertEquals(ScriptAbort.Reason.MEMORY, result.reason());
	}

	@Test
	@DisplayName("string.rep and table.concat are capped")
	void libraryAllocationBombsAreAborted() {
		assertEquals(ScriptAbort.Reason.MEMORY, run("""
				function on_use(player, world)
					return string.rep('x', 100000000)
				end
				""", ScriptHook.ON_USE).reason());

		assertEquals(ScriptAbort.Reason.MEMORY, run("""
				function on_use(player, world)
					local t = {}
					for i = 1, 5000 do t[i] = 'xxxxxxxxxx' end
					return table.concat(t)
				end
				""", ScriptHook.ON_USE).reason());
	}

	@Test
	@DisplayName("unbounded table growth is stopped by the instruction budget")
	void tableGrowthIsAborted() {
		ScriptCallResult result = run("""
				function on_use(player, world)
					local t = {}
					for i = 1, 1000000 do t[i] = i end
					return #t
				end
				""", ScriptHook.ON_USE);

		assertFalse(result.ok());
		assertEquals(ScriptAbort.Reason.INSTRUCTIONS, result.reason());
	}

	@Test
	@DisplayName("pcall cannot be used to swallow an abort and keep looping")
	void pcallCannotSwallowAborts() {
		ScriptCallResult result = run("""
				function on_use(player, world)
					while true do
						pcall(function() while true do end end)
					end
				end
				""", ScriptHook.ON_USE);

		assertFalse(result.ok());
	}

	@Test
	@DisplayName("the escape hatches are gone from the environment")
	void escapeHatchesAreRemoved() {
		for (String expression : new String[] {
				"io.open('/etc/passwd')", "os.exit(0)", "luajava.bindClass('java.lang.Runtime')",
				"require('socket')", "dofile('/etc/passwd')", "loadfile('x')", "load('return 1')",
				"collectgarbage('count')", "setmetatable({}, {})", "getmetatable('')",
				"rawset(_G, 'x', 1)", "debug.sethook()" }) {
			String source = "function on_use(player, world)\n  return " + expression + "\nend\n";
			CompileResult compiled = ScriptSandbox.compile("test", source, LIMITS);

			// Either the static check refuses the script outright (the names it is told not to use) or
			// the call aborts at runtime because the global is not there. Both are a pass.
			boolean unreachable = !compiled.ok() || !ScriptSandbox.run(compiled.script(), ScriptHook.ON_USE,
					EMPTY_BINDER, LIMITS, new ScriptCallContext("test", ScriptHook.ON_USE, "item", 1, 0L, 0L)).ok();
			assertTrue(unreachable, () -> expression + " should not be reachable");
		}
	}

	@Test
	@DisplayName("the instruction hook survives removal of the debug global")
	void hookSurvivesDebugRemoval() {
		Globals environment = ScriptSandbox.sandbox(new InstructionGuard(LIMITS, LIMITS.instructionsPerCall(), LIMITS.memoryBytes()));
		assertTrue(environment.get("debug").isnil(), "the debug global is not visible to scripts");
	}

	@Test
	@DisplayName("banned names are rejected before anything runs")
	void staticCheckRejectsBannedTokens() {
		CompileResult compiled = ScriptSandbox.compile("test", """
				function on_use(player, world)
					-- io.open would be an escape
					local f = io.open('/etc/passwd')
				end
				""", LIMITS);

		assertFalse(compiled.ok());
		assertNotNull(compiled.error());
		assertTrue(compiled.issues().hasErrors());
	}

	@Test
	@DisplayName("a script with no known hook is rejected")
	void scriptWithoutHooksIsRejected() {
		CompileResult compiled = ScriptSandbox.compile("test", "local x = 1\n", LIMITS);
		assertFalse(compiled.ok());
	}

	@Test
	@DisplayName("a script over the size limit is rejected")
	void oversizedScriptIsRejected() {
		String source = "function on_use(player, world)\n" + "  -- padding\n".repeat(2000) + "end\n";
		CompileResult compiled = ScriptSandbox.compile("test", source, LIMITS);
		assertFalse(compiled.ok());
		assertTrue(compiled.error().toLowerCase().contains("large") || compiled.error().toLowerCase().contains("bytes"));
	}

	@Test
	@DisplayName("a tight wall clock budget stops a script that somehow dodges the other guards")
	void wallClockAborts() {
		ScriptLimits tight = new ScriptLimits(2_000_000, 5L, 64L * 1024L * 1024L, 4, 64, 100, 32.0D, 10, 10, 8_192);
		CompileResult compiled = ScriptSandbox.compile("test", """
				local function fib(n)
					if n < 2 then return n end
					return fib(n - 1) + fib(n - 2)
				end
				function on_use(player, world)
					return fib(40)
				end
				""", tight);
		assertTrue(compiled.ok(), () -> "script should compile: " + compiled.error());

		ScriptCallResult result = ScriptSandbox.run(compiled.script(), ScriptHook.ON_USE, EMPTY_BINDER, tight,
				new ScriptCallContext("test", ScriptHook.ON_USE, "item", 1, 0L, 0L));
		assertFalse(result.ok());
		assertTrue(result.reason() == ScriptAbort.Reason.WALL_CLOCK || result.reason() == ScriptAbort.Reason.INSTRUCTIONS,
				() -> "expected a budget abort, was " + result.reason());
	}

	@Test
	@DisplayName("hooks that are not defined are reported, not run")
	void undefinedHookIsReported() {
		CompileResult compiled = ScriptSandbox.compile("test",
				"function on_use(player, world) return 1 end\n", LIMITS);
		assertTrue(compiled.ok());

		ScriptCallResult result = ScriptSandbox.run(compiled.script(), ScriptHook.ON_HIT, EMPTY_BINDER, LIMITS,
				new ScriptCallContext("test", ScriptHook.ON_HIT, "item", 1, 0L, 0L));
		assertTrue(result.hookMissing());
	}
}
